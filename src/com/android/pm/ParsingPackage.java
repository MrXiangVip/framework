package com.android.pm;
public interface ParsingPackage {
    String getPackageName();
    long getVersionCode();
    boolean isCoreApp();
}