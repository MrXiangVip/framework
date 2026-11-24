package com.android.view;

/** Deterministic scheduler tests: no native window or wall-clock sleeps. */
public final class FrameClockTest {
    static final class Manual implements FrameClock.TimeSource, Choreographer.Scheduler {
        long now, target; Runnable pending;
        public long nanoTime() { return now; }
        public void schedule(Runnable callback,long deadline) {
            if(pending!=null) throw new AssertionError("Duplicate pending frame");
            pending=callback; target=deadline;
        }
        public void cancel() { pending=null; }
        void fire(long time) {
            now=time; Runnable action=pending; pending=null;
            if(action==null) throw new AssertionError("No scheduled frame");
            action.run();
        }
    }
    static void check(boolean condition) { if(!condition) throw new AssertionError(); }
    public static void main(String[] args) {
        Manual manual=new Manual();
        FrameClock clock=new FrameClock(60,manual);
        Choreographer choreographer=new Choreographer(clock,manual);
        long period=clock.periodNanos();
        int[] draws={0}; boolean[] dirty={false}; Object token=new Object();
        Runnable draw=()->{ dirty[0]=false; draws[0]++; };
        for(int i=0;i<100;i++) {
            if(!dirty[0]) { dirty[0]=true; choreographer.postCallback(3,draw,token); }
        }
        check(manual.target==period);
        manual.fire(period); check(draws[0]==1 && manual.pending==null);
        choreographer.postCallback(3,()->{
            FrameClock.Frame frame=choreographer.getCurrentFrame();
            check(frame.skippedTicks==3 && frame.targetNanos==5*period);
            check(frame.deadlineNanos==6*period);
            choreographer.postCallback(3,draw,token);
        },token);
        manual.fire(5*period+123);
        check(manual.target==6*period);
        manual.fire(6*period); check(draws[0]==2 && manual.pending==null);
        choreographer.postCallback(3,draw,token);
        choreographer.removeCallbacks(3,null,token);
        check(manual.pending==null);
        choreographer.postCallbackDelayed(3,draw,token,100);
        check(manual.target>=manual.now+100_000_000L);
        manual.fire(manual.target); check(draws[0]==3);
        for(int i=0;i<10000;i++) {
            choreographer.postCallback(3,draw,token);
            check(manual.target%period==0);
            manual.fire(manual.target+1000);
        }
        check(manual.pending==null && choreographer.getCurrentFrame()==null);
        choreographer.postCallback(3,()->{throw new IllegalStateException("test");},token);
        try { manual.fire(manual.target); throw new AssertionError(); }
        catch(IllegalStateException expected) { check(choreographer.getCurrentFrame()==null); }
        System.out.println("FrameClockTest passed");
    }
}
