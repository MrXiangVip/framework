package com.android.view;

import com.android.view.*;
import java.io.File;

public class WindowAttributesTest {
    public static void main(String[] args) {
        Window window = new Window(null) {
            public View getDecorView() { return null; }
            public void setContentView(String path) {}
            public void setContentView(View view) {}
            public void setContentView(File file) {}
        };
        window.setLayout(640, 800);
        if (window.getAttributes().width != 640 || window.getAttributes().height != 800)
            throw new AssertionError("Initial dimensions lost");
        final int[] updates = {0};
        window.setCallback(new Window.Callback() {
            public void onWindowMinimizedChanged(boolean value) {}
            public void onWindowAttributesChanged(WindowManager.LayoutParams attrs) {
                updates[0]++;
                if (attrs.width != 420 || attrs.height != 300)
                    throw new AssertionError("Wrong update dimensions");
            }
        });
        window.setLayout(420, 300);
        WindowManager.LayoutParams attrs = new WindowManager.LayoutParams(420, 300);
        attrs.setTitle("Launcher");
        window.setAttributes(attrs);
        attrs.width = 1;
        if (updates[0] != 2 || window.getAttributes().width != 420
                || !window.getAttributes().getTitle().equals("Launcher"))
            throw new AssertionError("Attribute storage/callback failed");
        try {
            window.setLayout(-3, 300);
            throw new AssertionError("Invalid size accepted");
        } catch (IllegalArgumentException expected) {}
        System.out.println("WindowAttributesTest passed");
    }
}
