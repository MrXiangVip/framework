/*
*源码路径:  frameworks/base/services/java/com/android/server/SystemServer.java
* */
package com.android.server;

import com.android.am.ActivityManagerService;
import com.android.app.ActivityThread;
import com.android.content.Context;
import com.android.input.InputManagerService;
import com.android.os.Looper;
import com.android.pm.Installer;
import com.android.pm.PackageManagerService;
import com.android.policy.PhoneWindowManager;
import com.android.util.Log;
import com.android.wm.WindowManagerService;

public final class SystemServer {

    private static String TAG = "SystemServer.";
    private ActivityManagerService mActivityManagerService;
    private PackageManagerService mPackageManagerService;
    private WindowManagerService wm = null;

    private Context mSystemContext;
    private boolean mOnlyCore;
    private boolean mFirstBoot;
    InputManagerService inputManager = null;

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
        final Context context = mSystemContext;

        mActivityManagerService.systemReady();
        WindowManagerService wm = null;
        wm = WindowManagerService.main(context, inputManager, !mFirstBoot, mOnlyCore,
                new PhoneWindowManager(), mActivityManagerService.mActivityTaskManager);
    }

    private void createSystemContext() {
        ActivityThread activityThread = ActivityThread.systemMain();
        mSystemContext = activityThread.getSystemContext();

    }
}