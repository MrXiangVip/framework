package com.android.wm;

import com.android.os.Handler;
import com.android.os.Message;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Native X11 surface. All native access to this display is serialized. */
public final class XWindow implements AutoCloseable {
    static { System.loadLibrary("window"); }
    public interface Listener { void onEvent(int width, int height, boolean closed); }
    private long handle;
    private final ScheduledExecutorService events;
    private final AtomicBoolean callbackPending = new AtomicBoolean();
    private volatile int[] latestState;
    private static native long nativeOpen(int width, int height);
    private static native int[] nativePoll(long handle);
    private static native void nativePresent(long handle, int[] pixels, int width, int height);
    private static native void nativeClose(long handle);

    public XWindow(int width, int height, Listener listener) {
        handle = nativeOpen(width, height);
        if (handle == 0) throw new IllegalStateException("Cannot create X11 window; check DISPLAY");
        Handler ui = new Handler() {
            @Override public void handleMessage(Message message) {
                callbackPending.set(false);
                int[] state = latestState;
                if (isClosed()) return;
                if ((state[0] & 2) != 0) close();
                listener.onEvent(state[1], state[2], (state[0] & 2) != 0);
            }
        };
        events = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "X11-events");
            thread.setDaemon(true);
            return thread;
        });
        events.scheduleWithFixedDelay(() -> {
            int[] state = poll();
            if (state != null && state[0] != 0) {
                latestState = state;
                if (callbackPending.compareAndSet(false, true)) ui.sendMessage(ui.obtainMessage(0, null));
            }
        }, 0, 25, TimeUnit.MILLISECONDS);
    }

    private synchronized int[] poll() { return handle == 0 ? null : nativePoll(handle); }
    public synchronized boolean isClosed() { return handle == 0; }
    public synchronized void present(BufferedImage frame) {
        if (handle == 0) return;
        if (frame.getType() != BufferedImage.TYPE_INT_RGB) throw new IllegalArgumentException("Expected TYPE_INT_RGB");
        int[] pixels = ((DataBufferInt) frame.getRaster().getDataBuffer()).getData();
        nativePresent(handle, pixels, frame.getWidth(), frame.getHeight());
    }
    @Override public synchronized void close() {
        if (handle != 0) {
            nativeClose(handle);
            handle = 0;
            events.shutdownNow();
        }
    }
}
