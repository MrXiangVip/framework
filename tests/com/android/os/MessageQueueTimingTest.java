package com.android.os;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class MessageQueueTimingTest {
    static void check(boolean condition) { if(!condition) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        Handler handler=new Handler(); MessageQueue queue=Looper.myLooper().mQueue;
        long before=MessageQueue.nowMillis();
        Message delayed=handler.obtainMessage(1,null);
        handler.sendMessageDelayed(delayed,60);
        check(delayed.when>=before+60);
        check(queue.next()==delayed && MessageQueue.nowMillis()>=delayed.when);
        long due=MessageQueue.nowMillis()+20;
        Message a=handler.obtainMessage(2,null), b=handler.obtainMessage(3,null);
        handler.sendMessageAtTime(a,due); handler.sendMessageAtTime(b,due);
        check(queue.next()==a && queue.next()==b);
        Message late=handler.obtainMessage(4,null);
        handler.sendMessageDelayed(late,5000);
        CountDownLatch started=new CountDownLatch(1), completed=new CountDownLatch(1);
        Message[] received={null};
        Thread consumer=new Thread(()->{started.countDown(); received[0]=queue.next(); completed.countDown();});
        consumer.setDaemon(true); consumer.start(); check(started.await(1,TimeUnit.SECONDS));
        Message early=handler.obtainMessage(5,null); handler.sendMessage(early);
        check(completed.await(1,TimeUnit.SECONDS) && received[0]==early);
        handler.removeMessages(4); check(queue.isEmpty());
        com.android.view.Choreographer frames=com.android.view.Choreographer.getInstance();
        long[] ids=new long[3];
        frames.postCallback(3,()->{
            ids[0]=frames.getCurrentFrame().id;
            frames.postCallback(3,()->ids[2]=frames.getCurrentFrame().id,null);
        },null);
        frames.postCallback(3,()->ids[1]=frames.getCurrentFrame().id,null);
        Message firstFrame=queue.next(); firstFrame.target.dispatchMessage(firstFrame);
        check(ids[0]>0 && ids[0]==ids[1] && ids[2]==0);
        Message secondFrame=queue.next(); secondFrame.target.dispatchMessage(secondFrame);
        check(ids[2]>ids[0] && queue.isEmpty());
        System.out.println("MessageQueueTimingTest passed");
    }
}
