package com.packages.windward.tests;
import com.android.app.*;
import com.android.os.*;
import com.android.res.Configuration;
import com.android.view.*;
import java.io.File;
import java.lang.reflect.*;
import javax.imageio.ImageIO;

/** Parent classpath excludes game and LWJGL, exercising the actual packaged APK. */
public final class WindowTest {
    static Object field(Object o,String key)throws Exception{Field f=o.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(o);}
    static void set(Object o,String key,Object value)throws Exception{Field f=o.getClass().getDeclaredField(key);f.setAccessible(true);f.set(o,value);}
    static void queue(Object game,Runnable task)throws Exception{Method m=game.getClass().getDeclaredMethod("queue",Runnable.class);m.setAccessible(true);m.invoke(game,task);}
    static void check(boolean b,String reason){if(!b)throw new AssertionError(reason);}
    static void pump(long millis)throws Exception{
        MessageQueue q=(MessageQueue)field(Looper.myLooper(),"mQueue");Method next=MessageQueue.class.getDeclaredMethod("next");next.setAccessible(true);
        long end=System.nanoTime()+millis*1000000;
        while(System.nanoTime()<end){if(q.size()>0){Message m=(Message)next.invoke(q);((Handler)field(m,"target")).dispatchMessage(m);}else Thread.sleep(3);}
    }
    static void key(View v,int type,int key){v.dispatchKeyEvent(new KeyEvent(type,key,0));}
    public static void main(String[] args)throws Exception{
        Looper.prepareMainLooper();
        ApplicationStartInfo info=new ApplicationStartInfo("com.packages.windward","com.packages.windward.MainActivity",new File("out/system/app/windward/windward.apk").getAbsolutePath());
        try(LoadedApk apk=new LoadedApk(info)){
            check(apk.getActivityLabel(info.activityName).equals("风起山野"),"Launcher label");
            Activity a=(Activity)apk.getClassLoader().loadClass(info.activityName).newInstance();
            a.attach(new Activity(){@Override public ClassLoader getClassLoader(){return apk.getClassLoader();}},null,new Configuration(),null);
            a.performCreate();a.performResume();View decor=a.getWindow().getDecorView();Object game=field(a,"game");View view=(View)game;
            try{
                a.getWindowManager().addView(decor,a.getWindow().getAttributes());
                long deadline=System.nanoTime()+20000000000L;
                while(field(game,"frame")==null){check(System.nanoTime()<deadline,"Startup timeout: "+field(game,"error"));pump(30);}
                check(field(game,"error").equals(""),"GL initializes from isolated APK");
                ImageIO.write(ViewRootImpl.renderToImage(decor,1200,800),"png",new File("out/windward-tests/framework-window.png"));
                double z=(double)field(field(game,"frame"),"z");key(view,KeyEvent.DOWN,'w');pump(400);key(view,KeyEvent.UP,'w');pump(100);
                double moved=(double)field(field(game,"frame"),"z");check(moved<z-.4,"Framework key dispatch moves character");pump(150);check(Math.abs((double)field(field(game,"frame"),"z")-moved)<.01,"Release stops walking");
                key(view,KeyEvent.DOWN,KeyEvent.ESCAPE);key(view,KeyEvent.UP,KeyEvent.ESCAPE);pump(150);double time=(double)field(field(game,"frame"),"time");pump(200);
                check((boolean)field(field(game,"frame"),"paused"),"Escape pauses");check((double)field(field(game,"frame"),"time")==time,"Pause freezes simulation");
                ImageIO.write(ViewRootImpl.renderToImage(decor,1200,800),"png",new File("out/windward-tests/paused.png"));
                key(view,KeyEvent.DOWN,KeyEvent.ENTER);key(view,KeyEvent.UP,KeyEvent.ENTER);pump(150);check(!(boolean)field(field(game,"frame"),"paused"),"Enter resumes");
                key(view,KeyEvent.DOWN,'r');key(view,KeyEvent.UP,'r');pump(100);check(Math.abs((double)field(field(game,"frame"),"z")-12)<.01,"Restart returns to camp");
                view.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_DOWN,400,300,3,0,0));view.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_MOVE,480,330,3,0,0));view.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_UP,480,330,3,0,0));pump(100);
                check(Math.abs((double)field(field(game,"camera"),"yaw"))>.1,"Right drag rotates camera");
                queue(game,()->{try{
                    Object w=field(game,"world");Method reset=w.getClass().getDeclaredMethod("reset");reset.setAccessible(true);reset.invoke(w);
                    set(w,"x",-7.0);set(w,"z",-10.6);Method floor=w.getClass().getDeclaredMethod("floor");floor.setAccessible(true);set(w,"y",floor.invoke(w));
                    Object c=field(game,"camera");set(c,"yaw",.45);set(c,"distance",5.0);
                }catch(Exception e){throw new RuntimeException(e);}});pump(150);
                key(view,KeyEvent.DOWN,'e');key(view,KeyEvent.UP,'e');key(view,KeyEvent.DOWN,'w');pump(400);key(view,KeyEvent.UP,'w');pump(100);
                check((boolean)field(field(game,"frame"),"climbing"),"E key attaches and W climbs");
                ImageIO.write(ViewRootImpl.renderToImage(decor,1200,800),"png",new File("out/windward-tests/climbing-window.png"));
                key(view,KeyEvent.DOWN,KeyEvent.ESCAPE);key(view,KeyEvent.UP,KeyEvent.ESCAPE);pump(150);
                double savedStamina=(double)field(field(game,"frame"),"stamina");pump(150);check((double)field(field(game,"frame"),"stamina")==savedStamina,"Paused climbing freezes stamina");
                key(view,KeyEvent.DOWN,KeyEvent.ENTER);key(view,KeyEvent.UP,KeyEvent.ENTER);pump(100);
                key(view,KeyEvent.DOWN,'e');key(view,KeyEvent.UP,'e');pump(100);check(!(boolean)field(field(game,"frame"),"climbing"),"E releases climb");
                queue(game,()->{try{Object w=field(game,"world");set(w,"x",-13.0);set(w,"z",-20.0);set(w,"y",8.0);set(w,"grounded",false);set(w,"vy",-3.0);}catch(Exception e){throw new RuntimeException(e);}});pump(100);
                key(view,KeyEvent.DOWN,' ');key(view,KeyEvent.UP,' ');pump(150);check((boolean)field(field(game,"frame"),"gliding"),"Space key deploys airborne wing");
                ImageIO.write(ViewRootImpl.renderToImage(decor,1200,800),"png",new File("out/windward-tests/gliding-window.png"));
                key(view,KeyEvent.DOWN,'r');key(view,KeyEvent.UP,'r');pump(150);check(!(boolean)field(field(game,"frame"),"gliding")&&(double)field(field(game,"frame"),"stamina")==100,"R resets traversal through window");
                queue(game,()->{try{
                    Object w=field(game,"world");Method reset=w.getClass().getDeclaredMethod("reset");reset.setAccessible(true);reset.invoke(w);
                    set(w,"x",3.0);set(w,"z",-10.4);set(w,"facing",Math.PI);set(w,"visualFacing",Math.PI);
                    Method floor=w.getClass().getDeclaredMethod("floor");floor.setAccessible(true);set(w,"y",floor.invoke(w));
                    Object c=field(game,"camera");set(c,"yaw",.6);set(c,"distance",5.5);
                }catch(Exception e){throw new RuntimeException(e);}});pump(100);
                key(view,KeyEvent.DOWN,'j');pump(220);check((int)field(field(game,"frame"),"enemyHealth")==2,"J key damages guard");
                key(view,KeyEvent.DOWN,'j');pump(80);check((int)field(field(game,"frame"),"enemyHealth")==2,"Held J repeat filtered");key(view,KeyEvent.UP,'j');
                ImageIO.write(ViewRootImpl.renderToImage(decor,1200,800),"png",new File("out/windward-tests/combat-window.png"));
                pump(300);view.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_DOWN,400,300,1,0,0));view.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_UP,400,300,1,0,0));pump(220);
                check((int)field(field(game,"frame"),"enemyHealth")==1,"Mouse left button attacks");pump(350);
                key(view,KeyEvent.DOWN,'k');key(view,KeyEvent.UP,'k');pump(100);check((boolean)field(field(game,"frame"),"dodging"),"K key starts dodge");
                queue(game,()->{try{set(field(field(game,"world"),"combat"),"health",0);}catch(Exception e){throw new RuntimeException(e);}});pump(100);
                ImageIO.write(ViewRootImpl.renderToImage(decor,1200,800),"png",new File("out/windward-tests/defeated-window.png"));
                key(view,KeyEvent.DOWN,'r');key(view,KeyEvent.UP,'r');pump(150);check((int)field(field(game,"frame"),"health")==100&&(int)field(field(game,"frame"),"enemyHealth")==3,"R restores player and guard");
                queue(game,()->{try{
                    Object w=field(game,"world");Method reset=w.getClass().getDeclaredMethod("reset");reset.setAccessible(true);reset.invoke(w);set(field(w,"combat"),"enemyHealth",0);
                    set(w,"x",22.0);set(w,"z",-17.85);Method floor=w.getClass().getDeclaredMethod("floor");floor.setAccessible(true);set(w,"y",floor.invoke(w));
                    Object c=field(game,"camera");set(c,"yaw",.65);set(c,"distance",6.0);
                }catch(Exception e){throw new RuntimeException(e);}});pump(150);
                key(view,KeyEvent.DOWN,'e');key(view,KeyEvent.UP,'e');key(view,KeyEvent.DOWN,'w');pump(350);key(view,KeyEvent.UP,'w');pump(100);
                check((boolean)field(field(game,"frame"),"pushing")&&(double)field(field(game,"frame"),"stoneZ")<-19.2,"E and W move push stone through window");
                ImageIO.write(ViewRootImpl.renderToImage(decor,1200,800),"png",new File("out/windward-tests/pushing-window.png"));
                key(view,KeyEvent.DOWN,KeyEvent.ESCAPE);key(view,KeyEvent.UP,KeyEvent.ESCAPE);pump(150);double stone=(double)field(field(game,"frame"),"stoneZ");pump(150);check((double)field(field(game,"frame"),"stoneZ")==stone,"Pause freezes push stone");
                key(view,KeyEvent.DOWN,KeyEvent.ENTER);key(view,KeyEvent.UP,KeyEvent.ENTER);pump(150);
                queue(game,()->{try{Object w=field(game,"world");set(field(w,"puzzle"),"stoneZ",-26.9);set(w,"z",-25.75);Method floor=w.getClass().getDeclaredMethod("floor");floor.setAccessible(true);set(w,"y",floor.invoke(w));}catch(Exception e){throw new RuntimeException(e);}});pump(1400);
                check((boolean)field(field(game,"frame"),"powered")&&!(boolean)field(field(game,"frame"),"pushing"),"Plate charges and releases push through window");
                queue(game,()->{try{Object w=field(game,"world");set(w,"x",18.5);set(w,"z",-23.6);Method floor=w.getClass().getDeclaredMethod("floor");floor.setAccessible(true);set(w,"y",floor.invoke(w));Object c=field(game,"camera");set(c,"yaw",.4);set(c,"pitch",.4);set(c,"distance",10.0);}catch(Exception e){throw new RuntimeException(e);}});pump(150);
                key(view,KeyEvent.DOWN,'e');key(view,KeyEvent.UP,'e');pump(150);check((boolean)field(field(game,"frame"),"complete")&&(boolean)field(field(game,"frame"),"summaryOpen"),"E activates tower and opens completion summary");
                double completedTime=(double)field(field(game,"frame"),"time");pump(150);check((double)field(field(game,"frame"),"time")==completedTime,"Completion summary freezes simulation");
                ImageIO.write(ViewRootImpl.renderToImage(decor,1200,800),"png",new File("out/windward-tests/completion-window.png"));
                key(view,KeyEvent.DOWN,KeyEvent.ENTER);key(view,KeyEvent.UP,KeyEvent.ENTER);pump(150);check(!(boolean)field(field(game,"frame"),"summaryOpen")&&(double)field(field(game,"frame"),"time")>completedTime,"Enter continues free exploration");
                key(view,KeyEvent.DOWN,'r');key(view,KeyEvent.UP,'r');pump(150);check(!(boolean)field(field(game,"frame"),"complete")&&!(boolean)field(field(game,"frame"),"powered")&&(double)field(field(game,"frame"),"stoneZ")==-19,"R resets completed region and puzzle");
                key(view,KeyEvent.DOWN,0xffe1);key(view,KeyEvent.DOWN,'w');pump(650);
                ImageIO.write(ViewRootImpl.renderToImage(decor,1200,800),"png",new File("out/windward-tests/running-window.png"));
                key(view,KeyEvent.UP,'w');key(view,KeyEvent.UP,0xffe1);
                queue(game,()->{try{
                    Object w=field(game,"world");Method reset=w.getClass().getDeclaredMethod("reset");reset.setAccessible(true);reset.invoke(w);
                    set(w,"x",175.0);set(w,"z",-130.0);Method floor=w.getClass().getDeclaredMethod("floor");floor.setAccessible(true);set(w,"y",floor.invoke(w));
                    Object c=field(game,"camera");set(c,"yaw",1.2);set(c,"distance",5.0);set(c,"pitch",.25);
                }catch(Exception e){throw new RuntimeException(e);}});pump(800);
                check((boolean)field(field(game,"frame"),"swimming")&&((String)field(field(game,"frame"),"traversalHint")).startsWith("踩水中"),"Deep water presents animated treading state");
                key(view,KeyEvent.DOWN,'j');key(view,KeyEvent.UP,'j');key(view,KeyEvent.DOWN,'k');key(view,KeyEvent.UP,'k');pump(100);
                check(!(boolean)field(field(game,"frame"),"attacking")&&!(boolean)field(field(game,"frame"),"dodging"),"Water input cannot attack or land-dodge");
                key(view,KeyEvent.DOWN,'w');pump(500);
                check(((String)field(field(game,"frame"),"traversalHint")).startsWith("游泳中"),"Directional input switches to swimming");
                ImageIO.write(ViewRootImpl.renderToImage(decor,1200,800),"png",new File("out/windward-tests/swimming-window.png"));
                key(view,KeyEvent.UP,'w');key(view,KeyEvent.DOWN,'r');key(view,KeyEvent.UP,'r');pump(150);
                // Returning across regions rebuilds static terrain before publishing the next frame.
                long restartDeadline=System.nanoTime()+5000000000L;
                while((boolean)field(field(game,"frame"),"swimming")&&System.nanoTime()<restartDeadline)pump(30);
                check(!(boolean)field(field(game,"frame"),"swimming"),"Restart returns from swimming to dry camp");
                view.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_DOWN,400,300));
                key(view,KeyEvent.DOWN,'w');view.clearFocus();pump(150);check((boolean)field(field(game,"frame"),"paused"),"Focus loss pauses");
                a.getWindow().setLayout(800,600);pump(150);
                ImageIO.write(ViewRootImpl.renderToImage(decor,800,600),"png",new File("out/windward-tests/resized.png"));
                check(field(game,"error").equals(""),"No runtime errors");
            }finally{
                a.getWindowManager().removeView(decor);a.performDestroy();Thread worker=(Thread)field(game,"worker");if(worker!=null){worker.join(5000);check(!worker.isAlive(),"Destruction releases render worker");}
            }
        }
        System.out.println("PASS: isolated APK, framework X11 window, input, running/swimming, combat keys/mouse, traversal, push/tower/summary, pause, restart, camera drag, focus, resize and GL cleanup");
    }
}
