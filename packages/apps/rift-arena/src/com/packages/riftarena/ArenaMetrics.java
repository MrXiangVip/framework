package com.packages.riftarena;

import java.util.*;
import java.lang.management.*;

/** Bounded, opt-in timings; no glFinish and no per-sample arrays. */
final class ArenaMetrics {
    private final long[][] samples=new long[5][256];
    private final int[] counts=new int[5],positions=new int[5];
    private static final String[] NAMES={"simulation","GL+readback","UI-compose","frame-age-at-UI","input-to-simulation"};
    final boolean enabled=Boolean.getBoolean("framework.riftarena.profile");
    private long lastReport=System.nanoTime();
    synchronized void record(int stage,long nanos){if(!enabled)return;samples[stage][positions[stage]]=Math.max(0,nanos);positions[stage]=(positions[stage]+1)%256;counts[stage]=Math.min(256,counts[stage]+1);}
    synchronized void report(){if(!enabled||System.nanoTime()-lastReport<5000000000L)return;lastReport=System.nanoTime();StringBuilder out=new StringBuilder("RiftArena timings (ms)");for(int i=0;i<samples.length;i++){if(counts[i]==0)continue;long[] sorted=Arrays.copyOf(samples[i],counts[i]);Arrays.sort(sorted);out.append(String.format(Locale.ROOT," | %s p50=%.2f p95=%.2f p99=%.2f",NAMES[i],percentile(sorted,.5),percentile(sorted,.95),percentile(sorted,.99)));}long gc=0,ms=0;for(GarbageCollectorMXBean bean:ManagementFactory.getGarbageCollectorMXBeans()){gc+=Math.max(0,bean.getCollectionCount());ms+=Math.max(0,bean.getCollectionTime());}System.out.println(out+" | process GC total="+gc+" / "+ms+"ms");}
    private static double percentile(long[] values,double p){return values[Math.max(0,(int)Math.ceil(values.length*p)-1)]/1e6;}
}
