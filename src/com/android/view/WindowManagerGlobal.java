package com.android.view;

import java.util.IdentityHashMap;
import java.util.Map;

public final class WindowManagerGlobal {
    private static final WindowManagerGlobal INSTANCE = new WindowManagerGlobal();
    private final Map<View, ViewRootImpl> roots = new IdentityHashMap<>();
    public static WindowManagerGlobal getInstance() { return INSTANCE; }

    public void addView(View view, ViewGroup.LayoutParams params,
                        int display, Window parentWindow, int userId) {
        if (roots.containsKey(view)) throw new IllegalStateException("Window already added");
        WindowManager.LayoutParams windowParams = params == null ? new WindowManager.LayoutParams()
                : (WindowManager.LayoutParams) params;
        ViewRootImpl root = new ViewRootImpl(view.getContext(), display);
        roots.put(view, root);
        try { root.setView(view, windowParams, null, userId); }
        catch (RuntimeException e) { roots.remove(view); root.close(); throw e; }
    }
    public void removeView(View view) {
        ViewRootImpl root = roots.remove(view);
        if (root != null) root.close();
    }
}
