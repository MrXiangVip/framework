package com.android.view;

import com.android.content.Context;
import com.android.graphics.Canvas;
import com.android.util.AttributeSet;

public class View {
    protected Context mContext;
    protected ViewGroup.LayoutParams mLayoutParams;
    protected int mMeasuredWidth, mMeasuredHeight;
    protected int mLeft, mTop, mRight, mBottom;
    protected ViewParent mParent;
    protected int mPadding;
    protected Integer mBackground;
    private String mId;
    private OnClickListener mClickListener;
    private boolean mPressed;
    public interface OnClickListener { void onClick(View view); }
    public static final int MEASURED_SIZE_MASK = 0x00ffffff;

    public View(Context context) { this(context, null); }
    public View(Context context, AttributeSet attrs) { this(context, attrs, 0); }
    public View(Context context, AttributeSet attrs, int defStyleAttr) { this(context, attrs, defStyleAttr, 0); }
    public View(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        mContext = context;
        if (attrs != null) {
            mId = normalizeId(attrs.get("id"));
            mPadding = attrs.dimension("padding", 0);
            if (attrs.get("background") != null) mBackground = attrs.color("background", 0);
        }
    }
    public final Context getContext() { return mContext; }
    private static String normalizeId(String id) {
        if (id == null) return null;
        return id.startsWith("@+id/") ? id.substring(5) : id.startsWith("@id/") ? id.substring(4) : id;
    }
    public String getId() { return mId; }
    public void setId(String id) { mId = normalizeId(id); }
    public View findViewById(String id) {
        return mId != null && mId.equals(normalizeId(id)) ? this : null;
    }
    public void setOnClickListener(OnClickListener listener) { mClickListener = listener; }
    public boolean performClick() {
        if (mClickListener == null) return false;
        mClickListener.onClick(this);
        return true;
    }
    public boolean isPressed() { return mPressed; }
    public boolean dispatchKeyEvent(KeyEvent event) { return false; }
    public boolean dispatchTouchEvent(MotionEvent event) { return onTouchEvent(event); }
    protected boolean onTouchEvent(MotionEvent event) {
        if (mClickListener == null) return false;
        boolean inside = event.getX() >= 0 && event.getY() >= 0
                && event.getX() < getWidth() && event.getY() < getHeight();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: mPressed = inside; invalidate(); return inside;
            case MotionEvent.ACTION_MOVE:
                if (!inside && mPressed) { mPressed = false; invalidate(); }
                return true;
            case MotionEvent.ACTION_UP:
                boolean click = mPressed && inside;
                mPressed = false; invalidate();
                if (click) performClick();
                return true;
            case MotionEvent.ACTION_CANCEL: mPressed = false; invalidate(); return true;
            default: return false;
        }
    }
    public ViewGroup.LayoutParams getLayoutParams() { return mLayoutParams; }
    public void setLayoutParams(ViewGroup.LayoutParams params) { mLayoutParams = params; requestLayout(); }
    public ViewParent getParent() { return mParent; }
    public int getLeft() { return mLeft; }
    public int getTop() { return mTop; }
    public int getRight() { return mRight; }
    public int getBottom() { return mBottom; }
    public int getWidth() { return mRight - mLeft; }
    public int getHeight() { return mBottom - mTop; }
    public int getPadding() { return mPadding; }
    public void requestLayout() { if (mParent != null) mParent.requestLayout(); }
    public void invalidate() { if (mParent != null) mParent.invalidate(); }

    public final void measure(int widthSpec, int heightSpec) { onMeasure(widthSpec, heightSpec); }
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(resolveSize(2 * mPadding, widthSpec), resolveSize(2 * mPadding, heightSpec));
    }
    protected final void setMeasuredDimension(int width, int height) {
        mMeasuredWidth = Math.max(0, width); mMeasuredHeight = Math.max(0, height);
    }
    public static int resolveSize(int desired, int spec) {
        int mode = MeasureSpec.getMode(spec), size = MeasureSpec.getSize(spec);
        return mode == MeasureSpec.EXACTLY ? size
                : mode == MeasureSpec.AT_MOST ? Math.min(desired, size) : desired;
    }
    public static int getDefaultSize(int desired, int spec) {
        return MeasureSpec.getMode(spec) == MeasureSpec.UNSPECIFIED ? desired : MeasureSpec.getSize(spec);
    }
    protected int getSuggestedMinimumWidth() { return 0; }
    protected int getSuggestedMinimumHeight() { return 0; }
    public final int getMeasuredWidth() { return mMeasuredWidth; }
    public final int getMeasuredHeight() { return mMeasuredHeight; }
    public void layout(int l, int t, int r, int b) {
        boolean changed = l != mLeft || t != mTop || r != mRight || b != mBottom;
        mLeft = l; mTop = t; mRight = r; mBottom = b;
        onLayout(changed, l, t, r, b);
    }
    protected void onLayout(boolean changed, int l, int t, int r, int b) {}
    public void draw(Canvas canvas) {
        if (mBackground != null) canvas.fillRect(0, 0, getWidth(), getHeight(), mBackground);
        onDraw(canvas);
        dispatchDraw(canvas);
    }
    boolean draw(Canvas canvas, ViewGroup parent, long drawingTime) {
        canvas.save();
        try {
            canvas.translate(mLeft, mTop);
            canvas.clipRect(0, 0, getWidth(), getHeight());
            draw(canvas);
        } finally { canvas.restore(); }
        return false;
    }
    protected void onDraw(Canvas canvas) {}
    protected void dispatchDraw(Canvas canvas) {}

    public static class MeasureSpec {
        private static final int MODE_SHIFT = 30;
        private static final int MODE_MASK = 3 << MODE_SHIFT;
        public static final int UNSPECIFIED = 0;
        public static final int EXACTLY = 1 << MODE_SHIFT;
        public static final int AT_MOST = 2 << MODE_SHIFT;
        public static int makeMeasureSpec(int size, int mode) { return (size & ~MODE_MASK) | mode; }
        public static int getSize(int spec) { return spec & ~MODE_MASK; }
        public static int getMode(int spec) { return spec & MODE_MASK; }
    }
}
