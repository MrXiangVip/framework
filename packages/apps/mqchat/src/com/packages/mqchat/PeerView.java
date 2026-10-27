package com.packages.mqchat;

import com.android.content.Context;
import com.android.graphics.Canvas;
import com.android.os.Handler;
import com.android.util.AttributeSet;
import com.android.view.*;
import com.android.widget.EditText;
import com.packages.mqchat.PeerProtocol.*;
import com.packages.mqchat.PeerStore.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;

/** Friend-oriented UI using the framework's XIM Unicode events and bundled CJK fonts. */
public final class PeerView extends ViewGroup implements AutoCloseable {
    private final ChatText text;
    private final Map<String,Control> buttons=new LinkedHashMap<>();
    private final Map<String,String> drafts=new HashMap<>();
    private final Map<String,BufferedImage> images=new LinkedHashMap<String,BufferedImage>(){protected boolean removeEldestEntry(Map.Entry<String,BufferedImage> e){return size()>128;}};
    private final EditText friendCode,nickname,avatarPath,backupPath,composer,search;
    private String page="friends",selected="",notice="";private boolean confirmImport,closed,wasAttached;
    private final Panel panel;private PeerSession session;private final java.util.Timer lifecycle=new java.util.Timer("mqchat-view-lifecycle",true);
    private final Handler ui=new Handler(message->{if(!closed){invalidate();if(wasAttached&&!attached())close();}return true;});
    public PeerView(Context context,AttributeSet attrs){
        super(context,attrs);text=new ChatText(context.getClassLoader());
        friendCode=input("friendCode","输入好友 UUID 或 13 位展示码",80,false);nickname=input("nickname","昵称（1–24 字）",24,false);
        avatarPath=input("avatarPath","头像图片的完整文件路径（PNG / JPEG）",1024,false);
        backupPath=input("backupPath","SQLite 备份文件完整路径",1024,false);composer=input("composer","写消息…",2000,true);
        search=input("search","搜索昵称、展示码或 UUID",80,false);
        panel=new Panel(context);panel.setId("panel");addView(panel,new LayoutParams(-2,-2));
        button("network","连接",()->{if(session.connected())session.disconnect();else{session.disconnect();session.connect();}});
        button("friends","通讯录",()->show("friends"));button("profile","我的",()->show("profile"));button("backup","备份迁移",()->show("backup"));
        button("addFriend","添加好友",()->show("addFriend"));button("editProfile","编辑资料",()->{syncIdentity();show("editProfile");});
        button("add","发送好友申请",()->session.addFriend(friendCode.getText()));button("back","‹ 返回",()->show(page.equals("backup")||page.equals("editProfile")?"profile":"friends"));
        button("send","发送",()->send());button("accept","同意申请",()->{session.accept(selected);if(isFriend(selected))show("chat");});
        button("decline","拒绝",()->{session.decline(selected);show("friends");});
        button("saveProfile","保存资料",this::saveProfile);button("clearAvatar","清除头像",()->{session.updateProfile(nickname.getText(),new byte[0]);notice="头像已清除";});
        button("export","导出备份",()->{session.exportBackup(Paths.get(backupPath.getText()));confirmImport=false;});
        button("import","导入备份",()->{if(!confirmImport){confirmImport=true;notice="再次点击“确认恢复”：当前身份、好友及聊天记录将被备份替换";}else{session.importBackup(Paths.get(backupPath.getText()));syncIdentity();drafts.clear();selected="";confirmImport=false;notice="";}});
        PeerSession created=null;try{Path home=Paths.get(System.getProperty("framework.mqchat.dataDir",Paths.get(System.getProperty("user.home"),".mqchat").toString()));created=new PeerSession(home.resolve("mqchat.sqlite"),()->ui.sendMessage(ui.obtainMessage(0,null)));}catch(Exception e){e.printStackTrace(System.err);notice=e instanceof ClassNotFoundException?"数据库初始化失败：应用安装包缺少依赖，请重新构建并更新 MQChat":"数据库初始化失败："+e.getClass().getSimpleName()+" · "+e.getMessage();}
        session=created;if(session!=null)syncIdentity();setInput(backupPath, Paths.get(System.getProperty("user.home"),"mqchat-backup.sqlite").toString());focus(search);
        lifecycle.scheduleAtFixedRate(new TimerTask(){public void run(){ui.sendMessage(ui.obtainMessage(0,null));}},1000,1000);
    }
    public PeerSession getSession(){return session;}
    public String getPage(){return page;}
    public String getSelected(){return selected;}
    public String getComposer(){return composer.getText();}
    private void syncIdentity(){setInput(nickname, session.identity().name);setInput(avatarPath, "");}
    private boolean attached(){View root=this;while(root.getParent() instanceof View)root=(View)root.getParent();return root.getParent()!=null;}
    private boolean isFriend(String uuid){try{Contact c=session.store.contact(uuid);return c!=null&&c.state.equals("accepted");}catch(Exception e){return false;}}
    private void show(String next){if(page.equals("chat"))drafts.put(selected,composer.getText());page=next;notice="";confirmImport=false;panel.offset=0;focus(next.equals("chat")?composer:next.equals("friends")?search:next.equals("addFriend")?friendCode:next.equals("editProfile")?nickname:next.equals("backup")?backupPath:null);if(next.equals("chat"))setInput(composer, drafts.getOrDefault(selected,""));requestLayout();invalidate();}
    private void choose(Contact contact){selected=contact.profile.uuid;show(contact.state.equals("accepted")?"chat":"request");}
    private void send(){if(session!=null&&session.send(selected,composer.getText())){setInput(composer, "");drafts.remove(selected);panel.bottom=true;}invalidate();}
    private void saveProfile(){try{byte[] avatar=session.identity().avatar;if(!avatarPath.getText().trim().isEmpty())avatar=readAvatar(Paths.get(avatarPath.getText().trim()));session.updateProfile(nickname.getText(),avatar);setInput(avatarPath, "");notice="";}catch(Exception e){notice="头像读取失败："+e.getMessage();}invalidate();}
    static byte[] readAvatar(Path path)throws Exception{
        if(!Files.isRegularFile(path)||Files.size(path)>8*1024*1024)throw new IOException("请选择小于 8 MB 的图片");
        BufferedImage image;try(ImageInputStream stream=ImageIO.createImageInputStream(path.toFile())){Iterator<ImageReader> readers=ImageIO.getImageReaders(stream);if(!readers.hasNext())throw new IOException("图片格式不支持");ImageReader reader=readers.next();try{reader.setInput(stream);if(reader.getWidth(0)>8192||reader.getHeight(0)>8192)throw new IOException("图片尺寸过大");ImageReadParam param=reader.getDefaultReadParam();int sample=Math.max(1,Math.max(reader.getWidth(0),reader.getHeight(0))/256);param.setSourceSubsampling(sample,sample,0,0);image=reader.read(0,param);}finally{reader.dispose();}}
        BufferedImage small=new BufferedImage(64,64,BufferedImage.TYPE_INT_RGB);Graphics2D g=small.createGraphics();try{g.setColor(Color.WHITE);g.fillRect(0,0,64,64);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);int side=Math.min(image.getWidth(),image.getHeight()),x=(image.getWidth()-side)/2,y=(image.getHeight()-side)/2;g.drawImage(image,0,0,64,64,x,y,x+side,y+side,null);}finally{g.dispose();}ByteArrayOutputStream bytes=new ByteArrayOutputStream();ImageIO.write(small,"jpg",bytes);if(bytes.size()>6000)throw new IOException("图片压缩后仍过大");return bytes.toByteArray();
    }
    private EditText input(String id, String hint, int limit, boolean multiline) {
        EditText field = new EditText(getContext()) {
            @Override protected int measureText(String value) { return (int) Math.ceil(text.width(value, mTextSize)); }
            @Override protected void drawTextLine(Canvas canvas, String value, int x, int baseline, int color) {
                text.draw(canvas, value, x, baseline, mTextSize, color);
            }
        };
        field.setId(id); field.setHint(hint); field.setSingleLine(!multiline); field.setMaxCodePoints(limit); field.setTextSize(14);
        addView(field, new LayoutParams(-2, -2)); return field;
    }
    private void button(String id,String label,Runnable action){Control control=new Control(getContext(),label);control.setId(id);control.setOnClickListener(v->{if(session!=null)try{action.run();}catch(Exception e){notice="操作失败："+e.getMessage();}requestLayout();invalidate();});buttons.put(id,control);addView(control,new LayoutParams(-2,-2));}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if (event.getType() == KeyEvent.DOWN && (event.getValue() == KeyEvent.TAB || event.getValue() == KeyEvent.ISO_LEFT_TAB))
            return super.dispatchKeyEvent(event);
        if(event.getType()==KeyEvent.DOWN){int key=event.getValue();if(key==KeyEvent.ENTER&&(event.getModifiers()&KeyEvent.SHIFT)==0){if(focused()==composer)send();else if(focused()==friendCode&&session!=null)session.addFriend(friendCode.getText());return true;}if(key==KeyEvent.ESCAPE){show("friends");return true;}}
        if(focused()!=null){focused().dispatchKeyEvent(event);if(focused()==search)panel.offset=0;confirmImport=false;invalidate();}return true;}
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
    @Override protected void onMeasure(int width,int height){setMeasuredDimension(resolveSize(600,width),resolveSize(800,height));for(int i=0;i<getChildCount();i++)getChildAt(i).measure(MeasureSpec.makeMeasureSpec(getMeasuredWidth(),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(44,MeasureSpec.EXACTLY));}
    private void place(View view,int x,int y,int width,int height){view.layout(x,y,x+Math.max(0,width),y+Math.max(0,height));}
    private void place(String id,int x,int y,int width,int height){place(buttons.get(id),x,y,width,height);}
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        int width=getWidth(),height=getHeight(),inner=width-40;
        for(int i=0;i<getChildCount();i++)place(getChildAt(i),0,0,0,0);
        place("network",width-100,17,80,34);
        place("friends",0,height-64,width/2,64);place("profile",width/2,height-64,width-width/2,64);
        if(page.equals("friends")){
            place("addFriend",width-128,78,108,36);place(search,20,136,inner,44);
            place(panel,0,218,width,Math.max(30,height-326));
        }else if(page.equals("profile")){
            place("backup",0,326,width,56);place("editProfile",0,382,width,56);
        }else{
            place("back",20,78,84,32);
            if(page.equals("chat")){
                place(panel,20,180,inner,Math.max(30,height-396));
                place(composer,20,height-205,inner-78,88);place("send",width-88,height-205,68,88);
            }else if(page.equals("editProfile")){
                place(nickname,20,252,inner,44);place(avatarPath,20,338,inner,44);
                place("saveProfile",20,408,(inner-12)/2,44);place("clearAvatar",32+(inner-12)/2,408,(inner-12)/2,44);
            }else if(page.equals("backup")){
                place(backupPath,20,240,inner,44);place("export",20,308,inner,44);place("import",20,366,inner,44);
            }else if(page.equals("addFriend")){
                place(friendCode,20,218,inner,44);place("add",20,282,inner,44);
            }else if(page.equals("request")){
                place("accept",20,358,(inner-12)/2,44);place("decline",32+(inner-12)/2,358,(inner-12)/2,44);
            }
        }
    }
    private void label(Canvas canvas,String value,int x,int y,int size,int color){text.draw(canvas,value,x,y,size,color);}
    private String fit(String value,int width,int size){int end=value.length();while(end>0&&text.width(value.substring(0,end)+(end<value.length()?"…":""),size)>width)end=value.offsetByCodePoints(end,-1);return value.substring(0,end)+(end<value.length()?"…":"");}
    private List<String> wrap(String value,int width,int size){List<String> result=new ArrayList<>();StringBuilder line=new StringBuilder();for(int i=0;i<value.length();){int code=value.codePointAt(i);i+=Character.charCount(code);String glyph=new String(Character.toChars(code));if(code=='\n'){result.add(line.toString());line.setLength(0);}else{if(line.length()>0&&text.width(line.toString()+glyph,size)>Math.max(20,width)){result.add(line.toString());line.setLength(0);}line.append(glyph);}}result.add(line.toString());return result;}
    private void avatar(Canvas canvas,Profile profile,int x,int y,int size){
        String key=profile.uuid+":"+Arrays.hashCode(profile.avatar)+":"+size;
        BufferedImage image=images.get(key);
        if(image==null){
            image=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
            try{g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(new Color(0xffdceee8,true));g.fillOval(0,0,size,size);
                if(profile.avatar.length>0){BufferedImage original=ImageIO.read(new ByteArrayInputStream(profile.avatar));
                    if(original!=null){g.setClip(new java.awt.geom.Ellipse2D.Double(0,0,size,size));g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);g.drawImage(original,0,0,size,size,null);}}
            }catch(IOException ignored){}finally{g.dispose();}images.put(key,image);
        }
        canvas.drawImage(image,x,y,size,size);
        if(profile.avatar.length==0){String initial=profile.name.isEmpty()?"M":new String(Character.toChars(profile.name.codePointAt(0)));int font=Math.max(14,size/2);label(canvas,initial,x+(size-(int)text.width(initial,font))/2,y+size/2+font/3,font,0xff138f78);}
    }
    private boolean personalPage(){return page.equals("profile")||page.equals("editProfile")||page.equals("backup");}
    @Override protected void onDraw(Canvas canvas){
        wasAttached|=attached();canvas.drawColor(0xfff5f7f6);canvas.fillRect(0,0,getWidth(),66,0xffffffff);
        label(canvas,"MQChat",20,44,25,0xff183d38);canvas.fillRect(0,65,getWidth(),1,0xffe4eae7);
        if(session==null){label(canvas,fit(notice,getWidth()-40,13),20,150,13,0xffa44444);return;}
        Identity self=session.identity();int w=getWidth()-40;
        if(page.equals("friends")){
            label(canvas,"通讯录",20,103,20,0xff183d38);label(canvas,"联系人与好友申请",20,206,12,0xff708587);
        }else if(page.equals("profile")){
            canvas.fillRect(0,66,getWidth(),238,0xffffffff);avatar(canvas,self,24,98,76);
            label(canvas,fit(self.name,getWidth()-144,23),120,130,23,0xff183d38);label(canvas,"我的资料",120,157,12,0xff708587);
            label(canvas,"用展示码找到想聊的人",24,217,16,0xff526d64);
            label(canvas,"我的展示码",24,252,12,0xff708587);label(canvas,self.code(),24,282,23,0xff138f78);
        }else if(page.equals("editProfile")){
            label(canvas,"编辑资料",20,151,23,0xff183d38);avatar(canvas,self,getWidth()-80,122,60);
            label(canvas,"昵称",20,237,13,0xff708587);label(canvas,"头像图片路径 · PNG / JPEG",20,323,13,0xff708587);
            label(canvas,"保存后自动裁剪头像，并同步资料给好友",20,481,12,0xff708587);
            label(canvas,"专属 UUID",20,523,12,0xff708587);label(canvas,fit(self.uuid,w,12),20,547,12,0xff526d64);
        }else if(page.equals("backup")){
            label(canvas,"备份迁移",20,151,23,0xff183d38);label(canvas,"换设备，保留原来的你",20,184,14,0xff526d64);
            label(canvas,"SQLite 备份文件路径",20,225,13,0xff708587);
            String[] notes={"备份包含身份、资料、好友与聊天记录。","导入会替换本机数据，并断开连接。","备份包含身份密钥，请妥善保管。"};
            for(int i=0;i<notes.length;i++)label(canvas,notes[i],20,448+i*25,12,0xff708587);
        }else if(page.equals("addFriend")){
            label(canvas,"添加好友",20,151,23,0xff183d38);label(canvas,"输入对方的展示码或完整 UUID",20,194,13,0xff708587);
            label(canvas,"我的展示码："+self.code(),20,374,15,0xff138f78);
            label(canvas,"对方同意申请后，即可开始私聊",20,407,12,0xff708587);
        }else{try{Contact contact=session.store.contact(selected);if(contact!=null){
            avatar(canvas,contact.profile,20,128,40);label(canvas,fit(contact.profile.name,w-55,19),72,146,19,0xff183d38);
            if(page.equals("chat")){label(canvas,session.online(selected)?"在线 · 已加密私聊":"离线 · 消息等待送达",72,166,11,0xff708587);}
            else{label(canvas,"展示码："+contact.profile.code(),20,227,15,0xff138f78);label(canvas,fit(contact.profile.uuid,w,12),20,259,12,0xff708587);
                label(canvas,contact.state.equals("incoming")?"对方请求添加你为好友":contact.state.equals("outgoing")?"已发出申请，等待对方同意":"该申请已处理",20,315,15,0xff183d38);
                if(!contact.state.equals("incoming")){place("accept",0,0,0,0);place("decline",0,0,0,0);}}
        }}catch(Exception e){notice=e.getMessage();}}
        String status=notice.isEmpty()?session.status():notice;List<String> lines=wrap(status,w,11);
        for(int i=0;i<Math.min(2,lines.size());i++)label(canvas,lines.get(i),20,getHeight()-92+i*16,11,notice.isEmpty()?0xff708587:0xffa36b45);
    }
    private final class Control extends View {
        final String label;Control(Context context,String label){super(context);this.label=label;}
        @Override protected void onDraw(Canvas canvas){
            String id=getId();boolean nav=id.equals("friends")||id.equals("profile"),menu=id.equals("backup")||id.equals("editProfile");
            boolean active=nav?(id.equals("profile")==personalPage()):id.equals("send")||id.equals("accept")||id.equals("saveProfile")||id.equals("add");
            if(nav){
                canvas.fillRect(0,0,getWidth(),getHeight(),0xffffffff);canvas.fillRect(0,0,getWidth(),1,0xffdfe7e3);
                if(active)canvas.fillRect(getWidth()/2-18,0,36,3,0xff138f78);
                int color=active?0xff138f78:0xff85958e;
                String key="nav:"+id+":"+color;BufferedImage icon=images.get(key);
                if(icon==null){icon=new BufferedImage(26,26,BufferedImage.TYPE_INT_ARGB);Graphics2D g=icon.createGraphics();
                    try{g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(new Color(color,true));g.setStroke(new BasicStroke(1.5f));
                        if(id.equals("friends")){g.drawRoundRect(5,3,17,21,3,3);g.drawLine(2,9,7,9);g.drawLine(2,17,7,17);g.drawOval(11,7,5,5);g.drawArc(9,14,10,7,0,180);}
                        else{g.drawOval(9,2,9,9);g.draw(new java.awt.geom.Arc2D.Double(4,14,19,17,0,180,java.awt.geom.Arc2D.OPEN));g.drawLine(4,22,23,22);}
                    }finally{g.dispose();}images.put(key,icon);
                }
                canvas.drawImage(icon,getWidth()/2-13,9,26,26);
                label(canvas,label,(getWidth()-(int)text.width(label,13))/2,52,13,color);return;
            }
            if(menu){canvas.fillRect(0,0,getWidth(),getHeight(),isPressed()?0xffedf4f0:0xffffffff);canvas.fillRect(20,getHeight()-1,getWidth()-20,1,0xffedf0ee);
                label(canvas,label,24,35,16,0xff183d38);label(canvas,"›",getWidth()-32,35,22,0xff95a69d);return;}
            boolean plain=id.equals("network")||id.equals("back")||id.equals("addFriend");
            canvas.fillRect(0,0,getWidth(),getHeight(),isPressed()?0xffd9eae2:active?0xff138f78:plain?0xfff5f7f6:0xffe4eee9);
            if(id.equals("network"))canvas.strokeRect(0,0,getWidth(),getHeight(),0xffb5c9bf);
            String value=id.equals("network")?(session!=null&&session.connected()?"断开":"连接"):id.equals("import")&&confirmImport?"确认恢复":label;
            label(canvas,value,Math.max(4,(getWidth()-(int)text.width(value,14))/2),getHeight()/2+5,14,active?0xffffffff:0xff138f78);
        }
    }

    private final class Panel extends View {
        int offset,dragY,total;boolean dragging,bottom=true;List<Contact> visibleContacts=Collections.emptyList();
        Panel(Context context){super(context);}
        @Override public boolean dispatchTouchEvent(MotionEvent event){if(event.getAction()==MotionEvent.ACTION_DOWN){dragY=event.getY();dragging=false;}else if(event.getAction()==MotionEvent.ACTION_MOVE){int delta=dragY-event.getY();if(Math.abs(delta)>3)dragging=true;offset=Math.max(0,Math.min(Math.max(0,total-getHeight()),offset+delta));dragY=event.getY();bottom=offset>=total-getHeight();invalidate();}else if(event.getAction()==MotionEvent.ACTION_UP&&!dragging&&page.equals("friends")){int index=(event.getY()+offset)/76;if(index>=0&&index<visibleContacts.size())choose(visibleContacts.get(index));}return true;}
        @Override protected void onDraw(Canvas canvas){if(session==null||getWidth()<1||getHeight()<1)return;canvas.save();try{canvas.clipRect(0,0,getWidth(),getHeight());if(page.equals("friends")){visibleContacts=new ArrayList<>();String query=search.getText().trim().toLowerCase(Locale.ROOT);
                for(Contact c:session.store.contacts())if(query.isEmpty()||c.profile.name.toLowerCase(Locale.ROOT).contains(query)||c.profile.code().toLowerCase(Locale.ROOT).contains(query)||c.profile.uuid.toLowerCase(Locale.ROOT).contains(query))visibleContacts.add(c);
                total=visibleContacts.size()*76;offset=Math.min(offset,Math.max(0,total-getHeight()));
                if(visibleContacts.isEmpty()){
                    label(canvas,query.isEmpty()?"用展示码，找到想聊的人":"没有找到联系人",24,58,18,0xff526d64);
                    label(canvas,query.isEmpty()?"点击右上方添加好友，开始新的对话":"试试昵称、展示码或完整 UUID",24,88,12,0xff85958e);
                }
                for(int i=0;i<visibleContacts.size();i++){
                    int y=i*76-offset;if(y+76<0||y>getHeight())continue;Contact contact=visibleContacts.get(i);
                    canvas.fillRect(0,y,getWidth(),76,0xffffffff);avatar(canvas,contact.profile,20,y+14,48);
                    label(canvas,fit(contact.profile.name,getWidth()-112,16),84,y+31,16,0xff183d38);
                    String state=contact.state.equals("accepted")?(session.online(contact.profile.uuid)?"在线":"离线"):contact.state.equals("incoming")?"新的好友申请 · 点击处理":contact.state.equals("outgoing")?"等待对方同意":"申请已拒绝";
                    label(canvas,state,84,y+55,12,contact.state.equals("incoming")?0xff138f78:0xff85958e);
                    label(canvas,"›",getWidth()-28,y+44,20,0xffa4b1aa);canvas.fillRect(84,y+75,getWidth()-84,1,0xffedf0ee);
                }}
            else if(page.equals("chat")){List<Message> messages=session.store.messages(selected);List<List<String>> rows=new ArrayList<>();int width=Math.max(90,(int)(getWidth()*.8));total=16;for(Message message:messages){List<String> lines=wrap(message.body,width-24,14);rows.add(lines);total+=lines.size()*21+65;}if(bottom)offset=Math.max(0,total-getHeight());offset=Math.min(offset,Math.max(0,total-getHeight()));int y=8-offset;for(int i=0;i<messages.size();i++){Message message=messages.get(i);List<String> lines=rows.get(i);int x=message.own?getWidth()-width:0,h=lines.size()*21+19;if(y+h+55>=0&&y<getHeight()){label(canvas,(message.own?"我":"对方")+" · "+new SimpleDateFormat("HH:mm",Locale.ROOT).format(new Date(message.time)),x,y+12,10,0xff708587);canvas.fillRect(x,y+22,width,h,message.own?0xffd8f3e6:0xffffffff);for(int j=0;j<lines.size();j++)label(canvas,lines.get(j),x+12,y+44+j*21,14,0xff183d38);if(message.own)label(canvas,message.status,x,y+h+36,10,0xff138f78);}y+=h+46;}if(messages.isEmpty()){label(canvas,"现在可以开始聊天了",12,45,18,0xff183d38);label(canvas,"双方订阅同一个私聊 Topic，消息仅对方可解密",12,75,11,0xff708587);}}
        }catch(Exception e){notice="读取记录失败："+e.getMessage();}finally{canvas.restore();}}
    }
    @Override public void close(){if(closed)return;closed=true;lifecycle.cancel();if(session!=null)session.close();}
}
