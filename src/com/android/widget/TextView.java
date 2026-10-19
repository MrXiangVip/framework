package com.android.widget;

import com.android.content.Context;
import com.android.graphics.Canvas;
import com.android.util.AttributeSet;
import com.android.view.View;
import java.awt.Font;
import java.awt.font.FontRenderContext;

public class TextView extends View {
    protected String mText = "";
    protected int mTextSize = 20, mTextColor = 0xff202124;
    private static final FontRenderContext FONT_CONTEXT = new FontRenderContext(null, true, true);
    public TextView(Context context) { this(context, null); }
    public TextView(Context context, AttributeSet attrs) { this(context, attrs, 0); }
    public TextView(Context context, AttributeSet attrs, int style) { this(context, attrs, style, 0); }
    public TextView(Context context, AttributeSet attrs, int style, int res) {
        super(context, attrs, style, res);
        if (attrs != null) {
            if (attrs.get("text") != null) mText = attrs.get("text");
            mTextSize = Math.max(1, attrs.dimension("textSize", mTextSize));
            mTextColor = attrs.color("textColor", mTextColor);
        }
    }
    public String getText() { return mText; }
    public void setText(String text) { mText = text == null ? "" : text; requestLayout(); invalidate(); }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, mTextSize);
        int width = (int) Math.ceil(font.getStringBounds(mText, FONT_CONTEXT).getWidth());
        setMeasuredDimension(resolveSize(width + 2 * mPadding + 2, widthSpec),
                resolveSize(mTextSize + 8 + 2 * mPadding, heightSpec));
    }
    @Override protected void onDraw(Canvas canvas) {
        canvas.drawText(mText, mPadding, mPadding + mTextSize, mTextSize, mTextColor);
    }
}
