package com.android.policy;


import com.android.content.Context;
import com.android.graphics.Canvas;
import com.android.view.View;
import com.android.view.LayoutInflater;
import com.android.view.ViewGroup;
import com.android.widget.FrameLayout;

import java.io.File;

import static com.android.view.ViewGroup.LayoutParams.MATCH_PARENT;

public class DecorView extends FrameLayout {
    private PhoneWindow mWindow;
    ViewGroup mContentRoot;

    DecorView(Context context, int featureId, PhoneWindow window,
              LayoutParams params) {
        super(context);
        setWindow(window);

    }
    void onResourcesLoaded(LayoutInflater inflater, File file) {
        final View root = inflater.inflate(file, null);
        addView(root, 0, new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        mContentRoot = (ViewGroup) root;
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