package com.packages.tonghuashun;

import java.awt.*;
import java.io.*;

/** Load a physical Chinese font from this APK, independently of Linux font packages. */
final class MarketText {
    private final Font font;
    MarketText(ClassLoader loader) {
        try (InputStream in = loader.getResourceAsStream("res/fonts/NotoSansCJKsc-Regular.otf")) {
            if (in == null) throw new IOException("Missing CJK font");
            font = Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (IOException | FontFormatException e) { throw new IllegalStateException("Cannot load market font", e); }
    }
    void draw(Graphics2D g, String value, float x, float y, int size, int color, boolean bold) {
        g.setFont(font.deriveFont(bold ? Font.BOLD : Font.PLAIN, size)); g.setColor(new Color(color)); g.drawString(value, x, y);
    }
    int width(Graphics2D g, String value, int size) { g.setFont(font.deriveFont((float) size)); return g.getFontMetrics().stringWidth(value); }
}
