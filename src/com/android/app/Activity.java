package com.android.app;

import com.android.content.Context;
import com.android.policy.PhoneWindow;
import com.android.res.Configuration;
import com.android.view.*;
import com.android.util.Log;
import com.android.am.ActivityManagerClient;
import java.io.File;

public class Activity extends ContextThemeWrapper {
    private static Activity activity;

    private Window mWindow;
    private WindowManager mWindowManager;
    View mDecor = null;
    /*package*/ ActivityThread mMainThread;

    private String TAG = "Activity.";

/*    Activity() {
        mWindow = new PhoneWindow(this);
    }*/

//    public static void main(String[] argv) {

//        setContentView( );
//        activity = new Activity();
//        activity.setContentView(1);
//    }



    public View findViewById(String id) { return getWindow().getDecorView().findViewById(id); }

    public Window getWindow() {
        return mWindow;
    }
    public WindowManager getWindowManager() {
        return mWindowManager;
    }

    public void attach(Context context, ActivityThread aThread, Configuration config, Window window) {
        attachBaseContext(context);
        mMainThread = aThread;

        mWindow = new PhoneWindow(this, window);
        mWindow.setWindowManager( new WindowManagerImpl(context),  null, false);
        mWindowManager = mWindow.getWindowManager();


    }

    public final void performCreate() {
        onCreate();
    }

    protected void onCreate() {
        Log.d(TAG, "onCreate");
    }

    protected void onResume() {
        Log.d(TAG, "onResume");
    }

    public void startActivity(String className){
        System.out.println("startActivity "+className);
        ActivityManagerClient.startActivity(className);
    }
	
    /** Resource path relative to the application APK, e.g. res/layout/activity_main.xml. */
    public void setContentView(String layoutPath) {
        getWindow().setContentView(layoutPath);
    }

    public void setContentView(View view) {
        getWindow().setContentView(view);
    }

    public void setContentView( File filePath) {
        getWindow().setContentView(filePath);
    }
}