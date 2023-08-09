package com.wave;

public class PhoneLayoutInflater extends LayoutInflater {
    private static final String[] sClassPrefixList = {
            "com.wave.widget.",
    };

    protected PhoneLayoutInflater(Context context) {
        super(context);
    }

    @Override
    protected View onCreateView(String name, AttributeSet attrs) {
        for (String prefix : sClassPrefixList) {
            try {

                View view = createView(name, prefix, attrs);
                if (view != null) {
                    return view;
                }
            } catch (Exception e) {

            }
        }
        return super.onCreateView(name, attrs);
    }
}
