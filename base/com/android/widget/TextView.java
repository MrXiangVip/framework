package com.android.widget;

import com.android.content.Context;
import com.android.graphics.Canvas;
import com.android.util.AttributeSet;
import com.android.view.View;
import java.awt.Font;
import com.android.graphics.FontManager;
import java.awt.font.TextLayout;

public class TextView extends View {
    protected String mText = "";
    protected int mTextSize = 20, mTextColor = 0xff202124;
    private boolean centerText;
    public TextView(Context context) { this(context, null); }
    public TextView(Context context, AttributeSet attrs) { this(context, attrs, 0); }
    public TextView(Context context, AttributeSet attrs, int style) { this(context, attrs, style, 0); }
    public TextView(Context context, AttributeSet attrs, int style, int res) {
        super(context, attrs, style, res);
        if (attrs != null) {
            centerText = "center".equals(attrs.get("gravity"));
            if (attrs.get("text") != null) mText = attrs.get("text");
            mTextSize = Math.max(1, attrs.dimension("textSize", mTextSize));
            mTextColor = attrs.color("textColor", mTextColor);
        }
    }
    public int getTextSize() { return mTextSize; }
    public void setTextSize(int size) { mTextSize = Math.max(1, size); requestLayout(); invalidate(); }
    public void setTextColor(int color) { mTextColor = color; invalidate(); }
    public String getText() { return mText; }
    public void setText(String text) { mText = text == null ? "" : text; requestLayout(); invalidate(); }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = (int) Math.ceil(FontManager.measureText(mText, mTextSize));
        setMeasuredDimension(resolveSize(width + 2 * mPadding + 2, widthSpec),
                resolveSize(mTextSize + 8 + 2 * mPadding, heightSpec));
    }
    @Override protected void onDraw(Canvas canvas) {
        int x = mPadding, baseline = mPadding + mTextSize;
        if (centerText) {
            TextLayout metrics = FontManager.layout(mText.isEmpty() ? " " : mText, mTextSize, Font.PLAIN);
            x = (getWidth() - (int) Math.ceil(FontManager.measureText(mText, mTextSize))) / 2;
            baseline = Math.round((getHeight() - metrics.getAscent() - metrics.getDescent() - metrics.getLeading()) / 2 + metrics.getAscent());
        }
        canvas.drawText(mText, x, baseline, mTextSize, mTextColor);
    }
}
