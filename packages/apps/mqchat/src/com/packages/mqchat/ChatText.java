package com.packages.mqchat;

import com.android.graphics.Canvas;
import com.android.graphics.FontManager;
import java.awt.*;

/** Measurement, line breaking, input carets and drawing share framework glyph runs. */
public final class ChatText {
    public ChatText(ClassLoader loader) {} // Source-compatible; fonts belong to the framework loader.
    // The framework's static Java 8 emoji font predates some Android emoji.
    // Use a close older glyph for display only; stored/encrypted Unicode stays unchanged.
    static String displayText(String value) {
        StringBuilder result=new StringBuilder();
        for(int i=0;i<value.length();){int code=value.codePointAt(i);i+=Character.charCount(code);
            if(code>=0x1f000&&!FontManager.hasGlyph(code)){
                String fallback;
                switch(code){
                    case 0x1f642: fallback="☺";break;
                    case 0x1f641: fallback="😞";break;
                    case 0x1f910: fallback="😶";break;
                    case 0x1f972: case 0x1f979: case 0x1f97a: fallback="😢";break;
                    case 0x1f92d: case 0x1fae2: fallback="😳";break;
                    case 0x1fae1: fallback="😌";break;
                    case 0x1f923: fallback="😂";break;
                    case 0x1f914: fallback="😕";break;
                    case 0x1f92b: fallback="😶";break;
                    case 0x1f92f: fallback="😲";break;
                    case 0x1f971: fallback="😴";break;
                    case 0x1f917: fallback="😃";break;
                    case 0x1f970: case 0x1f929: fallback="😍";break;
                    case 0x1f913: fallback="😎";break;
                    case 0x1f5a4: case 0x1f9e1: case 0x1f90d: case 0x1f90e: fallback="❤";break;
                    case 0x1f91e: case 0x1f91f: case 0x1f918: fallback="✌";break;
                    case 0x1f919: fallback="☎";break;
                    case 0x1f91a: case 0x1f596: fallback="✋";break;
                    case 0x1f932: case 0x1f91d: fallback="🙏";break;
                    case 0x1f91b: case 0x1f91c: fallback="👊";break;
                    case 0x1faf6: fallback="💕";break;
                    default: fallback="[表情]";
                }
                result.append(fallback);
            }else result.appendCodePoint(code);
        }
        return result.toString();
    }
    public boolean hasBundledGlyph(int code) { return FontManager.hasGlyph(code); }
    public float width(String value, int size) { return FontManager.measureText(displayText(value), size); }
    public void draw(Graphics2D graphics, String value, int x, int baseline, int size, int color) {
        if (value.isEmpty()) return;
        graphics.setColor(new Color(color)); FontManager.layout(displayText(value), size, Font.PLAIN).draw(graphics, x, baseline);
    }
    public void draw(Canvas canvas, String value, int x, int baseline, int size, int color) {
        canvas.drawText(displayText(value), x, baseline, size, color);
    }
}