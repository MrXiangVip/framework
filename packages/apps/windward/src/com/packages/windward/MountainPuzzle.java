package com.packages.windward;

/** A constrained push-stone puzzle: no rigid-body simulation or irreversible dead ends. */
final class MountainPuzzle {
    static final double X=22,START_Z=-19,PLATE_Z=-27,MIN_Z=-29,MAX_Z=-19;
    static final double TERMINAL_X=18.5,TERMINAL_Z=-25;
    double stoneZ=START_Z,charge;
    boolean pushing,powered;
    void reset(){stoneZ=START_Z;charge=0;pushing=powered=false;}
    double stoneY(){return MountainWorld.ground(X,stoneZ);}
    boolean nearStone(MountainWorld w){return w.grounded&&!w.climbing&&!w.gliding&&Math.abs(w.x-X)<1.4&&Math.abs(w.z-stoneZ)<2&&Math.abs(w.y-stoneY())<.8;}
    boolean nearTerminal(MountainWorld w){return w.grounded&&Math.hypot(w.x-TERMINAL_X,w.z-TERMINAL_Z)<2&&Math.abs(w.y-MountainWorld.ground(TERMINAL_X,TERMINAL_Z))<1;}
    boolean blocks(double x,double z,double feet,double margin){return Math.abs(x-X)<.7+margin&&Math.abs(z-stoneZ)<.7+margin&&feet<stoneY()+1.4;}
    double support(double x,double z){return Math.abs(x-X)<.7&&Math.abs(z-stoneZ)<.7?stoneY()+1.4:Double.NEGATIVE_INFINITY;}
    String hint(MountainWorld w){
        if(pushing&&charge>0)return "压盘中 · 松开方向键，等待风纹亮起";
        if(pushing)return "推石中 · W/S 沿石轨前后推拉  E 松手";
        if(nearStone(w)&&powered)return "石块已固定 · 前往塔旁石台";
        if(nearStone(w))return "E 扶住石块 · 将它推上北端圆盘";
        if(nearTerminal(w))return w.journey.complete?"风塔已激活":w.combat.enemyHealth>0?"先平息山路守卫，再来激活风塔":!powered?"让石块压住圆盘，等待风纹亮起":"E 激活古老风塔";
        return "";
    }
    boolean interact(MountainWorld w){
        if(pushing){pushing=false;return true;}
        if(nearTerminal(w)){
            if(w.journey.complete)return true;
            if(w.combat.enemyHealth>0){w.journey.tell(w,"风塔尚不回应：先平息山路上的守卫。",5);return true;}
            if(!powered){w.journey.tell(w,"风塔尚不回应：将东侧石块推上圆盘。",5);return true;}
            w.journey.complete=true;w.journey.summaryOpen=true;w.journey.completedAt=w.time;
            w.journey.tell(w,"风塔苏醒！山谷初行完成。",8);return true;
        }
        if(!nearStone(w)||powered||w.journey.complete)return false;
        // Always stand on the south handle; keep the whole puzzle recoverable.
        double handleZ=stoneZ+1.15;
        if(w.staticSolidAt(X,handleZ,MountainWorld.ground(X,handleZ)))return false;
        w.x=X;w.z=handleZ;w.y=MountainWorld.ground(w.x,w.z);w.facing=w.visualFacing=Math.PI;pushing=true;return true;
    }
    void push(MountainWorld w,double forward){
        double next=MountainCamera.clamp(stoneZ-MountainCamera.clamp(forward,-1,1)*1.4*MountainWorld.STEP,MIN_Z,MAX_Z);
        double handle=next+1.15;
        if(!w.staticSolidAt(X,next,MountainWorld.ground(X,next))&&!w.staticSolidAt(X,handle,MountainWorld.ground(X,handle))){
            double travel=Math.abs(next-stoneZ);stoneZ=next;w.x=X;w.z=handle;w.y=MountainWorld.ground(w.x,w.z);w.distance+=travel;w.moving=travel>1e-8;w.facing=Math.PI;
        }
    }
    void update(MountainWorld w){
        if(powered)return;
        charge=Math.abs(stoneZ-PLATE_Z)<.55?Math.min(1,charge+MountainWorld.STEP):0;
        if(charge>=1){
            powered=true;pushing=false;
            w.journey.tell(w,"风纹亮起。石块已固定，前往塔旁石台激活风塔。",7);
        }
    }
}
