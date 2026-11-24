package com.android.view;

import com.android.util.Log;
import java.util.Arrays;
import java.util.Locale;

/** Software budgets and submission timings; never physical display FPS. */
final class FrameTimings {
    private static final String TAG = "FrameTimings ";
    private final long[][] samples = new long[6][256];
    private int count, position;
    private long previous, lateCount, skippedTicks, totalCount;
    private long lastReport = System.nanoTime();
    void record(FrameClock.Frame frame, long request, long begin, long composed, long submitted) {
        long target = frame == null ? begin : frame.targetNanos;
        long deadline = frame == null ? submitted : frame.deadlineNanos;
        long start = frame == null ? begin : frame.startNanos;
        long overrun = Math.max(0, submitted - deadline);
        long[] values = {Math.max(0, start-target), composed-begin, submitted-composed,
                submitted-request, overrun, previous == 0 ? 0 : submitted-previous};
        for (int i=0; i<values.length; i++) samples[i][position]=values[i];
        previous=submitted; position=(position+1)%256; count=Math.min(count+1,256);
        totalCount++; if(overrun>0) lateCount++;
        if(frame!=null) skippedTicks+=frame.skippedTicks;
        if(Boolean.getBoolean("framework.render.trace")) {
            Log.d(TAG, "FrameTrace id="+(frame==null?0:frame.id)
                    +" requestNs="+request+" targetNs="+target+" startNs="+start
                    +" traversalBeginNs="+begin+" drawEndNs="+composed
                    +" submitEndNs="+submitted+" deadlineNs="+deadline
                    +" skippedTicks="+(frame==null?0:frame.skippedTicks));
        }
        if(submitted-lastReport<5_000_000_000L) return;
        lastReport=submitted;
        StringBuilder out=new StringBuilder("Software frame timings (ms, last "+count+" frames)");
        String[] names={"dispatch-lateness","traversal","native-submit","request-to-submit","budget-overrun","submission-interval"};
        for(int i=0;i<samples.length;i++) {
            long[] sorted=Arrays.copyOf(samples[i],count); Arrays.sort(sorted);
            out.append(String.format(Locale.ROOT," | %s p50=%.2f p95=%.2f p99=%.2f",
                    names[i],at(sorted,.5),at(sorted,.95),at(sorted,.99)));
        }
        out.append(" | totalFrames=").append(totalCount).append(" lateFrames=").append(lateCount)
                .append(" skippedTicks=").append(skippedTicks).append(" presentation=unavailable");
        Log.d(TAG, out.toString());
    }
    private static double at(long[] values,double fraction) {
        return values[Math.max(0,(int)Math.ceil(values.length*fraction)-1)]/1e6;
    }
}
