package com.packages.browser;

import java.awt.*;
import java.awt.font.*;
import java.io.*;
import java.text.AttributedString;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bundled glyph fallback shared by chrome drawing and address caret measurement. */
public final class BrowserText {
    private static final FontRenderContext METRICS = new FontRenderContext(null, true, true);
    private final Font chinese = load("NotoSansCJKsc-Regular.otf");
    private final Font emoji = load("NotoEmoji-Regular.ttf");
    private final Font system = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    private final Map<String, TextLayout> layouts = new LinkedHashMap<String, TextLayout>(128, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, TextLayout> entry) { return size() > 256; }
    };
    private static Font load(String name) {
        try (InputStream stream = BrowserText.class.getClassLoader().getResourceAsStream("res/fonts/" + name)) {
            if (stream == null) throw new IOException("Missing browser font: " + name);
            return Font.createFont(Font.TRUETYPE_FONT, stream);
        } catch (IOException | FontFormatException e) { throw new IllegalStateException("Cannot load browser font: " + name, e); }
    }
    public boolean hasBundledGlyph(int code) { return chinese.canDisplay(code) || emoji.canDisplay(code); }
    private Font choose(int code) { return chinese.canDisplay(code) ? chinese : emoji.canDisplay(code) ? emoji : system; }
    private TextLayout layout(String value, int size, boolean bold) {
        String key = size + ":" + bold + ":" + value;
        TextLayout cached = layouts.get(key); if (cached != null) return cached;
        AttributedString attributed = new AttributedString(value);
        int start = 0; Font previous = null;
        // Keep surrogate pairs together and select a physical font for every glyph run.
        for (int offset = 0; offset < value.length();) {
            int code = value.codePointAt(offset); Font font = choose(code);
            if (previous != null && previous != font) {
                attributed.addAttribute(TextAttribute.FONT, previous.deriveFont(bold ? Font.BOLD : Font.PLAIN, (float) size), start, offset);
                start = offset;
            }
            previous = font; offset += Character.charCount(code);
        }
        attributed.addAttribute(TextAttribute.FONT, previous.deriveFont(bold ? Font.BOLD : Font.PLAIN, (float) size), start, value.length());
        TextLayout result = new TextLayout(attributed.getIterator(), METRICS); layouts.put(key, result); return result;
    }
    public int width(String value, int size) { return value.isEmpty() ? 0 : (int) Math.ceil(layout(value, size, false).getAdvance()); }
    public void draw(Graphics2D g, String value, int x, int baseline, int size, int color, boolean bold) {
        if (value.isEmpty()) return;
        g.setColor(new Color(color)); layout(value, size, bold).draw(g, x, baseline);
    }
}
