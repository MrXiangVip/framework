/*
* 源码路径: frameworks/base/core/java/android/os/Looper.java
* */
package com.android.os;

public final class Looper {
    private static Looper sMainLooper;  // guarded by Looper.class

    static final ThreadLocal<Looper> sThreadLocal = new ThreadLocal<Looper>();
    final MessageQueue mQueue;
    final Thread mThread;

    public static void prepareMainLooper() {
        prepare(false);
        synchronized (Looper.class) {
            if (sMainLooper != null) {
                throw new IllegalStateException("The main Looper has already been prepared.");
            }
            sMainLooper = myLooper();
        }
    }
    public static  Looper myLooper() {
        return sThreadLocal.get();
    }
    private Looper(boolean quitAllowed) {
        mQueue = new MessageQueue(quitAllowed);
        mThread = Thread.currentThread();
    }

    private static void prepare(boolean quitAllowed) {
        if (sThreadLocal.get() != null) {
            throw new RuntimeException("Only one Looper may be created per thread");
        }
        sThreadLocal.set(new Looper(quitAllowed));
    }

    public static void loop() {
        final Looper me = myLooper();
        final MessageQueue queue = me.mQueue;
        for (; ; ) {

            Message msg = queue.next(); // might block
            msg.target.dispatchMessage(msg);

        }
    }

    public static Looper getMainLooper() {
        synchronized (Looper.class) {
            return sMainLooper;
        }
    }
}
