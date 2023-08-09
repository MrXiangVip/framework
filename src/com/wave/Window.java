package com.wave;

public abstract  class Window {
    private final Context mContext;
    private WindowManager mWindowManager;
    private String TAG="Window.";

    public Window(Context context) {
        mContext = context;
    }
    public final Context getContext() {
        return mContext;
    }
    public abstract  View getDecorView();

    public abstract void setContentView(int layoutResID);

    public void setWindowManager(WindowManager wm,  String appName,
                                 boolean hardwareAccelerated) {
        Log.d(TAG, "setWindowManager");
        mWindowManager = ((WindowManagerImpl)wm).createLocalWindowManager(this);

    }
    public WindowManager getWindowManager() {
        return mWindowManager;
    }
}
