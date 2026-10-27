package com.packages.mqchat;

import com.android.content.Context;
import com.android.graphics.Canvas;
import com.android.os.Handler;
import com.android.util.AttributeSet;
import com.android.view.*;
import com.android.widget.EditText;
import java.awt.*;

import java.awt.image.BufferedImage;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/** Native framework chat UI; network work stays on the MQTT daemon. No connection on launch. */
public class ChatView extends ViewGroup {
    private static final int INK=0x1d3638,MUTED=0x708587,GREEN=0x138f78;
    private final ChatText typography;
    private final String identity=UUID.randomUUID().toString();
    private final List<ChatMessage> messages=new ArrayList<>();
    private final LinkedHashSet<String> seen=new LinkedHashSet<>();
    private final Map<String,Control> buttons=new LinkedHashMap<>();
    private final EditText nickname,topic,composer;
    private final History history;
    private String status="填入昵称和 Topic，连接后即可聊天",activeTopic="";
    private boolean connecting,ready;
    private long generation;
    private MqttClient client;
    private int inset=24;
    private java.util.Timer lifecycle;
    private final ArrayBlockingQueue<Runnable> events=new ArrayBlockingQueue<>(256);
    private final AtomicBoolean scheduled=new AtomicBoolean();
    private final Handler ui=new Handler(message->{
        if(message.obj instanceof Runnable){((Runnable)message.obj).run();invalidate();}else drain();return true;
    });
    public ChatView(Context context,AttributeSet attrs){
        super(context,attrs);typography=new ChatText(getContext().getClassLoader());
        nickname=input("nickname","你的昵称",24,false);
        setInput(nickname, "用户-"+identity.substring(0,4));
        topic=input("topic","双方填写完全相同的 Topic",512,false);
        setInput(topic, "mqchat/room/"+identity.substring(0,8));
        composer=input("composer","写一条消息…",2000,true);
        history=new History(context);history.setId("history");addView(history,new LayoutParams(-2,-2));
        button("connect","连接频道",()->{if(connecting)disconnect();else connect();});
        button("send","发送  ↗",this::send);
        button("up","↑ 较早",()->history.scroll(-160));
        button("down","最新 ↓",()->{history.scroll(Integer.MAX_VALUE/2);});
        focus(nickname);
    }
    private void post(Runnable action){
        if(events.offer(action)&&scheduled.compareAndSet(false,true))ui.sendMessage(ui.obtainMessage(0,null));
    }
    private void drain(){
        for(int i=0;i<64;i++){Runnable action=events.poll();if(action==null)break;action.run();}
        scheduled.set(false);
        if(!events.isEmpty()&&scheduled.compareAndSet(false,true))ui.sendMessage(ui.obtainMessage(0,null));
        invalidate();
    }
    private EditText input(String id, String hint, int limit, boolean multiline) {
        EditText field = new EditText(getContext()) {
            @Override protected int measureText(String value) { return (int) Math.ceil(typography.width(value, mTextSize)); }
            @Override protected void drawTextLine(Canvas canvas, String value, int x, int baseline, int color) {
                typography.draw(canvas, value, x, baseline, mTextSize, color);
            }
        };
        field.setId(id); field.setHint(hint); field.setSingleLine(!multiline); field.setMaxCodePoints(limit); field.setTextSize(14);

        addView(field, new LayoutParams(-2, -2)); return field;
    }
    private void button(String id,String label,Runnable action){Control button=new Control(getContext(),label,id.equals("connect")||id.equals("send"));button.setId(id);button.setOnClickListener(v->{action.run();invalidate();});buttons.put(id,button);addView(button,new LayoutParams(-2,-2));}
    private void connect(){
        String room=topic.getText().trim(),name=nickname.getText().trim();
        try{MqttClient.validateTopic(room);ChatMessage.validate(name,"validation");}
        catch(IllegalArgumentException invalid){status=invalid.getMessage();return;}
        setInput(nickname, name);setInput(topic, room);
        if(!activeTopic.equals(room)){messages.clear();seen.clear();history.reset();activeTopic=room;}
        connecting=true;nickname.setFocusable(false);topic.setFocusable(false);ready=false;status="正在连接 EMQX…";focus(composer);
        final long token=++generation;
        String host=System.getProperty("framework.mqchat.host","broker.emqx.io");
        boolean tls=Boolean.parseBoolean(System.getProperty("framework.mqchat.tls","true"));
        int port;
        try{port=Integer.parseInt(System.getProperty("framework.mqchat.port",tls?"8883":"1883"));
        client=new MqttClient(host,port,tls,room,new MqttClient.Listener(){
            private void current(Runnable action){ui.sendMessage(ui.obtainMessage(1,(Runnable)()->{if(generation==token)action.run();}));}
            public void state(boolean value,String detail){current(()->{ready=value;status=value?"已连接 · 双方使用相同 Topic 即可聊天":detail;});}
            public void message(byte[] payload){post(()->{if(generation==token)receive(payload);});}
            public void published(String id){current(()->mark(id,"已发布"));}
            public void failed(String id){current(()->mark(id,"未确认，可重新发送"));}
        });client.start();}
        catch(RuntimeException invalid){connecting=false;nickname.setFocusable(true);topic.setFocusable(true);ready=false;status="连接配置无效："+invalid.getMessage();client=null;}
    }
    public void disconnect(){
        ++generation;ready=false;connecting=false;nickname.setFocusable(true);topic.setFocusable(true);
        if(client!=null){client.close();client=null;}
        for(ChatMessage message:messages)if(message.own&&message.status.equals("发送中"))message.status="未确认，可重新发送";
        status="已断开 · 可修改昵称或 Topic 后重新连接";invalidate();
    }
    private void send(){
        if(!ready||client==null){status="请先连接频道，连接成功后再发送";return;}
        ChatMessage message;
        try{message=ChatMessage.outgoing(identity,nickname.getText(),composer.getText());}
        catch(IllegalArgumentException invalid){status=invalid.getMessage();return;}
        if(!client.publish(message.id,message.bytes())){status="发送队列已满或连接已断开，消息保留在输入框";return;}
        add(message);setInput(composer, "");focus(composer);history.bottom=true;
    }
    private void mark(String id,String value){for(ChatMessage m:messages)if(m.id.equals(id)&&m.own){m.status=value;break;}}
    private void receive(byte[] payload){ChatMessage message=ChatMessage.incoming(payload,identity);if(message==null||seen.contains(message.id))return;add(message);}
    private void add(ChatMessage message){
        if(!seen.add(message.id))return;
        while(seen.size()>1000){Iterator<String> iterator=seen.iterator();iterator.next();iterator.remove();}
        messages.add(message);if(messages.size()>300)messages.remove(0);
    }
    public boolean isConnected(){return ready;}
    public String getStatus(){return status;}
    public String getTopic(){return topic.getText();}
    public String getComposer(){return composer.getText();}
    public int getMessageCount(){return messages.size();}
    public String getMessageBody(int index){return messages.get(index).body;}
    public String getMessageStatus(int index){return messages.get(index).status;}
    public boolean isOwnMessage(int index){return messages.get(index).own;}
    private boolean attached(){View root=this;while(root.getParent() instanceof View)root=(View)root.getParent();return root.getParent()!=null;}
    private void watch(){
        if(lifecycle!=null||!attached())return;
        lifecycle=new java.util.Timer("mqchat-lifecycle",true);
        lifecycle.scheduleAtFixedRate(new TimerTask(){public void run(){post(()->{if(!attached()){disconnect();lifecycle.cancel();lifecycle=null;}});}},1000,1000);
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        // Keep the text field focused across input-method/candidate-window focus changes.
        return super.dispatchTouchEvent(event);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if (event.getType() == KeyEvent.DOWN && (event.getValue() == KeyEvent.TAB || event.getValue() == KeyEvent.ISO_LEFT_TAB))
            return super.dispatchKeyEvent(event);

        int key=event.getValue();
        if(event.getType()==KeyEvent.DOWN){

            if(key==KeyEvent.ESCAPE){focus(null);invalidate();return true;}
            if(key==0xff55||key==0xff56){history.scroll(key==0xff55?-160:160);return true;}
            if(key==KeyEvent.ENTER&&(event.getModifiers()&KeyEvent.SHIFT)==0){if(focused()==composer)send();else if(!connecting)connect();invalidate();return true;}
        }
        if(focused()!=null&&(!connecting||focused()==composer)&&focused().dispatchKeyEvent(event)){invalidate();return true;}
        return true;
    }
    private EditText focused() {
        View view = findFocus();
        return view instanceof EditText ? (EditText) view : null;
    }
    private void focus(EditText field) {
        clearFocus();
        if (field != null) { field.requestFocus(); field.setSelection(field.getText().length()); }
    }
    private static void setInput(EditText field, String value) {
        field.setText(value);
        field.setSelection(field.getText().length());
    }
    @Override protected void onMeasure(int w,int h){
        setMeasuredDimension(resolveSize(600,w),resolveSize(800,h));inset=Math.max(12,Math.min(24,getMeasuredWidth()/24));
        for(int i=0;i<getChildCount();i++)getChildAt(i).measure(MeasureSpec.makeMeasureSpec(getMeasuredWidth(),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(44,MeasureSpec.EXACTLY));
    }
    private void place(View view,int x,int y,int w,int h){view.layout(x,y,x+Math.max(0,w),y+Math.max(0,h));}
    private void place(String id,int x,int y,int w,int h){place(buttons.get(id),x,y,w,h);}
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        int w=getWidth()-inset*2,h=getHeight(),buttonWidth=Math.min(140,w/3);
        place(nickname,inset,115,w-buttonWidth-12,42);place("connect",getWidth()-inset-buttonWidth,115,buttonWidth,42);
        place(topic,inset,185,w,42);
        int composeY=h-127;
        place(history,inset,292,w,Math.max(24,composeY-340));
        place("up",inset,composeY-38,90,28);place("down",getWidth()-inset-90,composeY-38,90,28);
        place(composer,inset,composeY,w-94,86);place("send",getWidth()-inset-82,composeY,82,86);
    }
    @Override protected void onDraw(Canvas canvas){
        canvas.drawColor(0xfff3f8f7);
        canvas.fillRect(0,0,getWidth(),79,0xffffffff);
        canvas.fillRect(inset,21,40,37,0xff138f78);typography.draw(canvas,"MQ",inset+5,46,21,0xffffffff);
        typography.draw(canvas,"Chat",inset+51,46,28,0xff1d3638);
        typography.draw(canvas,ready?"● 在线":connecting?"● 连接中":"○ 离线",getWidth()-inset-85,44,14,ready?0xff138f78:0xff708587);
        typography.draw(canvas,"你的昵称",inset,103,12,0xff708587);typography.draw(canvas,"频道 Topic · 区分大小写",inset,176,12,0xff708587);
        typography.draw(canvas,fit(status,getWidth()-inset*2,12),inset,251,12,ready?0xff138f78:0xff708587);
        typography.draw(canvas,"公共频道，请勿发送隐私信息",inset,274,12,0xff9b7955);
        typography.draw(canvas,"Enter 发送 · Shift+Enter 换行",inset,getHeight()-18,11,0xff708587);
        typography.draw(canvas,"本次会话",getWidth()-inset-55,getHeight()-18,11,0xff708587);
        watch();
    }
    private String fit(String value,int width,int size){
        int end=value.length();
        while(end>0&&typography.width(value.substring(0,end)+(end<value.length()?"…":""),size)>width)end=value.offsetByCodePoints(end,-1);
        return value.substring(0,end)+(end<value.length()?"…":"");
    }
    private List<String> wrap(String value,int width,int size){
        List<String> lines=new ArrayList<>();StringBuilder line=new StringBuilder();double length=0;
        for(int offset=0;offset<value.length();){int code=value.codePointAt(offset);offset+=Character.charCount(code);
            if(code=='\n'){lines.add(line.toString());line.setLength(0);length=0;continue;}
            String glyph=new String(Character.toChars(code));double advance=typography.width(glyph,size);
            if(line.length()>0&&length+advance>Math.max(20,width)){lines.add(line.toString());line.setLength(0);length=0;}
            line.append(glyph);length+=advance;
        }
        lines.add(line.toString());return lines;
    }
    private void text(Graphics2D g,String value,int x,int y,int size,int color){typography.draw(g,value,x,y,size,color);}
    private final class Control extends View {
        final String label;final boolean primary;
        Control(Context context,String label,boolean primary){super(context);this.label=label;this.primary=primary;}
        @Override protected void onDraw(Canvas canvas){
            boolean enabled=!getId().equals("send")||ready;
            int color=primary?(enabled?0xff138f78:0xff9db8b2):0xffe4efec;
            if(isPressed())color=0xff0a715f;
            canvas.fillRect(0,0,getWidth(),getHeight(),color);
            String value=getId().equals("connect")&&connecting?"断开连接":label;
            int size=getWidth()<100?12:14;
            int x=Math.max(4,(getWidth()-(int)typography.width(value,size))/2);
            typography.draw(canvas,value,x,getHeight()/2+5,size,primary?0xffffffff:0xff138f78);
        }
    }

