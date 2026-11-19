package com.packages.riftarena;

import com.android.app.Activity;
import com.android.graphics.Canvas;
import com.android.os.*;
import com.android.view.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;

public final class ArenaUiTest {
    static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void draw(ArenaView view,String name,int width,int height)throws Exception{
        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,width,height);
        BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);try(Canvas c=new Canvas(image)){view.draw(c);}if(name!=null)ImageIO.write(image,"png",new File("out/rift-arena-tests/"+name+".png"));
    }
    static ArenaView.Frame frame(ArenaView v){try{return (ArenaView.Frame)field(v,"frame");}catch(Exception e){throw new RuntimeException(e);}}
    static void waitFor(ArenaView view,java.util.function.BooleanSupplier ready)throws Exception{
        long deadline=System.nanoTime()+20000000000L;
        while(!ready.getAsBoolean()){
            check(System.nanoTime()<deadline,"UI timeout: "+field(view,"error"));Thread.sleep(15);
            MessageQueue queue=(MessageQueue)field(Looper.myLooper(),"mQueue");Method next=MessageQueue.class.getDeclaredMethod("next");next.setAccessible(true);
            while(queue.size()>0){Message m=(Message)next.invoke(queue);((Handler)field(m,"target")).dispatchMessage(m);}
        }
    }
    static void key(ArenaView v,int k){v.dispatchKeyEvent(new KeyEvent(KeyEvent.DOWN,k,0));v.dispatchKeyEvent(new KeyEvent(KeyEvent.UP,k,0));}
    public static void main(String[] args)throws Exception{
        ArenaWorld hudWorld=new ArenaWorld();hudWorld.reset(0);ArenaRenderer hudRenderer=new ArenaRenderer();
        check(ArenaView.skillState(new ArenaView.Frame(null,hudWorld,hudRenderer),0).equals("就绪"),"Ready skill");
        hudWorld.cooldowns[0]=3;check(ArenaView.skillState(new ArenaView.Frame(null,hudWorld,hudRenderer),0).equals("3.0 s"),"Cooldown text");
        hudWorld.player.dashTime=.1;check(ArenaView.skillState(new ArenaView.Frame(null,hudWorld,hudRenderer),0).equals("冲刺"),"Dash takes precedence over cooldown");
        hudWorld.player.slamTime=.4;check(ArenaView.skillState(new ArenaView.Frame(null,hudWorld,hudRenderer),1).equals("蓄力"),"Slam windup");
        hudWorld.player.slamLanded=true;check(ArenaView.skillState(new ArenaView.Frame(null,hudWorld,hudRenderer),1).equals("收招"),"Slam recovery");
        hudWorld.player.meteorTime=.3;check(ArenaView.skillState(new ArenaView.Frame(null,hudWorld,hudRenderer),2).equals("蓄力"),"Meteor windup");
        hudWorld.player.meteorTime=.1;check(ArenaView.skillState(new ArenaView.Frame(null,hudWorld,hudRenderer),2).equals("收招"),"Meteor recovery");
        check(ArenaView.purchaseState(new ArenaView.Frame(null,hudWorld,hudRenderer),0).equals("可购买"),"Fountain purchase available");
        hudWorld.gold=0;check(ArenaView.purchaseState(new ArenaView.Frame(null,hudWorld,hudRenderer),0).equals("金币不足"),"Purchase affordability");
        hudWorld.player.x=0;check(ArenaView.purchaseState(new ArenaView.Frame(null,hudWorld,hudRenderer),0).equals("需回泉水"),"Fountain restriction");
        hudWorld.player.hp=0;ArenaView.Frame deadHud=new ArenaView.Frame(null,hudWorld,hudRenderer);
        check(ArenaView.skillState(deadHud,2).equals("阵亡")&&ArenaView.purchaseState(deadHud,1).equals("阵亡不可购买"),"Death overrides active and shopping states");
        Looper.prepareMainLooper();ArenaView v=new ArenaView(new Activity());
        try{
            draw(v,null,1200,800);waitFor(v,()->frame(v)!=null);check(((String)field(v,"error")).isEmpty(),"Real OpenGL startup");draw(v,"title",1200,800);
            key(v,'2');key(v,KeyEvent.ENTER);waitFor(v,()->frame(v).screen==ArenaWorld.Screen.PLAYING&&frame(v).hero==1);draw(v,"playing",1200,800);
            final ArenaView.Frame aimFrame=frame(v);
            v.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_HOVER,720,450,0,0,0));
            ArenaWorld aimWorld=(ArenaWorld)field(v,"world");waitFor(v,()->Math.abs(aimWorld.aimX-(aimFrame.cameraX+120/ArenaRenderer.HUD_SCALE))<.001);
            check(Math.abs(aimWorld.aimZ-(aimFrame.cameraZ+50/(ArenaRenderer.HUD_SCALE*ArenaRenderer.SIN)))<.001,"Zoomed mouse coordinates match ground projection");
            // Thousands of pointer updates occupy one coalesced slot, not thousands of commands.
            for(int i=0;i<2000;i++)v.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_HOVER,700+i%100,450,0,0,0));
            @SuppressWarnings("unchecked") ConcurrentLinkedQueue<Runnable> aimCommands=(ConcurrentLinkedQueue<Runnable>)field(v,"commands");check(aimCommands.size()<10,"Mouse aim does not flood command queue");
            v.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_SCROLL,700,450,0,0,-1));waitFor(v,()->frame(v).zoom>1.05);draw(v,"zoomed-ui",1200,800);
            final ArenaView.Frame zoomed=frame(v);v.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_HOVER,720,450,0,0,0));waitFor(v,()->Math.abs(aimWorld.aimX-(zoomed.cameraX+120/zoomed.scale))<.001);check(Math.abs(aimWorld.aimZ-(zoomed.cameraZ+50/(zoomed.scale*ArenaRenderer.SIN)))<.001,"Mouse projection follows zoom");
            v.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_SCROLL,700,450,0,0,1));waitFor(v,()->Math.abs(frame(v).zoom-1)<.001);
            key(v,32);waitFor(v,()->frame(v).notice.contains("射程内无敌人"));draw(v,"basic-attack",1200,800);
            final double x=frame(v).x;v.dispatchKeyEvent(new KeyEvent(KeyEvent.DOWN,'d',0));waitFor(v,()->frame(v).x>x+.5);v.dispatchKeyEvent(new KeyEvent(KeyEvent.UP,'d',0));
            // Verify release and reversal using published frames rather than OS key-repeat timing.
            double releaseTime=frame(v).time;waitFor(v,()->frame(v).time>releaseTime+.05);
            final double stoppedX=frame(v).x,stoppedTime=frame(v).time;
            waitFor(v,()->frame(v).time>stoppedTime+.08);
            check(Math.abs(frame(v).x-stoppedX)<.001,"Release stops movement without continued drift");
            v.dispatchKeyEvent(new KeyEvent(KeyEvent.DOWN,'a',0));waitFor(v,()->frame(v).x<stoppedX-.2);
            v.dispatchKeyEvent(new KeyEvent(KeyEvent.UP,'a',0));

            final double before=frame(v).x;v.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_DOWN,600,400,3,0,0));v.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_UP,600,400,3,0,0));waitFor(v,()->frame(v).x>before+.5);
            @SuppressWarnings("unchecked") ConcurrentLinkedQueue<Runnable> feedbackCommands=(ConcurrentLinkedQueue<Runnable>)field(v,"commands");
            ArenaWorld feedbackWorld=(ArenaWorld)field(v,"world");
            feedbackCommands.add(()->{feedbackWorld.player.x=0;feedbackWorld.player.z=10;feedbackWorld.player.cooldown=0;feedbackWorld.shots.clear();feedbackWorld.impacts.clear();ArenaWorld.Unit dummy=feedbackWorld.spawn(ArenaWorld.Kind.SOLDIER,1,4,12);dummy.speed=0;dummy.attack=0;feedbackWorld.requestAttack();});
            waitFor(v,()->!frame(v).damageTexts.isEmpty());draw(v,"attack-impact",1200,800);
            v.dispatchTouchEvent(new MotionEvent(0,571,720));v.dispatchTouchEvent(new MotionEvent(1,571,720));waitFor(v,()->frame(v).cooldowns[0]>0);key(v,'e');key(v,'r');waitFor(v,()->frame(v).cooldowns[1]>0&&frame(v).cooldowns[2]>0);draw(v,"skills",1200,800);
            key(v,KeyEvent.ESCAPE);waitFor(v,()->frame(v).screen==ArenaWorld.Screen.PAUSED);draw(v,"paused",1200,800);draw(v,"resized",800,800);
            // Click Continue through the square window's letterboxed coordinate transform.
            v.dispatchTouchEvent(new MotionEvent(0,400,383));v.dispatchTouchEvent(new MotionEvent(1,400,383));waitFor(v,()->frame(v).screen==ArenaWorld.Screen.PLAYING);
            @SuppressWarnings("unchecked") ConcurrentLinkedQueue<Runnable> commands=(ConcurrentLinkedQueue<Runnable>)field(v,"commands");ArenaWorld w=(ArenaWorld)field(v,"world");
            commands.add(()->{w.player.x=0;w.damage(w.player,10000,1);});waitFor(v,()->frame(v).hp==0);draw(v,"respawn",1200,800);
            commands.add(()->{w.player.respawn=.01;});waitFor(v,()->frame(v).hp>0);
            commands.add(()->{for(ArenaWorld.Unit u:w.units)if(u.team==1&&u.kind==ArenaWorld.Kind.TOWER)w.damage(u,10000,0);for(ArenaWorld.Unit u:w.units)if(u.team==1&&u.kind==ArenaWorld.Kind.CORE)w.damage(u,10000,0);});waitFor(v,()->frame(v).screen==ArenaWorld.Screen.WON);draw(v,"victory",1200,800);
            key(v,KeyEvent.ENTER);waitFor(v,()->frame(v).screen==ArenaWorld.Screen.PLAYING&&frame(v).deaths==0&&frame(v).cooldowns[0]==0);
            commands.add(()->{w.player.x=0;for(int i=0;i<150;i++)w.update(.1,0,0,true);w.player.hp=w.player.maxHp;w.player.x=w.rival.x-6;w.player.z=0;w.player.respawn=0;});waitFor(v,()->frame(v).time>=15);draw(v,"combat",1200,800);
            // Select a displayed enemy body, not just its ground position.
            ArenaView.Dot target=null;for(ArenaView.Dot dot:frame(v).dots)if(dot.team==1&&dot.kind==ArenaWorld.Kind.HERO)target=dot;
            if(target!=null){int px=(int)(600+(target.x-frame(v).cameraX)*ArenaRenderer.HUD_SCALE),py=(int)(400+(target.z-frame(v).cameraZ)*ArenaRenderer.HUD_SCALE*ArenaRenderer.SIN-20);
                v.dispatchTouchEvent(new MotionEvent(0,px,py,3,0,0));commands.add(()->check(w.selected!=null,"Enemy body picking"));}
            commands.add(()->{w.reset(0);w.player.x=-12;w.player.z=0;w.waveTimer=100;w.aimX=-8;w.aimZ=0;ArenaWorld.Unit dummy=w.spawn(ArenaWorld.Kind.SOLDIER,1,-9,0);dummy.speed=dummy.range=dummy.attack=0;});
            waitFor(v,()->frame(v).hero==0&&frame(v).x==-12&&frame(v).cooldowns[1]==0);
            key(v,'e');waitFor(v,()->frame(v).slamTime>0&&!frame(v).slamLanded);draw(v,"slam-charge-ui",1200,800);
            waitFor(v,()->frame(v).slamLanded&&!frame(v).damageTexts.isEmpty());draw(v,"slam-impact-ui",1200,800);
            v.dispatchKeyEvent(new KeyEvent(KeyEvent.DOWN,'a',0));waitFor(v,()->frame(v).x<-12.1&&frame(v).slamTime==0);v.dispatchKeyEvent(new KeyEvent(KeyEvent.UP,'a',0));
            double dashX=frame(v).x;key(v,'q');waitFor(v,()->frame(v).dashTime>0);draw(v,"dash-ui",1200,800);
            waitFor(v,()->frame(v).dashTime==0&&frame(v).x>dashX+5);check(frame(v).cooldowns[0]>0,"Q key dispatch and cooldown");
            commands.add(()->{
                w.reset(0);w.waveTimer=100;w.player.x=17;w.player.z=0;
                w.rival.x=35;w.rival.z=10;
                for(ArenaWorld.Unit u:w.units)if(u.kind==ArenaWorld.Kind.TOWER&&u.team==1){u.attack=0;w.selected=u;}
            });
            waitFor(v,()->frame(v).hero==0&&frame(v).dots.stream().anyMatch(d->d.selected&&d.threat));
            draw(v,"target-tower-warning",1200,800);
            commands.add(()->w.damage(w.player,150,1));
            waitFor(v,()->frame(v).dots.stream().anyMatch(d->d.player&&d.trail>d.hp));
            draw(v,"health-damage-trail",1200,800);
            v.pause();waitFor(v,()->frame(v).screen==ArenaWorld.Screen.PAUSED);
            double frozenTrail=frame(v).dots.stream().filter(d->d.player).findFirst().get().trail;
            draw(v,null,1200,800);
            check(frame(v).dots.stream().filter(d->d.player).findFirst().get().trail==frozenTrail,"Pause freezes health feedback");
            commands.add(()->{
                ArenaWorld.Unit soldier=w.spawn(ArenaWorld.Kind.SOLDIER,0,21,0);soldier.speed=0;
                for(ArenaWorld.Unit u:w.units)if(u.kind==ArenaWorld.Kind.TOWER&&u.team==1)check(!ArenaRenderer.towerThreat(w,u),"Tower prioritizes minion over player");
                w.resume();w.damage(w.selected,10000,0);
            });
            waitFor(v,()->frame(v).dots.stream().noneMatch(d->d.selected||d.threat));
            commands.add(()->{
                w.reset(1);w.units.clear();w.units.add(w.player);w.player.x=w.player.z=0;w.waveTimer=1000;w.aimX=12;w.aimZ=0;
                for(int i=-1;i<=1;i++){double a=Math.toRadians(i*18);ArenaWorld.Unit scatterDummy=w.spawn(ArenaWorld.Kind.SOLDIER,1,7*Math.cos(a),7*Math.sin(a));scatterDummy.speed=scatterDummy.range=scatterDummy.attack=0;}
            });
            waitFor(v,()->frame(v).hero==1&&frame(v).x==0&&frame(v).cooldowns[1]==0);
            draw(v,"scatter-aim-ui",1200,800);
            v.dispatchTouchEvent(new MotionEvent(0,671,720));v.dispatchTouchEvent(new MotionEvent(1,671,720));
            waitFor(v,()->frame(v).scatterDraw);check(ArenaView.skillState(frame(v),1).equals("拉弓"),"Scatter UI windup state");
            draw(v,"scatter-charge-ui",1200,800);
            final double scatterStart=frame(v).time;
            waitFor(v,()->!frame(v).scatterDraw&&frame(v).time>scatterStart+.26);draw(v,"scatter-flight-ui",1200,800);
            waitFor(v,()->frame(v).dots.stream().filter(d->d.team==1).allMatch(d->d.hp<d.maxHp));
            check(frame(v).cooldowns[1]>0,"E button click starts scatter cooldown");

            commands.add(()->{w.reset(0);w.player.x=w.player.z=0;w.waveTimer=1000;});waitFor(v,()->frame(v).hero==0&&frame(v).x==0&&frame(v).cooldowns[2]==0);
            CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
            commands.add(()->{entered.countDown();try{check(release.await(5,TimeUnit.SECONDS),"Release queued input test");}catch(InterruptedException e){Thread.currentThread().interrupt();}});
            check(entered.await(5,TimeUnit.SECONDS),"Worker reaches input ordering gate");
            try{
                v.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_HOVER,720,400,0,0,0));key(v,'r');
                v.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_HOVER,480,400,0,0,0));
            }finally{release.countDown();}
            waitFor(v,()->frame(v).cooldowns[2]>0);
            check(!w.meteors.isEmpty()&&w.meteors.get(0).x>0&&w.aimX<0,"Queued cast retains its aim while newer pointer aim remains current");
        }finally{v.close();Thread worker=(Thread)field(v,"worker");worker.join(5000);check(!worker.isAlive(),"Close stops render worker and frees native resources");}
        System.out.println("PASS: real GL, selection, keyboard, right click, skills, pause, resized buttons, death/respawn, victory, restart and cleanup");
    }
}
