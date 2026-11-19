package com.android.view;

import java.util.Arrays;
import java.util.Locale;

/** Optional UI/X11 timings. Submission completion is not physical screen scanout. */
final class FrameTimings {
    private final long[][] samples=new long[3][256];
    private int count,position;
    private long previous,lastReport=System.nanoTime();
    void record(long begin,long composed,long submitted){
        samples[0][position]=composed-begin;samples[1][position]=submitted-composed;samples[2][position]=previous==0?0:submitted-previous;previous=submitted;position=(position+1)%256;count=Math.min(count+1,256);
        if(submitted-lastReport<5000000000L)return;lastReport=submitted;
        StringBuilder out=new StringBuilder("ViewRoot timings (ms)");String[] names={"measure-layout-draw","native-present","submission-interval"};
        for(int i=0;i<samples.length;i++){long[] values=Arrays.copyOf(samples[i],count);Arrays.sort(values);out.append(String.format(Locale.ROOT," | %s p50=%.2f p95=%.2f p99=%.2f",names[i],at(values,.5),at(values,.95),at(values,.99)));}System.out.println(out);
    }
    private static double at(long[] values,double fraction){return values[Math.max(0,(int)Math.ceil(values.length*fraction)-1)]/1e6;}
}
