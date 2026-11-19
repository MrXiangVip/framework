package com.packages.riftarena;

/** Fixed 120 Hz simulation, bounded catch-up and an independent render deadline. */
final class ArenaClock {
    static final double STEP=1.0/120;
    private double accumulated;
    private boolean paused;
    double droppedSeconds;
    int advance(double elapsed,boolean playing,Runnable step){
        if(!playing){accumulated=0;paused=true;return 0;}
        if(paused){paused=false;return 0;}
        double available=Math.max(0,elapsed);droppedSeconds+=Math.max(0,available-.2);accumulated+=Math.min(.2,available);
        int count=0;while(accumulated+1e-12>=STEP&&count<24){step.run();accumulated-=STEP;count++;}return count;
    }
}
