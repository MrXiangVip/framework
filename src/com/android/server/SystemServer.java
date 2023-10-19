/*
*源码路径:  frameworks/base/services/java/com/android/server/SystemServer.java
* */
package com.android.server;

import com.android.am.ActivityManagerService;
import com.android.app.ActivityThread;
import com.android.content.Context;
import com.android.os.Looper;
import com.android.pm.Installer;
import com.android.pm.PackageManagerService;
import com.android.util.Log;

public final class SystemServer {

    private static String TAG = "SystemServer.";
    private ActivityManagerService mActivityManagerService;
    private PackageManagerService mPackageManagerService;
    private Context mSystemContext;
    private boolean mOnlyCore;
    Installer installer ;
    public static void main(String[] args) {
        Log.d(TAG, "main start");
        new SystemServer().run();
    }

    public SystemServer() {

    }

    private void run() {
        Log.d(TAG, "run");
        Looper.prepareMainLooper();
        createSystemContext();

        try {
            startBootstrapServices();
            startCoreServices();
            startOtherServices();

        } catch (Exception e) {

        }
        Looper.loop();

    }

    private void startBootstrapServices() {
        mActivityManagerService = ActivityManagerService.getInstance();

        mPackageManagerService = PackageManagerService.main(mSystemContext, installer, false, mOnlyCore);
    }

    private void startCoreServices() {

    }

    private void startOtherServices() {
        mActivityManagerService.systemReady();

    }

    private void createSystemContext() {
        ActivityThread activityThread = ActivityThread.systemMain();
        mSystemContext = activityThread.getSystemContext();

    }
}