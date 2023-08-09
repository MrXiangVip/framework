package com.wave;

public final class WindowManagerGlobal {
    private static WindowManagerGlobal sDefaultWindowManager;
    private String TAG="WindowManagerGlobal.";

    public static WindowManagerGlobal getInstance() {
        synchronized (WindowManagerGlobal.class) {
            if (sDefaultWindowManager == null) {
                sDefaultWindowManager = new WindowManagerGlobal();
            }
            return sDefaultWindowManager;
        }
    }

    public void addView(View view, ViewGroup.LayoutParams params,
                        int display, Window parentWindow, int userId) {
        Log.d(TAG, "addView");
        ViewRootImpl root;
        root = new ViewRootImpl(view.getContext(), display);

    }
}
