package com.android.graphics;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;

/** Software Canvas. BufferedImage keeps the View renderer independent of X11/JNI. */
public class Canvas implements AutoCloseable {
    private final BufferedImage image;
    private Graphics2D graphics;
    private final ArrayDeque<Graphics2D> stack = new ArrayDeque<>();

    public Canvas() { this(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)); }
    public Canvas(BufferedImage image) {
        this.image = image;
        graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }
    public BufferedImage getImage() { return image; }
    public void save() { stack.push(graphics); graphics = (Graphics2D) graphics.create(); }
    public void restore() {
        if (stack.isEmpty()) throw new IllegalStateException("Unbalanced Canvas.restore");
        graphics.dispose();
        graphics = stack.pop();
    }
    public void translate(int x, int y) { graphics.translate(x, y); }
    public void clipRect(int x, int y, int width, int height) {
        graphics.clipRect(x, y, Math.max(0, width), Math.max(0, height));
    }
    public void drawColor(int color) {
        fillRect(0, 0, image.getWidth(), image.getHeight(), color);
    }
    public void fillRect(int x, int y, int width, int height, int color) {
        graphics.setColor(new Color(color, true));
        graphics.fillRect(x, y, Math.max(0, width), Math.max(0, height));
    }
    public void strokeRect(int x, int y, int width, int height, int color) {
        graphics.setColor(new Color(color, true));
        graphics.drawRect(x, y, Math.max(0, width - 1), Math.max(0, height - 1));
    }
    public void drawText(String text, int x, int baseline, int size, int color) {
        graphics.setColor(new Color(color, true));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, size));
        graphics.drawString(text, x, baseline);
    }
    @Override public void close() {
        while (!stack.isEmpty()) restore();
        graphics.dispose();
    }
}
