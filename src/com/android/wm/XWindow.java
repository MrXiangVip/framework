package com.android.wm;

import com.android.os.Handler;
import com.android.os.Message;
import com.android.view.MotionEvent;
import com.android.view.KeyEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.concurrent.*;

/** Native X11 surface. All native access to this display is serialized. */
public final class XWindow implements AutoCloseable {
    static { System.loadLibrary("window"); }
    public interface Listener {
        void onEvent(int width, int height, boolean closed);
        default void onInput(MotionEvent event) {}
        default void onKeyInput(KeyEvent event) {}
    }
    private long handle;
    private final ScheduledExecutorService events;
    private static native long nativeOpen(int width, int height);
    private static native int[] nativePoll(long handle);
    private static native void nativePresent(long handle, int[] pixels, int width, int height);
    private static native void nativeClose(long handle);

    public XWindow(int width, int height, Listener listener) {
        handle = nativeOpen(width, height);
        if (handle == 0) throw new IllegalStateException("Cannot create X11 window; check DISPLAY");
        Handler ui = new Handler() {
            @Override public void handleMessage(Message message) {
                int[] batch = (int[]) message.obj;
                for (int i = 0; i < batch.length && !isClosed(); i += 3) {
                    int kind = batch[i], x = batch[i + 1], y = batch[i + 2];
                    if (kind == 1) listener.onEvent(x, y, false);
                    else if (kind == 2) {
                        close();
                        listener.onEvent(x, y, true);
                    } else if (kind == 7 || kind == 8) {
                        listener.onKeyInput(new KeyEvent(kind == 7 ? KeyEvent.TEXT : KeyEvent.DOWN, x, y));
                    } else {
                        int action = kind == 3 ? MotionEvent.ACTION_DOWN : kind == 4 ? MotionEvent.ACTION_UP
                                : kind == 5 ? MotionEvent.ACTION_MOVE : MotionEvent.ACTION_CANCEL;
                        listener.onInput(new MotionEvent(action, x, y));
                    }
                }
            }
        };
        events = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "X11-events");
            thread.setDaemon(true);
            return thread;
        });
        events.scheduleWithFixedDelay(() -> {
            int[] batch = poll();
            if (batch != null && batch.length != 0) ui.sendMessage(ui.obtainMessage(0, batch));
        }, 0, 25, TimeUnit.MILLISECONDS);
    }

    private synchronized int[] poll() { return handle == 0 ? null : nativePoll(handle); }
    public synchronized boolean isClosed() { return handle == 0; }
    public synchronized void present(BufferedImage frame) {
        if (handle == 0) return;
        if (frame.getType() != BufferedImage.TYPE_INT_RGB) throw new IllegalArgumentException("Expected TYPE_INT_RGB");
        nativePresent(handle, ((DataBufferInt) frame.getRaster().getDataBuffer()).getData(), frame.getWidth(), frame.getHeight());
    }
    @Override public synchronized void close() {
        if (handle != 0) {
            nativeClose(handle);
            handle = 0;
            events.shutdownNow();
        }
    }
}
