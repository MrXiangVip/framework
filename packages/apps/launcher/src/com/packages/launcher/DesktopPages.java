package com.packages.launcher;

import com.android.content.Context;
import com.android.view.View;
import com.android.view.MotionEvent;
import java.util.function.IntConsumer;
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
    private int page, pageCount = 1, downX, downY, dragOffset;
    private boolean tracking, dragging, vertical;
    private IntConsumer pageListener;

    void setPageListener(IntConsumer listener) { pageListener = listener; }
    void setPage(int page, int count) {
        this.page = page; pageCount = count;
        indicator.setText((page + 1) + " / " + count);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            tracking = paged && event.getButton() == 1 && event.getY() < grid.getBottom();
            dragging = vertical = false; downX = event.getX(); downY = event.getY();
            boolean handled = super.dispatchTouchEvent(event);
            return tracking || handled;
        }
        if (tracking) {
            int dx = event.getX() - downX, dy = event.getY() - downY;
            if (!dragging && !vertical && (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP)) {
                if (Math.abs(dy) > 12 && Math.abs(dy) >= Math.abs(dx)) {
                    vertical = true;
                    super.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_CANCEL, 0, 0));
                }
                else if (Math.abs(dx) > 12 && Math.abs(dx) > Math.abs(dy)) {
                    dragging = true;
                    // Cancel the icon's press before taking over, so a swipe never launches it.
                    super.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_CANCEL, 0, 0));
                }
            }
            if (dragging) {
                boolean edge = dx > 0 && page == 0 || dx < 0 && page == pageCount - 1;
                dragOffset = Math.max(-getWidth(), Math.min(getWidth(), edge ? dx / 4 : dx));
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    int target = page;
                    if (action == MotionEvent.ACTION_UP && Math.abs(dx) >= Math.max(48, getWidth() / 6))
                        target = Math.max(0, Math.min(pageCount - 1, page + (dx < 0 ? 1 : -1)));
                    tracking = dragging = false; dragOffset = 0;
                    layoutGrid(); invalidate();
                    if (target != page && pageListener != null) pageListener.accept(target);
                } else { layoutGrid(); invalidate(); }
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) tracking = false;
            super.dispatchTouchEvent(event);
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    private void layoutGrid() {
        grid.layout(dragOffset, 0, dragOffset + grid.getMeasuredWidth(), grid.getMeasuredHeight());
    }

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
        layoutGrid();
        if (paged) {
            int y = grid.getBottom() + 6;
            previous.layout(12, y, 12 + previous.getMeasuredWidth(), y + 44);
            int x = (getWidth() - indicator.getMeasuredWidth()) / 2;
            indicator.layout(x, y, x + indicator.getMeasuredWidth(), y + 44);
            next.layout(getWidth() - 12 - next.getMeasuredWidth(), y, getWidth() - 12, y + 44);
        }
    }
}
