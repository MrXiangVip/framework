package com.android.view;

import com.android.content.ContextWrapper;
import com.android.content.Context;

public class ContextThemeWrapper extends ContextWrapper {
    private int mThemeResource;

    public ContextThemeWrapper() {
        super(null);
    }

    public ContextThemeWrapper(Context base, int themeResId) {
        super(base);
        mThemeResource = themeResId;
    }
}
