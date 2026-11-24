package com.android.view;

import com.android.os.Handler;
import com.android.os.Looper;
import com.android.os.Message;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Demand-driven software frame scheduler. All callbacks execute on the owning UI thread. */
public final class Choreographer {
    public static final int VSYNC_SOURCE_APP = 0;
    public static final int CALLBACK_TRAVERSAL = 3;
    private static final int MSG_FRAME = 0;
    interface Scheduler {
        void schedule(Runnable callback, long targetNanos);
        void cancel();
    }
    private final FrameClock clock;
    private final Scheduler scheduler;
    private final List<Callback> callbacks = new ArrayList<>();
    private long scheduledTarget, nextId;
    private boolean scheduled;
    private FrameClock.Frame currentFrame;

    private static final ThreadLocal<Choreographer> INSTANCES = new ThreadLocal<Choreographer>() {
        @Override protected Choreographer initialValue() {
            Looper looper = Looper.myLooper();
            if (looper == null) throw new IllegalStateException("A UI Looper is required");
            FrameClock clock = new FrameClock(Double.parseDouble(
                    System.getProperty("framework.render.hz", "60")), System::nanoTime);
            Handler handler = new Handler(looper) {
                @Override public void handleMessage(Message message) { ((Runnable) message.obj).run(); }
            };
            return new Choreographer(clock, new Scheduler() {
                public void schedule(Runnable action, long target) {
                    // Round up: the millisecond queue must not dispatch before the target.
                    handler.sendMessageAtTime(handler.obtainMessage(MSG_FRAME, action),
                            Math.floorDiv(target, 1_000_000L) + (Math.floorMod(target, 1_000_000L) == 0 ? 0 : 1));
                }
                public void cancel() { handler.removeMessages(MSG_FRAME); }
            });
        }
    };

    Choreographer(FrameClock clock, Scheduler scheduler) {
        this.clock = clock; this.scheduler = scheduler;
    }
    public static Choreographer getInstance() { return INSTANCES.get(); }
    public synchronized FrameClock.Frame getCurrentFrame() { return currentFrame; }
    public void postCallback(int type, Runnable action, Object token) {
        postCallbackDelayed(type, action, token, 0);
    }
    public synchronized void postCallbackDelayed(int type, Runnable action, Object token, long delayMillis) {
        long now = clock.now();
        long delay = Math.max(0, delayMillis);
        long due = delay > (Long.MAX_VALUE - Math.max(0, now)) / 1_000_000L
                ? Long.MAX_VALUE : now + delay * 1_000_000L;
        callbacks.add(new Callback(type, Objects.requireNonNull(action), token, now, due));
        scheduleNext();
    }
    public synchronized void removeCallbacks(int type, Runnable action, Object token) {
        callbacks.removeIf(c -> c.type == type && (action == null || c.action == action)
                && (token == null || c.token == token));
        scheduleNext();
    }
    private void scheduleNext() {
        if (callbacks.isEmpty()) {
            if (scheduled) scheduler.cancel();
            scheduled = false;
            return;
        }
        long due = Long.MAX_VALUE;
        for (Callback callback : callbacks) due = Math.min(due, callback.due);
        long target = clock.nextTick(Math.max(clock.now(), due));
        if (!scheduled || target < scheduledTarget) {
            if (scheduled) scheduler.cancel();
            scheduled = true; scheduledTarget = target;
            scheduler.schedule(this::doFrame, target);
        }
    }
    private void doFrame() {
        List<Callback> ready = new ArrayList<>();
        synchronized (this) {
            long now = clock.now();
            long skipped = Math.max(0, (now - scheduledTarget) / clock.periodNanos());
            long target = scheduledTarget + skipped * clock.periodNanos();
            scheduled = false;
            long request = now;
            for (Callback callback : callbacks) {
                if (callback.due <= now) { ready.add(callback); request = Math.min(request, callback.request); }
            }
            callbacks.removeAll(ready);
            currentFrame = new FrameClock.Frame(++nextId, request, target, now, clock.periodNanos(), skipped);
        }
        ready.sort(Comparator.comparingInt(c -> c.type));
        try {
            for (Callback callback : ready) {
                callback.action.run();
            }
        } finally {
            synchronized (this) { currentFrame = null; scheduleNext(); }
        }
    }
    private static final class Callback {
        final int type;
        final Runnable action;
        final Object token;
        final long request, due;
        Callback(int type, Runnable action, Object token, long request, long due) {
            this.type = type; this.action = action; this.token = token; this.request = request; this.due = due;
        }
    }
}
