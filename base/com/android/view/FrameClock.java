package com.android.view;

/** Software cadence, not a hardware VSYNC or physical presentation clock. */
public final class FrameClock {
    public interface TimeSource { long nanoTime(); }
    private final TimeSource time;
    private final long origin;
    private final long period;

    public FrameClock(double refreshRate, TimeSource time) {
        if (!Double.isFinite(refreshRate) || refreshRate < 1 || refreshRate > 1000)
            throw new IllegalArgumentException("Refresh rate must be between 1 and 1000 Hz");
        this.time = java.util.Objects.requireNonNull(time);
        period = Math.round(1_000_000_000.0 / refreshRate);
        origin = time.nanoTime();
    }

    public long now() { return time.nanoTime(); }
    public long periodNanos() { return period; }
    long nextTick(long after) {
        long elapsed = after - origin;
        return elapsed < 0 ? origin : origin + (elapsed / period + 1) * period;
    }
    long tickAtOrBefore(long now) { return origin + ((now - origin) / period) * period; }

    public static final class Frame {
        public final long id, requestNanos, targetNanos, startNanos, deadlineNanos, skippedTicks;
        Frame(long id, long request, long target, long start, long period, long skipped) {
            this.id = id; requestNanos = request; targetNanos = target;
            startNanos = start; deadlineNanos = target + period; skippedTicks = skipped;
        }
    }
}
