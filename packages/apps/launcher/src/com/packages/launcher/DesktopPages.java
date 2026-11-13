package com.packages.launcher;

import com.android.content.Context;
import com.android.graphics.Canvas;
import com.android.view.MotionEvent;
import com.android.view.View;
import com.android.view.ViewGroup;
import com.android.widget.Button;
import com.android.widget.GridLayout;
import com.android.widget.TextView;
import java.util.List;

/** Persistent page grids share one horizontal track; only visible grids are drawn. */
final class DesktopPages extends ViewGroup {
    final Button previous, next;
    final TextView indicator;
    private final List<GridLayout> pages;
    private int page, downX, downY, lastX, offset;
    private boolean tracking, dragging, vertical, settling;
    private long lastSample, animationStart;
    private double velocity;
    private int animationFrom, animationTo, targetPage;
    private static final long SNAP_NANOS = 200000000L;

    DesktopPages(Context context, List<GridLayout> pages) {
        super(context);
        if (pages.isEmpty()) throw new IllegalArgumentException("At least one page required");
        this.pages = pages;
        mBackground = 0xffffffff;
        for (GridLayout grid : pages) addView(grid, new LayoutParams(-1, -1));
        previous = new Button(context); previous.setId("previous_page"); previous.setText("<");
        next = new Button(context); next.setId("next_page"); next.setText(">");
        indicator = new TextView(context); indicator.setId("page_indicator");
        if (pages.size() > 1) {
            addView(previous, new LayoutParams(72, 44));
            addView(indicator, new LayoutParams(120, 44));
            addView(next, new LayoutParams(72, 44));
        }
        previous.setOnClickListener(v -> showPage(page - 1));
        next.setOnClickListener(v -> showPage(page + 1));
        updateIndicator();
    }

    int currentPage() { return page; }
    boolean isSettling() { return settling; }
    void showPage(int requested) {
        if (settling) return;
        startSettle(Math.max(0, Math.min(requested, pages.size() - 1)));
    }
    private void updateIndicator() { indicator.setText((page + 1) + " / " + pages.size()); }
    private void startSettle(int target) {
        tracking = dragging = false;
        targetPage = target;
        animationFrom = offset;
        animationTo = (page - target) * getWidth();
        animationStart = System.nanoTime();
        settling = animationFrom != animationTo;
        if (!settling) finishSettle();
        invalidate();
    }
    private void finishSettle() {
        page = targetPage; offset = 0; settling = false;
        layoutPages(); updateIndicator();
    }
    // Draw-driven animation stays on the UI thread and needs no timer/worker lifecycle.
    void advanceAnimation(long now) {
        if (!settling) return;
        double t = Math.min(1, Math.max(0, (now - animationStart) / (double) SNAP_NANOS));
        double eased = 1 - Math.pow(1 - t, 3);
        offset = (int) Math.round(animationFrom + (animationTo - animationFrom) * eased);
        if (t >= 1) finishSettle(); else layoutPages();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getAction();
        // A settling track cannot deliver taps to moving icons.
        if (settling) {
            if (action == MotionEvent.ACTION_CANCEL) { finishSettle(); invalidate(); }
            return true;
        }
        if (action == MotionEvent.ACTION_DOWN) {
            tracking = pages.size() > 1 && event.getButton() == 1
                    && event.getY() >= 0 && event.getY() < contentHeight();
            dragging = vertical = false;
            downX = lastX = event.getX(); downY = event.getY();
            lastSample = System.nanoTime(); velocity = 0;
            boolean handled = super.dispatchTouchEvent(event);
            return tracking || handled;
        }
        if (!tracking) return super.dispatchTouchEvent(event);
        int dx = event.getX() - downX, dy = event.getY() - downY;
        long now = System.nanoTime();
        long elapsed = now - lastSample;
        if (elapsed > 0 && event.getX() != lastX) velocity = (event.getX() - lastX) * 1e9 / elapsed;
        // An UP at the last MOVE coordinate retains recent velocity, but not a stale fling.
        if (event.getX() != lastX) { lastX = event.getX(); lastSample = now; }
        if (!dragging && !vertical && (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP)) {
            if (Math.abs(dy) > 12 && Math.abs(dy) >= Math.abs(dx)) {
                vertical = true;
                super.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_CANCEL, 0, 0));
            } else if (Math.abs(dx) > 12 && Math.abs(dx) > Math.abs(dy)) {
                dragging = true;
                super.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_CANCEL, 0, 0));
            }
        }
        if (dragging) {
            boolean edge = dx > 0 && page == 0 || dx < 0 && page == pages.size() - 1;
            offset = Math.max(-getWidth(), Math.min(getWidth(), edge ? dx / 4 : dx));
            layoutPages(); invalidate();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                boolean far = Math.abs(dx) >= Math.max(48, getWidth() / 6);
                boolean fling = Math.abs(dx) >= 40 && now - lastSample < 100000000L
                        && Math.abs(velocity) >= 650 && velocity * dx > 0;
                int target = page;
                if (action == MotionEvent.ACTION_UP && (far || fling))
                    target = Math.max(0, Math.min(pages.size() - 1, page + (dx < 0 ? 1 : -1)));
                startSettle(target);
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) tracking = false;
        super.dispatchTouchEvent(event);
        return true;
    }

    private int contentHeight() { return Math.max(0, getHeight() - (pages.size() > 1 ? 56 : 0)); }
    private void layoutPages() {
        for (int i = 0; i < pages.size(); i++) {
            int x = (i - page) * getWidth() + offset;
            pages.get(i).layout(x, 0, x + getWidth(), contentHeight());
        }
    }
    @Override protected void dispatchDraw(Canvas canvas) {
        advanceAnimation(System.nanoTime());
        canvas.save(); canvas.clipRect(0, 0, getWidth(), contentHeight());
        for (GridLayout grid : pages)
            if (grid.getRight() > 0 && grid.getLeft() < getWidth()) drawChild(canvas, grid, 0);
        canvas.restore();
        if (pages.size() > 1) {
            drawChild(canvas, previous, 0); drawChild(canvas, indicator, 0); drawChild(canvas, next, 0);
        }
        if (settling) invalidate();
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(resolveSize(600, widthSpec), resolveSize(800, heightSpec));
        int h = Math.max(0, getMeasuredHeight() - (pages.size() > 1 ? 56 : 0));
        for (GridLayout grid : pages) grid.measure(
                MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        if (pages.size() > 1) for (View child : new View[]{previous, indicator, next})
            child.measure(MeasureSpec.makeMeasureSpec(Math.min(child.getLayoutParams().width, getMeasuredWidth() / 3), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(44, MeasureSpec.EXACTLY));
    }
    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (changed && (dragging || settling)) {
            super.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_CANCEL, 0, 0));
            tracking = dragging = false;
            if (settling) page = targetPage;
            settling = false; offset = 0; updateIndicator();
        }
        layoutPages();
        if (pages.size() > 1) {
            int y = contentHeight() + 6;
            previous.layout(12, y, 12 + previous.getMeasuredWidth(), y + 44);
            int x = (getWidth() - indicator.getMeasuredWidth()) / 2;
            indicator.layout(x, y, x + indicator.getMeasuredWidth(), y + 44);
            next.layout(getWidth() - 12 - next.getMeasuredWidth(), y, getWidth() - 12, y + 44);
        }
    }
}
