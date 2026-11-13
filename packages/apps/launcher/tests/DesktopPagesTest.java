package com.packages.launcher;

import com.android.view.MotionEvent;
import com.android.view.View;
import com.android.widget.GridLayout;

public final class DesktopPagesTest {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static void event(DesktopPages d, int action, int x, int y) {
        check(d.dispatchTouchEvent(new MotionEvent(action, x, y)), "Gesture handled");
    }
    static void swipe(DesktopPages d, int from, int to, int action) {
        event(d, MotionEvent.ACTION_DOWN, from, 80);
        event(d, MotionEvent.ACTION_MOVE, to, 80);
        event(d, action, to, 80);
    }
    public static void main(String[] args) {
        GridLayout grid = new GridLayout(null);
        View icon = new View(null); int[] clicks = {0}, turns = {0};
        icon.setOnClickListener(v -> clicks[0]++);
        grid.addView(icon, new com.android.view.ViewGroup.LayoutParams(160, 180));
        DesktopPages d = new DesktopPages(null, grid, true);
        d.setPage(0, 3);
        d.setPageListener(page -> { turns[0]++; d.setPage(page, 3); });
        d.measure(View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        d.layout(0, 0, 640, 800);
        event(d, MotionEvent.ACTION_DOWN, 30, 80); event(d, MotionEvent.ACTION_UP, 30, 80);
        check(clicks[0] == 1, "Tap opens icon");
        swipe(d, 130, 0, MotionEvent.ACTION_UP);
        check(turns[0] == 1 && clicks[0] == 1 && grid.getLeft() == 0, "Swipe cancels icon and changes page");
        swipe(d, 30, 60, MotionEvent.ACTION_UP);
        check(turns[0] == 1 && clicks[0] == 1, "Short drag snaps back without opening icon");
        swipe(d, 30, 160, MotionEvent.ACTION_UP);
        check(turns[0] == 2, "Right swipe returns to previous page");
        swipe(d, 30, 160, MotionEvent.ACTION_UP);
        check(turns[0] == 2, "First page cannot overscroll");
        d.setPage(2, 3); swipe(d, 160, 0, MotionEvent.ACTION_UP);
        check(turns[0] == 2, "Last page cannot overscroll");
        d.setPage(1, 3); swipe(d, 160, 0, MotionEvent.ACTION_CANCEL);
        check(turns[0] == 2 && grid.getLeft() == 0 && !icon.isPressed(), "Cancellation resets drag without changing page");
        event(d, MotionEvent.ACTION_DOWN, 30, 40); event(d, MotionEvent.ACTION_MOVE, 30, 90);
        event(d, MotionEvent.ACTION_UP, 30, 90);
        check(turns[0] == 2 && clicks[0] == 1, "Vertical drag neither pages nor launches");
        event(d, MotionEvent.ACTION_DOWN, 400, 400); event(d, MotionEvent.ACTION_MOVE, 100, 400);
        event(d, MotionEvent.ACTION_UP, 100, 400);
        check(turns[0] == 3, "Swipe works on empty desktop space");
        System.out.println("PASS: launcher swipe, taps, short/vertical drags, edges, empty space and cancellation");
    }
}
