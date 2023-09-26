package com.android.content;

import com.android.res.Resources;

public abstract class Context {

    public abstract Resources getResources();

    public abstract Intent registerReceiver( BroadcastReceiver receiver,
                                            IntentFilter filter);
    public abstract ComponentName startService(Intent service);

    public abstract ContentResolver getContentResolver();

}
