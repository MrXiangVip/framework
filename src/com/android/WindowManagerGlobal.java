package com.android;

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
        View panelParentView = null;
        final WindowManager.LayoutParams wparams = (WindowManager.LayoutParams) params;

        root = new ViewRootImpl(view.getContext(), display);
        root.setView( view, wparams, panelParentView, userId);
    }
}
