package com.android.content;

import com.android.res.Resources;

public abstract class Context {

    public ClassLoader getClassLoader() { return Context.class.getClassLoader(); }

    public abstract Resources getResources();

    public String getString(int id) { return getResources().getString(id); }
    public String getString(int id, Object... args) { return getResources().getString(id, args); }

    public abstract Intent registerReceiver( BroadcastReceiver receiver,
                                            IntentFilter filter);
    public abstract ComponentName startService(Intent service);

    public abstract ContentResolver getContentResolver();

}
