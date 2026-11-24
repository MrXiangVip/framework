package com.packages.windward;

/** Exercise the authored route through the same fixed-step controller used by input. */
public final class TraversalTest {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void steps(MountainWorld w,int count,double x,double forward){for(int i=0;i<count;i++)w.step(x,forward,false,0);}
    static MountainWorld atFace(){MountainWorld w=new MountainWorld();w.x=-7;w.z=MountainTraversal.FRONT+MountainWorld.RADIUS+.04;w.y=w.floor();return w;}
    public static void main(String[] args){
        MountainWorld w=new MountainWorld();w.interact();check(!w.climbing,"Cannot climb arbitrary terrain");
        w.x=1;w.z=-2;w.y=w.floor();
        for(int i=0;i<900&&Math.hypot(w.x+7,w.z+10.5)>.2;i++){double dx=-7-w.x,dz=-10.5-w.z,len=Math.hypot(dx,dz);w.step(dx/len,-dz/len,false,0);}
        check(Math.hypot(w.x+7,w.z+10.5)<.2,"Camp branch reaches climb face");
        w.interact();check(w.climbing&&!w.grounded,"Attach at pale face");
        double y=w.y;w.paused=true;steps(w,120,0,1);check(w.y==y&&w.stamina==100,"Pause freezes climb and stamina");w.paused=false;
        steps(w,100,1,1);check(w.x<MountainTraversal.RIGHT&&w.y>y+1,"Climb and traverse face");
        for(int i=0;i<800&&w.climbing;i++)w.step(0,1,false,0);
        check(w.grounded&&!w.climbing&&w.journey.summit&&Math.abs(w.y-MountainTraversal.TOP)<1e-8,"Mantle onto supported top");
        steps(w,120,0,0);check(w.grounded&&Math.abs(w.y-MountainTraversal.TOP)<1e-8,"Standing on ledge does not sink");
        w.jump();steps(w,20,0,0);w.jump();check(w.gliding,"Airborne second press opens wing");
        boolean flew=false;
        for(int i=0;i<1200;i++){
            double dx=MountainJourney.CACHE_X-w.x,dz=MountainJourney.CACHE_Z-w.z,len=Math.hypot(dx,dz);
            if(len>1)w.step(dx/len,-dz/len,false,0);else w.step(0,0,false,0);
            flew|=w.gliding&&!w.grounded&&w.x<MountainTraversal.LEFT-1;
            if(w.grounded&&len<1.5)break;
        }
        check(flew&&w.grounded&&!w.gliding,"Fly off ledge and land near cache");
        check(w.journey.nearCache(w),"Climb/glide route reaches hidden reward");w.interact();check(w.journey.treasure,"Open hidden relic cache");
        double expiry=w.journey.noticeUntil;steps(w,30,0,0);w.interact();check(w.journey.noticeUntil==expiry,"Reward collected once");
        w.reset();check(w.stamina==100&&!w.climbing&&!w.gliding&&!w.journey.treasure&&!w.journey.summit,"Restart clears traversal and reward");
        w=atFace();w.interact();steps(w,100,0,1);w.interact();check(!w.climbing&&!w.grounded,"Release wall falls");steps(w,200,0,0);check(w.grounded,"Release lands safely");
        w=atFace();w.interact();steps(w,90,0,1);w.stamina=.01;w.step(0,0,false,0);check(!w.climbing&&w.stamina==0,"Climb exhaustion releases grip");steps(w,240,0,0);check(w.grounded&&w.stamina>0,"Ground recharges stamina");
        w=atFace();steps(w,180,0,1);check(w.z>MountainTraversal.FRONT,"Cannot walk through ledge wall");
        w.x=-7;w.z=-14;w.y=MountainTraversal.TOP;w.grounded=true;steps(w,100,1,0);check(!w.grounded&&w.y<MountainTraversal.TOP,"Walking off top starts falling");
        w=new MountainWorld();w.y+=10;w.grounded=false;w.vy=-12;w.jump();steps(w,30,0,0);check(w.gliding&&w.vy>=-2.5,"Glide limits descent speed");
        w.paused=true;double stamina=w.stamina;w.jump();steps(w,30,0,0);check(w.gliding&&w.stamina==stamina,"Pause freezes wing and stamina");w.paused=false;
        w.stamina=.01;w.step(0,0,false,0);check(!w.gliding&&w.vy< -2.5,"Glide exhaustion restores gravity");
        w.stamina=100;w.jump();check(w.gliding,"Wing can reopen with stamina");w.jump();check(!w.gliding,"Press closes wing");
        w=atFace();w.y+=2;MountainCamera camera=new MountainCamera();camera.yaw=Math.PI;camera.update(w);check(camera.eyeZ>MountainTraversal.FRONT,"Camera retracts before climb wall");
        System.out.println("PASS: reachable climb, mantle/support, release/exhaustion, glide/retract/landing, cache, pause/reset and ledge camera");
    }
}
