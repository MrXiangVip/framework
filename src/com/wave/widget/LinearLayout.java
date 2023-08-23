package com.wave.widget;

import com.wave.AttributeSet;
import com.wave.Context;
import com.wave.View;
import com.wave.ViewGroup;

public class LinearLayout extends ViewGroup {
    private int mOrientation;

    public static final int HORIZONTAL = 0;
    public static final int VERTICAL = 1;

    public LinearLayout(Context context) {
        super(context);
    }

    public LinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LinearLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public LinearLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mOrientation == VERTICAL) {
            measureVertical(widthMeasureSpec, heightMeasureSpec);
        } else {
            measureHorizontal(widthMeasureSpec, heightMeasureSpec);
        }
    }
    void measureVertical(int widthMeasureSpec, int heightMeasureSpec) {
        final int count = getVirtualChildCount();
        for (int i = 0; i < count; ++i) {
            final View child = getVirtualChildAt(i);
            final int usedHeight = 0;
            measureChildBeforeLayout(child, i, widthMeasureSpec, 0,
                    heightMeasureSpec, usedHeight);
        }
    }
    void measureChildBeforeLayout(View child, int childIndex,
                                  int widthMeasureSpec, int totalWidth, int heightMeasureSpec,
                                  int totalHeight) {
        measureChildWithMargins(child, widthMeasureSpec, totalWidth,
                heightMeasureSpec, totalHeight);
    }

    void measureHorizontal(int widthMeasureSpec, int heightMeasureSpec) {
        final int count = getVirtualChildCount();
        for (int i = 0; i < count; ++i) {
            final View child = getVirtualChildAt(i);
            final int usedHeight = 0;
            measureChildBeforeLayout(child, i, widthMeasureSpec, 0,
                    heightMeasureSpec, usedHeight);
        }
    }
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (mOrientation == VERTICAL) {
            layoutVertical(l, t, r, b);
        } else {
            layoutHorizontal(l, t, r, b);
        }
    }
    void layoutVertical(int left, int top, int right, int bottom) {
        int childTop=0;
        int childLeft=0;
        final int count = getVirtualChildCount();

        for (int i = 0; i < count; i++) {
            final View child = getVirtualChildAt(i);
            final int childWidth = child.getMeasuredWidth();
            final int childHeight = child.getMeasuredHeight();
            setChildFrame(child, childLeft, childTop ,
                    childWidth, childHeight);
        }

    }

    private void setChildFrame(View child, int left, int top, int width, int height) {
        child.layout(left, top, left + width, top + height);
    }

    void layoutHorizontal(int left, int top, int right, int bottom) {
        int childTop=0;
        int childLeft=0;
        final int count = getVirtualChildCount();

        for (int i = 0; i < count; i++) {
            final View child = getVirtualChildAt(i);
            final int childWidth = child.getMeasuredWidth();
            final int childHeight = child.getMeasuredHeight();
            setChildFrame(child, childLeft, childTop ,
                    childWidth, childHeight);
        }
    }

    int getVirtualChildCount() {
        return getChildCount();
    }

    View getVirtualChildAt(int index) {
        return getChildAt(index);
    }
}
