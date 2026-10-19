package com.android.content;

public final class ComponentName {
    private final String mPackage;
    private final String mClass;


    public ComponentName(String pkg,  String cls) {
        mPackage = pkg;
        mClass = cls;
    }

}
