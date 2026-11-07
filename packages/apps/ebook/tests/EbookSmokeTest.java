package com.packages.ebook;
import com.android.app.Activity;
import com.android.graphics.Canvas;
import com.android.view.View;
import com.android.view.MotionEvent;
import com.android.view.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import javax.imageio.ImageIO;
public final class EbookSmokeTest {
 private static Object value(Object v,String name)throws Exception {Field f=EbookView.class.getDeclaredField(name);f.setAccessible(true);return f.get(v);}
 private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 private static void click(EbookView view,int x,int y){view.dispatchTouchEvent(new MotionEvent(0,x,y));view.dispatchTouchEvent(new MotionEvent(1,x,y));}
 private static void render(EbookView view,int w,int h,String name)throws Exception{
  view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));view.layout(0,0,w,h);
  BufferedImage image=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
  try(Canvas canvas=new Canvas(image)){view.draw(canvas);}ImageIO.write(image,"png",new File("out/ebook-tests/"+name+".png"));
 }
 public static void main(String[] args)throws Exception{
  new File("out/ebook-tests").mkdirs();
  EbookView view=new EbookView(new Activity() { @Override public ClassLoader getClassLoader() { return EbookSmokeTest.class.getClassLoader(); } });
  for(BufferedImage cover:(BufferedImage[])value(view,"covers"))check(cover!=null,"Missing cover resource");
  render(view,600,880,"bookshelf");
  click(view,80,200);check((Integer)value(view,"book")==0,"Cover must open reader");
  render(view,600,880,"reader");
  view.dispatchKeyEvent(new KeyEvent(KeyEvent.DOWN,KeyEvent.RIGHT,0));check((Integer)value(view,"page")==1,"Next page");
  render(view,600,880,"page-two");click(view,530,35);render(view,600,880,"night");check((Boolean)value(view,"night"),"Night toggle");
  click(view,285,830);render(view,600,880,"bookmark");
  view.save();EbookView restored=new EbookView(new Activity() { @Override public ClassLoader getClassLoader() { return EbookSmokeTest.class.getClassLoader(); } });render(restored,600,880,"restored-shelf");click(restored,80,200);render(restored,600,880,"restored-reader");
  check((Integer)value(restored,"page")==1,"Progress must survive recreation");
  check((Boolean)value(restored,"night"),"Theme must survive recreation");
  restored.dispatchKeyEvent(new KeyEvent(KeyEvent.DOWN,KeyEvent.ESCAPE,0));render(restored,600,880,"returned");
  click(restored,470,40);restored.dispatchKeyEvent(new KeyEvent(KeyEvent.TEXT,'龙',0));render(restored,600,880,"search");
  check("龙".equals(value(restored,"query")),"Unicode search input");
  restored.dispatchKeyEvent(new KeyEvent(KeyEvent.DOWN,KeyEvent.BACKSPACE,0));check("".equals(value(restored,"query")),"Search backspace");
  render(restored,360,640,"compact");render(restored,1000,700,"landscape");
  System.out.println("PASS: covers, reader, pagination, theme, bookmark, saved progress, search and resize");
 }
}