    private final class History extends View {
        int offset,total,dragY;boolean bottom=true,dragging;
        History(Context context){super(context);}
        void reset(){offset=0;total=0;bottom=true;}
        void scroll(int amount){offset=Math.max(0,Math.min(Math.max(0,total-getHeight()),offset+amount));bottom=offset>=Math.max(0,total-getHeight());invalidate();}
        @Override public boolean dispatchTouchEvent(MotionEvent event){
            if(event.getAction()==MotionEvent.ACTION_DOWN){dragging=true;dragY=event.getY();}
            else if(event.getAction()==MotionEvent.ACTION_MOVE&&dragging){scroll(dragY-event.getY());dragY=event.getY();}
            else if(event.getAction()==MotionEvent.ACTION_UP||event.getAction()==MotionEvent.ACTION_CANCEL)dragging=false;
            return true;
        }
        @Override protected void onDraw(Canvas canvas){
            if(getWidth()<=0||getHeight()<=0)return;
            BufferedImage image=new BufferedImage(getWidth(),getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();
            try{
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setColor(new Color(0xf3f8f7));g.fillRect(0,0,getWidth(),getHeight());
                if(messages.isEmpty()){
                    text(g,"同一频道，即刻相聊",Math.max(12,getWidth()/2-87),Math.max(36,getHeight()/2-10),18,INK);
                    text(g,"将上方 Topic 告诉对方，双方连接后开始聊天",Math.max(12,getWidth()/2-146),Math.max(65,getHeight()/2+19),12,MUTED);
                    text(g,"EMQX · broker.emqx.io · MQTT",Math.max(12,getWidth()/2-107),Math.max(90,getHeight()/2+47),12,GREEN);
                }else{
                    int width=Math.max(70,(int)(getWidth()*.78));List<List<String>> rows=new ArrayList<>();total=12;
                    for(ChatMessage m:messages){List<String> lines=wrap(m.body,width-28,14);rows.add(lines);total+=lines.size()*21+74;}
                    if(bottom)offset=Math.max(0,total-getHeight());offset=Math.min(offset,Math.max(0,total-getHeight()));
                    int y=12-offset;
                    for(int i=0;i<messages.size();i++){
                        ChatMessage m=messages.get(i);List<String> lines=rows.get(i);int height=lines.size()*21+24;
                        int x=m.own?getWidth()-width-8:8;
                        if(y+height+60>=0&&y<getHeight()){
                            String when=new SimpleDateFormat("HH:mm",Locale.ROOT).format(new Date(m.time));
                            text(g,fit((m.own?"我":m.name)+"  ·  "+when,width,11),x,y+12,11,MUTED);
                            g.setColor(new Color(m.own?0xd8f3e6:0xffffff));g.fillRoundRect(x,y+22,width,height,16,16);
                            for(int j=0;j<lines.size();j++)text(g,lines.get(j),x+14,y+46+j*21,14,INK);
                            if(m.own)text(g,m.status,x,y+height+40,10,GREEN);
                        }
                        y+=height+50;
                    }
                    if(total>getHeight()){int thumb=Math.max(18,getHeight()*getHeight()/total);int sy=offset*(getHeight()-thumb)/Math.max(1,total-getHeight());g.setColor(new Color(0xb3cbc3));g.fillRoundRect(getWidth()-3,sy,3,thumb,3,3);}
                }
            }finally{g.dispose();}
            canvas.drawImage(image,0,0,getWidth(),getHeight());
        }
    }
}