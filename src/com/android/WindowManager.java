package com.android;

public interface WindowManager extends ViewManager{


    public static class LayoutParams extends ViewGroup.LayoutParams{
        public int x;
        public int y;
        public LayoutParams() {
            super(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        }
        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
        }
    }
}
