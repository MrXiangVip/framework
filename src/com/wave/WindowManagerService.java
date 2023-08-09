package com.wave;

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
