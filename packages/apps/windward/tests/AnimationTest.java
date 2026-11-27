package com.packages.windward;

/** Exercise actual skeleton deformation and fixed-step land/water animation transitions. */
public final class AnimationTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static double[][] pose(TravellerRig rig,double phase,double move,double run,double swim){
        return rig.pose(phase,move,run,0,0,phase,0,false,false,-1,-1,false,swim,phase);
    }
    static double distance(double[] a,double[] b){return Math.sqrt(Math.pow(a[3]-b[3],2)+Math.pow(a[7]-b[7],2)+Math.pow(a[11]-b[11],2));}
    static void skeleton()throws Exception{
        TravellerRig rig=TravellerRig.load(new MountainAssets(AnimationTest.class.getClassLoader()));
        int head=rig.required("head"),pelvis=rig.required("pelvis"),hand=rig.required("hand_r"),foot=rig.required("foot_l");
        double[][] walk=pose(rig,Math.PI/2,1,0,0),run=pose(rig,Math.PI/2,1,1,0);
        check(distance(walk[foot],run[foot])>rig.height()*.025,"Run uses a distinct leg recovery, not only faster walking");
        double moved=0;
        for(int i=0;i<16;i++){
            double phase=i*Math.PI/8;
            double[][] swim=pose(rig,phase,1,0,1),next=pose(rig,phase+.5,1,0,1),tread=pose(rig,phase,0,0,1);
            check(swim[head][11]-swim[pelvis][11]>rig.height()*.18,"Swimming torso points forward along the surface");
            check(swim[head][7]-rig.minY>rig.height()*.51,"Swimming head remains above water at buoyant support");
            check(tread[head][7]-tread[pelvis][7]>rig.height()*.25,"Idle treading keeps head upright");
            check(distance(swim[hand],next[hand])>rig.height()*.015,"Swim arms actually stroke over time");
            for(TravellerRig.Mesh mesh:rig.meshes){
                float[] a=new float[mesh.vertices.length],b=a.clone();rig.skin(mesh,swim,a);rig.skin(mesh,next,b);
                for(int j=0;j<a.length;j++){check(Float.isFinite(a[j])&&Float.isFinite(b[j]),"Finite swimming skin and normals");if(j%8<3)moved+=Math.abs(a[j]-b[j]);}
            }
        }
        check(moved>rig.height(),"Stroke and kick deform the real skinned mesh");
        for(double blend:new double[]{0,.1,.5,.9,1}){
            double[][] mixed=pose(rig,.7,.8,.3,blend);
            for(double[] matrix:mixed)for(double v:matrix)check(Double.isFinite(v),"Water transition stays finite");
        }
    }
    static void transitions(){
        MountainWorld w=new MountainWorld();
        for(int i=0;i<90;i++)w.step(0,1,true,0);
        check(w.runBlend>.99&&w.swimBlend==0,"Shift on land smoothly enters running");
        for(int i=0;i<120;i++)w.step(0,0,false,0);
        check(w.runBlend<.001&&w.locomotion<.001,"Stopping returns to idle");
        w.x=175;w.z=-130;w.y=w.floor();
        w.combat.attackTime=.1;w.combat.dodgeTime=.1;
        for(int i=0;i<120;i++)w.step(0,0,false,0);
        check(w.swimBlend>.999&&w.locomotion<.001&&w.swimPhase>0,"Deep water idles in animated treading pose");
        check(w.combat.attackTime<0&&w.combat.dodgeTime<0,"Entering water cancels incompatible combat actions");
        w.combat.attack(w);w.combat.dodge(w,0,1,0);
        check(!w.combat.busy(),"Swimming cannot swing a sword or land-dodge");
        double phase=w.swimPhase;
        for(int i=0;i<90;i++)w.step(0,1,true,0);
        check(w.swimming()&&w.locomotion>.99&&w.runBlend<.001&&w.swimPhase>phase+3,"Moving in water uses fast strokes, not running");
        w.paused=true;double frozen=w.swimPhase,blend=w.swimBlend;
        for(int i=0;i<30;i++)w.step(0,1,true,0);
        check(w.swimPhase==frozen&&w.swimBlend==blend,"Pause freezes swimming motion and transitions");
        w.paused=false;w.jump();for(int i=0;i<35;i++)w.step(0,0,false,0);
        check(!w.swimming()&&w.swimBlend<.4,"Jumping out of water blends to airborne pose");
        for(int i=0;i<180;i++)w.step(0,0,false,0);
        check(w.swimming()&&w.swimBlend>.99,"Landing back in lake resumes treading");
        w.x=120;w.z=-130;w.y=w.floor();boolean entered=false,exited=false;
        for(int i=0;i<6000;i++){
            w.step(1,0,false,0);entered|=w.swimming();
            if(entered&&!w.swimming()&&MountainWorld.ground(w.x,w.z)>MountainPlanet.WATER+.5){exited=true;break;}
        }
        for(int i=0;i<120;i++)w.step(0,0,false,0);
        check(exited&&w.swimBlend<.001,"Leaving the shore restores land animation");
        w.reset();check(w.swimBlend==0&&w.swimPhase==0&&w.runBlend==0,"Restart clears animation state");
    }
    public static void main(String[] args)throws Exception{
        transitions();skeleton();System.out.println("PASS: distinct running, swim strokes/kicks, treading, smooth water transitions, pause/reset and weapon exclusion");
    }
}
