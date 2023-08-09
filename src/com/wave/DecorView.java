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
}
