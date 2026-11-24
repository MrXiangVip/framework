package com.packages.windward;

import java.io.*;
import java.util.*;

/** Warm, repeatable full-resolution scenes, with explicit stage synchronization. */
public final class PerformanceTest {
    static final String[] SCENES={"camp","forest","gliding","tower"};
    static void setup(MountainWorld w,MountainCamera c,int scene){
        w.reset();c.reset();
        if(scene==1){w.x=9;w.z=-17;w.y=w.floor();c.yaw=-.5;}
        if(scene==2){w.x=-13;w.z=-20;w.y=8;w.grounded=false;w.gliding=true;w.airBlend=1;c.yaw=-.7;c.distance=7;}
        if(scene==3){w.x=18.5;w.z=-23.6;w.y=w.floor();w.journey.complete=true;w.puzzle.powered=true;w.puzzle.stoneZ=-27;w.combat.enemyHealth=0;c.yaw=.4;c.pitch=.4;c.distance=10;}
    }
    static double percentile(double[] data,double fraction){double[] copy=data.clone();Arrays.sort(copy);return copy[(int)((copy.length-1)*fraction)];}
    public static void main(String[] args)throws Exception{
        File output=new File(args.length==0?"out/windward-tests/performance.csv":args[0]);
        MountainWorld w=new MountainWorld();MountainCamera c=new MountainCamera();
        try(MountainRenderer r=new MountainRenderer();PrintWriter csv=new PrintWriter(output,"UTF-8")){
            r.open(w);r.profile=true;
            csv.println("scene,frame,total_ms,scene_ms,character_ms,skin_cpu_ms,props_ms,read_ms,copy_ms,visible_draws,total_draws");
            System.out.println("PROFILE: "+r.device+"; 900x600; synchronized stages, 50 warmup + 80 samples per scene");
            for(int scene=0;scene<SCENES.length;scene++){
                setup(w,c,scene);double[][] values=new double[7][80];
                for(int i=-50;i<80;i++){
                    w.time+=1./60;w.gait+=.12;w.locomotion=scene==0?.7:0;
                    long begin=System.nanoTime();r.render(w,c);double total=(System.nanoTime()-begin)/1e6;
                    if(i<0)continue;
                    double[] row={total,r.sceneMs,r.characterMs,r.skinMs,r.propsMs,r.readMs,r.copyMs};
                    csv.print(SCENES[scene]+","+i);for(int j=0;j<row.length;j++){values[j][i]=row[j];csv.printf(Locale.ROOT,",%.4f",row[j]);}csv.println(","+r.sceneDraws+","+r.sceneSize());
                }
                System.out.printf(Locale.ROOT,"PROFILE %s: total %.2f / p95 %.2f; scene %.2f; character %.2f (skin CPU %.2f); props %.2f; read %.2f; copy %.2f ms%n",SCENES[scene],percentile(values[0],.5),percentile(values[0],.95),percentile(values[1],.5),percentile(values[2],.5),percentile(values[3],.5),percentile(values[4],.5),percentile(values[5],.5),percentile(values[6],.5));
                System.out.println("PROFILE draws "+SCENES[scene]+": "+r.sceneDraws+" / "+r.sceneSize());
            }
            if(org.lwjgl.opengl.GL11.glGetError()!=0)throw new AssertionError("GL error in performance scenes");
        }
    }
}
