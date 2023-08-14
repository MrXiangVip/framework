package com.wave;

public class DecorView extends  FrameLayout {
    private PhoneWindow mWindow;

    DecorView(Context context, int featureId, PhoneWindow window,
              LayoutParams params) {
        super(context);
        setWindow(window);

    }

    void setWindow(PhoneWindow phoneWindow) {
        mWindow = phoneWindow;

    }

    public void draw(Canvas canvas) {
        super.draw(canvas);

    }

    public void onDraw(Canvas c) {
        super.onDraw(c);
    }
}