package com.android.view;

import com.android.app.Activity;
import com.android.os.Looper;

/** Headless regression checks for window close and Activity destruction. */
public final class WindowCloseLifecycleTest {
    private static final class TestActivity extends Activity {
        final StringBuilder events = new StringBuilder();
        ViewRootImpl root;

        @Override protected void onResume() { events.append("resume "); }
        @Override protected void onPause() { events.append("pause "); }
        @Override protected void onDestroy() {
            events.append("destroy ");
            root.close(); // Application cleanup may re-enter window removal.
        }
    }

    private static void checkLifecycle(boolean minimized) {
        TestActivity activity = new TestActivity();
        activity.root = new ViewRootImpl(null, 0);
        activity.root.setWindowCallback(activity);
        activity.performResume();
        if (minimized) activity.onWindowMinimizedChanged(true);
        activity.root.close();
        activity.root.close();
        if (!activity.isDestroyed()
                || !"resume pause destroy ".equals(activity.events.toString())) {
            throw new AssertionError("Unexpected lifecycle: " + activity.events);
        }
    }

    public static void main(String[] args) {
        Looper.prepareMainLooper();
        checkLifecycle(false);
        checkLifecycle(true);

        ViewRootImpl root = new ViewRootImpl(null, 0);
        final int[] calls = {0};
        RuntimeException failure = new RuntimeException("Application cleanup failed");
        root.setWindowCallback(new Window.Callback() {
            @Override public void onWindowMinimizedChanged(boolean minimized) {}
            @Override public void onWindowClosed() {
                calls[0]++;
                throw failure;
            }
        });
        try {
            root.close();
            throw new AssertionError("Expected callback exception");
        } catch (RuntimeException expected) {
            if (expected != failure) throw expected;
        }
        root.close();
        if (calls[0] != 1) throw new AssertionError("Close callback repeated");
        System.out.println("WindowCloseLifecycleTest passed");
    }
}
