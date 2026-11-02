package com.android.view;

import com.android.content.Context;
import com.android.graphics.Canvas;
import com.android.wm.XWindow;
import com.android.wm.WindowManagerService;
import java.awt.image.BufferedImage;
import java.util.Objects;

/** Coordinates a View tree, software frames and an X11 surface on the UI thread. */
public class ViewRootImpl implements ViewParent, AutoCloseable {
    private View mView;
    private Window.Callback mWindowCallback;
    private boolean mMinimized;

    void setWindowCallback(Window.Callback callback) { mWindowCallback = callback; }
    private final Choreographer mChoreographer;
    private XWindow mWindow;
    private int mWidth, mHeight;
    private boolean mWindowSizeDirty = true;
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

        }
        resolveWindowSize();
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
            @Override public void onMinimizedChanged(boolean minimized) {
                if (mClosed || mMinimized == minimized) return;
                mMinimized = minimized;
                if (minimized) {
                    mView.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_CANCEL, 0, 0));
                }
                if (mWindowCallback != null) mWindowCallback.onWindowMinimizedChanged(minimized);
                if (!minimized) scheduleTraversals();
            }
            @Override public void onKeyInput(KeyEvent event) {
                if (!mMinimized) mView.dispatchKeyEvent(event);
            }
            @Override public void onInput(MotionEvent event) {
                if (mMinimized) return;
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
    public void setLayoutParams(WindowManager.LayoutParams attrs) {
        mWindowAttributes.width = attrs.width;
        mWindowAttributes.height = attrs.height;
        mWindowAttributes.x = attrs.x;
        mWindowAttributes.y = attrs.y;
        mWindowAttributes.setTitle(attrs.getTitle());
        requestLayout();
    }
    private void resolveWindowSize() {
        int[] display = XWindow.getDisplaySize();
        int[] size = WindowManagerService.getInstance().computeWindowSize(
                mView, mWindowAttributes, display[0], display[1]);
        if (mWindow != null && (size[0] != mWidth || size[1] != mHeight))
            mWindow.resize(size[0], size[1]);
        mWidth = size[0]; mHeight = size[1];
        mWindowSizeDirty = false;
    }
    @Override public void requestLayout() { mWindowSizeDirty = true; scheduleTraversals(); }
    @Override public void invalidate() { scheduleTraversals(); }
    void scheduleTraversals() {
        if (mClosed || mMinimized || mTraversalScheduled) return;
        mTraversalScheduled = true;
        mChoreographer.postCallback(Choreographer.CALLBACK_TRAVERSAL, () -> {
            mTraversalScheduled = false;
            if (!mClosed && !mMinimized) performTraversals();
        }, null);
    }
    private void performTraversals() {
        if (mWindowSizeDirty) resolveWindowSize();
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
        mWindowCallback = null;
        if (mWindow != null) mWindow.close();
        if (mView != null) {
            mView.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_CANCEL, 0, 0));
            mView.mParent = null;
        }
    }
}
