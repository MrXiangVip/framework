package com.packages.mqchat;

import com.android.graphics.Canvas;
import com.android.graphics.FontManager;
import java.awt.*;

/** Measurement, line breaking, input carets and drawing share framework glyph runs. */
public final class ChatText {
    public ChatText(ClassLoader loader) {} // Source-compatible; fonts belong to the framework loader.
    public boolean hasBundledGlyph(int code) { return FontManager.hasGlyph(code); }
    public float width(String value, int size) { return FontManager.measureText(value, size); }
    public void draw(Graphics2D graphics, String value, int x, int baseline, int size, int color) {
        if (value.isEmpty()) return;
        graphics.setColor(new Color(color)); FontManager.layout(value, size, Font.PLAIN).draw(graphics, x, baseline);
    }
    public void draw(Canvas canvas, String value, int x, int baseline, int size, int color) {
        canvas.drawText(value, x, baseline, size, color);
    }
}