package com.android.widget;

import com.android.content.Context;
import com.android.util.AttributeSet;
import com.android.view.View;
import com.android.view.ViewGroup;

public class FrameLayout extends ViewGroup {
    public FrameLayout(Context context) { this(context, null); }
    public FrameLayout(Context context, AttributeSet attrs) { this(context, attrs, 0); }
    public FrameLayout(Context context, AttributeSet attrs, int style) { this(context, attrs, style, 0); }
    public FrameLayout(Context context, AttributeSet attrs, int style, int res) { super(context, attrs, style, res); }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = 0, height = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            measureChildWithMargins(child, widthSpec, 0, heightSpec, 0);
            width = Math.max(width, child.getMeasuredWidth());
            height = Math.max(height, child.getMeasuredHeight());
        }
        setMeasuredDimension(resolveSize(width + 2 * mPadding, widthSpec),
                resolveSize(height + 2 * mPadding, heightSpec));
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            LayoutParams p = child.getLayoutParams();
            if (p.width == LayoutParams.MATCH_PARENT || p.height == LayoutParams.MATCH_PARENT) {
                child.measure(p.width == LayoutParams.MATCH_PARENT
                                ? MeasureSpec.makeMeasureSpec(Math.max(0, getMeasuredWidth() - 2 * mPadding), MeasureSpec.EXACTLY)
                                : getChildMeasureSpec(widthSpec, 2 * mPadding, p.width),
                        p.height == LayoutParams.MATCH_PARENT
                                ? MeasureSpec.makeMeasureSpec(Math.max(0, getMeasuredHeight() - 2 * mPadding), MeasureSpec.EXACTLY)
                                : getChildMeasureSpec(heightSpec, 2 * mPadding, p.height));
            }
        }
    }
    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            child.layout(mPadding, mPadding, mPadding + child.getMeasuredWidth(), mPadding + child.getMeasuredHeight());
        }
    }
}
