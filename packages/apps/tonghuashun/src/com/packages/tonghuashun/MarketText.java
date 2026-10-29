package com.packages.tonghuashun;

import com.android.graphics.FontManager;
import java.awt.*;

/** Uses the framework's shared fonts; no APK-local font resources. */
final class MarketText {
    MarketText(ClassLoader loader) {}
    int width(String value, int size) { return (int) Math.ceil(FontManager.measureText(value, size)); }
    void draw(com.android.graphics.Canvas canvas, String value, int x, int baseline, int size, int color) {
        canvas.drawText(value, x, baseline, size, color);
    }
    void draw(Graphics2D g, String value, float x, float y, int size, int color, boolean bold) {
        if (value.isEmpty()) return;
        g.setColor(new Color(color)); FontManager.layout(value, size, bold ? Font.BOLD : Font.PLAIN).draw(g, x, y);
    }
    int width(Graphics2D g, String value, int size) { return width(value, size); }
}
