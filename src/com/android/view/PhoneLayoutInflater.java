package com.android.view;

import com.android.content.Context;
import com.android.util.AttributeSet;

public class PhoneLayoutInflater extends LayoutInflater {
    protected PhoneLayoutInflater(Context context) { super(context); }

    @Override protected View onCreateView(String name, AttributeSet attrs) {
        View view = createView(name, "com.android.widget.", attrs);
        if (view == null) view = createView(name, "com.android.view.", attrs);
        return view;
    }
}
