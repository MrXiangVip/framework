package com.android.content;

import com.android.res.Resources;

public class ContextWrapper extends Context {
    Context mBase;

    public ContextWrapper(Context base) {
        mBase = base;
    }

    public Resources getResources() {
        return mBase.getResources();
    }

    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        return mBase.registerReceiver(receiver, filter);

    }

    @Override
    public ComponentName startService(Intent service) {
        return mBase.startService(service);
    }

    @Override
    public ContentResolver getContentResolver() {
        return  mBase.getContentResolver();
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
