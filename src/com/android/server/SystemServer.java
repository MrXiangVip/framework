/*
*源码路径:  frameworks/base/services/java/com/android/server/SystemServer.java
* */
package com.android.server;

import com.android.am.ActivityManagerService;
import com.android.os.Looper;
import com.android.util.Log;

public final class SystemServer {

    private static String TAG="SystemServer.";
    private ActivityManagerService mActivityManagerService;

    public static void main(String[] args) {
        Log.d(TAG, "main start");
        new SystemServer().run();
    }

    public SystemServer() {

    }

    private void run() {
        Log.d(TAG, "run");
        Looper.prepareMainLooper();
        try{
            startBootstrapServices( );
            startCoreServices( );
            startOtherServices( );

        }catch (Exception e){

        }
        Looper.loop();

    }
    private void startBootstrapServices( ) {
        mActivityManagerService = ActivityManagerService.getInstance();

    }
    private void startCoreServices( ) {

    }
    private void startOtherServices() {
        mActivityManagerService.systemReady();

    }

}