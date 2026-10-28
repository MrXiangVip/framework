package com.android.widget;

import com.android.content.Context;
import com.android.graphics.Canvas;
import com.android.util.AttributeSet;

/** ImageView with this framework's existing button chrome and click handling. */
public class ImageButton extends ImageView {
    public ImageButton(Context context) { this(context, null); }
    public ImageButton(Context context, AttributeSet attrs) { this(context, attrs, 0); }
    public ImageButton(Context context, AttributeSet attrs, int style) { this(context, attrs, style, 0); }
    public ImageButton(Context context, AttributeSet attrs, int style, int res) {
        super(context, attrs, style, res);
        // Preserve the old ImageButton's no-upscale placement when XML does not override it.
        if (attrs == null || attrs.get("scaleType") == null) setScaleType(ScaleType.CENTER_INSIDE);
        if (attrs == null || (attrs.get("padding") == null && attrs.get("paddingLeft") == null
                && attrs.get("paddingTop") == null && attrs.get("paddingRight") == null && attrs.get("paddingBottom") == null)) setPadding(12);
        if (mBackground == null) mBackground = 0xffe8edf3;
        setFocusable(true);
    }
    @Override protected void onDraw(Canvas canvas) {
        canvas.strokeRect(0, 0, getWidth(), getHeight(), 0xff8c99a8);
        if (isPressed()) canvas.fillRect(1, 1, getWidth() - 2, getHeight() - 2, 0xffc9d8ec);
        super.onDraw(canvas);
    }
}
