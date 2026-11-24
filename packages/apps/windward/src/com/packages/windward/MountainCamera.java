package com.packages.windward;

/** Orbit camera with terrain/solid clearance along the entire target-to-eye segment. */
final class MountainCamera {
    double yaw=0,pitch=.18,distance=9,eyeX,eyeY,eyeZ;
    private double arm=Double.NaN,lastTime=Double.NaN;
    void orbit(double dx,double dy){yaw-=dx*.006;pitch=clamp(pitch+dy*.004,.12,1.1);}
    void zoom(int ticks){distance=clamp(distance-ticks*.65,3,15);}
    void reset(){yaw=0;pitch=.18;distance=9;arm=Double.NaN;lastTime=Double.NaN;}
    void update(MountainWorld w){
        double dt=Double.isNaN(lastTime)?0:Math.max(0,w.time-lastTime);
        lastTime=w.time;
        update(w,dt);
    }
    void update(MountainWorld w,double dt){
        double target=w.y+1.35,dx=-Math.sin(yaw)*Math.cos(pitch),dz=Math.cos(yaw)*Math.cos(pitch),dy=Math.sin(pitch);
        double allowed=distance;
        for(double t=.2;t<=distance;t+=.1){
            double px=w.x+dx*t,py=target+dy*t,pz=w.z+dz*t;
            boolean hit=py<MountainTraversal.floor(px,pz)+.3||w.puzzle.blocks(px,pz,py-.3,.3);
            for(MountainWorld.Obstacle o:w.trees)hit|=Math.hypot(px-o.x,pz-o.z)<o.r+.3&&py<MountainWorld.ground(o.x,o.z)+o.height+.3;
            for(MountainWorld.Obstacle o:w.rocks)hit|=Math.hypot(px-o.x,pz-o.z)<o.r+.3&&py<MountainWorld.ground(o.x,o.z)+o.height+.3;
            for(MountainWorld.Obstacle o:w.props)hit|=Math.hypot(px-o.x,pz-o.z)<o.r+.3&&py<MountainWorld.ground(o.x,o.z)+o.height+.3;
            if(hit){allowed=Math.max(.1,t-.2);break;}
        }
        // Contract immediately for safety; recover with frame-rate independent damping.
        if(Double.isNaN(arm)||allowed<arm)arm=allowed;
        else arm+=(allowed-arm)*(-Math.expm1(-5*Math.max(0,dt)));
        eyeX=w.x+dx*arm;eyeY=target+dy*arm;eyeZ=w.z+dz*arm;
    }
    static double clamp(double value,double min,double max){return Math.max(min,Math.min(max,value));}
}
