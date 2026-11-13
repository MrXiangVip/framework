package com.packages.riftarena;
import java.util.*;
public final class ArenaPerformanceTest {
    static double median(long[] a){Arrays.sort(a);return a[a.length/2]/1e6;}
    public static void main(String[] args)throws Exception{
        ArenaRenderer r=new ArenaRenderer();r.open(ArenaPerformanceTest.class.getClassLoader(),900,600);
        try{
            System.out.println("Renderer: "+r.device);
            for(boolean cache:new boolean[]{false,true})for(int count:new int[]{2,34}){
                r.cachedScene=cache;
                ArenaWorld w=ArenaSwordTest.fresh();
                for(int i=2;i<count;i++){ArenaWorld.Unit u=w.spawn(ArenaWorld.Kind.SOLDIER,i%2,(i%8-4)*2,(i/8-2)*2);u.range=u.speed=u.attack=0;}
                long[] total=new long[40];
                for(int i=-15;i<40;i++){w.update(.016,0,0,false);long begin=System.nanoTime();r.render(w);if(i>=0)total[i]=System.nanoTime()-begin;}
                System.out.printf(Locale.ROOT,"cache=%s / %d units normal median %.2f ms%n",cache,count,median(total));
                long[][] stages=new long[4][20];r.profile=true;
                for(int i=0;i<20;i++){w.update(.016,0,0,false);r.render(w);stages[0][i]=r.sceneNanos;stages[1][i]=r.unitNanos;stages[2][i]=r.effectNanos;stages[3][i]=r.readbackNanos;}
                r.profile=false;
                System.out.printf(Locale.ROOT,"diagnostic sync stages: scene %.2f / units %.2f / effects %.2f / readback+copy %.2f ms%n",median(stages[0]),median(stages[1]),median(stages[2]),median(stages[3]));
            }
        }finally{r.close();}
    }
}
