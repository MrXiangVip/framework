package com.wave;

public class ContextThemeWrapper extends  ContextWrapper{
    private int mThemeResource;

    public ContextThemeWrapper() {
        super(null);
    }

    public ContextThemeWrapper(Context base,  int themeResId) {
        super(base);
        mThemeResource = themeResId;
    }
}
