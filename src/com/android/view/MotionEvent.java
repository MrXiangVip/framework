package com.android.view;

/** Single-pointer events in the receiving View's local pixel coordinates. */
public final class MotionEvent {
    public static final int ACTION_DOWN = 0, ACTION_UP = 1, ACTION_MOVE = 2, ACTION_CANCEL = 3;
    private final int action, x, y;
    public MotionEvent(int action, int x, int y) { this.action = action; this.x = x; this.y = y; }
    public int getAction() { return action; }
    public int getX() { return x; }
    public int getY() { return y; }
    public MotionEvent offset(int dx, int dy) { return new MotionEvent(action, x + dx, y + dy); }
}
