package com.packages.windward;

/** Fixed-step melee duel. The sentinel telegraphs a committed, avoidable strike. */
final class MountainCombat {
    enum State { IDLE, CHASE, WINDUP, RECOVERY, RETURN, DEFEATED }
    static final double HOME_X=3,HOME_Z=-12;
    State state=State.IDLE;
    double x,z,facing,timer,attackTime=-1,dodgeTime=-1,dodgeCooldown,hurt,enemyHurt;
    double dodgeX,dodgeZ;
    int health=100,enemyHealth=3;
    boolean swingHit;
    void reset(){state=State.IDLE;x=HOME_X;z=HOME_Z;facing=0;timer=0;attackTime=dodgeTime=-1;dodgeCooldown=hurt=enemyHurt=0;health=100;enemyHealth=3;swingHit=false;}
    boolean busy(){return attackTime>=0||dodgeTime>=0;}
    boolean vulnerable(){return !(dodgeTime>=.04&&dodgeTime<.26)&&hurt<=0;}
    void attack(MountainWorld w){
        if(w.paused||w.journey.summaryOpen||health<=0||!w.grounded||w.climbing||w.gliding||w.puzzle.pushing||busy())return;
        attackTime=0;swingHit=false;
    }
    void dodge(MountainWorld w,double horizontal,double forward,double yaw){
        if(w.paused||w.journey.summaryOpen||health<=0||!w.grounded||w.climbing||w.gliding||w.puzzle.pushing||busy()||dodgeCooldown>0||w.stamina<25)return;
        double len=Math.hypot(horizontal,forward);
        if(len>.01){dodgeX=(horizontal*Math.cos(yaw)+forward*Math.sin(yaw))/len;dodgeZ=(horizontal*Math.sin(yaw)-forward*Math.cos(yaw))/len;}
        else{dodgeX=Math.sin(w.facing);dodgeZ=Math.cos(w.facing);}
        dodgeTime=0;dodgeCooldown=.7;w.stamina-=25;
    }
    void tick(MountainWorld w){
        double dt=MountainWorld.STEP;
        hurt=Math.max(0,hurt-dt);enemyHurt=Math.max(0,enemyHurt-dt);dodgeCooldown=Math.max(0,dodgeCooldown-dt);
        if(dodgeTime>=0){dodgeTime+=dt;if(dodgeTime>=.32)dodgeTime=-1;}
        if(attackTime>=0){
            attackTime+=dt;
            if(attackTime>=.12&&!swingHit){
                swingHit=true;
                if(enemyHealth>0&&inArc(w.x,w.z,w.facing,x,z,2.4,.25)&&Math.abs(w.y-MountainWorld.ground(x,z))<1.3&&clear(w,w.x,w.z,x,z)){
                    enemyHealth--;enemyHurt=.22;
                    if(enemyHealth==0){state=State.DEFEATED;timer=0;w.journey.tell(w,"山谷守卫已平息。通往风塔的山路安全了。",6);}
                    else{state=State.RECOVERY;timer=0;}
                }
            }
            if(attackTime>=.5)attackTime=-1;
        }
        if(enemyHealth<=0)return;
        double distance=Math.hypot(w.x-x,w.z-z);
        boolean reachable=w.grounded&&!w.climbing&&!w.gliding&&Math.abs(w.y-MountainWorld.ground(x,z))<1.3;
        boolean engaged=reachable&&distance<8&&Math.hypot(w.x-HOME_X,w.z-HOME_Z)<10;
        timer+=dt;
        if(state==State.WINDUP){
            if(timer>=.7){
                if(reachable&&inArc(x,z,facing,w.x,w.z,2.35,.3)&&clear(w,x,z,w.x,w.z)&&vulnerable()){
                    health=Math.max(0,health-25);hurt=.65;
                    if(health==0)w.journey.tell(w,"暂时力竭 · 按 R 返回营地重新出发",999);
                }
                state=State.RECOVERY;timer=0;
            }
        }else if(state==State.RECOVERY){if(timer>=.85){state=engaged?State.CHASE:State.RETURN;timer=0;}}
        else if(engaged){
            state=State.CHASE;facing=Math.atan2(w.x-x,w.z-z);
            if(distance<1.8){state=State.WINDUP;timer=0;}
            else walk(w,w.x,w.z,2.1);
        }else{
            double home=Math.hypot(x-HOME_X,z-HOME_Z);
            state=home>.1?State.RETURN:State.IDLE;
            if(home>.1){facing=Math.atan2(HOME_X-x,HOME_Z-z);walk(w,HOME_X,HOME_Z,1.8);}
        }
    }
    private void walk(MountainWorld w,double tx,double tz,double speed){
        double len=Math.hypot(tx-x,tz-z);if(len<.01)return;
        double nx=x+(tx-x)/len*speed*MountainWorld.STEP,nz=z+(tz-z)/len*speed*MountainWorld.STEP;
        if(canStand(w,nx,z))x=nx;if(canStand(w,x,nz))z=nz;
    }
    private boolean canStand(MountainWorld w,double px,double pz){
        return !w.solidAt(px,pz,MountainWorld.ground(px,pz))&&Math.abs(MountainWorld.ground(px,pz)-MountainWorld.ground(x,z))<.08;
    }
    static boolean clear(MountainWorld w,double ax,double az,double bx,double bz){
        double length=Math.hypot(bx-ax,bz-az);
        for(double t=.15;t<length;t+=.15){double u=t/length;if(w.solidAt(ax+(bx-ax)*u,az+(bz-az)*u,w.y))return false;}
        return true;
    }
    static boolean inArc(double ax,double az,double angle,double bx,double bz,double range,double cosine){
        double dx=bx-ax,dz=bz-az,len=Math.hypot(dx,dz);
        return len<=range&&(len<1e-8||(dx*Math.sin(angle)+dz*Math.cos(angle))/len>=cosine);
    }
}
