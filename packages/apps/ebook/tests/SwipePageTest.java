package com.packages.ebook;

import com.android.app.Activity;
import com.android.graphics.Canvas;
import com.android.os.*;
import com.android.view.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.*;

public final class SwipePageTest {
    static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    static int page(EbookView v)throws Exception{return (Integer)field(v,"page");}
    static void event(EbookView v,int action,int x,int y){v.dispatchTouchEvent(new MotionEvent(action,x,y));}
    static void gesture(EbookView v,int x,int y,int endX,int endY){event(v,0,x,y);event(v,2,endX,endY);event(v,1,endX,endY);}
    static void stop(EbookView v)throws Exception{Method m=EbookView.class.getDeclaredMethod("stopSlide");m.setAccessible(true);m.invoke(v);}
    static void draw(EbookView v,String name,int w,int h)throws Exception{
        v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h);
        BufferedImage im=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);try(Canvas c=new Canvas(im)){v.draw(c);}
        if(name!=null)ImageIO.write(im,"png",new File("out/ebook-tests/"+name+".png"));
    }
    static void draw(EbookView v)throws Exception{draw(v,null,600,880);}
    static void settle(EbookView v)throws Exception{
        // Advance animation time without sleeping; dispatch its real UI timer callback.
        Field start=EbookView.class.getDeclaredField("animationStart");start.setAccessible(true);start.setLong(v,System.nanoTime()-300000000L);
        java.util.Timer timer=(java.util.Timer)field(v,"pageTimer");
        check(timer!=null,"Animation timer started");
        MessageQueue queue=(MessageQueue)field(Looper.myLooper(),"mQueue");
        Method next=MessageQueue.class.getDeclaredMethod("next");next.setAccessible(true);
        Message message=(Message)next.invoke(queue);((Handler)field(message,"target")).dispatchMessage(message);
        check((Long)field(v,"animationStart")==0,"Animation completes and releases timer");
    }
    public static void main(String[] args)throws Exception{
        Looper.prepareMainLooper();
        File root=new File(System.getProperty("user.home"));root.mkdirs();
        BookLibrary library=new BookLibrary(new File(root,"books"));
        EbookView v=new EbookView(new Activity(){@Override public ClassLoader getClassLoader(){return SwipePageTest.class.getClassLoader();}});
        StringBuilder text=new StringBuilder();for(int i=0;i<300;i++)text.append("第 "+i+" 行：滑动阅读，检查页面跟手移动和相邻页面内容。\n");
        File txt=new File(root,"swipe.txt");Files.write(txt.toPath(),text.toString().getBytes(StandardCharsets.UTF_8));
        BookLibrary.Book book=library.importFile(txt);v.addImported(book);draw(v);
        event(v,0,450,350);event(v,2,240,355);draw(v,"swipe-drag",600,880);
        check(page(v)==0&&(Double)field(v,"swipeOffset")<-150,"Drag follows pointer before committing");
        event(v,1,200,355);check(page(v)==1,"Swipe left advances exactly one page");
        check(field(v,"outgoingFrame")!=null,"Transition retains outgoing page");draw(v,"swipe-transition",600,880);
        settle(v);draw(v);check(((java.util.Properties)field(v,"state")).getProperty("page."+book.id).equals("1"),"Committed progress saved");
        gesture(v,160,350,410,350);check(page(v)==0,"Swipe right goes back");stop(v);draw(v);
        gesture(v,300,350,550,350);check(page(v)==0,"First page resists overscroll");stop(v);
        gesture(v,300,350,260,350);check(page(v)==0,"Short swipe springs back");check(field(v,"outgoingFrame")==null,"Short swipe does not commit");stop(v);
        gesture(v,300,350,200,550);check(page(v)==0,"Vertical gesture does not turn");
        event(v,0,450,350);event(v,2,180,350);event(v,MotionEvent.ACTION_CANCEL,180,350);check(page(v)==0&&(Double)field(v,"swipeOffset")==0,"Cancel resets drag");
        draw(v);event(v,0,520,835);event(v,1,520,835);check(page(v)==1,"Next button still works");
        draw(v,null,900,1320);gesture(v,700,600,300,600);check(page(v)==2,"Scaled window gesture");stop(v);
        draw(v);event(v,0,480,350);event(v,2,220,350);v.dispatchKeyEvent(new KeyEvent(KeyEvent.DOWN,KeyEvent.ESCAPE,0));check((Integer)field(v,"book")==-1&&!(Boolean)field(v,"swiping"),"Leaving reader cancels gesture");
        File md=new File(root,"swipe.md");Files.write(md.toPath(),("# Markdown\n"+text).getBytes(StandardCharsets.UTF_8));v.addImported(library.importFile(md));draw(v);gesture(v,450,350,180,350);check(page(v)==1,"Markdown swipe");stop(v);
        File pdf=new File(root,"swipe.pdf");try(PDDocument d=new PDDocument()){d.addPage(new PDPage());d.addPage(new PDPage());d.save(pdf);}
        v.addImported(library.importFile(pdf));draw(v);gesture(v,450,350,180,350);check(page(v)==1,"PDF fit-page swipe");stop(v);draw(v);
        gesture(v,450,350,180,350);check(page(v)==1,"Last page cannot advance");stop(v);draw(v);
        event(v,0,470,37);event(v,1,470,37);draw(v);check((Double)field(v,"pdfZoom")>1,"PDF zoom enabled");
        gesture(v,450,350,180,350);check(page(v)==1&&!(Boolean)field(v,"swiping"),"Zoomed PDF pans without page turn");
        v.close();System.out.println("PASS: swipe preview, transition completion, directions, thresholds, boundaries, cancel, resize, buttons, Markdown and PDF pan");
    }
}
