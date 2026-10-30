package com.android.view;

import com.android.content.Context;
import com.android.graphics.Canvas;
import com.android.wm.XWindow;
import java.awt.image.BufferedImage;
import java.util.Objects;

/** Coordinates a View tree, software frames and an X11 surface on the UI thread. */
public class ViewRootImpl implements ViewParent, AutoCloseable {
    private View mView;
    private final Choreographer mChoreographer;
    private XWindow mWindow;
    private int mWidth = 600, mHeight = 800;
    private boolean mTraversalScheduled, mClosed;
    public final WindowManager.LayoutParams mWindowAttributes = new WindowManager.LayoutParams();

    public ViewRootImpl(Context context, int display) { this(context, display, false); }
    public ViewRootImpl(Context context, int display, boolean useSfChoreographer) {
        mChoreographer = Choreographer.getInstance();
    }
    public void setView(View view, WindowManager.LayoutParams attrs, View panelParentView, int userId) {
        if (mView != null) throw new IllegalStateException("ViewRoot already attached");
        Objects.requireNonNull(view, "view");
        if (view.mParent != null) throw new IllegalStateException("View already attached");
        mView = view;
        mView.mParent = this;
        if (attrs != null) {
            mWindowAttributes.width = attrs.width;
            mWindowAttributes.height = attrs.height;
            mWindowAttributes.setTitle(attrs.getTitle());
            if (attrs.width > 0) mWidth = attrs.width;
            if (attrs.height > 0) mHeight = attrs.height;
        }
//      1.创建窗口
        if (mWindow == null) {
            mWindow = new XWindow(mWidth, mHeight, mWindowAttributes.getTitle());
        }
//      2.将图像画到窗口上
        requestLayout();

//      3.注册事件接收
        mWindow.registeListener( new XWindow.Listener() {
            @Override public void onEvent(int width, int height, boolean closed) {
                if (closed) { WindowManagerGlobal.getInstance().removeView(mView); close(); return; }
                mWidth = Math.max(1, width); mHeight = Math.max(1, height);
                scheduleTraversals();
            }
            @Override public void onKeyInput(KeyEvent event) { mView.dispatchKeyEvent(event); }
            @Override public void onInput(MotionEvent event) {
                // A resize and press may arrive in the same native batch.
                if (mView.getWidth() != mWidth || mView.getHeight() != mHeight) {
                    mView.measure(View.MeasureSpec.makeMeasureSpec(mWidth, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(mHeight, View.MeasureSpec.EXACTLY));
                    mView.layout(0, 0, mWidth, mHeight);
                }
                mView.dispatchTouchEvent(event);
            }
        } );
    }
    @Override public void requestLayout() { scheduleTraversals(); }
    @Override public void invalidate() { scheduleTraversals(); }
    void scheduleTraversals() {
        if (mClosed || mTraversalScheduled) return;
        mTraversalScheduled = true;
        mChoreographer.postCallback(Choreographer.CALLBACK_TRAVERSAL, () -> {
            mTraversalScheduled = false;
            if (!mClosed) performTraversals();
        }, null);
    }
    private void performTraversals() {
        if (mWindow == null) {
            mWindow = new XWindow(mWidth, mHeight, mWindowAttributes.getTitle());
        }
        mWindow.present(renderToImage(mView, mWidth, mHeight));
    }
    /** Also used by headless regression tests; no X11 or native library required. */
    public static BufferedImage renderToImage(View view, int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Frame size must be positive");
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        try (Canvas canvas = new Canvas(image)) {
            canvas.drawColor(0xffffffff);
            view.draw(canvas);
        }
        return image;
    }
    @Override public void close() {
        if (mClosed) return;
        mClosed = true;
        if (mWindow != null) mWindow.close();
        if (mView != null) {
            mView.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_CANCEL, 0, 0));
            mView.mParent = null;
        }
    }
}
