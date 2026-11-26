package com.packages.windward;

import java.util.*;

/** Graphics-independent fixed-step character controller. All lengths are world metres. */
final class MountainWorld {
    static final double RADIUS=.36, STEP=1.0/120;
    final PlanetTerrain terrain=new PlanetTerrain();
    double headingTurn;
    static final class Obstacle {
        final double x,z,r,height;
        Obstacle(double x,double z,double r,double height){this.x=x;this.z=z;this.r=r;this.height=height;}
    }
    final MountainPuzzle puzzle=new MountainPuzzle();
    final MountainCombat combat=new MountainCombat();
    final MountainJourney journey=new MountainJourney();
    final List<Obstacle> trees=new ArrayList<>(), rocks=new ArrayList<>();
    final Obstacle tent=new Obstacle(-7,12,2.2,2.6),rune=new Obstacle(12,-22,.9,2.3);
    final List<Obstacle> props=Arrays.asList(tent,rune);
    double x,z,y,vy,facing,time,distance;
    double stamina=100;boolean climbing,gliding;
    double gait,locomotion,runBlend,airBlend,landing,visualFacing;
    boolean grounded=true,paused,moving;
    MountainWorld(){
        for(int i=0;i<65;i++){
            double tx=-43+(i*17%86),tz=-40+(i*29%82);
            if((tx>16&&tx<28&&tz>-33&&tz<-15)||MountainTraversal.inside(tx,tz,5)||Math.hypot(tx+13,tz+20)<10||Math.hypot(tx,tz)<9||Math.hypot(tx,tz-12)<15||Math.abs(tx-pathX(tz))<4||Math.hypot(tx-15,tz+25)<8)continue;
            trees.add(new Obstacle(tx,tz,.65,5+i%4));
        }
        rocks.add(new Obstacle(-5,8,1.8,2.2));rocks.add(new Obstacle(7,-9,2,2.8));
        rocks.add(new Obstacle(29,-17,1.6,2.4));rocks.add(new Obstacle(-14,-13,2.2,3));
        reset();
    }
    void reset(){headingTurn=0;x=0;z=12;y=ground(x,z);vy=0;facing=Math.PI;grounded=true;time=distance=0;paused=moving=false;gait=locomotion=runBlend=airBlend=landing=0;visualFacing=facing;stamina=100;climbing=gliding=false;journey.reset();combat.reset();puzzle.reset();}
    static double pathX(double z){return MountainJourney.pathX(z);}
    static double rawHeight(double x,double z){return MountainPlanet.height(MountainPlanet.normal(x,z));}
    static double ground(double x,double z){return PlanetTerrain.ground(x,z);}
    boolean blocked(double px,double pz){return solidAt(px,pz,y);}
    boolean solidAt(double px,double pz,double feet){return puzzle.blocks(px,pz,feet,RADIUS)||staticSolidAt(px,pz,feet);}
    boolean staticSolidAt(double px,double pz,double feet){
        if(MountainTraversal.inside(px,pz,RADIUS)&&feet<MountainTraversal.TOP-.05)return true;
        for(Obstacle o:trees)if(Math.hypot(px-o.x,pz-o.z)<o.r+RADIUS&&feet<ground(o.x,o.z)+o.height)return true;
        for(Obstacle o:rocks)if(Math.hypot(px-o.x,pz-o.z)<o.r+RADIUS&&feet<ground(o.x,o.z)+o.height)return true;
        for(Obstacle o:props)if(Math.hypot(px-o.x,pz-o.z)<o.r+RADIUS&&feet<ground(o.x,o.z)+o.height)return true;
        for(PlanetTerrain.Prop p:terrain.nearby(px,pz))
            if(MountainPlanet.distance(px,pz,p.x,p.z)<p.r+RADIUS&&feet<ground(p.x,p.z)+p.height)return true;
        return false;
    }
    double floorAt(double px,double pz){return Math.max(MountainTraversal.floor(px,pz),puzzle.support(px,pz));}
    double floor(){return floorAt(x,z);}
    void interact(){
        if(paused||journey.summaryOpen||combat.health<=0)return;
        if(combat.busy())return;
        if(puzzle.interact(this))return;
        if(climbing){climbing=false;vy=0;return;}
        if(MountainTraversal.nearFace(this)&&stamina>5){climbing=true;gliding=false;grounded=false;vy=0;z=MountainTraversal.FRONT+RADIUS+.04;y=Math.max(y,ground(x,z)+.05);facing=Math.PI;return;}
        journey.interact(this);
    }
    void jump(){
        if(paused||journey.summaryOpen||combat.health<=0)return;
        if(combat.busy()||puzzle.pushing)return;
        if(climbing){climbing=false;vy=3;return;}
        if(grounded){vy=7.6;grounded=false;}
        else if(gliding)gliding=false;
        else if(stamina>5&&y-floor()>.7)gliding=true;
    }
    void step(double horizontal,double forward,boolean sprint,double yaw){
        if(paused||journey.summaryOpen||combat.health<=0)return;
        boolean wasGrounded=grounded;double impact=vy,oldDistance=distance;
        moving=false;time+=STEP;double length=Math.hypot(horizontal,forward);
        if(puzzle.pushing){puzzle.push(this,forward);}
        else if(climbing){
            stamina=Math.max(0,stamina-18*STEP);facing=Math.PI;
            x=MountainCamera.clamp(x+horizontal*1.3*STEP,MountainTraversal.LEFT+.5,MountainTraversal.RIGHT-.5);
            y+=Math.max(-1,Math.min(1,forward))*1.7*STEP;gait+=Math.abs(forward)*STEP*5;moving=length>0;
            if(y>=MountainTraversal.TOP){y=MountainTraversal.TOP;z=MountainTraversal.FRONT-.6;climbing=false;grounded=true;journey.summit=true;journey.tell(this,"登上风蚀岩台！跳起后再按空格，乘风飞向西北方。",7);}
            else if(y<=ground(x,z)){y=ground(x,z);climbing=false;grounded=true;}
            else if(stamina<=0){climbing=false;vy=0;}
        }else{
            if(gliding){stamina=Math.max(0,stamina-22*STEP);if(stamina<=0)gliding=false;}
            if(combat.dodgeTime>=0){
                double travel=Math.hypot(move(combat.dodgeX*10*STEP,0),move(0,combat.dodgeZ*10*STEP));
                distance+=travel;moving=travel>1e-7;
            }else if(length>0&&combat.attackTime<0){
                horizontal/=Math.max(1,length);forward/=Math.max(1,length);
                double speed=gliding?7:sprint?7:4.5;
                double dx=(horizontal*Math.cos(yaw)+forward*Math.sin(yaw))*speed*STEP;
                double dz=(horizontal*Math.sin(yaw)-forward*Math.cos(yaw))*speed*STEP;
                double oldTurn=headingTurn;
                double travel=Math.hypot(move(dx,0),move(0,dz));distance+=travel;
                if(travel>1e-7){facing=Math.atan2(dx,dz)+headingTurn-oldTurn;moving=true;}
            }
            double floor=floor();
            if(grounded&&y>floor+.15)grounded=false;
            if(grounded){y=floor;vy=0;}
            else{vy=gliding?Math.max(-2.5,vy-8*STEP):vy-20*STEP;y+=vy*STEP;if(y<=floor){y=floor;vy=0;grounded=true;gliding=false;}}
        }
        combat.tick(this);
        if(combat.health<=0)puzzle.pushing=false;
        puzzle.update(this);
        if(grounded&&combat.dodgeTime<0)stamina=Math.min(100,stamina+25*STEP);
        // Animation is driven by simulation time, never by render-frame frequency.
        double travel=distance-oldDistance;
        locomotion+=(moving?1-locomotion:-locomotion)*(1-Math.exp(-STEP*12));
        runBlend+=((moving&&sprint?1:0)-runBlend)*(1-Math.exp(-STEP*8));
        airBlend+=((grounded?0:1)-airBlend)*(1-Math.exp(-STEP*16));
        gait+=grounded?travel*(2.7-runBlend*.65):0;
        landing*=Math.exp(-STEP*11);
        if(!wasGrounded&&grounded)landing=Math.min(1,Math.abs(impact)/9);
        journey.update(this);
        double turn=Math.atan2(Math.sin(facing-visualFacing),Math.cos(facing-visualFacing));
        visualFacing+=turn*(1-Math.exp(-STEP*14));
    }
    /** Displacements are local tangent metres, never differences in longitude chart coordinates. */
    private double move(double east,double south){
        double run=Math.hypot(east,south);if(run<1e-14)return 0;
        MountainPlanet.Move next=MountainPlanet.advance(x,z,east,south);
        if(blocked(next.x,next.z))return 0;
        double rise=floorAt(next.x,next.z)-floor();
        if(grounded&&rise>run*Math.tan(Math.toRadians(48)))return 0;
        x=next.x;z=next.z;headingTurn+=next.turn;facing+=next.turn;visualFacing+=next.turn;
        double dx=combat.dodgeX,dz=combat.dodgeZ;
        combat.dodgeX=dx*Math.cos(next.turn)+dz*Math.sin(next.turn);
        combat.dodgeZ=dz*Math.cos(next.turn)-dx*Math.sin(next.turn);
        return run;
    }
}
