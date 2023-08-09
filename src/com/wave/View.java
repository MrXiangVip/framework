package com.wave;

public class View {

    protected Context mContext;
    protected ViewGroup.LayoutParams mLayoutParams;

    public View(Context context) {
        mContext = context;

    }

    public View(Context context,  AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public View(Context context,  AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public View(Context context,  AttributeSet attrs, int defStyleAttr, int defStyleRes) {
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

}