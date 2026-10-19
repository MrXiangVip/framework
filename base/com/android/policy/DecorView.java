package com.android.policy;


import com.android.content.Context;
import com.android.graphics.Canvas;
import com.android.view.View;
import com.android.view.ViewGroup;
import com.android.widget.FrameLayout;


import static com.android.view.ViewGroup.LayoutParams.MATCH_PARENT;

public class DecorView extends FrameLayout {
    private PhoneWindow mWindow;
    ViewGroup mContentRoot;

    DecorView(Context context, int featureId, PhoneWindow window,
              LayoutParams params) {
        super(context);
        setWindow(window);

    }
    ViewGroup createContentRoot() {
        if (mContentRoot == null) {
            mContentRoot = new FrameLayout(getContext());
            addView(mContentRoot, 0, new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        }
        return mContentRoot;
    }
	
    void setWindow(PhoneWindow phoneWindow) {
        mWindow = phoneWindow;

    }

    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

    }

    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

    }

    public void draw(Canvas canvas) {
        super.draw(canvas);

    }

    public void onDraw(Canvas c) {
        super.onDraw(c);
    }
}