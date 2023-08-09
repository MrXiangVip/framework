package com.wave;

public abstract  class Window {
    private final Context mContext;

    public Window(Context context) {
        mContext = context;
    }
    public final Context getContext() {
        return mContext;
    }

    public abstract void setContentView(int layoutResID);
}
