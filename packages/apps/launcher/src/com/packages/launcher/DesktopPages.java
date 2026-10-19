package com.packages.launcher;

import com.android.content.Context;
import com.android.view.View;
import com.android.view.ViewGroup;
import com.android.widget.Button;
import com.android.widget.GridLayout;
import com.android.widget.TextView;

/** Keeps nine icons per page; navigation appears only when there is another page. */
final class DesktopPages extends ViewGroup {
    final GridLayout grid;
    final Button previous, next;
    final TextView indicator;
    private final boolean paged;

    DesktopPages(Context context, GridLayout grid, boolean paged) {
        super(context);
        this.grid = grid;
        this.paged = paged;
        mBackground = 0xffffffff;
        addView(grid, new LayoutParams(-1, -1));
        previous = new Button(context); previous.setId("previous_page"); previous.setText("<");
        next = new Button(context); next.setId("next_page"); next.setText(">");
        indicator = new TextView(context); indicator.setId("page_indicator");
        if (paged) {
            addView(previous, new LayoutParams(72, 44));
            addView(indicator, new LayoutParams(120, 44));
            addView(next, new LayoutParams(72, 44));
        }
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(resolveSize(600, widthSpec), resolveSize(800, heightSpec));
        grid.measure(MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.max(0, getMeasuredHeight() - (paged ? 56 : 0)), MeasureSpec.EXACTLY));
        if (paged) for (int i = 1; i < getChildCount(); i++) {
            View child = getChildAt(i);
            child.measure(MeasureSpec.makeMeasureSpec(Math.min(child.getLayoutParams().width, getMeasuredWidth() / 3), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(44, MeasureSpec.EXACTLY));
        }
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        grid.layout(0, 0, grid.getMeasuredWidth(), grid.getMeasuredHeight());
        if (paged) {
            int y = grid.getBottom() + 6;
            previous.layout(12, y, 12 + previous.getMeasuredWidth(), y + 44);
            int x = (getWidth() - indicator.getMeasuredWidth()) / 2;
            indicator.layout(x, y, x + indicator.getMeasuredWidth(), y + 44);
            next.layout(getWidth() - 12 - next.getMeasuredWidth(), y, getWidth() - 12, y + 44);
        }
    }
}
