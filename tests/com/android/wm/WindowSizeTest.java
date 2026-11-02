package com.android.wm;

import com.android.view.*;

/** Headless checks for the window sizing contract. */
public final class WindowSizeTest {
    private static final View content = new View(null) {
        @Override protected void onMeasure(int width, int height) {
            setMeasuredDimension(resolveSize(320, width), resolveSize(180, height));
        }
    };
    private static void size(int requestedWidth, int requestedHeight, int width, int height) {
        int[] actual = WindowManagerService.getInstance().computeWindowSize(content,
                new WindowManager.LayoutParams(requestedWidth, requestedHeight), 800, 600);
        if (actual[0] != width || actual[1] != height)
            throw new AssertionError(actual[0] + "x" + actual[1]);
    }
    public static void main(String[] args) {
        size(400, 250, 400, 250);
        size(-1, -1, 800, 600);
        size(-2, -2, 320, 180);
        size(-1, -2, 800, 180);
        size(900, 700, 800, 600);
        size(0, 0, 1, 1);
        try {
            size(-3, 200, 1, 200);
            throw new AssertionError("Invalid dimension accepted");
        } catch (IllegalArgumentException expected) { }
        View empty = new View(null);
        int[] actual = WindowManagerService.getInstance().computeWindowSize(empty,
                new WindowManager.LayoutParams(-2, -2), 800, 600);
        if (actual[0] != 1 || actual[1] != 1) throw new AssertionError("Empty window");
        System.out.println("WindowSizeTest passed");
    }
}
