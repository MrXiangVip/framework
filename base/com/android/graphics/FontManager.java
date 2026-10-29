package com.android.graphics;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.io.IOException;
import java.io.InputStream;
import java.text.AttributedString;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Process-wide framework fonts. Measurement and drawing share physical glyph runs. */
public final class FontManager {
    private FontManager() {}
    private static final FontRenderContext METRICS = new FontRenderContext(null, true, true);
    private static final class Fonts {
        static final Font CJK = load("NotoSansCJKsc-Regular.otf");
        static final Font EMOJI = load("NotoEmoji-Regular.ttf");
        static final Font SYSTEM = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
    }
    // Bound retained text as well as entry count. Long documents are never cached.
    private static final Map<String, TextLayout> LAYOUTS = new LinkedHashMap<String, TextLayout>(128, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, TextLayout> entry) { return size() > 256; }
    };
    private static Font load(String name) {
        String path = "/framework/fonts/" + name;
        try (InputStream stream = FontManager.class.getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Missing framework font " + path + "; run base/tools/install_fonts.py --classes <framework classes>");
            return Font.createFont(Font.TRUETYPE_FONT, stream);
        } catch (IOException | FontFormatException error) {
            throw new IllegalStateException("Cannot load shared framework font: " + name, error);
        }
    }
    /** Primary physical font for applications drawing directly with Graphics2D. */
    public static Font getFont(int style, float size) { return Fonts.CJK.deriveFont(style, Math.max(1, size)); }
    public static boolean hasGlyph(int codePoint) { return Fonts.CJK.canDisplay(codePoint) || Fonts.EMOJI.canDisplay(codePoint); }
    private static Font choose(int codePoint) {
        return Fonts.CJK.canDisplay(codePoint) ? Fonts.CJK : Fonts.EMOJI.canDisplay(codePoint) ? Fonts.EMOJI : Fonts.SYSTEM;
    }
    /** Nonempty text only. Surrogate pairs always stay in the same physical font run. */
    public static TextLayout layout(String text, int size, int style) {
        Objects.requireNonNull(text, "text");
        if (text.isEmpty()) throw new IllegalArgumentException("TextLayout requires nonempty text");
        size = Math.max(1, size);
        boolean cache = text.length() <= 1024;
        String key = cache ? style + ":" + size + ":" + text : null;
        if (cache) synchronized (LAYOUTS) {
            TextLayout found = LAYOUTS.get(key); if (found != null) return found;
        }
        AttributedString attributed = new AttributedString(text);
        int start = 0; Font previous = null;
        for (int offset = 0; offset < text.length();) {
            int code = text.codePointAt(offset); Font font = choose(code);
            if (previous != null && previous != font) {
                attributed.addAttribute(TextAttribute.FONT, previous.deriveFont(style, (float) size), start, offset);
                start = offset;
            }
            previous = font; offset += Character.charCount(code);
        }
        attributed.addAttribute(TextAttribute.FONT, previous.deriveFont(style, (float) size), start, text.length());
        TextLayout result = new TextLayout(attributed.getIterator(), METRICS);
        if (cache) synchronized (LAYOUTS) { LAYOUTS.put(key, result); }
        return result;
    }
    public static float measureText(String text, int size) {
        return text.isEmpty() ? 0 : layout(text, size, Font.PLAIN).getAdvance();
    }
}
