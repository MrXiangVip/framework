package com.packages.riftarena;

import java.awt.image.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Ownership under a slow consumer, fixed-time equivalence and tactical decisions. */
public final class ArenaOptimizationTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        ArenaFramePool pool=new ArenaFramePool(64,64);BufferedImage first=pool.beginWrite();pool.publish(first);pool.retain(first);BufferedImage second=pool.beginWrite();check(second!=first,"Cannot overwrite reader");pool.publish(second);pool.retain(second);BufferedImage third=pool.beginWrite();pool.publish(third);check(pool.beginWrite()==null,"Backpressure instead of overwrite/allocation");pool.release(first);check(pool.beginWrite()==first,"Released slot recycled");pool.publish(first);pool.release(second);
        AtomicReference<BufferedImage> current=new AtomicReference<>();AtomicReference<Throwable> failure=new AtomicReference<>();AtomicBoolean done=new AtomicBoolean();Set<BufferedImage> allocated=Collections.newSetFromMap(new IdentityHashMap<BufferedImage,Boolean>());
        Thread producer=new Thread(()->{try{for(int n=1;n<=1000;n++){BufferedImage target;while((target=pool.beginWrite())==null)Thread.yield();allocated.add(target);Arrays.fill(((DataBufferInt)target.getRaster().getDataBuffer()).getData(),n);synchronized(pool){pool.publish(target);current.set(target);}Thread.yield();}}catch(Throwable e){failure.set(e);}finally{done.set(true);}});
        producer.start();int read=0;while(!done.get()||read==0){if(done.get()&&failure.get()!=null)break;BufferedImage image;synchronized(pool){image=current.get();if(image!=null)pool.retain(image);}if(image==null){Thread.yield();continue;}try{int[] pixels=((DataBufferInt)image.getRaster().getDataBuffer()).getData();int value=pixels[0];Thread.yield();for(int pixel:pixels)check(pixel==value,"Published image changed while UI was reading");read++;}finally{pool.release(image);}}
        producer.join();if(failure.get()!=null)throw new AssertionError(failure.get());check(allocated.size()<=3,"Bounded frame storage");
        ArenaWorld a=ArenaSwordTest.fresh(),b=ArenaSwordTest.fresh();ArenaClock ca=new ArenaClock(),cb=new ArenaClock();for(int i=0;i<120;i++)ca.advance(1.0/120,true,()->a.update(ArenaClock.STEP,1,0,false));for(int i=0;i<30;i++)cb.advance(1.0/30,true,()->b.update(ArenaClock.STEP,1,0,false));check(Math.abs(a.player.x-b.player.x)<1e-8&&Math.abs(a.elapsed-b.elapsed)<1e-8,"Simulation independent of render cadence");double time=a.elapsed;ca.advance(1,false,()->a.update(ArenaClock.STEP,1,0,false));check(a.elapsed==time,"Paused wall time discarded");check(ca.advance(2,true,()->a.update(ArenaClock.STEP,0,0,false))==0,"Resume does not simulate paused wall time");int steps=ca.advance(2,true,()->a.update(ArenaClock.STEP,0,0,false));check(steps<=24&&ca.droppedSeconds>1,"Bounded stall catch-up");
        ArenaWorld w=new ArenaWorld();w.reset(0);w.waveTimer=1000;w.rival.x=0;w.rival.hp=w.rival.maxHp*.2;w.player.x=1;double old=w.rival.x;w.update(.1,0,0,false);check(w.rival.x>old&&w.rival.retreating,"Low health retreats");w.rival.hp=w.rival.maxHp*.5;old=w.rival.x;w.update(.1,0,0,false);check(w.rival.x>old&&w.rival.retreating,"Retreat has recovery hysteresis");w.rival.hp=w.rival.maxHp;w.update(.01,0,0,false);check(!w.rival.retreating,"Healthy AI returns to battle");
        w=new ArenaWorld();w.reset(0);w.waveTimer=1000;w.rival.x=0;w.rival.z=0;w.player.x=-30;w.effects.add(new ArenaWorld.Effect(0,0,5,0,2,1.1));w.update(.1,0,0,false);check(Math.abs(w.rival.z)>.1,"AI moves out of delayed danger");
        w=new ArenaWorld();w.reset(0);w.waveTimer=1000;w.player.x=-33;w.rival.x=-10;w.rival.z=0;old=w.rival.x;w.update(.1,0,0,false);check(w.rival.x==old,"AI waits outside untanked tower");ArenaWorld.Unit ally=w.spawn(ArenaWorld.Kind.SOLDIER,1,-15,0);ally.speed=0;w.update(.1,0,0,false);check(w.rival.x<old,"AI follows a tanking minion");
        w=ArenaSwordTest.fresh();w.configure(w.player,2);w.hero=2;w.aimX=7;w.aimZ=0;check(w.cast(0)&&Math.abs(w.player.x-7)<.001,"Mage blink retains seven-unit travel");w.cooldowns[1]=0;w.cast(1);check(w.player.attackTime>0&&ArenaWorld.skillName(2,1).equals("奥术新星"),"Mage nova uses casting animation and its own name");
        System.out.println("PASS: triple-buffer concurrency, fixed clock, bounded catch-up, retreat, hazard avoidance, wave tactics and mage identity");
    }
}
