package com.android.view;

import com.android.view.View;
import com.android.view.ViewGroup;

public interface ViewManager {
    public void addView(View view, ViewGroup.LayoutParams params);
    public void updateViewLayout(View view, ViewGroup.LayoutParams params);

    public void removeView(View view);
}
