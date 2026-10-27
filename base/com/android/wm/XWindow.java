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

    private Listener  mListener=null;
    public XWindow(int width, int height) {
        handle = nativeOpen(width, height);
        if (handle == 0) throw new IllegalStateException("Cannot create X11 window; check DISPLAY");
        Handler ui = new Handler() {
            @Override public void handleMessage(Message message) {
                int[] batch = (int[]) message.obj;
                for (int i = 0; i < batch.length && !isClosed(); i += 3) {
                    int modifiers = batch[i] >>> 8, kind = batch[i] & 255, x = batch[i + 1], y = batch[i + 2];
                    if (kind == 1) {
                        if(mListener!=null){
                            mListener.onEvent(x, y, false);
                        }
                    } else if (kind == 2) {
                        close();
                        if(mListener!=null){
                            mListener.onEvent(x, y, true);
                        }
                    } else if (kind == 7 || kind == 8 || kind == 9) {
                       if(mListener!=null ){
                           mListener.onKeyInput(new KeyEvent(kind == 7 ? KeyEvent.TEXT : kind == 8 ? KeyEvent.DOWN : KeyEvent.UP, x, y));
                       }
                    } else {
                        int action = kind == 3 ? MotionEvent.ACTION_DOWN : kind == 4 ? MotionEvent.ACTION_UP
                                : kind == 11 ? MotionEvent.ACTION_DOWN : kind == 12 ? MotionEvent.ACTION_UP
                                : kind == 10 || kind == 14 ? MotionEvent.ACTION_SCROLL : kind == 13 ? MotionEvent.ACTION_HOVER
                                : kind == 5 || kind == 15 ? MotionEvent.ACTION_MOVE : MotionEvent.ACTION_CANCEL;
                        if( mListener !=null ){
                            mListener.onInput(new MotionEvent(action, x, y, kind == 11 || kind == 12 || kind == 15 ? 3 : 1, modifiers,
                                    kind == 10 ? 120 : kind == 14 ? -120 : 0));
                        }
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

    public void registeListener(Listener listener ){
        if( listener !=null ){
            mListener = listener;
        }
    }
}
