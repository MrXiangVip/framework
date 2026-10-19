package com.android.app;

import com.android.content.Intent;
import com.android.os.Handler;
import com.android.os.ApplicationStartInfo;
import com.android.os.Looper;
import com.android.os.Message;
import com.android.res.Configuration;
import com.android.view.View;
import com.android.util.Log;
import com.android.view.ViewManager;
import com.android.view.Window;
import com.android.view.WindowManager;

public class ActivityThread {
    private static String TAG = "ActivityThread. ";
    final H mH = new H();
    private ApplicationStartInfo mStartInfo;
    private LoadedApk mLoadedApk;
    ActivityClientRecord r;
    static long startSeq = 0;
    private ContextImpl mSystemContext;
    boolean mSystemThread = false;

    public static void main(String[] argv) {

        ApplicationStartInfo info = ApplicationStartInfo.fromArgs(argv);
        Log.d(TAG, "className " + info.activityName + " apk=" + info.apkPath);
        Looper.prepareMainLooper();
        ActivityThread thread = new ActivityThread();
        thread.mStartInfo = info;
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try {
            thread.attach(false, startSeq);
            Looper.loop();
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
            if (thread.mLoadedApk != null) {
                try { thread.mLoadedApk.close(); }
                catch (java.io.IOException e) { Log.w("Cannot close application APK: " + e); }
            }
        }

    }
    public static ActivityThread systemMain() {
        Log.d(TAG, "systemMain");
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
        mSystemThread = system;
        if( !system ){
            sendMessage(H.BIND_APPLICATION, null);
            Log.d(TAG, "in application");
        }else{
            Log.d(TAG, "in systemserver");
        }

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
        bindApplication(mStartInfo);
        sendMessage(H.EXECUTE_TRANSACTION, null);
    }

    void bindApplication(ApplicationStartInfo info) {
        if (mLoadedApk != null) throw new IllegalStateException("Application already bound");
        mLoadedApk = new LoadedApk(info);
        mStartInfo = info;
        Thread.currentThread().setContextClassLoader(mLoadedApk.getClassLoader());
    }

    public ClassLoader getApplicationClassLoader() {
        return mLoadedApk == null ? ActivityThread.class.getClassLoader() : mLoadedApk.getClassLoader();
    }

    public Activity handleLaunchActivity(ActivityClientRecord r) {
        final Activity a = performLaunchActivity(r, null);
        return a;
    }

    private Activity performLaunchActivity(ActivityClientRecord r, Intent customIntent) {
        Log.d(TAG, "performLaunchActivity");
        ContextImpl appContext = createBaseContextForActivity(r);
        Activity activity = newActivity(r.className);
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
            WindowManager.LayoutParams l = new WindowManager.LayoutParams();
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
        Log.d(TAG,"newActivity className " + className);
        try {
            if (mLoadedApk == null) throw new IllegalStateException("Application is not bound");
            return getApplicationClassLoader().loadClass(className).asSubclass(Activity.class)
                    .getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | ClassCastException e) {
            throw new IllegalStateException("Cannot instantiate Activity " + className
                    + " from " + mLoadedApk.getApkFile(), e);
        }
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
                    r = new ActivityClientRecord(mStartInfo.activityName);
                    r.packageInfo = mStartInfo.packageName;
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