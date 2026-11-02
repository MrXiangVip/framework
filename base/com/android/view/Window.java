package com.android.view;
import java.io.File;
import com.android.content.Context;
import com.android.util.Log;

public abstract  class Window {
    private final Context mContext;
    private WindowManager mWindowManager;
    public interface Callback {
        void onWindowMinimizedChanged(boolean minimized);
        default void onWindowClosed() {}
    }
    private Callback mCallback;

    public final void setCallback(Callback callback) { mCallback = callback; }
    public final Callback getCallback() { return mCallback; }
    private String TAG="Window.";

    public Window(Context context) {
        mContext = context;
    }
    public final Context getContext() {
        return mContext;
    }
    public abstract View getDecorView();

    public abstract void setContentView(String layoutPath);
    public abstract void setContentView(View view);
    public abstract void setContentView( File file);

    public void setWindowManager(WindowManager wm,  String appName,
                                 boolean hardwareAccelerated) {
        Log.d(TAG, "setWindowManager");
        mWindowManager = ((WindowManagerImpl)wm).createLocalWindowManager(this);

    }
    public WindowManager getWindowManager() {
        return mWindowManager;
    }
}
