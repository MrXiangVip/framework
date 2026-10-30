package com.android.view;

import com.android.content.Context;
import com.android.util.AttributeSet;
import com.android.view.ViewGroup;
import com.android.view.ViewManager;

public interface WindowManager extends ViewManager {


    public static class LayoutParams extends ViewGroup.LayoutParams{
        public int x;
        public int y;
        private String title = "";
        public void setTitle(CharSequence value) { title = value == null ? "" : value.toString(); }
        public String getTitle() { return title; }
        public LayoutParams() {
            super(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        }
        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
        }
    }
}
