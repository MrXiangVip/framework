package com.packages.ebook;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import java.lang.reflect.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import com.android.app.Activity;
import com.android.graphics.Canvas;
import com.android.view.*;
import com.android.os.*;

public final class BookImportTest {
    private static final String CONTAINER="<?xml version='1.0'?><container xmlns='urn:oasis:names:tc:opendocument:xmlns:container'><rootfiles><rootfile full-path='OPS/book.opf' media-type='application/oebps-package+xml'/></rootfiles></container>";
    private static final String OPF="<package xmlns='http://www.idpf.org/2007/opf' version='3.0'><metadata xmlns:dc='http://purl.org/dc/elements/1.1/'><dc:title>中文测试书</dc:title></metadata><manifest><item id='second' href='text/second.xhtml' media-type='application/xhtml+xml'/><item id='first' href='text/%E7%AC%AC%E4%B8%80.xhtml#start' media-type='application/xhtml+xml'/></manifest><spine><itemref idref='first'/><itemref idref='second'/></spine></package>";
    private static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    private static void check(boolean yes,String msg){if(!yes)throw new AssertionError(msg);}
    private static File write(File dir,String name,byte[] bytes)throws Exception{File f=new File(dir,name);Files.write(f.toPath(),bytes);return f;}
    private static void zipEntry(ZipOutputStream z,String name,String text)throws Exception{z.putNextEntry(new ZipEntry(name));z.write(text.getBytes(StandardCharsets.UTF_8));z.closeEntry();}
    private static File epub(File dir,String name,String opf,boolean drm)throws Exception{
        File f=new File(dir,name);
        try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(f))){
            zipEntry(z,"mimetype","application/epub+zip");zipEntry(z,"META-INF/container.xml",CONTAINER);zipEntry(z,"OPS/book.opf",opf);
            zipEntry(z,"OPS/text/second.xhtml","<html><head><title>隐藏标题</title><style>hidden CSS</style></head><body><h1>第二章</h1><p>后面的内容<br/>换行。</p></body></html>");
            zipEntry(z,"OPS/text/第一.xhtml","<?xml version='1.0' encoding='UTF-8'?><!DOCTYPE html><html xmlns='http://www.w3.org/1999/xhtml'><head><title>隐藏标题</title></head><body><section><h1>第一章</h1><p>中文 &amp; 实体&nbsp;测试。😀</p><script>hidden script</script><p>第二段。</p></section></body></html>");
            if(drm)zipEntry(z,"META-INF/encryption.xml","<encryption/>");
        }return f;
    }
    private static void rejects(BookLibrary l,File f,String reason)throws Exception{try{l.importFile(f);throw new AssertionError(reason);}catch(IOException expected){check(expected.getMessage()!=null,reason+" requires an error");}}
    private static EbookView view(){return new EbookView(new Activity(){@Override public ClassLoader getClassLoader(){return BookImportTest.class.getClassLoader();}});}
    private static void render(EbookView v,String name)throws Exception{
        v.measure(View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(880,View.MeasureSpec.EXACTLY));v.layout(0,0,600,880);
        BufferedImage im=new BufferedImage(600,880,BufferedImage.TYPE_INT_RGB);try(Canvas c=new Canvas(im)){v.draw(c);}ImageIO.write(im,"png",new File("out/ebook-tests/"+name+".png"));
    }
    private static void click(EbookView v,int x,int y){v.dispatchTouchEvent(new MotionEvent(0,x,y));v.dispatchTouchEvent(new MotionEvent(1,x,y));}
    private static void key(EbookView v,int key){v.dispatchKeyEvent(new KeyEvent(KeyEvent.DOWN,key,0));}
    private static void completeImport(EbookView v)throws Exception{
        Thread t=(Thread)field(v,"importWorker");check(t!=null,"Import must start a worker");t.join(10000);check(!t.isAlive(),"Import must finish");
        Field q=Looper.class.getDeclaredField("mQueue");q.setAccessible(true);MessageQueue queue=(MessageQueue)q.get(Looper.myLooper());
        check(queue.size()>0,"Import result must be posted to UI queue");
        Method next=MessageQueue.class.getDeclaredMethod("next");next.setAccessible(true);Message message=(Message)next.invoke(queue);
        Field target=Message.class.getDeclaredField("target");target.setAccessible(true);((Handler)target.get(message)).dispatchMessage(message);
    }
    public static void main(String[] args)throws Exception{
        File root=new File(System.getProperty("user.home"));root.mkdirs();
        BookLibrary library=new BookLibrary(new File(root,"parser-tests"));
        StringBuilder body=new StringBuilder("\ufeff第一章\r\n\r\n");for(int i=0;i<100;i++)body.append("这是第"+i+"行中文阅读内容，包含表情😀和完整段落。\r\n");
        File txt=write(root,"旅行笔记.TXT",body.toString().getBytes(StandardCharsets.UTF_8));
        BookLibrary.Book plain=library.importFile(txt);
        check(plain.title.equals("旅行笔记")&&plain.text.startsWith("第一章\n\n"),"TXT title, BOM and line endings");
        check(plain.text.contains("😀")&&!plain.text.contains("\r"),"TXT Unicode");
        check(library.importFile(txt).id.equals(plain.id)&&library.load().size()==1,"Repeated import must deduplicate");
        File epub=epub(root,"reading.epub",OPF,false);BookLibrary.Book parsed=library.importFile(epub);
        check(parsed.title.equals("中文测试书"),"EPUB metadata title");
        check(parsed.text.indexOf("第一章")>=0&&parsed.text.indexOf("第一章")<parsed.text.indexOf("第二章"),"EPUB spine order, relative encoded paths");
        check(parsed.text.contains("中文 & 实体 测试。😀"),"EPUB entities and Unicode: "+parsed.text);
        check(!parsed.text.contains("xml version")&&!parsed.text.contains("DOCTYPE"),"XML declarations must not appear as text");
        check(!parsed.text.contains("隐藏标题")&&!parsed.text.contains("hidden"),"HTML head, scripts and styles must not enter body: "+parsed.text);
        check(parsed.text.contains("内容\n换行"),"HTML paragraph boundaries");
        rejects(library,write(root,"bad.txt",new byte[]{(byte)0xc3,0x28}),"Invalid UTF-8");
        rejects(library,write(root,"empty.txt","\ufeff \n".getBytes(StandardCharsets.UTF_8)),"Empty TXT");
        rejects(library,write(root,"binary.txt",new byte[]{'a',0,'b'}),"Binary TXT");
        rejects(library,write(root,"unsupported.pdf",new byte[]{1}),"Wrong extension");
        rejects(library,write(root,"broken.epub",new byte[]{1,2}),"Corrupt ZIP");
        rejects(library,epub(root,"drm.epub",OPF,true),"DRM EPUB");
        rejects(library,epub(root,"missing.epub",OPF.replace("text/second.xhtml","text/missing.xhtml"),false),"Missing chapter");
        rejects(library,epub(root,"traversal.epub",OPF.replace("text/second.xhtml","../../secret.xhtml"),false),"Traversal path");
        rejects(library,epub(root,"remote.epub",OPF.replace("text/second.xhtml","https://example.invalid/book.xhtml"),false),"External chapter");
        File expanded=new File(root,"expanded.epub");
        try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(expanded))){
            zipEntry(z,"META-INF/container.xml",CONTAINER);zipEntry(z,"OPS/book.opf",OPF);
            z.putNextEntry(new ZipEntry("OPS/text/第一.xhtml"));byte[] chunk=new byte[8192];Arrays.fill(chunk,(byte)'a');
            for(int i=0;i<1025;i++)z.write(chunk);z.closeEntry();
        }
        rejects(library,expanded,"Decompressed chapter limit");
        check(library.importFile(epub(root,"epub2.epub",OPF.replace("version='3.0'","version='2.0'"),false)).text.contains("第一章"),"EPUB 2 spine");
        File huge=new File(root,"huge.txt");try(RandomAccessFile file=new RandomAccessFile(huge,"rw")){file.setLength(BookLibrary.MAX_SOURCE+1);}rejects(library,huge,"Source size limit");huge.delete();
        try{EpubReader.readLimited(new ByteArrayInputStream(new byte[20]),10);throw new AssertionError("Expansion limit");}catch(IOException expected){}
        check(library.load().size()==3,"Failures must leave library unchanged");

        Looper.prepareMainLooper();EbookView v=view();render(v,"import-shelf");
        click(v,400,40);check((Boolean)field(v,"choosing"),"Import button must open picker");render(v,"file-picker");
        v.dispatchKeyEvent(new KeyEvent(KeyEvent.DOWN,'u',KeyEvent.CONTROL));
        for(int cp:txt.getAbsolutePath().codePoints().toArray())v.dispatchKeyEvent(new KeyEvent(KeyEvent.TEXT,cp,0));
        key(v,KeyEvent.ENTER);completeImport(v);render(v,"imported-txt");
        check(!(Boolean)field(v,"choosing")&&(Integer)field(v,"book")==11,"File picker must import and open book");
        check(((List<?>)field(v,"pages")).toString().contains("中文阅读内容"),"Imported TXT must be displayed");
        key(v,KeyEvent.RIGHT);click(v,285,830);v.save();
        Properties state=(Properties)field(v,"state");check("1".equals(state.getProperty("page."+plain.id)),"Stable imported progress key");
        check("1".equals(state.getProperty("mark."+plain.id)),"Imported bookmark");
        key(v,KeyEvent.ESCAPE);render(v,"after-import");click(v,400,40);
        v.dispatchKeyEvent(new KeyEvent(KeyEvent.DOWN,'u',KeyEvent.CONTROL));
        for(int cp:epub.getAbsolutePath().codePoints().toArray())v.dispatchKeyEvent(new KeyEvent(KeyEvent.TEXT,cp,0));
        key(v,KeyEvent.ENTER);completeImport(v);render(v,"imported-epub");
        check(((List<?>)field(v,"pages")).toString().contains("第一章"),"EPUB reader integration");
        v.addImported(new BookLibrary(new File(root,".local/share/framework/ebook/books")).importFile(txt));render(v,"reopened-txt");
        check((Integer)field(v,"page")==1,"Duplicate import must preserve progress");
        check(((List<?>)field(v,"imported")).size()==2,"UI must deduplicate");
        key(v,KeyEvent.ESCAPE);render(v,"before-invalid-import");click(v,400,40);
        v.dispatchKeyEvent(new KeyEvent(KeyEvent.DOWN,'u',KeyEvent.CONTROL));
        for(int cp:new File(root,"bad.txt").getAbsolutePath().codePoints().toArray())v.dispatchKeyEvent(new KeyEvent(KeyEvent.TEXT,cp,0));
        key(v,KeyEvent.ENTER);completeImport(v);render(v,"import-error");
        check((Boolean)field(v,"choosing")&&!(Boolean)field(v,"importing"),"Failed import must remain retryable");
        check(((String)field(v,"fileNotice")).contains("UTF-8"),"Actionable encoding error");
        key(v,KeyEvent.ESCAPE);check(!(Boolean)field(v,"choosing"),"Picker cancellation");
        v.close();Files.delete(txt.toPath());Files.delete(epub.toPath());
        EbookView restored=view();render(restored,"import-restored");
        List<?> restoredBooks=(List<?>)field(restored,"imported");check(restoredBooks.size()==2,"Copies survive source deletion and restart");
        click(restored,525,840);render(restored,"import-profile");click(restored,100,310);render(restored,"continued-import");
        check((Integer)field(restored,"page")==1,"Continue last imported book despite sorted library reload");
        check(((List<?>)field(restored,"pages")).toString().contains("中文阅读内容"),"Correct book restored");
        key(restored,KeyEvent.ESCAPE);render(restored,"before-search");click(restored,80,840);render(restored,"import-library");click(restored,470,40);
        for(int cp:"旅行笔记".codePoints().toArray())restored.dispatchKeyEvent(new KeyEvent(KeyEvent.TEXT,cp,0));render(restored,"import-search");
        click(restored,80,200);render(restored,"search-opened");check((Integer)field(restored,"book")>=11,"Search opens imported book");
        System.out.println("PASS: UTF-8 TXT, EPUB spine/entities, invalid inputs, local copies, async file picker, deduplication, search, bookmarks and restart");
    }
}
