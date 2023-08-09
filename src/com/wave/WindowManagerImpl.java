package com.wave;

public final  class WindowManagerImpl implements WindowManager{
    public final Context mContext;
    private final Window mParentWindow;

    private final WindowManagerGlobal mGlobal = WindowManagerGlobal.getInstance();
    public WindowManagerImpl(Context context) {
        this(context, null);
    }
    private WindowManagerImpl(Context context, Window parentWindow) {
        mContext = context;
        mParentWindow = parentWindow;
    }

    public WindowManagerImpl createLocalWindowManager(Window parentWindow) {
        return new WindowManagerImpl(mContext, parentWindow);
    }

    public void addView( View view,  ViewGroup.LayoutParams params) {
        mGlobal.addView(view, params, 0, mParentWindow,
                0);
    }
}
