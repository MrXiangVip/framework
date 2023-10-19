package com.android.wm;

import com.android.content.Context;
import com.android.input.InputManagerService;
import com.android.policy.WindowManagerPolicy;
import com.android.view.View;
import com.android.view.ViewGroup;
import com.android.view.WindowManager;

public class WindowManagerService implements WindowManager {

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
    @Override
    public void addView(View view, ViewGroup.LayoutParams params) {

    }
}
