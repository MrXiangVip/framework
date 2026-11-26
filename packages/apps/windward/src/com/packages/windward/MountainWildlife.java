package com.packages.windward;

import java.util.*;

/** Deterministic ambient wildlife; only nearby spawns are retained, motion follows simulation time. */
final class MountainWildlife {
    static final int MAX_ACTIVE=80;
    static final class Spawn {
        final double x,z,phase;final boolean bird;
        Spawn(double x,double z,double phase,boolean bird){this.x=x;this.z=z;this.phase=phase;this.bird=bird;}
    }
    static final class Sample {
        final double x,z,y,heading,flap;
        Sample(double x,double z,double y,double heading,double flap){this.x=x;this.z=z;this.y=y;this.heading=heading;this.flap=flap;}
    }
    private PlanetTerrain.Tile anchor;
    private List<Spawn> active=Collections.emptyList();
    List<Spawn> active(){return active;}
    static List<Spawn> spawns(PlanetTerrain.Tile tile){
        Random random=new Random(0x4249524457494e47L+tile.hashCode()*7919L);
        List<Spawn> result=new ArrayList<>();
        MountainPlanet.Vec n=PlanetTerrain.direction(tile.face,tile.u()+tile.size()/2,tile.v()+tile.size()/2);
        double x=MountainPlanet.longitude(n),z=MountainPlanet.latitude(n);
        if(MountainPlanet.distance(0,0,x,z)<75)return result;
        double h=MountainWorld.ground(x,z);
        if(random.nextDouble()<.09)for(int i=0;i<3;i++)result.add(new Spawn(x,z,random.nextDouble()*Math.PI*2,true));
        if(h>MountainPlanet.WATER+1&&h<28&&MountainPlanet.moisture(n)>.35&&random.nextDouble()<.3)
            for(int i=0;i<2;i++)result.add(new Spawn(x,z,random.nextDouble()*Math.PI*2,false));
        return result;
    }
    void update(Set<PlanetTerrain.Tile> tiles,PlanetTerrain.Tile next){
        if(next.equals(anchor))return;
        List<Spawn> result=new ArrayList<>();
        for(int i=0;i<5;i++)result.add(new Spawn(3+i*1.3,5+i*1.1,i*1.7,false));
        for(int i=0;i<3;i++)result.add(new Spawn(0,-12,i*.7,true));
        for(PlanetTerrain.Tile tile:tiles)if(tile.level==PlanetTerrain.DEPTH)result.addAll(spawns(tile));
        MountainPlanet.Vec n=PlanetTerrain.direction(next.face,next.u()+next.size()/2,next.v()+next.size()/2);
        final double x=MountainPlanet.longitude(n),z=MountainPlanet.latitude(n);
        result.removeIf(s->MountainPlanet.distance(x,z,s.x,s.z)>120);
        result.sort(Comparator.comparingDouble(s->MountainPlanet.distance(x,z,s.x,s.z)));
        if(result.size()>MAX_ACTIVE)result=new ArrayList<>(result.subList(0,MAX_ACTIVE));
        active=Collections.unmodifiableList(result);anchor=next;
    }
    static Sample sample(Spawn spawn,double time,double playerX,double playerZ){
        double angle=spawn.phase+time*(spawn.bird?.22:.9),radius=spawn.bird?18+spawn.phase:1.2;
        double east=Math.cos(angle)*radius,south=Math.sin(angle)*radius;
        double distance=MountainPlanet.distance(spawn.x,spawn.z,playerX,playerZ);
        if(!spawn.bird&&distance<3){
            MountainPlanet.Frame f=MountainPlanet.frame(spawn.x,spawn.z);MountainPlanet.Vec p=MountainPlanet.normal(playerX,playerZ);
            double dx=-f.east.dot(p),dz=-f.south.dot(p),length=Math.hypot(dx,dz);
            double avoidance=2*Math.pow(1-distance/3,2);
            if(length>1e-9){east+=dx/length*avoidance;south+=dz/length*avoidance;}
        }
        MountainPlanet.Move p=MountainPlanet.advance(spawn.x,spawn.z,east,south);
        double y=Math.max(MountainWorld.ground(p.x,p.z),MountainPlanet.WATER)+(spawn.bird?13:1.1)
                +Math.sin(time*(spawn.bird?.8:2.5)+spawn.phase)*(spawn.bird?1.5:.3);
        return new Sample(p.x,p.z,y,-angle+p.turn,time*(spawn.bird?7:18)+spawn.phase);
    }
}
