package com.android.view;
import java.io.File;
import com.android.content.Context;
import com.android.util.Log;

public abstract  class Window {
    private final Context mContext;
    private WindowManager mWindowManager;
    private final WindowManager.LayoutParams mAttributes = new WindowManager.LayoutParams();
    public interface Callback {
        void onWindowMinimizedChanged(boolean minimized);
        default void onWindowClosed() {}
        default void onWindowAttributesChanged(WindowManager.LayoutParams attrs) {}
    }
    private Callback mCallback;

    public final void setCallback(Callback callback) { mCallback = callback; }
    public final Callback getCallback() { return mCallback; }

    public final WindowManager.LayoutParams getAttributes() { return mAttributes; }

    public void setAttributes(WindowManager.LayoutParams attrs) {
        java.util.Objects.requireNonNull(attrs, "attrs");
        mAttributes.width = attrs.width;
        mAttributes.height = attrs.height;
        mAttributes.x = attrs.x;
        mAttributes.y = attrs.y;
        mAttributes.gravity = attrs.gravity;
        mAttributes.setTitle(attrs.getTitle());
        if (mCallback != null) mCallback.onWindowAttributesChanged(mAttributes);
    }

    /** Set the requested client size in pixels, or MATCH_PARENT/WRAP_CONTENT. */
    public void setLayout(int width, int height) {
        if (width < ViewGroup.LayoutParams.WRAP_CONTENT || height < ViewGroup.LayoutParams.WRAP_CONTENT)
            throw new IllegalArgumentException("Invalid window dimension");
        mAttributes.width = width;
        mAttributes.height = height;
        if (mCallback != null) mCallback.onWindowAttributesChanged(mAttributes);
    }
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
