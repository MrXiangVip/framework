package com.packages.ebook;

import com.android.app.Activity;
import com.android.graphics.Canvas;
import com.android.view.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

public final class MarkdownImportTest {
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static EbookView view(){return new EbookView(new Activity(){@Override public ClassLoader getClassLoader(){return MarkdownImportTest.class.getClassLoader();}});}
    private static void key(EbookView v,int key){v.dispatchKeyEvent(new KeyEvent(KeyEvent.DOWN,key,0));}
    private static void click(EbookView v,int x,int y){v.dispatchTouchEvent(new MotionEvent(0,x,y));v.dispatchTouchEvent(new MotionEvent(1,x,y));}
    private static void render(EbookView v,int w,int h,String name)throws Exception{
        v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h);
        BufferedImage image=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);try(Canvas c=new Canvas(image)){v.draw(c);}ImageIO.write(image,"png",new File("out/ebook-tests/"+name+".png"));
    }
    public static void main(String[] args)throws Exception{
        File home=new File(System.getProperty("user.home"));File source=new File(home,"source");File images=new File(source,"images");images.mkdirs();
        BufferedImage picture=new BufferedImage(360,170,BufferedImage.TYPE_INT_RGB);Graphics2D g=picture.createGraphics();g.setColor(new Color(0xe4eff6));g.fillRect(0,0,360,170);g.setColor(new Color(0x40859d));g.fillOval(30,30,105,105);g.setColor(new Color(0xbb6b48));g.fillRect(205,35,115,100);g.dispose();
        File png=new File(images,"示意图.png");ImageIO.write(picture,"png",png);
        String beginning="# 自然科学课本\n\n这是**重点知识**，也是*观察练习*。阅读[参考资料](https://example.invalid)。\n\n> 仔细观察，再作出判断。\n\n- 圆形\n- 方形\n\n| 图形 | 特点 |\n| --- | --- |\n| 圆 | 没有角 |\n| 方 | 四个角 |\n\n![几何形状示意图](images/%E7%A4%BA%E6%84%8F%E5%9B%BE.png)\n\n```java\nString a = \"**保持原样**\";\n```\n\n![矢量插图](images/diagram.svg)\n\n---\n\n";
        StringBuilder md=new StringBuilder("\ufeff"+beginning);for(int i=0;i<60;i++)md.append("## 练习 "+i+"\n\n观察图形并记录结果。中文与表情😀。\n\n");
        File file=new File(source,"课本.MD");Files.write(file.toPath(),md.toString().getBytes(StandardCharsets.UTF_8));
        BookLibrary library=new BookLibrary(new File(home,".local/share/framework/ebook/books"));
        BookLibrary.Book book=library.importFile(file);check(book.format.equals("MD")&&book.title.equals("自然科学课本"),"MD format and first heading as title");
        check(new File(book.assets,"images/示意图.png").isFile(),"Relative Unicode image must be copied");
        check(library.importFile(file).id.equals(book.id)&&library.load().size()==1,"Markdown deduplication");
        File longExtension=new File(source,"lesson.markdown");Files.write(longExtension.toPath(),"Setext title\n===\nText".getBytes(StandardCharsets.UTF_8));
        check(library.importFile(longExtension).title.equals("Setext title"),".markdown and Setext headings");
        File text=new File(source,"same.txt");Files.write(text.toPath(),md.toString().getBytes(StandardCharsets.UTF_8));check(!library.importFile(text).id.equals(book.id),"TXT and MD of identical bytes must not overwrite each other");
        Files.write(new File(source,"bad.md").toPath(),new byte[]{(byte)0xc3,0x28});try{library.importFile(new File(source,"bad.md"));throw new AssertionError("Invalid MD encoding");}catch(IOException expected){}
        check(MarkdownReader.localImage(source,"../outside.png")==null&&MarkdownReader.localImage(source,"https://example.invalid/a.png")==null,"Image paths must remain local to source folder");
        List<MarkdownReader.Span> spans=MarkdownReader.inline("文字 **粗体** *斜体* `**代码**` [链接](url)");
        check(spans.stream().anyMatch(s->s.text.equals("粗体")&&(s.style&Font.BOLD)!=0),"Bold span");
        check(spans.stream().anyMatch(s->s.text.equals("斜体")&&(s.style&Font.ITALIC)!=0),"Italic span");
        check(spans.stream().anyMatch(s->s.text.equals("**代码**")),"Inline code must preserve markers");
        List<List<MarkdownReader.Line>> layout=MarkdownReader.layout(book.text,book.assets,536,660,22);
        StringBuilder all=new StringBuilder();int picturePage=-1;boolean heading=false,table=false,code=false;
        for(int p=0;p<layout.size();p++){int height=0;for(MarkdownReader.Line line:layout.get(p)){height+=line.height;all.append(line.plain()).append('\n');
            if(line.image!=null)picturePage=p;if(line.kind.equals("heading")&&line.size>22)heading=true;if(line.kind.equals("table"))table=true;if(line.kind.equals("code")&&line.plain().contains("**保持原样**"))code=true;
        }check(height<=660,"Rich page height must fit");}
        check(picturePage>=0&&heading&&table&&code,"Headings, images, tables and code rendering");check(all.toString().contains("矢量插图 · 缺失或暂不支持"),"SVG placeholder must be explicit");
        com.android.os.Looper.prepareMainLooper();
        EbookView v=view();render(v,600,880,"markdown-picker-start");click(v,400,40);render(v,600,880,"markdown-picker");
        v.dispatchKeyEvent(new KeyEvent(KeyEvent.DOWN,'u',KeyEvent.CONTROL));
        for(int cp:file.getAbsolutePath().codePoints().toArray())v.dispatchKeyEvent(new KeyEvent(KeyEvent.TEXT,cp,0));
        key(v,KeyEvent.ENTER);
        Method complete=BookImportTest.class.getDeclaredMethod("completeImport",EbookView.class);complete.setAccessible(true);complete.invoke(null,v);
        render(v,600,880,"markdown-reader");
        check(!(Boolean)field(v,"choosing"),"MD picker import must complete");
        check(field(v,"markdownPages")!=null,"MD must use rich pagination");
        for(int p=0;p<picturePage;p++)key(v,KeyEvent.RIGHT);render(v,600,880,"markdown-image");
        key(v,KeyEvent.RIGHT);click(v,285,830);v.save();int saved=(Integer)field(v,"page");
        Properties state=(Properties)field(v,"state");check(Integer.toString(saved).equals(state.getProperty("mark."+book.id)),"MD bookmark");
        render(v,360,640,"markdown-compact");render(v,1000,700,"markdown-landscape");render(v,600,880,"markdown-normal");
        click(v,530,35);render(v,600,880,"markdown-night");v.save();saved=(Integer)field(v,"page");v.close();
        Files.delete(file.toPath());Files.delete(png.toPath());
        BookLibrary.Book restored=library.load().stream().filter(b->b.id.equals(book.id)).findFirst().get();
        check(MarkdownReader.layout(restored.text,restored.assets,536,660,22).stream().flatMap(List::stream).anyMatch(line->line.image!=null),"Images must survive source deletion and reload");
        EbookView reopened=view();reopened.addImported(restored);render(reopened,600,880,"markdown-restored");
        check((Integer)field(reopened,"page")==saved,"MD reading progress survives restart");
        key(reopened,KeyEvent.ESCAPE);render(reopened,600,880,"markdown-shelf");click(reopened,470,40);
        for(int cp:"自然科学".codePoints().toArray())reopened.dispatchKeyEvent(new KeyEvent(KeyEvent.TEXT,cp,0));render(reopened,600,880,"markdown-search");click(reopened,80,200);render(reopened,600,880,"markdown-search-open");
        check(field(reopened,"markdownPages")!=null,"Search must open MD reader");
        check(BookLibrary.supported("test.MD")&&BookLibrary.supported("test.markdown"),"File picker extension filter");
        System.out.println("PASS: Markdown import, formatting, tables, code, local images, placeholders, deduplication, search, resize, bookmarks and source-independent restore");
    }
}
