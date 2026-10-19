package com.android.view;

public interface ViewParent {
    default void requestLayout() {}
    default void invalidate() {}
}
