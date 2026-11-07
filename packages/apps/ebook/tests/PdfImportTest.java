package com.packages.ebook;

import java.io.*;
import java.nio.file.*;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import javax.imageio.ImageIO;
import com.android.app.Activity;
import com.android.graphics.Canvas;
import com.android.view.*;
import com.android.os.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.*;

public final class PdfImportTest {
    static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    static void rejected(BookLibrary library,File file)throws Exception{try{library.importFile(file);throw new AssertionError("Must reject "+file);}catch(IOException expected){}}
    static void draw(EbookView view)throws Exception{
        view.measure(View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(880,View.MeasureSpec.EXACTLY));view.layout(0,0,600,880);
        BufferedImage image=new BufferedImage(600,880,BufferedImage.TYPE_INT_RGB);try(Canvas c=new Canvas(image)){view.draw(c);}
        ImageIO.write(image,"png",new File("out/ebook-tests/pdf-reader.png"));
    }
    static void click(EbookView view,int x,int y){view.dispatchTouchEvent(new MotionEvent(0,x,y));view.dispatchTouchEvent(new MotionEvent(1,x,y));}
    static void complete(EbookView view)throws Exception{
        ((java.util.concurrent.Future<?>)field(view,"pdfTask")).get(30,java.util.concurrent.TimeUnit.SECONDS);
        MessageQueue queue=(MessageQueue)field(Looper.myLooper(),"mQueue");
        Method next=MessageQueue.class.getDeclaredMethod("next");next.setAccessible(true);
        while(queue.size()>0){Message m=(Message)next.invoke(queue);((Handler)field(m,"target")).dispatchMessage(m);}
        check(field(view,"pdfImage")!=null,"Background render must reach UI");draw(view);
    }
    static EbookView view(){return new EbookView(new Activity(){@Override public ClassLoader getClassLoader(){return PdfImportTest.class.getClassLoader();}});}
    public static void main(String[] args)throws Exception{
        File root=new File(System.getProperty("user.home"));root.mkdirs();File source=new File(root,"课本.PDF");
        try(PDDocument doc=new PDDocument()){
            for(int i=0;i<2;i++){
                PDPage page=new PDPage(new PDRectangle(300,400));doc.addPage(page);
                try(PDPageContentStream stream=new PDPageContentStream(doc,page)){stream.setNonStrokingColor(i==0?Color.RED:Color.BLUE);stream.addRect(0,0,300,400);stream.fill();}
            }doc.save(source);
        }
        BookLibrary library=new BookLibrary(new File(root,".local/share/framework/ebook/books"));
        BookLibrary.Book book=library.importFile(source);
        check(book.format.equals("PDF")&&book.text.equals("2"),"PDF metadata");
        check(library.importFile(source).id.equals(book.id)&&library.load().size()==1,"Deduplication");
        Files.delete(source.toPath());check(PdfReader.pageCount(book.assets)==2,"Independent PDF copy");
        BufferedImage red=PdfReader.render(book.assets,0,300,400,1),blue=PdfReader.render(book.assets,1,300,400,1);
        check((red.getRGB(150,200)&0xffffff)==0xff0000,"Preserve page art");
        check((blue.getRGB(150,200)&0xffffff)==0x0000ff,"Distinct page art");
        File bad=new File(root,"bad.pdf");Files.write(bad.toPath(),new byte[]{1,2,3});rejected(library,bad);
        File encrypted=new File(root,"locked.pdf");try(PDDocument doc=new PDDocument()){doc.addPage(new PDPage());doc.protect(new StandardProtectionPolicy("owner","secret",new AccessPermission()));doc.save(encrypted);}rejected(library,encrypted);
        File huge=new File(root,"huge.pdf");try(RandomAccessFile f=new RandomAccessFile(huge,"rw")){f.setLength(256L*1024*1024+1);}rejected(library,huge);
        Looper.prepareMainLooper();EbookView view=view();view.addImported(book);draw(view);complete(view);
        view.dispatchKeyEvent(new KeyEvent(KeyEvent.DOWN,KeyEvent.RIGHT,0));draw(view);complete(view);
        check(((Integer)field(view,"page"))==1,"PDF page turn");
        Method zoom=EbookView.class.getDeclaredMethod("zoomPdf",double.class);zoom.setAccessible(true);zoom.invoke(view,1.5);draw(view);complete(view);
        check(((BufferedImage)field(view,"pdfImage")).getHeight()>600,"Zoom rerenders page");
        view.dispatchTouchEvent(new MotionEvent(0,300,350));view.dispatchTouchEvent(new MotionEvent(1,300,420));draw(view);
        check(((Double)field(view,"pdfY"))>0,"Zoomed page pans");
        click(view,300,835);draw(view);
        check(((java.util.Properties)field(view,"state")).containsKey("mark."+book.id),"PDF bookmark saved");
        view.dispatchKeyEvent(new KeyEvent(KeyEvent.DOWN,KeyEvent.LEFT,0));draw(view);complete(view);
        click(view,500,90);draw(view);complete(view);
        check(((Integer)field(view,"page"))==1,"PDF bookmark jump");
        view.save();view.close();EbookView restored=view();restored.addImported(book);draw(restored);complete(restored);
        check(((Integer)field(restored,"page"))==1,"Restore PDF progress");restored.close();
        File textbook=new File("packages/apps/ebook/res/语文 统编版 一年级 上册 2024版.pdf");
        if(textbook.isFile()){
            check(PdfReader.pageCount(textbook)>1,"Real textbook pages");
            BufferedImage cover=PdfReader.render(textbook,0,900,1200,1);
            int colored=0;for(int y=0;y<cover.getHeight();y+=10)for(int x=0;x<cover.getWidth();x+=10)if((cover.getRGB(x,y)&0xffffff)!=0xffffff)colored++;
            check(ImageIO.getImageReadersByFormatName("JPEG2000").hasNext(),"Packaged JPEG2000 reader registered");
            check(colored>100,"Real textbook image must not render blank");
            ImageIO.write(cover,"png",new File("out/ebook-tests/pdf-textbook.png"));
        }
        System.out.println("PDF import, render, navigation, zoom and persistence passed");
    }
}
