package com.packages.windward;

/** Exploration discoveries and the region completion summary. */
final class MountainJourney {
    static final double[][] ROUTE={{0,12},{1,-2},{4,-21},{15,-25}};
    static final String[] NAMES={"营地","林间路标","山脊岔口","风塔山脚"};
    int stage=1;
    static final double CACHE_X=-17,CACHE_Z=-23;
    boolean runeRead,summit,treasure,complete,summaryOpen;
    double completedAt;
    double noticeUntil;
    String notice="";
    void reset(){stage=1;runeRead=summit=treasure=complete=summaryOpen=false;completedAt=0;noticeUntil=0;notice="";}
    boolean arrived(){return stage==ROUTE.length;}
    double targetX(){return ROUTE[Math.min(stage,ROUTE.length-1)][0];}
    double targetZ(){return ROUTE[Math.min(stage,ROUTE.length-1)][1];}
    double guideX(MountainWorld w){
        if(complete)return 15;
        if(arrived()||treasure){
            if(w.combat.enemyHealth>0)return MountainCombat.HOME_X;
            return w.puzzle.powered?MountainPuzzle.TERMINAL_X:MountainPuzzle.X;
        }
        return summit&&!treasure?CACHE_X:targetX();
    }
    double guideZ(MountainWorld w){
        if(complete)return -25;
        if(arrived()||treasure){
            if(w.combat.enemyHealth>0)return MountainCombat.HOME_Z;
            if(w.puzzle.powered)return MountainPuzzle.TERMINAL_Z;
            return w.puzzle.pushing?MountainPuzzle.PLATE_Z:w.puzzle.stoneZ;
        }
        return summit&&!treasure?CACHE_Z:targetZ();
    }
    String objective(MountainWorld w){
        if(complete)return "山谷初行完成 · 可继续自由探索";
        if(arrived()||treasure){
            if(w.combat.enemyHealth>0)return "平息山路上的守卫";
            return w.puzzle.powered?"前往塔旁石台 · 激活风塔":"将东侧石块推上圆盘";
        }
        return summit?"从岩台滑翔 · 寻找西北方的遗物":"沿山路前往"+NAMES[stage];
    }
    void update(MountainWorld w){
        if(!complete&&!arrived()&&w.grounded&&Math.hypot(w.x-targetX(),w.z-targetZ())<3){
            stage++;notice=arrived()?"发现古老风塔：东侧石轨似乎连接着塔旁石台。":stage==2?"穿过林间，前方山路通向山脊。":"抵达山脊。岔路旁似乎有一块古老符文石。";
            noticeUntil=w.time+6;
        }
    }
    void tell(MountainWorld w,String text,double seconds){notice=text;noticeUntil=w.time+seconds;}
    boolean nearCache(MountainWorld w){return w.grounded&&Math.hypot(w.x-CACHE_X,w.z-CACHE_Z)<2.4;}
    boolean nearRune(MountainWorld w){return w.grounded&&Math.hypot(w.x-w.rune.x,w.z-w.rune.z)<3;}
    void interact(MountainWorld w){
        if(w.paused)return;
        if(nearCache(w)){if(!treasure){treasure=true;tell(w,"获得：风之印记。山谷隐藏的遗物已找到！",8);}return;}
        if(!nearRune(w))return;
        runeRead=true;notice="石刻：攀上西侧岩台，乘风寻找遗物。";noticeUntil=w.time+7;
    }
    static double pathX(double z){
        for(int i=0;i<ROUTE.length-1;i++)if(z>=ROUTE[i+1][1]){
            double t=Math.max(0,Math.min(1,(ROUTE[i][1]-z)/(ROUTE[i][1]-ROUTE[i+1][1])));
            t=t*t*(3-2*t);return ROUTE[i][0]+(ROUTE[i+1][0]-ROUTE[i][0])*t;
        }
        return ROUTE[ROUTE.length-1][0];
    }
}
