package com.wave;

public class Handler {
    final Looper mLooper;
    final MessageQueue mQueue;
    final Callback mCallback;
    final boolean mAsynchronous;

    private String TAG="Handler.";
    public Handler() {
        this(null, false);
    }

    public Handler( Callback callback) {
        this(callback, false);
    }
    public Handler( Callback callback, boolean async) {
        mLooper = Looper.myLooper();
        if (mLooper == null) {
            throw new RuntimeException(
                    "Can't create handler inside thread " + Thread.currentThread()
                            + " that has not called Looper.prepare()");
        }
        mQueue = mLooper.mQueue;
        mCallback = callback;
        mAsynchronous = async;
    }

    public Handler(Looper looper,  Callback callback) {
        this(looper, callback, false);
    }
    public Handler( Looper looper) {
        this(looper, null, false);
    }
    public Handler( Looper looper,  Callback callback, boolean async) {
        mLooper = looper;
        mQueue = looper.mQueue;
        mCallback = callback;
        mAsynchronous = async;
    }

    public final boolean sendMessage( Message msg) {
        return sendMessageDelayed(msg, 0);
    }
    public final boolean sendMessageDelayed( Message msg, long delayMillis) {
        if (delayMillis < 0) {
            delayMillis = 0;
        }
        return sendMessageAtTime(msg, delayMillis);
    }
    public boolean sendMessageAtTime( Message msg, long uptimeMillis) {
        MessageQueue queue = mQueue;
        if (queue == null) {
            return false;
        }
        return enqueueMessage(queue, msg, uptimeMillis);
    }
    private boolean enqueueMessage( MessageQueue queue,  Message msg,
                                   long uptimeMillis) {
        Log.d(TAG, "enqueueMessage");
        msg.target = this;

        return queue.enqueueMessage(msg, uptimeMillis);
    }

    public void handleMessage( Message msg) {
    }

    public void dispatchMessage( Message msg) {
        Log.d(TAG, "dispatchMessage");
        if (msg.callback != null) {
            handleCallback(msg);
        } else {
            if (mCallback != null) {
                if (mCallback.handleMessage(msg)) {
                    return;
                }
            }
            handleMessage(msg);
        }
    }
    public final Message obtainMessage(int what,  Object obj) {
        return Message.obtain(this, what, obj);
    }

    private static void handleCallback(Message message) {
        message.callback.run();
    }
    public interface Callback {
        boolean handleMessage( Message msg);

    }


}
