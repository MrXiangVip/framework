package com.android;

public final class Choreographer {
    public static final int VSYNC_SOURCE_APP = 0;
    private static volatile Choreographer mMainInstance;
    private final Looper mLooper;
    private final FrameHandler mHandler;
    private final Object mLock = new Object();


    private static final int MSG_DO_FRAME = 0;
    private static final int MSG_DO_SCHEDULE_VSYNC = 1;
    private static final int MSG_DO_SCHEDULE_CALLBACK = 2;

    public static final int CALLBACK_TRAVERSAL = 3;
    private static String TAG="Choreographer.";

    private Choreographer(Looper looper, int vsyncSource) {
        mLooper = looper;
        mHandler = new FrameHandler(looper);
/*         mDisplayEventReceiver = USE_VSYNC
                ? new FrameDisplayEventReceiver(looper, vsyncSource)
                : null;
        mLastFrameTimeNanos = Long.MIN_VALUE;

        mFrameIntervalNanos = (long)(1000000000 / getRefreshRate());

        mCallbackQueues = new CallbackQueue[CALLBACK_LAST + 1];
        for (int i = 0; i <= CALLBACK_LAST; i++) {
            mCallbackQueues[i] = new CallbackQueue();
        }
        // b/68769804: For low FPS experiments.
        setFPSDivisor(SystemProperties.getInt(ThreadedRenderer.DEBUG_FPS_DIVISOR, 1));*/
    }

    private static final ThreadLocal<Choreographer> sThreadInstance =
            new ThreadLocal<Choreographer>() {
                @Override
                protected Choreographer initialValue() {
                    Log.d(TAG,"initialValue");
                    Looper looper = Looper.myLooper();
                    if (looper == null) {
                        throw new IllegalStateException("The current thread must have a looper!");
                    }
                    Choreographer choreographer = new Choreographer(looper, VSYNC_SOURCE_APP);
                    if (looper == Looper.getMainLooper()) {
                        mMainInstance = choreographer;
                    }
                    return choreographer;
                }
            };

    public static Choreographer getInstance() {
        return sThreadInstance.get();
    }

    public void postCallback(int callbackType, Runnable action, Object token) {
        postCallbackDelayed(callbackType, action, token, 0);
    }
    public void postCallbackDelayed(int callbackType,
                                    Runnable action, Object token, long delayMillis) {


        postCallbackDelayedInternal(callbackType, action, token, delayMillis);
    }

    private void postCallbackDelayedInternal(int callbackType,
                                             Object action, Object token, long delayMillis) {
        synchronized (mLock) {
            final long dueTime =  delayMillis;
            Message msg = mHandler.obtainMessage(MSG_DO_SCHEDULE_CALLBACK, action);
            msg.arg1 = callbackType;
//            msg.setAsynchronous(true);
            mHandler.sendMessageAtTime(msg, dueTime);
        }
    }

    private final class FrameHandler extends Handler {
        public FrameHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "handleMessage "+msg.what);
            switch (msg.what) {
                case MSG_DO_FRAME:
//                    doFrame(System.nanoTime(), 0);
                    break;
                case MSG_DO_SCHEDULE_VSYNC:
//                    doScheduleVsync();
                    break;
                case MSG_DO_SCHEDULE_CALLBACK:
//                    doScheduleCallback(msg.arg1);
                    ((Runnable)msg.obj).run();
                    break;
            }
        }
    }

}
