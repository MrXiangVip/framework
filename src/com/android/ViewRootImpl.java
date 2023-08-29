package com.android;

import  com.android.View.MeasureSpec;

public class ViewRootImpl implements  ViewParent {

    private String TAG = "ViewRootImpl.";
    View mView;
    final Choreographer mChoreographer;
    final Rect mWinFrame; // frame given by window manager.
    int mWidth;
    int mHeight;
    Rect mDirty;

    public final WindowManager.LayoutParams mWindowAttributes = new WindowManager.LayoutParams();

    public ViewRootImpl(Context context, int display) {
        this(context, display,
                false /* useSfChoreographer */);
    }

    public ViewRootImpl(Context context, int display,
                        boolean useSfChoreographer) {
        mChoreographer = Choreographer.getInstance();
        mWinFrame = new Rect();

    }

    public void setView(View view, WindowManager.LayoutParams attrs, View panelParentView,
                        int userId) {
        Log.d(TAG, "setView");
        if (mView == null) {
            mView = view;
            requestLayout();
        }
    }

    public void requestLayout() {
        Log.d(TAG, "requestLayout");
        scheduleTraversals();
    }

    void scheduleTraversals() {
        Log.d(TAG, "scheduleTraversals");
        mChoreographer.postCallback(
                Choreographer.CALLBACK_TRAVERSAL, mTraversalRunnable, null);
    }

    final TraversalRunnable mTraversalRunnable = new TraversalRunnable();

    final class TraversalRunnable implements Runnable {
        @Override
        public void run() {
            doTraversal();
        }
    }

    void doTraversal() {
        Log.d(TAG, "doTraversal");
        performTraversals();

    }

    private void performTraversals() {
        Log.d(TAG, "performTraversals");
        WindowManager.LayoutParams lp = mWindowAttributes;

        int desiredWindowWidth;
        int desiredWindowHeight;

        desiredWindowWidth = 1200;
        desiredWindowHeight = 800;

        int childWidthMeasureSpec = getRootMeasureSpec(mWidth, lp.width);
        int childHeightMeasureSpec = getRootMeasureSpec(mHeight, lp.height);
        performMeasure(childWidthMeasureSpec, childHeightMeasureSpec);
        performLayout(lp, desiredWindowWidth, desiredWindowHeight);
        performDraw();

    }

    private static int getRootMeasureSpec(int windowSize, int rootDimension) {
        int measureSpec;
        switch (rootDimension) {
            //匹配父容器时，测量模式为MeasureSpec.EXACTLY，测量大小直接为屏幕的大小，也就是充满真个屏幕
            case ViewGroup.LayoutParams.MATCH_PARENT:
                // Window can't resize. Force root view to be windowSize.
                measureSpec = MeasureSpec.makeMeasureSpec(windowSize, MeasureSpec.EXACTLY);
                break;
            //包裹内容时，测量模式为MeasureSpec.AT_MOST，测量大小直接为屏幕大小，也就是充满真个屏幕
            case ViewGroup.LayoutParams.WRAP_CONTENT:
                // Window can resize. Set max size for root view.
                measureSpec = MeasureSpec.makeMeasureSpec(windowSize, MeasureSpec.AT_MOST);
                break;
            //其他情况时，测量模式为MeasureSpec.EXACTLY，测量大小为DecorView顶层视图布局设置的大小。
            default:
                // Window wants to be an exact size. Force root view to be that size.
                measureSpec = MeasureSpec.makeMeasureSpec(rootDimension, MeasureSpec.EXACTLY);
                break;
        }
        return measureSpec;
    }

    private void performMeasure(int childWidthMeasureSpec, int childHeightMeasureSpec) {
        Log.d(TAG, "performMeasure");
        try {
            mView.measure(childWidthMeasureSpec, childHeightMeasureSpec);
        } finally {

        }
    }

    private void performLayout(WindowManager.LayoutParams lp, int desiredWindowWidth,
                               int desiredWindowHeight) {
        Log.d(TAG, "performLayout");
        final View host = mView;
        try {
            host.layout(0, 0, host.getMeasuredWidth(), host.getMeasuredHeight());
        }finally {
        }

    }
    private void performDraw() {
        Log.d(TAG, "performDraw");
        final boolean fullRedrawNeeded = true;
        try{
            boolean canUseAsync = draw(fullRedrawNeeded);

        }finally {

        }
    }
    private boolean draw(boolean fullRedrawNeeded) {
        Log.d(TAG, "draw ");
        final Rect dirty = mDirty;

        drawSoftware( dirty );
        return  false;
    }

    private boolean drawSoftware(Rect dirty){
        Log.d(TAG, "drawSoftware");
        Canvas canvas= new Canvas();

        mView.draw(canvas);
        return  true;
    }
}