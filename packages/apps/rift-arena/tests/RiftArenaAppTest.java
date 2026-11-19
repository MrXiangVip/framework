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
    static void pump(MessageQueue queue,Method next,long millis)throws Exception{
        long until=System.nanoTime()+millis*1000000L;
        while(System.nanoTime()<until){if(queue.size()>0){Message m=(Message)next.invoke(queue);((Handler)field(m,"target")).dispatchMessage(m);}else Thread.sleep(2);}
    }
    static void xdo(String... args)throws Exception{
        java.util.List<String> command=new java.util.ArrayList<>();command.add("xdotool");java.util.Collections.addAll(command,args);
        java.lang.Process p=new ProcessBuilder(command).inheritIO().start();check(p.waitFor()==0,"X11 input command succeeded");
    }
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
                if(Boolean.getBoolean("framework.riftarena.heldInput")){
                    com.android.wm.XWindow surface=(com.android.wm.XWindow)field(decor.getParent(),"mWindow");
                    com.android.wm.XWindow.Listener original=(com.android.wm.XWindow.Listener)field(surface,"mListener");
                    java.util.List<KeyEvent> nativeKeys=new java.util.ArrayList<>();
                    surface.registeListener(new com.android.wm.XWindow.Listener(){
                        public void onEvent(int width,int height,boolean closed){original.onEvent(width,height,closed);}
                        public void onInput(MotionEvent event){original.onInput(event);}
                        public void onMinimizedChanged(boolean minimized){original.onMinimizedChanged(minimized);}
                        public void onKeyInput(KeyEvent event){nativeKeys.add(event);original.onKeyInput(event);}
                    });
                    // Exercise real X11 auto-repeat, not direct dispatch of a single key pair.
                    java.lang.Process search=new ProcessBuilder("xdotool","search","--onlyvisible","--pid",java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0]).start();
                    String windows=new String(readAll(search.getInputStream()),"UTF-8").trim();check(search.waitFor()==0&&!windows.isEmpty(),"Find framework X11 window");
                    String window=windows.split("\\s+")[0];xdo("windowfocus","--sync",window);
                    View gameView=(View)game;gameView.dispatchKeyEvent(new KeyEvent(KeyEvent.DOWN,KeyEvent.ENTER,0));gameView.dispatchKeyEvent(new KeyEvent(KeyEvent.UP,KeyEvent.ENTER,0));pump(queue,next,200);
                    for(String direction:new String[]{"Right","Left","Down","Up","d","a","s","w"}){
                        boolean horizontal=direction.equals("Right")||direction.equals("Left")||direction.equals("d")||direction.equals("a");
                        boolean positive=direction.equals("Right")||direction.equals("Down")||direction.equals("d")||direction.equals("s");
                        String axis=horizontal?"x":"z";
                        int symbol=direction.length()==1?direction.charAt(0):direction.equals("Right")?KeyEvent.RIGHT:direction.equals("Left")?KeyEvent.LEFT:direction.equals("Down")?KeyEvent.ARROW_DOWN:KeyEvent.ARROW_UP;
                        Object before=field(game,"frame");double startPosition=(double)field(before,axis),startTime=(double)field(before,"time");
                        nativeKeys.clear();
                        xdo("keydown",direction);
                        try{
                            for(int sample=0;sample<6;sample++){
                                pump(queue,next,200);Object current=field(game,"frame");double position=(double)field(current,axis),time=(double)field(current,"time");
                                check(time>startTime,"Simulation keeps advancing during held "+direction+": "+field(game,"error"));
                                check(positive?position>startPosition:position<startPosition,"Player keeps moving during held "+direction+" at sample "+sample+" ("+startPosition+" -> "+position+")");
                                if(sample>0)check(Math.abs(Math.abs(position-startPosition)-(time-startTime)*8)<.14,"Held movement maintains full speed instead of alternating walk and idle: "+direction+" sample "+sample+" displacement="+Math.abs(position-startPosition)+" dt="+(time-startTime));
                                for(KeyEvent event:nativeKeys)check(event.getType()!=KeyEvent.UP||event.getValue()!=symbol,"Auto-repeat must not synthesize a release during held "+direction);
                                startPosition=position;startTime=time;
                            }
                            int repeats=0;for(KeyEvent event:nativeKeys)if(event.getType()==KeyEvent.DOWN&&event.getValue()==symbol)repeats++;
                            check(repeats>=3,"Native hold exercised real server auto-repeat");
                        }finally{xdo("keyup",direction);}
                        pump(queue,next,100);double stopped=(double)field(field(game,"frame"),axis);pump(queue,next,150);
                        check(Math.abs((double)field(field(game,"frame"),axis)-stopped)<.001,"Native key release stops movement");
                    }
                    for(String[] pair:new String[][]{{"Right","Left","x"},{"Left","Right","x"},{"Down","Up","z"},{"Up","Down","z"},{"d","a","x"},{"w","s","z"}}){
                        boolean positive=pair[1].equals("Right")||pair[1].equals("Down")||pair[1].equals("s");
                        xdo("keydown",pair[0]);pump(queue,next,250);double reversalPosition=(double)field(field(game,"frame"),pair[2]);
                        try{
                            xdo("keydown",pair[1]);pump(queue,next,100);double change=(double)field(field(game,"frame"),pair[2])-reversalPosition;
                            check(positive?change>.15:change<-.15,"Opposite key reverses within 100ms while original direction remains held: "+pair[0]+" -> "+pair[1]);
                            xdo("keyup",pair[1]);pump(queue,next,80);double resumed=(double)field(field(game,"frame"),pair[2]);pump(queue,next,100);double resumeChange=(double)field(field(game,"frame"),pair[2])-resumed;
                            check(positive?resumeChange<-.15:resumeChange>.15,"Releasing newest direction resumes original key: "+pair[0]);
                        }finally{xdo("keyup",pair[1],pair[0]);pump(queue,next,100);}
                    }
                    System.out.println("PASS: native auto-repeat without false releases, full-speed held movement and overlapping direction reversals within 100ms");
                }
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
    static byte[] readAll(InputStream input)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] bytes=new byte[1024];int n;while((n=input.read(bytes))!=-1)out.write(bytes,0,n);return out.toByteArray();}
}
