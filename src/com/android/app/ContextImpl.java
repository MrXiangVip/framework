package com.android.app;

import com.android.content.*;
import com.android.res.Configuration;
import com.android.res.Resources;

public class ContextImpl extends Context {
    private Resources mResources;
    final   ActivityThread mMainThread;
    private ClassLoader mClassLoader;

    @Override
    public Resources getResources() {
        return mResources;
    }

    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        return null;
    }

    @Override
    public ComponentName startService(Intent service) {
        return null;
    }

    @Override
    public ContentResolver getContentResolver() {
        return null;
    }

    private ContextImpl( ContextImpl container,  ActivityThread mainThread,
                         String packageInfo,  String attributionTag,
                         String splitName,  String activityToken,  String user,
                        int flags,  ClassLoader classLoader,  String overrideOpPackageName) {
        mResources = new Resources();
        mMainThread = mainThread;
        mClassLoader = classLoader;


    }
    static ContextImpl createActivityContext(ActivityThread mainThread,
                                             String packageInfo, String activityInfo, String activityToken, int displayId,
                                             Configuration overrideConfiguration) {

        ContextImpl context = new ContextImpl(null, mainThread, packageInfo, null,
                activityInfo, activityToken, null, 0, null, null);
        return  context;
    }



}
