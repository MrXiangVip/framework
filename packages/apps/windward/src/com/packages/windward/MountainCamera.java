package com.packages.windward;

/** Orbit camera with terrain/solid clearance along the entire target-to-eye segment. */
final class MountainCamera {
    double yaw=0,pitch=.18,distance=9,eyeX,eyeY,eyeZ;
    boolean globe;
    double globeDistance=3000;
    private double arm=Double.NaN,lastTime=Double.NaN,transported;
    void orbit(double dx,double dy){yaw-=dx*.006;pitch=clamp(pitch+dy*.004,.12,1.1);}
    void zoom(int ticks){if(globe)globeDistance=clamp(globeDistance-ticks*120,1500,5000);else distance=clamp(distance-ticks*.65,3,15);}
    void reset(){globe=false;globeDistance=3000;yaw=0;pitch=.18;distance=9;arm=Double.NaN;lastTime=Double.NaN;transported=0;}
    void update(MountainWorld w){
        double dt=Double.isNaN(lastTime)?0:Math.max(0,w.time-lastTime);
        lastTime=w.time;
        update(w,dt);
    }
    void transport(MountainWorld w){yaw-=w.headingTurn-transported;transported=w.headingTurn;}
    void update(MountainWorld w,double dt){
        transport(w);
        if(globe){eyeX=w.x-Math.sin(yaw)*Math.cos(pitch)*globeDistance;eyeY=-MountainPlanet.RADIUS+Math.sin(pitch)*globeDistance;eyeZ=w.z+Math.cos(yaw)*Math.cos(pitch)*globeDistance;return;}
        double target=w.y+1.35,dx=-Math.sin(yaw)*Math.cos(pitch),dz=Math.cos(yaw)*Math.cos(pitch),dy=Math.sin(pitch);
        double allowed=distance;MountainPlanet.Frame frame=MountainPlanet.frame(w.x,w.z);
        for(double t=.2;t<=distance;t+=.1){
            MountainPlanet.Vec sample=frame.point(dx*t,target+dy*t,dz*t);
            double px=MountainPlanet.longitude(sample),pz=MountainPlanet.latitude(sample),py=sample.length()-MountainPlanet.RADIUS;
            boolean hit=py<w.floorAt(px,pz)+.3||w.puzzle.blocks(px,pz,py-.3,.3);
            for(MountainWorld.Obstacle o:w.trees)hit|=MountainPlanet.distance(px,pz,o.x,o.z)<o.r+.3&&py<MountainWorld.ground(o.x,o.z)+o.height+.3;
            for(MountainWorld.Obstacle o:w.rocks)hit|=MountainPlanet.distance(px,pz,o.x,o.z)<o.r+.3&&py<MountainWorld.ground(o.x,o.z)+o.height+.3;
            for(MountainWorld.Obstacle o:w.props)hit|=MountainPlanet.distance(px,pz,o.x,o.z)<o.r+.3&&py<MountainWorld.ground(o.x,o.z)+o.height+.3;
            for(PlanetTerrain.Prop p:w.terrain.nearby(px,pz))hit|=p.r>0&&MountainPlanet.distance(px,pz,p.x,p.z)<p.r+.3&&py<MountainWorld.ground(p.x,p.z)+p.height+.3;
            if(hit){allowed=Math.max(.1,t-.2);break;}
        }
        // Contract immediately for safety; recover with frame-rate independent damping.
        if(Double.isNaN(arm)||allowed<arm)arm=allowed;
        else arm+=(allowed-arm)*(-Math.expm1(-5*Math.max(0,dt)));
        eyeX=w.x+dx*arm;eyeY=target+dy*arm;eyeZ=w.z+dz*arm;
    }
    double targetAltitude(MountainWorld w){return globe?-MountainPlanet.RADIUS:w.y+1.35;}
    double farClip(){return globe?7000:220;}
    static double clamp(double value,double min,double max){return Math.max(min,Math.min(max,value));}
}
