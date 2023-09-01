package com.android.wm;

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

    @Override
    public void addView(View view, ViewGroup.LayoutParams params) {

    }
}
