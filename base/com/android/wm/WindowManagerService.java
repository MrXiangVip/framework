package com.android.wm;

import com.android.content.Context;
import com.android.input.InputManagerService;
import com.android.policy.WindowManagerPolicy;
import com.android.view.View;
import com.android.view.ViewGroup;
import com.android.view.WindowManager;

public class WindowManagerService  {

    private static WindowManagerService sInstance;
    static WindowManagerService getInstance() {
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
