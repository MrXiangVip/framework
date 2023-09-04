package com.android.wm;

import com.android.util.Log;

public class XWindow {

    native private static int nativeCreateAWindow();

    public static int createAWindow() {

        Log.d("createAWindow \n");
        int val = nativeCreateAWindow();
        return val;
    }
}