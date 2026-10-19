package com.android.widget;

import com.android.content.Context;
import com.android.util.AttributeSet;
import com.android.view.View;
import com.android.view.ViewGroup;

/** Equal cells, placed in row-major order. Supports columnCount and uniform spacing. */
public class GridLayout extends ViewGroup {
    private final int columns, spacing;
    public GridLayout(Context context) { this(context, null); }
    public GridLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        columns = attrs == null || attrs.get("columnCount") == null ? 3 : Integer.parseInt(attrs.get("columnCount"));
        spacing = attrs == null ? 0 : attrs.dimension("spacing", 0);
        if (columns <= 0 || spacing < 0) throw new IllegalArgumentException("Invalid grid dimensions");
    }
    private int rows() { return Math.max(1, (getChildCount() + columns - 1) / columns); }
    private int available(int size, int count) { return Math.max(0, size - 2 * mPadding - (count - 1) * spacing); }
    private int start(int index, int space, int count) { return mPadding + index * space / count + index * spacing; }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int desiredWidth = 0, desiredHeight = 0, rows = rows();
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            measureChildWithMargins(child, widthSpec, 0, heightSpec, 0);
            desiredWidth = Math.max(desiredWidth, child.getMeasuredWidth());
            desiredHeight = Math.max(desiredHeight, child.getMeasuredHeight());
        }
        setMeasuredDimension(resolveSize(columns * desiredWidth + 2 * mPadding + (columns - 1) * spacing, widthSpec),
                resolveSize(rows * desiredHeight + 2 * mPadding + (rows - 1) * spacing, heightSpec));
        int width = available(getMeasuredWidth(), columns), height = available(getMeasuredHeight(), rows);
        for (int i = 0; i < getChildCount(); i++) {
            int col = i % columns, row = i / columns;
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec((col + 1) * width / columns - col * width / columns, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec((row + 1) * height / rows - row * height / rows, MeasureSpec.EXACTLY));
        }
    }
    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int rows = rows(), width = available(getWidth(), columns), height = available(getHeight(), rows);
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            int x = start(i % columns, width, columns), y = start(i / columns, height, rows);
            child.layout(x, y, x + child.getMeasuredWidth(), y + child.getMeasuredHeight());
        }
    }
}
