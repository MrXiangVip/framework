package com.packages.riftarena;
import java.util.*;

public final class ArenaRigTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static List<String> positions(ArenaAssets.Model model){
        List<String> out=new ArrayList<>();for(ArenaAssets.Part part:model.parts)for(int i=0;i<part.vertices.length;i+=8)out.add(key(part.vertices[i],part.vertices[i+1],part.vertices[i+2]));Collections.sort(out);return out;
    }
    static String key(double x,double y,double z){return Math.round(x*100)+","+Math.round(y*100)+","+Math.round(z*100);}
    static void legacySkin(ArenaRig.Mesh mesh,double[][] globals,float[] out){
        double[][] palette=new double[mesh.palette.length][];
        for(int b=0;b<palette.length;b++)palette[b]=ArenaRig.multiply(globals[mesh.palette[b].node],mesh.palette[b].offset);
        for(int vertex=0;vertex<mesh.bones.length;vertex++){
            int o=vertex*8;double px=0,py=0,pz=0,nx=0,ny=0,nz=0;float[] v=mesh.vertices;
            int influences=Math.max(1,mesh.bones[vertex].length);
            for(int j=0;j<influences;j++){
                double[] m=mesh.bones[vertex].length==0?globals[mesh.node]:palette[mesh.bones[vertex][j]];
                double w=mesh.bones[vertex].length==0?1:mesh.weights[vertex][j];
                px+=(m[0]*v[o]+m[1]*v[o+1]+m[2]*v[o+2]+m[3])*w;
                py+=(m[4]*v[o]+m[5]*v[o+1]+m[6]*v[o+2]+m[7])*w;
                pz+=(m[8]*v[o]+m[9]*v[o+1]+m[10]*v[o+2]+m[11])*w;
                // Inverse transpose for non-uniform scale; translation never affects normals.
                double a=m[5]*m[10]-m[6]*m[9],b=m[6]*m[8]-m[4]*m[10],c=m[4]*m[9]-m[5]*m[8];
                double det=m[0]*a+m[1]*b+m[2]*c;if(Math.abs(det)<1e-12)continue;
                nx+=(a*v[o+3]+b*v[o+4]+c*v[o+5])*w/det;
                ny+=((m[2]*m[9]-m[1]*m[10])*v[o+3]+(m[0]*m[10]-m[2]*m[8])*v[o+4]+(m[1]*m[8]-m[0]*m[9])*v[o+5])*w/det;
                nz+=((m[1]*m[6]-m[2]*m[5])*v[o+3]+(m[2]*m[4]-m[0]*m[6])*v[o+4]+(m[0]*m[5]-m[1]*m[4])*v[o+5])*w/det;
            }
            double length=Math.sqrt(nx*nx+ny*ny+nz*nz);if(length<1e-12)length=1;
            out[o]=(float)px;out[o+1]=(float)py;out[o+2]=(float)pz;out[o+3]=(float)(nx/length);out[o+4]=(float)(ny/length);out[o+5]=(float)(nz/length);out[o+6]=v[o+6];out[o+7]=v[o+7];
        }
    }
    static float[] skin(ArenaRig rig,String action,double time){
        double[][] pose=rig.bindPose();if(action!=null)rig.sample(action,time,true,pose);
        List<Float> all=new ArrayList<>();double[][] globals=rig.globals(pose);
        for(ArenaRig.Mesh mesh:rig.meshes){float[] v=new float[mesh.vertices.length];rig.skin(mesh,globals,v);float[] reference=new float[v.length];legacySkin(mesh,globals,reference);for(int i=0;i<v.length;i++)check(Math.abs(v[i]-reference[i])<.00001,"Optimized skinning matches scalar reference");for(float f:v){check(Float.isFinite(f),"Finite skinned geometry");all.add(f);}for(int i=0;i<v.length;i+=8){double length=Math.sqrt(v[i+3]*v[i+3]+v[i+4]*v[i+4]+v[i+5]*v[i+5]);check(Math.abs(length-1)<.001,"Unit skinned normal");check(v[i+6]==mesh.vertices[i+6]&&v[i+7]==mesh.vertices[i+7],"UVs preserved");}}
        float[] result=new float[all.size()];for(int i=0;i<result.length;i++)result[i]=all.get(i);return result;
    }
    public static void main(String[] args)throws Exception{
        ArenaAssets assets=new ArenaAssets(ArenaRigTest.class.getClassLoader());
        for(String name:new String[]{"warrior","archer","mage","soldier"}){
            ArenaRig rig=ArenaRig.load(assets,name);check(rig.boneCount>=17&&rig.clips.size()==(name.equals("warrior")?9:name.equals("archer")?6:5),"Complete skeleton and clips: "+name);
            double displayHeight=name.equals("soldier")?2.3:4;
            double stride=rig.stride("run",displayHeight);check(stride>displayHeight*.8&&stride<displayHeight*1.5,"Run stride scales to actual character height");
            double[][] planted=rig.bindPose();double duration=rig.clips.get("run").duration;
            rig.sample("run",duration*.21,false,planted);double footBefore=rig.globals(planted)[rig.nodeIds.get("Bip001 L Foot")][11];
            rig.sample("run",duration*.29,false,planted);double footAfter=rig.globals(planted)[rig.nodeIds.get("Bip001 L Foot")][11];
            double groundSlip=stride*.08+(footAfter-footBefore)*displayHeight/rig.height();
            check(Math.abs(groundSlip)<displayHeight*.035,"Planted foot stays near its ground contact while the body advances: "+name);
            List<String> actual=new ArrayList<>();double[][] bind=rig.globals(rig.bindPose());
            for(ArenaRig.Mesh mesh:rig.meshes){float[] out=new float[mesh.vertices.length];rig.skin(mesh,bind,out);for(int index:mesh.indices)actual.add(key(out[index*8],out[index*8+1],out[index*8+2]));for(float[] weights:mesh.weights){double sum=0;for(float w:weights)sum+=w;check(weights.length==0||Math.abs(sum-1)<.0001,"Normalized skin weights");}}
            Collections.sort(actual);List<String> expected=positions(assets.load(name));
            check(actual.size()==expected.size()+(name.equals("warrior")?(34-102)*3:0),"Bind triangle count accounts for axe-to-sword replacement");
            ArenaAssets.Model staticModel=assets.load(name);
            check(Math.abs(rig.height()-staticModel.height())<rig.height()*1e-4,"Bind height matches static model");
            for(ArenaRig.Mesh mesh:rig.meshes){
                if(mesh==rig.swordMesh)continue;
                float[] out=new float[mesh.vertices.length];rig.skin(mesh,bind,out);
                for(int vertex=0;vertex<out.length;vertex+=8){
                    double best=Double.POSITIVE_INFINITY;
                    for(ArenaAssets.Part part:staticModel.parts)for(int i=0;i<part.vertices.length;i+=8){double dx=out[vertex]-part.vertices[i],dy=out[vertex+1]-part.vertices[i+1],dz=out[vertex+2]-part.vertices[i+2];best=Math.min(best,dx*dx+dy*dy+dz*dz);}
                    check(best<rig.height()*rig.height()*1e-8,"Skinned bind vertex matches static geometry: "+name);
                }
            }
            if(name.equals("warrior")){
                check(rig.removedWeaponTriangles==102&&rig.swordMesh.indices.length==34*3,"Only original axe removed and source sword attached");
                int hand=rig.nodeIds.get("Bip001 R Hand");
                for(String action:rig.clips.keySet()){
                    double[][] pose=rig.bindPose();rig.sample(action,rig.clips.get(action).duration*.3,false,pose);double[][] globals=rig.globals(pose);
                    float[] sword=new float[rig.swordMesh.vertices.length];rig.skin(rig.swordMesh,globals,sword);
                    double[] matrix=globals[hand];
                    for(int i=0;i<sword.length;i+=8){
                        float[] local=rig.swordMesh.vertices;
                        for(int axis=0;axis<3;axis++)check(Math.abs(sword[i+axis]-(matrix[axis*4]*local[i]+matrix[axis*4+1]*local[i+1]+matrix[axis*4+2]*local[i+2]+matrix[axis*4+3]))<.0001,"Sword remains attached to hand in "+action);
                    }
                }
                check(rig.weaponPath2!=null&&rig.weaponPath2.length==121&&rig.weaponPath!=null&&rig.weaponPath.length==121,"Weapon trail samples actual skeleton");
                double[] raised=new double[6],contact=new double[6];rig.weaponPoint(.28,raised);rig.weaponPoint(.3,contact);
                check(raised[4]-contact[4]>rig.height()*.5,"Overhead weapon descends toward calibrated contact phase");
                double[] second=new double[6];rig.weaponPoint(.32,second,true);
                check(Math.abs(second[3]-contact[3])>rig.height()*.3,"Second attack uses a distinct diagonal weapon path");
                for(double[] sample:rig.weaponPath)for(double value:sample)check(Double.isFinite(value)&&Math.abs(value)<rig.height()*5,"Finite bounded weapon path");
            }
            float[] rest=skin(rig,null,0);
            for(String action:rig.clips.keySet()){
                float[] a=skin(rig,action,rig.clips.get(action).duration*.22),b=skin(rig,action,rig.clips.get(action).duration*.61);
                double changed=0,extent=0;
                for(int i=0;i<a.length;i+=8)for(int j=0;j<3;j++){changed+=Math.abs(a[i+j]-b[i+j]);extent=Math.max(extent,Math.abs(a[i+j]-rest[i+j]));}
                check(changed/rig.height()>.01,"Animated joints must deform: "+name+" "+action);
                check(extent/rig.height()<3,"Animation cannot explode mesh: "+name+" "+action);
            }
            ArenaAnimator animator=new ArenaAnimator(rig);ArenaWorld w=new ArenaWorld();w.reset(0);ArenaWorld.Unit u=w.player;
            animator.update(u,0);u.travel=1;u.lastMovedAt=.2;animator.update(u,.2);check(animator.action.equals("run"),"Run state");
            u.lastMovedAt=.24;u.travel=1.1;animator.update(u,.24);float[] firstStride=animator.vertices.get(0).clone();
            u.travel=1.2;animator.update(u,.24);double strideChange=0;
            for(int i=0;i<firstStride.length;i+=8)for(int axis=0;axis<3;axis++)strideChange+=Math.abs(firstStride[i+axis]-animator.vertices.get(0)[i+axis]);
            check(strideChange/rig.height()>.001,"Interpolated walking pose advances between fixed simulation ticks");
            u.attackTime=.3;u.attackSerial++;animator.update(u,.3);check(animator.action.equals("attack"),"Attack overrides run");
            u.hp=0;animator.update(u,.4);check(animator.action.equals("death"),"Death interrupts attack");
            u.hp=u.maxHp;u.attackTime=0;u.hitFlash=0;animator.update(u,.6);check(animator.action.equals("idle"),"Respawn leaves death pose");
            u.hurtTime=ArenaWorld.HURT_SECONDS;u.hitSerial++;animator.update(u,.7);check(animator.action.equals("hurt"),"Dedicated hurt state");
            float[] held=animator.vertices.get(0).clone();u.hitPauseTotal=.05;animator.update(u,.75);
            float[] frozen=animator.vertices.get(0);for(int i=0;i<held.length;i++)check(Math.abs(held[i]-frozen[i])<.0001,"Hit stop holds skinned pose");
            u.hp=0;u.deathAt=.75;animator.update(u,.8);check(animator.action.equals("death"),"Death overrides hurt immediately");
            if(name.equals("archer")){
                w.configure(u,1);u.hurtTime=u.hitFlash=u.attackTime=0;u.dashTime=.2;u.dashSerial++;u.travel=.6;
                animator.update(u,1);check(animator.action.equals("charge"),"Archer retreat uses imported skinned clip");
                u.travel=2;u.dashTime=.1;animator.update(u,1.1);check(animator.action.equals("charge"),"Reverse locomotion remains stable");
                u.dashTime=0;u.attackTime=.4;u.attackSerial++;animator.update(u,1.2);check(animator.action.equals("attack"),"Retreat transitions to draw");
            }
            if(name.equals("warrior")){
                u.hp=u.maxHp;u.hurtTime=u.attackTime=u.hitFlash=0;u.slamTime=ArenaWorld.SLAM_SECONDS;u.slamSerial++;animator.update(u,1);
                check(animator.action.equals("slam"),"Slam overrides locomotion");
                u.slamTime=ArenaWorld.SLAM_SECONDS-ArenaWorld.SLAM_WINDUP;animator.update(u,1.42);
                float[] windup=animator.vertices.get(0).clone();u.slamTime=ArenaWorld.SLAM_SECONDS-ArenaWorld.SLAM_CONTACT;animator.update(u,1.52);
                double changed=0;float[] landed=animator.vertices.get(0);for(int i=0;i<landed.length;i+=8)for(int j=0;j<3;j++)changed+=Math.abs(windup[i+j]-landed[i+j]);
                check(changed/rig.height()>.01,"Slam retiming deforms real weapon and body");
                u.slamTime=0;u.dashTime=ArenaWorld.DASH_SECONDS;u.dashSerial++;u.travel=4;animator.update(u,1.55);
                check(animator.action.equals("charge"),"Dedicated charge clip overrides recovery");
                u.dashTime=.03;animator.update(u,1.58);check(animator.action.equals("charge"),"Brake blends within charge state");u.dashTime=0;
                u.slamTime=0;u.lastMovedAt=1.6;animator.update(u,1.6);check(animator.action.equals("run"),"Movement exits slam recovery");
                u.slamTime=.8;u.slamSerial++;animator.update(u,1.7);u.hp=0;u.deathAt=1.7;animator.update(u,1.8);check(animator.action.equals("death"),"Death overrides slam");
            }
            if(name.equals("warrior")){
                u.hp=u.maxHp;u.hitFlash=u.hurtTime=u.attackTime=u.slamTime=u.dashTime=0;u.combatTime=2;
                u.lastMovedAt=-100;u.facing=170;animator.update(u,3);
                check(animator.action.equals("combat-idle"),"Recovery returns to combat idle");
                u.facing=-170;u.lastMovedAt=3.005;u.travel+=.04;animator.update(u,3.005);
                check(animator.action.equals("run"),"Full-speed combat movement uses run instead of a sped-up shuffle");
                check(animator.renderFacing==-170,"Player walking turns immediately across wrap");
                u.hitFlash=.15;u.lastMovedAt=3.015;animator.update(u,3.015);check(animator.action.equals("run"),"Hit flash does not override player locomotion");u.hitFlash=0;
                u.lastMovedAt=3.025;animator.update(u,3.025);
                check(Math.abs(animator.renderFacing+170)<.001,"Visual turn settles without delaying movement");
                u.speed=3;u.lastMovedAt=3.026;u.travel+=.003;animator.update(u,3.026);check(animator.action.equals("combat-walk"),"Slow combat movement retains guarded walk");u.speed=8;
                u.attackTime=.4;u.attackSerial++;u.attackFacing=40;animator.update(u,3.03);
                check(animator.action.equals("attack")&&animator.renderFacing==40,"Attack immediately locks exact hit direction");
                u.attackTime=0;u.lastMovedAt=-100;animator.update(u,3.04);
                check(animator.action.equals("combat-idle"),"Attack exits into guard stance");
                u.meteorTime=ArenaWorld.METEOR_CAST;u.meteorSerial++;u.meteorFacing=70;animator.update(u,3.05);
                check(animator.action.equals("meteor")&&animator.renderFacing==70,"Meteor raise overrides idle and locks aim");
                u.meteorTime=.1;animator.update(u,3.15);check(animator.action.equals("meteor"),"Meteor recovery samples real sword animation");u.meteorTime=0;
                u.combatTime=0;animator.update(u,3.2);check(animator.action.equals("idle"),"Combat timeout restores ordinary idle");
                u.lastMovedAt=3.21;animator.update(u,3.21);check(animator.action.equals("run"),"Out of combat uses ordinary run");
            }
            System.out.println("PASS: "+name+" "+rig.boneCount+" bones, bind-pose parity, five deforming clips, weights/normals/UVs, state transitions");
        }
    }
}
