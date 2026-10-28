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

    public void addWindow(View view, ViewGroup.LayoutParams params) {

    }

    public void relayoutWindow(View view, ViewGroup.LayoutParams params) {

    }

    public void removeWindowToken(View view) {

    }

    public void finishDrawingWindow(View view ){

    }
}
