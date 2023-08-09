package com.wave;

public interface WindowManager extends ViewManager{


    public static class LayoutParams extends ViewGroup.LayoutParams{

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
        }
    }
}
