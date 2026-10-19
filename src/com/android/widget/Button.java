package com.android.widget;

import com.android.content.Context;
import com.android.graphics.Canvas;
import com.android.util.AttributeSet;

public class Button extends TextView {
    public Button(Context context) { this(context, null); }
    public Button(Context context, AttributeSet attrs) { this(context, attrs, 0); }
    public Button(Context context, AttributeSet attrs, int style) { this(context, attrs, style, 0); }
    public Button(Context context, AttributeSet attrs, int style, int res) {
        super(context, attrs, style, res);
        if (attrs == null || attrs.get("padding") == null) mPadding = 12;
        if (mBackground == null) mBackground = 0xffe8edf3;
    }
    @Override protected void onDraw(Canvas canvas) {
        canvas.strokeRect(0, 0, getWidth(), getHeight(), 0xff8c99a8);
        super.onDraw(canvas);
    }
}
