package com.android.app;

import com.android.res.Configuration;
import com.android.content.Context;
import com.android.res.Resources;

public class ContextImpl extends Context {
    private Resources mResources;

    @Override
    public Resources getResources() {
        return mResources;
    }
    private ContextImpl( ContextImpl container,  ActivityThread mainThread,
                         String packageInfo,  String attributionTag,
                         String splitName,  String activityToken,  String user,
                        int flags,  ClassLoader classLoader,  String overrideOpPackageName) {
        mResources = new Resources();
    }
    static ContextImpl createActivityContext(ActivityThread mainThread,
                                             String packageInfo, String activityInfo, String activityToken, int displayId,
                                             Configuration overrideConfiguration) {

        ContextImpl context = new ContextImpl(null, mainThread, packageInfo, null,
                activityInfo, activityToken, null, 0, null, null);
        return  context;
    }
}
