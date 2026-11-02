package com.android.wm;

import com.android.content.Context;
import com.android.input.InputManagerService;
import com.android.policy.WindowManagerPolicy;
import com.android.view.View;
import com.android.view.ViewGroup;
import com.android.view.WindowManager;

public class WindowManagerService  {

    private static WindowManagerService sInstance;
    public static WindowManagerService getInstance() {
        if( sInstance == null ){
            sInstance = new WindowManagerService();
        }
        return sInstance;
    }

    public static WindowManagerService main(final Context context, final InputManagerService im,
                                            final boolean showBootMsgs, final boolean onlyCore, WindowManagerPolicy policy,
                                            ActivityTaskManagerService atm) {
        return  getInstance();
    }

    /** Resolve client size in pixels within the supplied display area. No X11 dependency. */
    public int[] computeWindowSize(View view, WindowManager.LayoutParams params,
                                   int availableWidth, int availableHeight) {
        if (availableWidth <= 0 || availableHeight <= 0)
            throw new IllegalArgumentException("Display area must be positive");
        int widthSpec = makeWindowMeasureSpec(params.width, availableWidth);
        int heightSpec = makeWindowMeasureSpec(params.height, availableHeight);
        view.measure(widthSpec, heightSpec);
        // X11 windows require at least one pixel, even for empty WRAP_CONTENT views.
        return new int[] { Math.max(1, Math.min(availableWidth, view.getMeasuredWidth())),
                Math.max(1, Math.min(availableHeight, view.getMeasuredHeight())) };
    }

    private static int makeWindowMeasureSpec(int requested, int available) {
        if (requested < ViewGroup.LayoutParams.WRAP_CONTENT)
            throw new IllegalArgumentException("Invalid window dimension: " + requested);
        return View.MeasureSpec.makeMeasureSpec(
                requested >= 0 ? Math.min(requested, available) : available,
                requested == ViewGroup.LayoutParams.WRAP_CONTENT
                        ? View.MeasureSpec.AT_MOST : View.MeasureSpec.EXACTLY);
    }

//    增加窗口
    public void addWindow(View view, ViewGroup.LayoutParams params) {

    }

// 窗口后续每一次布局更新、显示、隐藏、改大小、改 LayoutParams 的主入口
    public void relayoutWindow(View view, ViewGroup.LayoutParams params) {

    }
//
    public void removeWindowToken(View view) {

    }
//
    public void finishDrawingWindow(View view ){

    }

//    给指定 Display（屏幕）设置窗口模式（windowingMode）,比如全屏模式、分屏、自由窗口、画中画这些模式切换
    public void setWindowingMode(int displayId, int mode) {

    }
}
