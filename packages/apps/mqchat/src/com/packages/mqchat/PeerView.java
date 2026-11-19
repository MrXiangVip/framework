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
    private String page="chats",selected="",notice="",chatOrigin="chats";private boolean confirmImport,closed,wasAttached;
    private final EmojiPicker emojiPicker;
    private final Panel panel;private PeerSession session;private final java.util.Timer lifecycle=new java.util.Timer("mqchat-view-lifecycle",true);
    private final Handler ui=new Handler(message->{if(!closed){invalidate();if(wasAttached&&!attached())close();}return true;});
    public PeerView(Context context,AttributeSet attrs){
        super(context,attrs);text=new ChatText(context.getClassLoader());
        friendCode=input("friendCode","输入好友 UUID 或 13 位展示码",80,false);nickname=input("nickname","昵称（1–24 字）",24,false);
        avatarPath=input("avatarPath","头像图片的完整文件路径（PNG / JPEG）",1024,false);
        backupPath=input("backupPath","SQLite 备份文件完整路径",1024,false);composer=input("composer","发消息…",2000,true);
        search=input("search","搜索昵称、展示码或 UUID",80,false);
        panel=new Panel(context);panel.setId("panel");addView(panel,new LayoutParams(-2,-2));
        emojiPicker=new EmojiPicker(context,text,composer);emojiPicker.setId("emojiPicker");addView(emojiPicker,new LayoutParams(-2,-2));
        button("network","连接",()->{if(session.connected())session.disconnect();else{session.disconnect();session.connect();}});
        button("chats","消息",()->show("chats"));
        button("emoji","☺",()->{emojiPicker.toggle();requestLayout();invalidate();});
        button("copyCode","我的展示码",this::copyCode);
        button("settings","设置",()->show("settings"));
        button("friends","通讯录",()->show("friends"));button("profile","我",()->show("profile"));button("backup","备份迁移",()->show("backup"));
        button("addFriend","添加好友",()->show("addFriend"));button("editProfile","编辑资料",()->{syncIdentity();show("editProfile");});
        button("add","发送好友申请",()->session.addFriend(friendCode.getText()));button("back","‹",()->show(page.equals("backup")||page.equals("editProfile")||page.equals("settings")?"profile":page.equals("chat")?chatOrigin:"friends"));
        button("send","发送",()->send());button("accept","同意申请",()->{session.accept(selected);if(isFriend(selected))show("chat");});
        button("decline","拒绝",()->{session.decline(selected);show("friends");});
        button("saveProfile","保存资料",this::saveProfile);button("clearAvatar","清除头像",()->{session.updateProfile(nickname.getText(),new byte[0]);notice="头像已清除";});
        button("export","导出备份",()->{session.exportBackup(Paths.get(backupPath.getText()));confirmImport=false;});
        button("import","导入备份",()->{if(!confirmImport){confirmImport=true;notice="再次点击“确认恢复”：当前身份、好友及聊天记录将被备份替换";}else{session.importBackup(Paths.get(backupPath.getText()));syncIdentity();drafts.clear();selected="";confirmImport=false;notice="";}});
        PeerSession created=null;try{Path home=Paths.get(System.getProperty("framework.mqchat.dataDir",Paths.get(System.getProperty("user.home"),".mqchat").toString()));created=new PeerSession(home.resolve("mqchat.sqlite"),()->ui.sendMessage(ui.obtainMessage(0,null)));}catch(Exception e){e.printStackTrace(System.err);notice=e instanceof ClassNotFoundException?"数据库初始化失败：应用安装包缺少依赖，请重新构建并更新 MQChat":"数据库初始化失败："+e.getClass().getSimpleName()+" · "+e.getMessage();}
        session=created;if(session!=null){syncIdentity();if(Boolean.parseBoolean(System.getProperty("framework.mqchat.autoConnect","true")))session.connect();}setInput(backupPath, Paths.get(System.getProperty("user.home"),"mqchat-backup.sqlite").toString());focus(search);
        search.setOnTextChangedListener((field,value)->{panel.offset=0;invalidate();});
        lifecycle.scheduleAtFixedRate(new TimerTask(){public void run(){ui.sendMessage(ui.obtainMessage(0,null));}},1000,1000);
    }
    public PeerSession getSession(){return session;}
    public String getPage(){return page;}
    public String getSelected(){return selected;}
    public String getComposer(){return composer.getText();}
    private void copyCode(){try{Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(session.identity().code()),null);notice="展示码已复制";}catch(Exception e){notice="我的展示码："+session.identity().code();}}
    private void syncIdentity(){setInput(nickname, session.identity().name);setInput(avatarPath, "");}
    private boolean attached(){View root=this;while(root.getParent() instanceof View)root=(View)root.getParent();return root.getParent()!=null;}
    private boolean isFriend(String uuid){try{Contact c=session.store.contact(uuid);return c!=null&&c.state.equals("accepted");}catch(Exception e){return false;}}
    private void show(String next){if(page.equals("chat"))drafts.put(selected,composer.getText());page=next;emojiPicker.hide();notice="";confirmImport=false;panel.offset=0;focus(next.equals("chat")?composer:(next.equals("friends")||next.equals("chats"))?search:next.equals("addFriend")?friendCode:next.equals("editProfile")?nickname:next.equals("backup")?backupPath:null);if(next.equals("chat"))setInput(composer, drafts.getOrDefault(selected,""));requestLayout();invalidate();}
    private void choose(Contact contact){if(page.equals("chats")||page.equals("friends"))chatOrigin=page;selected=contact.profile.uuid;show(contact.state.equals("accepted")?"chat":"request");}
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
        if(event.getType()==KeyEvent.DOWN){int key=event.getValue();if(key==KeyEvent.ENTER&&(event.getModifiers()&KeyEvent.SHIFT)==0){if(focused()==composer)send();else if(focused()==friendCode&&session!=null)session.addFriend(friendCode.getText());return true;}if(key==KeyEvent.ESCAPE){if(emojiPicker.isOpen()){emojiPicker.hide();requestLayout();invalidate();}else show(page.equals("chat")?chatOrigin:"chats");return true;}}
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
        boolean home=page.equals("chats")||page.equals("friends")||page.equals("profile");
        if(home){
            int third=width/3;place("chats",0,height-64,third,64);place("friends",third,height-64,third,64);place("profile",third*2,height-64,width-third*2,64);
            place("network",width-98,12,78,36);
            if(!page.equals("profile")){place("addFriend",width-72,65,52,40);place(search,16,116,width-32,40);place(panel,0,168,width,Math.max(0,height-252));}
            else{place("copyCode",0,274,width,56);place("backup",0,342,width,56);place("network",0,398,width,56);place("settings",0,466,width,56);place("editProfile",0,78,width,178);}
        }else{
            place("back",8,10,44,40);
            if(page.equals("chat")){
                int dock=emojiPicker.isOpen()?Math.min(320,Math.max(180,height/3)):0;
                int bar=height-dock-70;
                place(panel,0,96,width,Math.max(0,bar-96));
                place(composer,12,bar+10,Math.max(30,width-142),50);place("emoji",width-124,bar+12,46,46);place("send",width-72,bar+16,60,38);
                place(emojiPicker,0,height-dock,width,dock);
            }else if(page.equals("editProfile")){
                place(nickname,20,230,inner,44);place(avatarPath,20,316,inner,44);
                place("saveProfile",20,386,(inner-12)/2,44);place("clearAvatar",32+(inner-12)/2,386,(inner-12)/2,44);
            }else if(page.equals("backup")){
                place(backupPath,20,222,inner,44);place("export",20,286,inner,44);place("import",20,344,inner,44);
            }else if(page.equals("addFriend")){
                place(friendCode,20,206,inner,44);place("add",20,270,inner,44);
            }else if(page.equals("request")){
                place("accept",20,336,(inner-12)/2,44);place("decline",32+(inner-12)/2,336,(inner-12)/2,44);
            }else if(page.equals("settings")){place("network",0,146,width,56);place("backup",0,214,width,56);place("editProfile",0,282,width,56);}
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
                g.setColor(new Color(0xffdce9e3,true));g.fillRoundRect(0,0,size,size,10,10);
                if(profile.avatar.length>0){BufferedImage original=ImageIO.read(new ByteArrayInputStream(profile.avatar));
                    if(original!=null){g.setClip(new java.awt.geom.RoundRectangle2D.Double(0,0,size,size,10,10));g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);g.drawImage(original,0,0,size,size,null);}}
            }catch(IOException ignored){}finally{g.dispose();}images.put(key,image);
        }
        canvas.drawImage(image,x,y,size,size);
        if(profile.avatar.length==0){String initial=profile.name.isEmpty()?"M":new String(Character.toChars(profile.name.codePointAt(0)));int font=Math.max(14,size/2);label(canvas,initial,x+(size-(int)text.width(initial,font))/2,y+size/2+font/3,font,0xff07c160);}
    }
    private boolean personalPage(){return page.equals("profile")||page.equals("editProfile")||page.equals("backup");}
    private boolean homePage(){return page.equals("chats")||page.equals("friends")||page.equals("profile");}
    @Override protected void onDraw(Canvas canvas){
        wasAttached|=attached();int width=getWidth(),w=width-40;
        canvas.drawColor(0xffededed);canvas.fillRect(0,0,width,60,0xffededed);canvas.fillRect(0,59,width,1,0xffe5e5e5);
        String heading=page.equals("chats")?"消息":page.equals("friends")?"通讯录":page.equals("profile")?"我":page.equals("chat")?"聊天":page.equals("backup")?"聊天记录备份":page.equals("editProfile")?"个人信息":page.equals("settings")?"设置":page.equals("addFriend")?"添加朋友":"朋友申请";
        if(session==null){label(canvas,fit(notice,w,13),20,150,13,0xffa44444);return;}
        Identity self=session.identity();
        if(page.equals("chat"))try{Contact c=session.store.contact(selected);if(c!=null)heading=c.profile.name;}catch(Exception ignored){}
        label(canvas,fit(heading,Math.max(20,width-150),20),homePage()?20:62,39,20,0xff191919);
        if(page.equals("chats")||page.equals("friends")){
            label(canvas,page.equals("chats")?"MQChat":"朋友与新的申请",20,92,15,0xff999999);
        }else if(page.equals("profile")){
            canvas.fillRect(0,78,width,178,0xffffffff);avatar(canvas,self,24,110,66);
            label(canvas,fit(self.name,width-144,24),110,138,24,0xff191919);
            label(canvas,"MQ号："+self.code(),110,169,13,0xff999999);label(canvas,"›",width-28,154,25,0xffb2b2b2);
        }else if(page.equals("editProfile")){
            avatar(canvas,self,width-84,91,64);label(canvas,"昵称",20,215,13,0xff999999);label(canvas,"头像图片路径 · PNG / JPEG",20,301,13,0xff999999);
            label(canvas,"保存后自动裁剪头像，并同步资料给好友",20,466,12,0xff999999);
            label(canvas,"专属 UUID",20,505,12,0xff999999);label(canvas,fit(self.uuid,w,12),20,530,12,0xff576b95);
        }else if(page.equals("backup")){
            label(canvas,"换设备，保留原来的你",20,141,19,0xff191919);label(canvas,"SQLite 备份文件路径",20,203,13,0xff999999);
            String[] notes={"备份包含身份、资料、好友与聊天记录。","导入会替换本机数据，并断开连接。","备份包含身份密钥，请妥善保管。"};
            for(int i=0;i<notes.length;i++)label(canvas,notes[i],20,427+i*25,12,0xff999999);
        }else if(page.equals("addFriend")){
            label(canvas,"输入对方的展示码或完整 UUID",20,177,15,0xff191919);label(canvas,"我的展示码："+self.code(),20,366,14,0xff576b95);label(canvas,"对方同意申请后，即可开始私聊",20,398,12,0xff999999);
        }else if(page.equals("chat")){
            label(canvas,session.online(selected)?"在线 · 已加密私聊":"离线 · 消息等待送达",20,83,12,0xff999999);
            int dock=emojiPicker.isOpen()?Math.min(320,Math.max(180,getHeight()/3)):0;canvas.fillRect(0,getHeight()-dock-70,width,70,0xfff7f7f7);
        }else if(page.equals("settings")){label(canvas,"MQChat · 连接与数据管理",20,108,14,0xff999999);}
        else try{Contact c=session.store.contact(selected);if(c!=null){avatar(canvas,c.profile,20,100,60);label(canvas,fit(c.profile.name,w-90,21),100,132,21,0xff191919);label(canvas,"展示码："+c.profile.code(),20,215,15,0xff576b95);label(canvas,fit(c.profile.uuid,w,12),20,247,12,0xff999999);label(canvas,c.state.equals("incoming")?"请求添加你为朋友":c.state.equals("outgoing")?"等待对方通过申请":"好友申请已处理",20,300,15,0xff191919);if(!c.state.equals("incoming")){place("accept",0,0,0,0);place("decline",0,0,0,0);}}}catch(Exception e){notice=e.getMessage();}
        String status=notice.isEmpty()?session.status():notice;
        if(!page.equals("chat")){List<String> lines=wrap(status,w,11);int y=getHeight()-(homePage()?73:22);for(int i=0;i<Math.min(2,lines.size());i++)label(canvas,lines.get(i),20,y+i*14,11,notice.isEmpty()?0xff999999:0xffa36b45);}
        else if(!notice.isEmpty())label(canvas,fit(notice,w,11),20,94,11,0xffa36b45);
    }
    private final class Control extends View {
        final String label;Control(Context context,String label){super(context);this.label=label;}
        @Override protected void onDraw(Canvas canvas){
            String id=getId();boolean nav=id.equals("chats")||id.equals("friends")||id.equals("profile"),menu=id.equals("backup")||id.equals("settings")||id.equals("copyCode")||id.equals("network")&&getHeight()>40;
            boolean active=nav?page.equals(id):id.equals("send")||id.equals("accept")||id.equals("saveProfile")||id.equals("add");
            if(nav){
                canvas.fillRect(0,0,getWidth(),getHeight(),0xffffffff);canvas.fillRect(0,0,getWidth(),1,0xffdfe7e3);

                int color=active?0xff07c160:0xff999999;
                String key="nav:"+id+":"+color;BufferedImage icon=images.get(key);
                if(icon==null){icon=new BufferedImage(26,26,BufferedImage.TYPE_INT_ARGB);Graphics2D g=icon.createGraphics();
                    try{g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(new Color(color,true));g.setStroke(new BasicStroke(1.5f));
                        if(id.equals("chats")){g.drawRoundRect(2,3,23,18,6,6);g.drawLine(7,21,4,25);g.drawLine(4,25,13,21);for(int x=8;x<23;x+=6)g.fillOval(x,11,2,2);}
                        else if(id.equals("friends")){g.drawRoundRect(5,3,17,21,3,3);g.drawLine(2,9,7,9);g.drawLine(2,17,7,17);g.drawOval(11,7,5,5);g.drawArc(9,14,10,7,0,180);}
                        else{g.drawOval(9,2,9,9);g.draw(new java.awt.geom.Arc2D.Double(4,14,19,17,0,180,java.awt.geom.Arc2D.OPEN));g.drawLine(4,22,23,22);}
                    }finally{g.dispose();}images.put(key,icon);
                }
                canvas.drawImage(icon,getWidth()/2-13,9,26,26);
                label(canvas,label,(getWidth()-(int)text.width(label,13))/2,52,13,color);return;
            }
            if(id.equals("editProfile")&&page.equals("profile"))return;
            if(menu){canvas.fillRect(0,0,getWidth(),getHeight(),isPressed()?0xffedf4f0:0xffffffff);canvas.fillRect(20,getHeight()-1,getWidth()-20,1,0xffedf0ee);
                label(canvas,id.equals("network")?"连接管理":label,24,35,16,0xff191919);label(canvas,"›",getWidth()-32,35,22,0xff95a69d);return;}
            boolean plain=id.equals("network")||id.equals("back")||id.equals("addFriend")||id.equals("emoji");
            canvas.fillRect(0,0,getWidth(),getHeight(),isPressed()?0xffd9eae2:active?0xff07c160:plain?0xffededed:0xffe4eee9);
            if(id.equals("network"))canvas.strokeRect(0,0,getWidth(),getHeight(),0xffb5c9bf);
            String value=id.equals("addFriend")?"＋":id.equals("network")?(session!=null&&session.connected()?"断开":"连接"):id.equals("import")&&confirmImport?"确认恢复":label;
            label(canvas,value,Math.max(4,(getWidth()-(int)text.width(value,14))/2),getHeight()/2+5,14,active?0xffffffff:0xff07c160);
        }
    }

    private final class Panel extends View {
        int offset,dragY,total;boolean dragging,bottom=true;List<Contact> visibleContacts=Collections.emptyList();
        Panel(Context context){super(context);}
        private void scroll(int delta){offset=Math.max(0,Math.min(Math.max(0,total-getHeight()),offset+delta));bottom=offset>=total-getHeight();invalidate();}
        @Override public boolean dispatchTouchEvent(MotionEvent event){
            if(event.getAction()==MotionEvent.ACTION_SCROLL){scroll(event.getScrollDelta()*44);return true;}
            if(event.getAction()==MotionEvent.ACTION_DOWN){dragY=event.getY();dragging=false;}
            else if(event.getAction()==MotionEvent.ACTION_MOVE){int delta=dragY-event.getY();if(Math.abs(delta)>3)dragging=true;scroll(delta);dragY=event.getY();}
            else if(event.getAction()==MotionEvent.ACTION_UP&&!dragging&&(page.equals("friends")||page.equals("chats"))){int index=(event.getY()+offset)/80;if(index>=0&&index<visibleContacts.size())choose(visibleContacts.get(index));}
            return true;
        }
        @Override protected void onDraw(Canvas canvas){
            if(session==null||getWidth()<1||getHeight()<1)return;
            canvas.save();try{canvas.clipRect(0,0,getWidth(),getHeight());
                if(page.equals("friends")||page.equals("chats"))drawContacts(canvas);
                else if(page.equals("chat"))drawMessages(canvas);
            }catch(Exception e){notice="读取记录失败："+e.getMessage();}finally{canvas.restore();}
        }
        private void drawContacts(Canvas canvas)throws Exception{
            visibleContacts=new ArrayList<>();String query=search.getText().trim().toLowerCase(Locale.ROOT);Map<String,Message> latest=new HashMap<>();
            for(Contact c:session.store.contacts()){
                if((c.profile.name+c.profile.code()+c.profile.uuid).toLowerCase(Locale.ROOT).contains(query))visibleContacts.add(c);
                Message last=session.store.latestMessage(c.profile.uuid);if(last!=null)latest.put(c.profile.uuid,last);
            }
            if(page.equals("chats"))visibleContacts.sort((a,b)->Long.compare(latest.containsKey(b.profile.uuid)?latest.get(b.profile.uuid).time:0,latest.containsKey(a.profile.uuid)?latest.get(a.profile.uuid).time:0));
            total=visibleContacts.size()*80;offset=Math.min(offset,Math.max(0,total-getHeight()));
            if(visibleContacts.isEmpty()){label(canvas,query.isEmpty()?(page.equals("chats")?"还没有会话":"还没有朋友"):"没有找到相关联系人",24,65,18,0xff999999);label(canvas,query.isEmpty()?"点击右上角 ＋ 添加朋友，开始聊天":"试试昵称、展示码或完整 UUID",24,97,12,0xff999999);}
            for(int i=0;i<visibleContacts.size();i++){
                int y=i*80-offset;if(y+80<0||y>getHeight())continue;Contact c=visibleContacts.get(i);Message last=latest.get(c.profile.uuid);
                canvas.fillRect(0,y,getWidth(),80,0xffffffff);avatar(canvas,c.profile,16,y+14,50);
                label(canvas,fit(c.profile.name,Math.max(30,getWidth()-170),17),80,y+31,17,0xff191919);
                String detail=c.state.equals("incoming")?"请求添加你为朋友":c.state.equals("outgoing")?"等待对方通过申请":c.state.equals("declined")?"好友申请已拒绝":page.equals("chats")?(last==null?"点击开始聊天":(last.own?"我：":"")+last.body.replace('\n',' ')):(session.online(c.profile.uuid)?"在线":"离线")+" · "+c.profile.code();
                label(canvas,fit(detail,getWidth()-100,13),80,y+56,13,c.state.equals("incoming")?0xffe77737:0xff999999);
                if(page.equals("chats")&&last!=null){String time=formatTime(last.time);label(canvas,time,getWidth()-16-(int)text.width(time,11),y+28,11,0xff999999);}
                canvas.fillRect(80,y+79,getWidth()-80,1,0xffe5e5e5);
            }
        }
        private void drawMessages(Canvas canvas)throws Exception{
            List<Message> messages=session.store.messages(selected);Contact friend=session.store.contact(selected);List<List<String>> rows=new ArrayList<>();
            int maxWidth=Math.max(50,Math.min(360,getWidth()-136));total=18;
            for(Message m:messages){List<String> lines=wrap(m.body,maxWidth-24,16);rows.add(lines);total+=lines.size()*24+76;}
            if(bottom)offset=Math.max(0,total-getHeight());offset=Math.min(offset,Math.max(0,total-getHeight()));int y=12-offset;
            for(int i=0;i<messages.size();i++){
                Message m=messages.get(i);List<String> lines=rows.get(i);int h=lines.size()*24+16,bw=44;for(String line:lines)bw=Math.max(bw,(int)Math.ceil(text.width(line,16))+24);bw=Math.min(maxWidth,bw);
                int x=m.own?getWidth()-68-bw:68;
                if(y+h+64>=0&&y<getHeight()){
                    String time=formatTime(m.time);label(canvas,time,(getWidth()-(int)text.width(time,11))/2,y+12,11,0xff999999);
                    Profile profile=m.own?session.identity():friend==null?session.identity():friend.profile;
                    avatar(canvas,profile,m.own?getWidth()-56:12,y+25,40);
                    bubble(canvas,x,y+25,bw,h,m.own);
                    for(int j=0;j<lines.size();j++)label(canvas,lines.get(j),x+12,y+51+j*24,16,0xff191919);
                    if(m.own)label(canvas,m.status,x,y+h+41,10,0xff999999);
                }
                y+=h+60;
            }
            if(messages.isEmpty()){label(canvas,"现在可以开始聊天了",24,55,17,0xff999999);label(canvas,"消息加密传输，离线时自动排队",24,85,12,0xff999999);}
        }
    }
    private String formatTime(long time){java.util.Calendar a=java.util.Calendar.getInstance(),b=java.util.Calendar.getInstance();b.setTimeInMillis(time);boolean today=a.get(java.util.Calendar.YEAR)==b.get(java.util.Calendar.YEAR)&&a.get(java.util.Calendar.DAY_OF_YEAR)==b.get(java.util.Calendar.DAY_OF_YEAR);return new SimpleDateFormat(today?"HH:mm":"M月d日",Locale.getDefault()).format(new Date(time));}
    private void bubble(Canvas canvas,int x,int y,int width,int height,boolean own){
        String key="bubble:"+own+":"+width+":"+height;BufferedImage image=images.get(key);
        if(image==null){image=new BufferedImage(width+12,height,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();try{g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(new Color(own?0xff95ec69:0xffffffff,true));g.fillRoundRect(6,0,width,height,10,10);int edge=own?width+6:6;g.fillPolygon(new int[]{edge,edge+(own?6:-6),edge},new int[]{12,18,24},3);}finally{g.dispose();}images.put(key,image);}
        canvas.drawImage(image,x-6,y,width+12,height);
    }
    @Override public void close(){if(closed)return;closed=true;lifecycle.cancel();if(session!=null)session.close();}
}
