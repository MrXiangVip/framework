package com.packages.browser;

import com.android.graphics.FontManager;
import java.awt.*;

/** Framework glyph fallback shared by chrome drawing and address caret measurement. */
public final class BrowserText {
    public boolean hasBundledGlyph(int code) { return FontManager.hasGlyph(code); }
    public int width(String value, int size) { return (int) Math.ceil(FontManager.measureText(value, size)); }
    public void draw(com.android.graphics.Canvas canvas, String value, int x, int baseline, int size, int color) {
        canvas.drawText(value, x, baseline, size, color);
    }
    public void draw(Graphics2D g, String value, int x, int baseline, int size, int color, boolean bold) {
        if (value.isEmpty()) return;
        g.setColor(new Color(color)); FontManager.layout(value, size, bold ? Font.BOLD : Font.PLAIN).draw(g, x, baseline);
    }
}
