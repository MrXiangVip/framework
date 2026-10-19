package com.android.widget;

import com.android.content.Context;
import com.android.util.AttributeSet;
import com.android.view.View;
import com.android.view.ViewGroup;

/** Basic linear layout; weight, margins and gravity are intentionally outside this demo. */
public class LinearLayout extends ViewGroup {
    public static final int HORIZONTAL = 0, VERTICAL = 1;
    private int mOrientation;
    public LinearLayout(Context context) { this(context, null); }
    public LinearLayout(Context context, AttributeSet attrs) { this(context, attrs, 0); }
    public LinearLayout(Context context, AttributeSet attrs, int style) { this(context, attrs, style, 0); }
    public LinearLayout(Context context, AttributeSet attrs, int style, int res) {
        super(context, attrs, style, res);
        mOrientation = attrs != null && "vertical".equals(attrs.get("orientation")) ? VERTICAL : HORIZONTAL;
    }
    public void setOrientation(int orientation) {
        if (orientation != VERTICAL && orientation != HORIZONTAL) throw new IllegalArgumentException("orientation");
        mOrientation = orientation; requestLayout();
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int length = 0, cross = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            measureChildWithMargins(child, widthSpec, mOrientation == HORIZONTAL ? length : 0,
                    heightSpec, mOrientation == VERTICAL ? length : 0);
            length += mOrientation == VERTICAL ? child.getMeasuredHeight() : child.getMeasuredWidth();
            cross = Math.max(cross, mOrientation == VERTICAL ? child.getMeasuredWidth() : child.getMeasuredHeight());
        }
        setMeasuredDimension(resolveSize((mOrientation == VERTICAL ? cross : length) + 2 * mPadding, widthSpec),
                resolveSize((mOrientation == VERTICAL ? length : cross) + 2 * mPadding, heightSpec));
    }
    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int offset = mPadding;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            int x = mOrientation == HORIZONTAL ? offset : mPadding;
            int y = mOrientation == VERTICAL ? offset : mPadding;
            child.layout(x, y, x + child.getMeasuredWidth(), y + child.getMeasuredHeight());
            offset += mOrientation == VERTICAL ? child.getMeasuredHeight() : child.getMeasuredWidth();
        }
    }
}
