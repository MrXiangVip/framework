package com.packages.ebook;

import com.android.content.Context;
import com.android.graphics.Canvas;
import com.android.graphics.FontManager;
import com.android.view.View;
import com.android.view.KeyEvent;
import com.android.view.MotionEvent;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

/** A responsive, locally stored bookshelf and paginated sample reader. */
public final class EbookView extends View {
    private static final String[] TITLES = {"全网放榜日，做了谎言", "小可怜搬空家产去下乡", "绝世邪医", "无上杀神", "神级狂婿", "万古龙帝", "老公竟是千亿大佬", "废后向皇帝请辞", "我靠养崽洗白了", "天才小毒妃", "狂龙医婿"};
    private static final String[] TABS = {"书架", "书城", "免费专区", "我的"};
    private static final int RED = 0xc82831;
    private final BufferedImage[] covers = new BufferedImage[TITLES.length];
    private final List<Hit> hits = new ArrayList<>();
    private final Properties state = new Properties();
    private final File stateFile = new File(System.getProperty("user.home"), ".local/share/framework/ebook/reading.properties");
    private final List<String> pages = new ArrayList<>();
    private List<List<MarkdownReader.Line>> markdownPages;
    private final BookLibrary library = new BookLibrary(new File(stateFile.getParentFile(), "books"));
    private final List<BookLibrary.Book> imported = new ArrayList<>();
    private boolean choosing, importing;
    private volatile boolean closed;
    private Thread importWorker;
    private File folder = new File(System.getProperty("user.home"));
    private String filePath = folder.getAbsolutePath();
    private int filePage;
    private String fileNotice = "选择 UTF-8 TXT、MD 或 EPUB 文件";
    private int tab, book = -1, page, fontSize = 22, scroll, maxScroll;
    private boolean night, search;
    private String query = "", notice = "";
    private int pageWidth, pageHeight, pageFont;
    private float downX, downY;
    private double scale = 1;
    private int width, height;
    private static final class Hit {
        final Rectangle box; final Runnable action;
        Hit(int x,int y,int w,int h,Runnable a) { box=new Rectangle(x,y,w,h); action=a; }
    }
    public EbookView(Context context) {
        super(context); setFocusable(true);
        try (InputStream in = new FileInputStream(stateFile)) { state.load(in); } catch (IOException ignored) { }
        imported.addAll(library.load());
        fontSize = Math.max(16, Math.min(32, number("font",22)));
        night = Boolean.parseBoolean(state.getProperty("night","false"));
        for (int i=0;i<covers.length;i++) {
            try (InputStream in=context.getClassLoader().getResourceAsStream("res/drawable/cover"+i+".jpg")) {
                if(in!=null) covers[i]=ImageIO.read(in);
            } catch(IOException ignored) { }
        }
    }
    private int number(String key,int fallback) { try { return Integer.parseInt(state.getProperty(key)); } catch(Exception e) { return fallback; } }
    private int bookCount() { return TITLES.length + imported.size(); }
    private String title(int id) { return id < TITLES.length ? TITLES[id] : imported.get(id-TITLES.length).title; }
    private String bookKey(int id) { return id < TITLES.length ? ""+id : imported.get(id-TITLES.length).id; }
    public void close() { closed=true; if(importWorker!=null)importWorker.interrupt(); save(); }
    public void save() {
        if(book>=0) state.setProperty("page."+bookKey(book),Integer.toString(page));
        state.setProperty("font",Integer.toString(fontSize)); state.setProperty("night",Boolean.toString(night));
        try {
            java.nio.file.Files.createDirectories(stateFile.getParentFile().toPath());
            File temp = File.createTempFile("reading-", ".tmp", stateFile.getParentFile());
            try {
                try(OutputStream out=new FileOutputStream(temp)) { state.store(out,"Ebook reading progress"); }
                java.nio.file.Files.move(temp.toPath(), stateFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } finally { temp.delete(); }
        } catch(IOException e) { notice="阅读进度保存失败"; }
    }
    @Override protected void onMeasure(int w,int h) { setMeasuredDimension(resolveSize(600,w),resolveSize(880,h)); }
    @Override protected void onDraw(Canvas canvas) {
        if(getWidth()<1||getHeight()<1)return;
        scale=Math.max(0.35,Math.min(getWidth()/600.0,getHeight()/760.0));
        width=(int)(getWidth()/scale);height=(int)(getHeight()/scale);
        BufferedImage image=new BufferedImage(getWidth(),getHeight(),BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(scale,scale);hits.clear();
        g.setColor(new Color(book>=0?(night?0x202428:0xf8f3e8):0xffffff));g.fillRect(0,0,width,height);
        if(choosing) filePicker(g); else if(book>=0) reader(g); else shelf(g);
        g.dispose();canvas.drawImage(image,0,0,getWidth(),getHeight());
    }
    private void text(Graphics2D g,String s,int x,int y,int size,int color) {
        g.setColor(new Color(color));g.setFont(FontManager.getFont(Font.PLAIN,size));if(!s.isEmpty())FontManager.layout(s,size,Font.PLAIN).draw(g,x,y);
    }
    private void button(Graphics2D g,String label,int x,int y,int w,int h,Runnable action) {
        g.setColor(new Color(book>=0&&night?0x343b42:0xf5f5f5));g.fillRoundRect(x,y,w,h,12,12);
        text(g,label,x+10,y+h/2+6,16,book>=0&&night?0xe4ded3:0x454545);
        hits.add(new Hit(x,y,w,h,action));
    }
    private void changeTab(int target) {tab=target;scroll=0;search=false;notice="";invalidate();}
    private void shelf(Graphics2D g) {
        text(g,TABS[tab],28,63,36,0x171717);
        button(g,"导入",width-230,25,64,42,()->{choosing=true;fileNotice="选择 UTF-8 TXT、MD 或 EPUB 文件";invalidate();});
        button(g,"搜索",width-156,25,64,42,()->{search=!search;query="";scroll=0;invalidate();});
        button(g,"帮助",width-82,25,64,42,()->{notice="点击封面阅读 · 滚轮浏览 · 左右键翻页";invalidate();});
        if(tab==3) {
            text(g,"把阅读，留给自己",32,150,28,0x333333);
            int count=0;for(int i=0;i<bookCount();i++)if(state.containsKey("page."+bookKey(i)))count++;
            text(g,"已读书籍  "+count+" 本",32,210,20,0x666666);
            text(g,"阅读进度和书签自动保存在本机",32,250,18,0x777777);
            button(g,"继续上次阅读",32,290,190,48,()->open(Math.max(0,Math.min(bookCount()-1,lastBook()))));
            text(g,"内置正文为原创演示内容",32,390,17,0x888888);
            text(g,"封面来自你提供的界面参考图",32,425,17,0x888888);
        } else {
            g.setColor(new Color(0xfbf3f3));g.fillRoundRect(25,88,width-50,52,12,12);
            text(g,search?"搜索："+query+" ▏":"今日阅读  /  翻开一本书，遇见另一种生活",40,120,18,0x8e5555);
            List<Integer> visible=new ArrayList<>();
            for(int i=0;i<bookCount();i++)if(title(i).contains(query) && (tab!=1||i<TITLES.length) && (tab!=2||(i<TITLES.length&&i%3==0)))visible.add(i);
            int cell=Math.max(42,(width-56)/3), coverW=Math.max(20,Math.min(142,cell-28)),coverH=coverW*287/213,rowH=coverH+78;
            int rows=(visible.size()+2)/3;
            maxScroll=Math.max(0,160+rows*rowH-(height-90));scroll=Math.min(scroll,maxScroll);
            Shape old=g.getClip();g.clipRect(0,150,width,Math.max(0,height-240));
            for(int n=0;n<visible.size();n++) {
                final int id=visible.get(n);int x=28+(n%3)*cell+(cell-coverW)/2,y=165+(n/3)*rowH-scroll;
                for(int shadow=8;shadow>0;shadow--) {g.setColor(new Color(0,0,0,3));g.fillRoundRect(x-shadow/2,y+5,coverW+shadow,coverH+shadow,4,4);}
                if(id<covers.length&&covers[id]!=null)g.drawImage(covers[id],x,y,coverW,coverH,null);
                else {
                    g.setColor(new Color(id%2==0?0x344d58:0x72614e));g.fillRect(x,y,coverW,coverH);
                    g.setColor(new Color(0xbfa77e));g.fillRect(x+8,y+12,3,Math.max(0,coverH-24));
                    text(g,imported.get(id-TITLES.length).format,x+22,y+35,16,0xe5d8bc);
                    String name=title(id);int lineY=y+70;
                    for(int start=0;start<name.length()&&lineY<y+coverH-12;start+=5,lineY+=26)
                        text(g,name.substring(start,Math.min(name.length(),start+5)),x+18,lineY,18,0xffffff);
                }
                String title=title(id);while(title.length()>1&&FontManager.measureText(title,14)>cell-10)title=title.substring(0,title.length()-2)+"…";
                text(g,title,x,y+coverH+25,14,0x555555);
                g.setColor(new Color(0xeeeeee));g.fillRoundRect(18,y+coverH+36,width-36,7,4,4);
                int top=Math.max(y,150),bottom=Math.min(y+coverH+28,height-90);
                if(bottom>top)hits.add(new Hit(x,top,coverW,bottom-top,()->open(id)));
            }
            if(visible.isEmpty())text(g,"没有找到相关书籍",35,210,20,0x888888);
            g.setClip(old);
        }
        text(g,notice,28,height-95,14,0x9b6262);
        g.setColor(new Color(0xfafafa));g.fillRect(0,height-78,width,78);
        g.setColor(new Color(0xeeeeee));g.drawLine(0,height-78,width,height-78);
        for(int i=0;i<TABS.length;i++) {
            final int target=i;int x=i*width/4,w=width/4;
            text(g,new String[]{"▤","▥","FREE","○"}[i],x+w/2-18,height-43,i==2?14:24,i==tab?RED:0x999999);
            text(g,TABS[i],x+w/2-(TABS[i].length()*16/2),height-15,16,i==tab?RED:0x999999);
            hits.add(new Hit(x,height-78,w,78,()->changeTab(target)));
        }
    }
    private int lastBook() {
        String last=state.getProperty("last","0");
        for(int i=0;i<bookCount();i++)if(bookKey(i).equals(last))return i;
        return 0;
    }
    private void open(int id) { book=id;page=Math.max(0,number("page."+bookKey(id),0));state.setProperty("last",bookKey(id));pageWidth=0;notice="";invalidate(); }
    void addImported(BookLibrary.Book added) {
        for(int i=0;i<imported.size();i++)if(imported.get(i).id.equals(added.id)) {
            choosing=false;tab=0;search=false;query="";open(TITLES.length+i);notice="书籍已在书架中";save();return;
        }
        imported.add(added);choosing=false;tab=0;search=false;query="";scroll=0;open(bookCount()-1);save();
    }
    private void startImport(File source) {
        if(importing)return;
        final com.android.os.Handler handler=new com.android.os.Handler(message->{((Runnable)message.obj).run();return true;});
        importing=true;fileNotice="正在导入，请稍候…";invalidate();
        importWorker=new Thread(()->{
            Runnable result;
            try { BookLibrary.Book added=library.importFile(source);result=()->{importing=false;if(!closed)addImported(added);}; }
            catch(Exception e) { final String error=e.getMessage()==null?"无法读取该书籍":e.getMessage();result=()->{importing=false;if(!closed){fileNotice="导入失败："+error;invalidate();}}; }
            handler.sendMessage(handler.obtainMessage(0,result));
        },"ebook-import");
        importWorker.setDaemon(true);importWorker.start();
    }
    private void selectFile(File file) {
        if(importing)return;
        if(file.isDirectory()){folder=file;filePath=file.getAbsolutePath();filePage=0;fileNotice="选择 UTF-8 TXT、MD 或 EPUB 文件";invalidate();}
        else startImport(file);
    }
    private void filePicker(Graphics2D g) {
        text(g,"导入书籍",25,58,30,0x222222);
        button(g,"取消",width-90,23,68,40,()->{if(!importing){choosing=false;invalidate();}});
        g.setColor(new Color(0xf3f3f3));g.fillRoundRect(22,80,width-44,62,10,10);
        Shape clip=g.getClip();g.clipRect(32,85,width-64,50);
        String path=filePath+" ▏";while(path.length()>1&&FontManager.measureText(path,16)>width-64)path=path.substring(1);
        text(g,path,32,118,16,0x333333);g.setClip(clip);
        text(g,"可直接输入路径，按 Enter 打开；Ctrl+U 清空",25,169,15,0x777777);
        if(importing){text(g,fileNotice,25,225,18,0x9b6262);return;}
        button(g,"上级目录",25,185,110,38,()->{File parent=folder.getParentFile();if(parent!=null)selectFile(parent);});
        button(g,"打开路径",width-135,185,110,38,()->selectFile(new File(filePath)));
        File[] entries=folder.listFiles(f->f.isDirectory()||BookLibrary.supported(f.getName()));
        if(entries==null) {entries=new File[0];fileNotice="目录无法读取，可输入其他路径";}
        Arrays.sort(entries,Comparator.comparing((File f)->!f.isDirectory()).thenComparing(File::getName,String.CASE_INSENSITIVE_ORDER));
        int rows=Math.max(1,(height-380)/48),total=Math.max(1,(entries.length+rows-1)/rows);filePage=Math.max(0,Math.min(filePage,total-1));
        for(int i=filePage*rows;i<Math.min(entries.length,(filePage+1)*rows);i++) {
            final File file=entries[i];int y=238+(i-filePage*rows)*48;
            String label=(file.isDirectory()?"[目录] ":"[书籍] ")+file.getName();while(label.length()>1&&FontManager.measureText(label,16)>width-76)label=label.substring(0,label.length()-2)+"…";
            button(g,label,25,y,width-50,40,()->selectFile(file));
        }
        button(g,"上一页",25,height-110,95,38,()->{filePage=Math.max(0,filePage-1);invalidate();});
        final int count=total;
        button(g,"下一页",width-120,height-110,95,38,()->{filePage=Math.min(count-1,filePage+1);invalidate();});
        text(g,(filePage+1)+" / "+total,width/2-22,height-85,16,0x777777);
        String message=fileNotice;while(message.length()>1&&FontManager.measureText(message,15)>width-50)message=message.substring(0,message.length()-2)+"…";
        text(g,message,25,height-42,15,0x9b6262);
    }
    private String content() {
        if(book>=TITLES.length)return imported.get(book-TITLES.length).text;
        StringBuilder s=new StringBuilder("阅读示例 · "+TITLES[book]+"\n\n本书正文为电子书应用的原创演示，与封面小说内容无关。\n\n");
        String[] paragraphs={"清晨的阳光穿过窗棂，落在一封尚未拆开的信上。林溪放下手中的茶杯，听见远处传来第一声钟鸣。她知道，这一天会与以往不同。", "街角的旧书店刚刚开门，木牌在风里轻轻摇晃。店主从书页间抬起头，微笑着说：有些路，只有亲自走过，才知道会通向哪里。", "她沿着河岸向前走，水面倒映着云影。昨日的疑问还没有答案，但心中的犹豫已经渐渐散去。她把信放进口袋，决定先去看看山那边的世界。", "傍晚时分，小镇亮起了灯。一路遇见的人与事，都成了旅途中细小而明亮的注脚。故事仍在继续，而下一页，正等着她亲手翻开。"};
        for(int c=1;c<=6;c++){s.append("第"+c+"章  "+new String[]{"晨光","来信","远行","相逢","归途","新页"}[c-1]+"\n\n");for(String p:paragraphs)s.append("　　"+p+"\n\n");}
        return s.toString();
    }
    private void paginate(Graphics2D g) {
        int available=width-64,lineCount=Math.max(1,(height-220)/(fontSize+13));
        if(pageWidth==width&&pageHeight==height&&pageFont==fontSize)return;
        pageWidth=width;pageHeight=height;pageFont=fontSize;pages.clear();
        markdownPages=null;
        if(book>=TITLES.length&&"MD".equals(imported.get(book-TITLES.length).format)) {
            BookLibrary.Book md=imported.get(book-TITLES.length);
            markdownPages=MarkdownReader.layout(md.text,md.assets,Math.max(1,available),Math.max(40,height-220),fontSize);
            for(List<MarkdownReader.Line> mdPage:markdownPages){StringBuilder plain=new StringBuilder();for(MarkdownReader.Line line:mdPage)plain.append(line.plain()).append('\n');pages.add(plain.toString());}
            page=Math.max(0,Math.min(page,pages.size()-1));return;
        }
        g.setFont(FontManager.getFont(Font.PLAIN,fontSize));FontMetrics fm=g.getFontMetrics();
        List<String> lines=new ArrayList<>();StringBuilder line=new StringBuilder();
        String content=content();
        for(int offset=0;offset<content.length();) {
            int cp=content.codePointAt(offset);offset+=Character.charCount(cp);
            if(cp=='\n'){lines.add(line.toString());line.setLength(0);continue;}
            String ch=new String(Character.toChars(cp));
            if(line.length()>0&&fm.stringWidth(line.toString()+ch)>available){lines.add(line.toString());line.setLength(0);}
            line.append(ch);
        }
        if(line.length()>0)lines.add(line.toString());
        for(int i=0;i<lines.size();i+=lineCount)pages.add(String.join("\n",lines.subList(i,Math.min(lines.size(),i+lineCount))));
        if(pages.isEmpty())pages.add("");
        page=Math.max(0,Math.min(page,pages.size()-1));
    }
    private void turn(int delta){page=Math.max(0,Math.min(pages.size()-1,page+delta));save();invalidate();}
    private void reader(Graphics2D g) {
        paginate(g);int ink=night?0xd9d2c5:0x403b34;
        button(g,"‹ 书架",20,18,92,38,()->{save();book=-1;invalidate();});
        button(g,"A−",width-202,18,50,38,()->{fontSize=Math.max(16,fontSize-2);save();invalidate();});
        button(g,"A+",width-144,18,50,38,()->{fontSize=Math.min(32,fontSize+2);save();invalidate();});
        button(g,night?"日间":"夜间",width-86,18,66,38,()->{night=!night;save();invalidate();});
        String readerTitle=title(book);
        int titleWidth=state.containsKey("mark."+bookKey(book))?width-190:width-64;
        while(readerTitle.length()>1&&FontManager.measureText(readerTitle,18)>titleWidth)readerTitle=readerTitle.substring(0,readerTitle.length()-2)+"…";
        text(g,readerTitle,32,100,18,ink);
        if(markdownPages!=null) {
            int y=122;Shape clip=g.getClip();g.clipRect(32,122,width-64,Math.max(1,height-220));
            for(MarkdownReader.Line line:markdownPages.get(page)){line.draw(g,32,y,width-64,night);y+=line.height;}
            g.setClip(clip);
        }else {int y=145;for(String line:pages.get(page).split("\n",-1)){text(g,line,32,y,fontSize,ink);y+=fontSize+13;}}
        text(g,(page+1)+" / "+pages.size()+" 页",width/2-42,height-88,15,ink);
        button(g,"上一页",25,height-65,100,42,()->turn(-1));
        button(g,"书签",width/2-37,height-65,74,42,()->{state.setProperty("mark."+bookKey(book),""+page);save();notice="已添加书签";invalidate();});
        button(g,"下一页",width-125,height-65,100,42,()->turn(1));
        if(state.containsKey("mark."+bookKey(book)))button(g,"转到书签",width-130,73,110,34,()->{page=Math.max(0,Math.min(pages.size()-1,number("mark."+bookKey(book),0)));save();invalidate();});
        if(!notice.isEmpty())text(g,notice,32,height-88,14,ink);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if(e.getType()==KeyEvent.UP)return false;
        int key=e.getValue();
        if(choosing) {
            if(importing)return true;
            if(e.getType()==KeyEvent.DOWN) {
                if(key==KeyEvent.ESCAPE){choosing=false;invalidate();return true;}
                if(key==KeyEvent.ENTER){selectFile(new File(filePath));return true;}
                if((e.getModifiers()&KeyEvent.CONTROL)!=0&&(key=='u'||key=='U')){filePath="";invalidate();return true;}
                if(key==KeyEvent.BACKSPACE){if(!filePath.isEmpty())filePath=filePath.substring(0,filePath.offsetByCodePoints(filePath.length(),-1));invalidate();return true;}
            }
            if(e.getType()==KeyEvent.TEXT&&!Character.isISOControl(key)&&(e.getModifiers()&KeyEvent.CONTROL)==0){filePath+=new String(Character.toChars(key));invalidate();}
            return true;
        }
        if(e.getType()==KeyEvent.DOWN) {
            if(key==KeyEvent.ESCAPE){if(book>=0){save();book=-1;}else{search=false;query="";}invalidate();return true;}
            if(book>=0&&(key==KeyEvent.RIGHT||key==KeyEvent.LEFT)){turn(key==KeyEvent.RIGHT?1:-1);return true;}
            if(search&&key==KeyEvent.BACKSPACE){if(!query.isEmpty())query=query.substring(0,query.offsetByCodePoints(query.length(),-1));scroll=0;invalidate();return true;}
        }
        if(search&&e.getType()==KeyEvent.TEXT&&!Character.isISOControl(key)){query+=new String(Character.toChars(key));scroll=0;invalidate();return true;}
        return false;
    }
    @Override protected boolean onTouchEvent(MotionEvent e) {
        if(e.getAction()==MotionEvent.ACTION_SCROLL&&choosing){if(!importing){filePage=Math.max(0,filePage+(e.getScrollDelta()>0?1:-1));invalidate();}return true;}
        if(e.getAction()==MotionEvent.ACTION_SCROLL){if(book>=0)turn(e.getScrollDelta()>0?1:-1);else{scroll=Math.max(0,Math.min(maxScroll,scroll+e.getScrollDelta()*40));invalidate();}return true;}
        if(e.getButton()!=1)return false;
        if(e.getAction()==MotionEvent.ACTION_DOWN){requestFocus();downX=(float)(e.getX()/scale);downY=(float)(e.getY()/scale);return true;}
        if(e.getAction()==MotionEvent.ACTION_UP){int x=(int)(e.getX()/scale),y=(int)(e.getY()/scale);
            if(Math.abs(y-downY)>35){if(book<0&&!choosing){scroll=Math.max(0,Math.min(maxScroll,scroll+(int)downY-y));invalidate();}return true;}
            if(Math.abs(x-downX)>20)return true;
            for(Hit hit:new ArrayList<>(hits))if(hit.box.contains(x,y)){hit.action.run();return true;}
            return true;
        }
        return true;
    }
}
