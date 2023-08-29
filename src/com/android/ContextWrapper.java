package com.android;

public class ContextWrapper extends Context{
    Context mBase;

    public ContextWrapper(Context base) {
        mBase = base;
    }

    public Resources getResources() {
        return mBase.getResources();
    }

    protected void attachBaseContext(Context base) {
        if (mBase != null) {
            throw new IllegalStateException("Base context already set");
        }
        mBase = base;
    }

    public Context getBaseContext() {
        return mBase;
    }

}
