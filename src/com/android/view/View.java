package com.android.view;

import com.android.view.ViewGroup;
import com.android.content.Context;
import com.android.graphics.Canvas;
import com.android.util.AttributeSet;

public class View {

    protected Context mContext;
    protected ViewGroup.LayoutParams mLayoutParams;
    int mMeasuredWidth;
    int mMeasuredHeight;
    private int mMinWidth;
    private int mMinHeight;

    public static final int MEASURED_SIZE_MASK = 0x00ffffff;

    public View(Context context) {
        mContext = context;

    }

    public View(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public View(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public View(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        this(context);

    }

    public ViewGroup.LayoutParams getLayoutParams() {
        return mLayoutParams;
    }

    public void setLayoutParams(ViewGroup.LayoutParams params) {
        mLayoutParams = params;

    }

    public final Context getContext() {
        return mContext;
    }
    public final void measure(int widthMeasureSpec, int heightMeasureSpec) {
        onMeasure(widthMeasureSpec, heightMeasureSpec);

    }
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec));
    }
    protected final void setMeasuredDimension(int measuredWidth, int measuredHeight) {
        setMeasuredDimensionRaw(measuredWidth, measuredHeight);

    }
    public static int getDefaultSize(int size, int measureSpec) {
        int result = size;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);

        switch (specMode) {
            case MeasureSpec.UNSPECIFIED:
                result = size;
                break;
            case MeasureSpec.AT_MOST:
            case MeasureSpec.EXACTLY:
                result = specSize;
                break;
        }
        return result;
    }

    private void setMeasuredDimensionRaw(int measuredWidth, int measuredHeight) {
        mMeasuredWidth = measuredWidth;
        mMeasuredHeight = measuredHeight;
    }
    protected int getSuggestedMinimumWidth() {
        return  mMinWidth ;
    }
    protected int getSuggestedMinimumHeight() {
        return  mMinHeight ;

    }

    public void layout(int l, int t, int r, int b) {
        boolean changed=true;
        onLayout(changed, l, t, r, b);
    }
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {

    }
    public final int getMeasuredWidth() {
        return mMeasuredWidth & MEASURED_SIZE_MASK;
    }
    public final int getMeasuredHeight() {
        return mMeasuredHeight & MEASURED_SIZE_MASK;
    }
    public void draw(Canvas canvas) {
        /*
         * Draw traversal performs several drawing steps which must be executed
         * in the appropriate order:
         *
         *      1. Draw the background
         *      2. If necessary, save the canvas' layers to prepare for fading
         *      3. Draw view's content
         *      4. Draw children
         *      5. If necessary, draw the fading edges and restore layers
         *      6. Draw decorations (scrollbars for instance)
         *      7. If necessary, draw the default focus highlight
         */
        onDraw(canvas);

        dispatchDraw(canvas);

    }
    boolean draw(Canvas canvas, ViewGroup parent, long drawingTime) {
        boolean more = false;

        return  more;
    }
    protected void onDraw(Canvas canvas) {

    }
    protected void dispatchDraw(Canvas canvas) {

    }
    public static class MeasureSpec {

        private static final int MODE_SHIFT = 30;
        private static final int MODE_MASK  = 0x3 << MODE_SHIFT;
        public static final int UNSPECIFIED = 0 << MODE_SHIFT;
        public static final int EXACTLY     = 1 << MODE_SHIFT;
        public static final int AT_MOST     = 2 << MODE_SHIFT;

        public static int makeMeasureSpec( int size,
                                           int mode) {

                return (size & ~MODE_MASK) | (mode & MODE_MASK);
        }

        public static int getSize(int measureSpec) {
            return (measureSpec & ~MODE_MASK);
        }
        public static int getMode(int measureSpec) {
            //noinspection ResourceType
            return (measureSpec & MODE_MASK);
        }
    }
}