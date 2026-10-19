package com.android.view;

/** Desktop key input: committed Unicode text or an X11 keysym with modifiers. */
public final class KeyEvent {
    public static final int TEXT = 0, DOWN = 1;
    public static final int BACKSPACE = 0xff08, TAB = 0xff09, ENTER = 0xff0d, ESCAPE = 0xff1b;
    public static final int HOME = 0xff50, LEFT = 0xff51, RIGHT = 0xff53, END = 0xff57, DELETE = 0xffff;
    public static final int SHIFT = 1, CONTROL = 4;
    private final int type, value, modifiers;
    public KeyEvent(int type, int value, int modifiers) {
        this.type = type; this.value = value; this.modifiers = modifiers;
    }
    public int getType() { return type; }
    public int getValue() { return value; }
    public int getModifiers() { return modifiers; }
}
