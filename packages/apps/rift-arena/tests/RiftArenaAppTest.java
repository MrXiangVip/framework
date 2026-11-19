package com.packages.riftarena.tests;

import com.android.app.*;
import com.android.os.*;
import com.android.res.Configuration;
import com.android.view.*;
import java.io.*;
import java.lang.reflect.*;
import javax.imageio.ImageIO;

/** Runs with only framework on the parent classpath, as a normal app process does. */
public final class RiftArenaAppTest {
    static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        Looper.prepareMainLooper();
        try{Class.forName("com.packages.riftarena.MainActivity");throw new AssertionError("APK leaked onto parent classpath");}catch(ClassNotFoundException expected){}
        ApplicationStartInfo info=new ApplicationStartInfo("com.packages.riftarena","com.packages.riftarena.MainActivity",new File("out/system/app/rift-arena/rift-arena.apk").getAbsolutePath());
        try(LoadedApk apk=new LoadedApk(info)){
            check(apk.getActivityLabel(info.activityName).equals("星隙竞技场"),"Desktop manifest label");
            Activity activity=(Activity)apk.getClassLoader().loadClass(info.activityName).getDeclaredConstructor().newInstance();
            activity.attach(new Activity(){@Override public ClassLoader getClassLoader(){return apk.getClassLoader();}},null,new Configuration(),null);
            activity.performCreate();activity.performResume();
            check(activity.getWindow().getAttributes().width==1200&&activity.getWindow().getAttributes().height==800,"Initial window size");
            View decor=activity.getWindow().getDecorView();activity.getWindowManager().addView(decor,activity.getWindow().getAttributes());
            Object game=field(activity,"game");
            try{
                MessageQueue queue=(MessageQueue)field(Looper.myLooper(),"mQueue");Method next=MessageQueue.class.getDeclaredMethod("next");next.setAccessible(true);
                long deadline=System.nanoTime()+20000000000L;
                while(field(game,"frame")==null){
                    check(System.nanoTime()<deadline,"App startup timeout: "+field(game,"error"));
                    if(queue.size()>0){Message m=(Message)next.invoke(queue);((Handler)field(m,"target")).dispatchMessage(m);}else Thread.sleep(10);
                }
                check(((String)field(game,"error")).isEmpty(),"Isolated APK native loading");
                ImageIO.write(ViewRootImpl.renderToImage(decor,1200,800),"png",new File("out/rift-arena-tests/framework-window.png"));
                if(Boolean.getBoolean("framework.riftarena.profile")){
                    View gameView=(View)game;gameView.dispatchKeyEvent(new KeyEvent(KeyEvent.DOWN,KeyEvent.ENTER,0));gameView.dispatchKeyEvent(new KeyEvent(KeyEvent.UP,KeyEvent.ENTER,0));
                    long until=System.nanoTime()+8000000000L;
                    while(System.nanoTime()<until){if(queue.size()>0){Message m=(Message)next.invoke(queue);((Handler)field(m,"target")).dispatchMessage(m);}else Thread.sleep(2);}
                    check(((String)field(game,"error")).isEmpty(),"Profile session rendered without errors");
                    Object metrics=field(game,"metrics");int[] counts=(int[])field(metrics,"counts");check(counts[0]>0&&counts[1]>0&&counts[2]>0&&counts[3]>0,"Game timings cover simulation, GL, UI and frame age");
                    ImageIO.write(ViewRootImpl.renderToImage(decor,1200,800),"png",new File("out/rift-arena-tests/profile-window.png"));
                }

            }finally{
                activity.getWindowManager().removeView(decor);activity.performDestroy();
                Thread worker=(Thread)field(game,"worker");worker.join(5000);check(!worker.isAlive(),"Window close stops render worker");
            }
        }
        System.out.println("PASS: isolated APK classloader, launcher label/icon, 1200x800 native framework window, real OpenGL and Activity cleanup");
    }
}
