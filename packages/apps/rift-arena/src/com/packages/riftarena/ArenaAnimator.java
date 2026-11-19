package com.packages.riftarena;

import java.nio.FloatBuffer;
import java.util.*;
import org.lwjgl.BufferUtils;

/** Per-unit animation state and reusable CPU skinning buffers. */
final class ArenaAnimator {
    final ArenaRig rig;
    final double[][] pose,previous,brake;
    final List<float[]> vertices=new ArrayList<>();
    final List<FloatBuffer> buffers=new ArrayList<>();
    final double[][] globals;
    final double[] local=new double[16];
    final List<ArenaRig.Skinning> skinning=new ArrayList<>();
    String action="";
    double renderFacing, facingTime=Double.NaN;
    double gaitCycles;
    double started,blendStarted,lastTime=Double.NaN,lastTravel,lastAttack=-1,lastHit=-1,lastSlam=-1,lastDash=-1,lastMeteor=-1;
    ArenaAnimator(ArenaRig rig){
        this.rig=rig;globals=new double[rig.nodes.size()][16];pose=rig.bindPose();previous=rig.bindPose();brake=rig.bindPose();
        for(ArenaRig.Mesh mesh:rig.meshes){skinning.add(new ArenaRig.Skinning(mesh));vertices.add(new float[mesh.vertices.length]);buffers.add(BufferUtils.createFloatBuffer(mesh.vertices.length));}
    }
    void update(ArenaWorld.Unit unit,double now){
        boolean moved=now-unit.lastMovedAt>=0&&now-unit.lastMovedAt<(unit.player?.000001:.08);
        double targetFacing=ArenaWorld.visualFacing(unit);
        // Visual turn only: simulation movement and attack direction remain immediate.
        double turnDt=Double.isNaN(facingTime)?0:now-facingTime;
        if(Double.isNaN(facingTime)||turnDt<0||turnDt>.2||!unit.alive()||(unit.player&&moved)||unit.attackTime>0||unit.slamTime>0||unit.dashTime>0||unit.meteorTime>0)renderFacing=targetFacing;
        else {double delta=((targetFacing-renderFacing)%360+540)%360-180;renderFacing+=ArenaWorld.clamp(delta,-1800*turnDt,1800*turnDt);}
        facingTime=now;
        double wallNow=now;now-=unit.hitPauseTotal;
        if(now==lastTime&&unit.travel==lastTravel&&unit.hitSerial==lastHit&&unit.attackSerial==lastAttack&&unit.slamSerial==lastSlam&&unit.dashSerial==lastDash&&unit.meteorSerial==lastMeteor&&unit.alive())return;
        boolean combat=unit.combatTime>0&&rig.clips.containsKey("combat-idle");
        // A guarded shuffle is suitable for slow movement, not an eight-unit/second run.
        String next=!unit.alive()?"death":unit.dashTime>0?"charge":unit.meteorTime>0?"meteor":unit.slamTime>0?"slam":unit.attackTime>0?"attack":(unit.hurtTime>0||unit.hitFlash>0)&&!(unit.player&&moved)?"hurt":moved?(combat&&unit.speed<=4?"combat-walk":"run"):(combat?"combat-idle":"idle");
        boolean restart=(next.equals("meteor")&&unit.meteorSerial!=lastMeteor)||(next.equals("charge")&&unit.dashSerial!=lastDash)||(next.equals("slam")&&unit.slamSerial!=lastSlam)||(next.equals("attack")&&unit.attackSerial!=lastAttack)||(next.equals("hurt")&&unit.hitSerial!=lastHit);
        if(!next.equals(action)||restart||now<lastTime){
            for(int i=0;i<pose.length;i++)System.arraycopy(pose[i],0,previous[i],0,10);
            started=blendStarted=now;action=next;
        }
        double clipTime;
        String sourceAction=action.equals("charge")&&ArenaWorld.archer(unit)?"retreat":(action.equals("slam")||action.equals("meteor"))?"attack":action.equals("attack")&&unit.comboStep==1&&rig.clips.containsKey("attack2")?"attack2":action;
        ArenaRig.Clip clip=rig.clips.get(sourceAction);
        if(action.equals("charge"))clipTime=(ArenaWorld.archer(unit)?1-(unit.travel/4%1):unit.travel/4)*clip.duration;
        else if(action.equals("meteor"))clipTime=ArenaWorld.meteorPosePhase(unit)*clip.duration;
        else if(action.equals("slam"))clipTime=ArenaWorld.slamPosePhase(ArenaWorld.slamAge(unit))*clip.duration;
        else if(action.equals("attack"))clipTime=(1-unit.attackTime/ArenaWorld.attackDuration(unit))*clip.duration;
        else if(action.equals("run")||action.equals("combat-walk")){
            gaitCycles=(gaitCycles+Math.max(0,unit.travel-lastTravel)/rig.stride(action,unit.kind==ArenaWorld.Kind.HERO?4:2.3))%1;
            clipTime=gaitCycles*clip.duration;
        }
        else if(action.equals("hurt"))clipTime=(unit.hurtTime>0?(.12+.88*(1-unit.hurtTime/ArenaWorld.HURT_SECONDS)):(1-unit.hitFlash/.18))*clip.duration;
        else if(action.equals("death")&&unit.deathAt>=0)clipTime=wallNow-unit.deathAt;
        else clipTime=now-started;
        rig.sample(sourceAction,clipTime,action.equals("run")||action.equals("idle")||action.equals("combat-idle")||action.equals("combat-walk")||action.equals("charge"),pose);
        if(action.equals("charge")&&ArenaWorld.swordsman(unit)&&unit.dashTime<ArenaWorld.DASH_SECONDS-ArenaWorld.DASH_MOVE){
            rig.sample(combat?"combat-idle":"idle",0,false,brake);ArenaRig.blend(pose,brake,1-unit.dashTime/(ArenaWorld.DASH_SECONDS-ArenaWorld.DASH_MOVE));
            for(int i=0;i<pose.length;i++)System.arraycopy(brake[i],0,pose[i],0,10);
        }
        double blend=action.equals("charge")?.02:action.equals("run")||action.equals("combat-walk")||action.equals("attack")?.03:action.equals("hurt")?.025:action.equals("slam")?.04:action.equals("combat-idle")||action.equals("idle")?.10:.065;
        if(now-blendStarted<blend)ArenaRig.blend(previous,pose,ArenaWorld.clamp((now-blendStarted)/blend,0,1));
        rig.globals(pose,globals,local);
        for(int i=0;i<rig.meshes.size();i++){
            float[] out=vertices.get(i);rig.skin(rig.meshes.get(i),globals,out,skinning.get(i));
            FloatBuffer buffer=buffers.get(i);buffer.clear();buffer.put(out).flip();
        }
        lastTime=now;lastTravel=unit.travel;lastAttack=unit.attackSerial;lastHit=unit.hitSerial;lastSlam=unit.slamSerial;lastDash=unit.dashSerial;lastMeteor=unit.meteorSerial;
    }
}
