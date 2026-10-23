package com.android.view;

/** Single-pointer events in the receiving View's local pixel coordinates. */
public final class MotionEvent {
    public static final int ACTION_DOWN = 0, ACTION_UP = 1, ACTION_MOVE = 2, ACTION_CANCEL = 3;
    public static final int ACTION_SCROLL = 4, ACTION_HOVER = 5;
    private final int action, x, y, button, modifiers, scrollDelta;
    public MotionEvent(int action, int x, int y) { this(action, x, y, 1, 0, 0); }
    // Existing pointer clients keep their constructor and left-button semantics.
    public MotionEvent(int action, int x, int y, int button, int modifiers, int scrollDelta) {
        this.action = action; this.x = x; this.y = y;
        this.button = button; this.modifiers = modifiers; this.scrollDelta = scrollDelta;
    }
    public int getButton() { return button; }
    public int getModifiers() { return modifiers; }
    public int getScrollDelta() { return scrollDelta; }
    public int getAction() { return action; }
    public int getX() { return x; }
    public int getY() { return y; }
    public MotionEvent offset(int dx, int dy) { return new MotionEvent(action, x + dx, y + dy, button, modifiers, scrollDelta); }
}
