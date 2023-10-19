package com.android.app;

import com.android.content.Intent;
import com.android.os.Handler;
import com.android.os.Looper;
import com.android.os.Message;
import com.android.res.Configuration;
import com.android.view.View;
import com.android.util.Log;
import com.android.view.ViewManager;
import com.android.view.Window;
import com.android.view.WindowManager;

public class ActivityThread {
    private static String TAG = "ActivityThread.";
    final H mH = new H();
//    String className = "com.packages.launcher.Launcher";
    static String className ;
    static String defaultLauncher ="com.packages.launcher.Launcher";
    ActivityClientRecord r;
    static long startSeq = 0;
    private ContextImpl mSystemContext;

    public static void main(String[] argv) {

        if( argv.length ==0 ){
            className = defaultLauncher;
        }else{
            for (int i=0; i < argv.length; ++i) {
                Log.d(TAG, i + " "+argv[i]);
            }
            className = argv[0];
        }
        Log.d(TAG, "className "+className);
        Looper.prepareMainLooper();
        ActivityThread thread = new ActivityThread();
        thread.attach(false, startSeq);
        Looper.loop();

    }
    public static ActivityThread systemMain() {
        ActivityThread thread = new ActivityThread();
        thread.attach(true, 0);
        return thread;
    }
    public ContextImpl getSystemContext() {
        synchronized (this) {
            if (mSystemContext == null) {
                mSystemContext = ContextImpl.createSystemContext(this);
            }
            return mSystemContext;
        }
    }

    private void attach(boolean system, long startSeq) {
        sendMessage(H.BIND_APPLICATION, null);

    }

    void sendMessage(int what, Object obj) {
        sendMessage(what, obj, 0, 0, false);
    }

    private void sendMessage(int what, Object obj, int arg1, int arg2, boolean async) {
        Message msg = Message.obtain();
        msg.what = what;
        msg.obj = obj;
        msg.arg1 = arg1;
        msg.arg2 = arg2;
        mH.sendMessage(msg);

    }

    private void handleBindApplication() {
        Log.d(TAG, "handleBindApplication");
//        此处简化了实际流程
        sendMessage(H.EXECUTE_TRANSACTION, null);
    }

    public Activity handleLaunchActivity(ActivityClientRecord r) {
        final Activity a = performLaunchActivity(r, null);
        return a;
    }

    private Activity performLaunchActivity(ActivityClientRecord r, Intent customIntent) {
        Log.d(TAG, "performLaunchActivity");
        ContextImpl appContext = createBaseContextForActivity(r);
        Activity activity = null;
        try {
            activity = newActivity(r.className);
        } catch (Exception e) {

        }
        if (activity != null) {
            Window window = null;
            activity.attach(appContext, this, null,  window );
            activity.performCreate();

        }
        return activity;
    }

    public void handleResumeActivity(ActivityClientRecord r,String reason){
        performResumeActivity(r, reason);
        final Activity a = r.activity;
        boolean willBeVisible = true;
        if( willBeVisible ){
            r.window = r.activity.getWindow();
            View decor = r.window.getDecorView();
//            decor.setVisibility(View.INVISIBLE);
            ViewManager wm = a.getWindowManager();
            WindowManager.LayoutParams l = null;
            a.mDecor = decor;
            wm.addView(decor, l);

        }
    }
    public ActivityClientRecord performResumeActivity(ActivityClientRecord r, String reason) {
        r.activity.onResume();
        return  r;
    }
    private ContextImpl createBaseContextForActivity(ActivityClientRecord r) {
        final int displayId=0;
        ContextImpl appContext = ContextImpl.createActivityContext(
                this, r.packageInfo, r.activityInfo, r.token, displayId, r.overrideConfig);
        return appContext;
    }

    public Activity newActivity(String className) {
        Log.d(TAG,"newActivity " + className);
        try {
            Class<?> clazz = Class.forName(className);
            Activity activity = (Activity) clazz.newInstance();
            return activity;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    class H extends Handler {
        public static final int BIND_APPLICATION = 110;
        public static final int EXECUTE_TRANSACTION = 159;

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case BIND_APPLICATION:
                    handleBindApplication();
                    break;
                case EXECUTE_TRANSACTION:
                    r = new ActivityClientRecord(className);
                    r.activity =handleLaunchActivity(r);
                    handleResumeActivity(r,"RESUME_ACTIVITY");
                    break;
                default:
                    break;
            }
        }
    }

    public static final class ActivityClientRecord {
        public String packageInfo;
        String activityInfo;
        public String token;
        String className;
        Configuration overrideConfig;
        Activity activity;
        Window window;
        public ActivityClientRecord(String className){
            this.className = className;
        }

    }
}