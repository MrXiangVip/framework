package com.packages.riftarena;

/** Sword E is a cancellable animation event, not an immediate radial damage button. */
public final class ArenaSlamTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static ArenaWorld fresh(){ArenaWorld w=ArenaSwordTest.fresh();ArenaSwordTest.dummy(w).range=0;w.aimX=3;w.aimZ=0;return w;}
    static void advance(ArenaWorld w,int ticks){ArenaSwordTest.advance(w,ticks);}
    public static void main(String[] args){
        ArenaWorld w=fresh();ArenaWorld.Unit enemy=ArenaSwordTest.dummy(w);double hp=enemy.hp;
        ArenaWorld.Unit ally=w.spawn(ArenaWorld.Kind.SOLDIER,0,2,0);ally.speed=ally.range=ally.attack=0;double ahp=ally.hp;
        ArenaWorld.Unit far=w.spawn(ArenaWorld.Kind.SOLDIER,1,7,0);far.speed=far.range=far.attack=0;
        check(w.cast(1)&&w.player.slamTime==ArenaWorld.SLAM_SECONDS&&w.cooldowns[1]==9,"E starts retimed slam and cooldown");
        check(enemy.hp==hp&&w.soundEvents.contains("slam-charge")&&!w.soundEvents.contains("slam-impact"),"Charge has no early damage or impact sound");
        check(Math.abs(ArenaWorld.visualFacing(w.player)-90)<.001,"Cast faces aimed direction");
        advance(w,(int)(ArenaWorld.SLAM_CONTACT*100)-2);check(enemy.hp==hp&&!w.player.slamLanded,"Windup cannot damage");
        advance(w,4);check(enemy.hp==hp-150&&w.player.slamLanded&&w.soundEvents.contains("slam-impact"),"Strike deals damage and impact sound together");
        check(ally.hp==ahp&&far.hp==far.maxHp,"Allies and out-of-radius enemies excluded");
        check(w.effects.stream().filter(e->e.type==3).count()==1,"One ground shockwave per cast");
        double remaining=w.player.slamTime;w.update(.02,0,0,false);check(Math.abs(w.player.slamTime-remaining)<1e-9,"Impact stop holds slam pose");
        advance(w,70);check(enemy.hp==hp-150&&w.player.slamTime==0,"Recovery expires and damage cannot repeat");
        check(!w.cast(1),"Skill cooldown enforced");
        w=fresh();enemy=ArenaSwordTest.dummy(w);hp=enemy.hp;w.cast(1);advance(w,20);double x=w.player.x;w.update(.01,-1,0,false);advance(w,80);
        check(w.player.x<x&&w.player.slamTime==0&&enemy.hp==hp&&!w.soundEvents.contains("slam-impact"),"Movement cancels windup without phantom impact");
        check(w.cooldowns[1]>7,"Cancellation keeps cooldown");
        w=fresh();enemy=ArenaSwordTest.dummy(w);hp=enemy.hp;w.cast(1);advance(w,20);w.damage(w.player,10000,1);advance(w,80);
        check(enemy.hp==hp&&!w.soundEvents.contains("slam-impact"),"Dead caster cannot land a pending slam");
        w=fresh();enemy=ArenaSwordTest.dummy(w);hp=enemy.hp;w.cast(1);advance(w,20);w.pause();double time=w.player.slamTime;w.update(.2,0,0,false);
        check(w.player.slamTime==time&&enemy.hp==hp,"Pause freezes charge");w.resume();advance(w,35);check(enemy.hp==hp-150,"Resume strikes once");
        w=fresh();enemy=ArenaSwordTest.dummy(w);hp=enemy.hp;w.requestAttack();w.update(.01,0,0,false);w.cast(1);advance(w,56);
        check(enemy.hp==hp-150&&w.swordStrikes.isEmpty()&&w.shots.isEmpty(),"E cleanly replaces a basic attack without double damage");
        w=fresh();w.cast(1);for(int i=0;i<30;i++)w.update(.01,0,0,true);
        check(w.shots.isEmpty()&&w.player.attackTime==0,"Held basic attack cannot overwrite charge");
        w=fresh();enemy=ArenaSwordTest.dummy(w);hp=enemy.hp;w.cast(1);advance(w,15);w.cast(2);advance(w,45);
        check(w.player.slamTime==0&&enemy.hp==hp&&!w.soundEvents.contains("slam-impact"),"Another skill replaces pending charge");
        w=fresh();w.cast(1);advance(w,20);w.recall();advance(w,40);check(!w.soundEvents.contains("slam-impact"),"Recall cancels charge");
        w=fresh();enemy=ArenaSwordTest.dummy(w);w.cast(1);enemy.x=8;advance(w,55);check(enemy.hp==enemy.maxHp,"Range checked at impact, not start");
        w=fresh();w.cast(1);w.reset(0);advance(w,60);check(w.player.slamTime==0&&w.effects.isEmpty()&&!w.soundEvents.contains("slam-impact"),"Restart clears charge");
        for(int i=0;i<100;i++){
            double a=ArenaWorld.slamPosePhase(i*ArenaWorld.SLAM_SECONDS/100),b=ArenaWorld.slamPosePhase((i+1)*ArenaWorld.SLAM_SECONDS/100);
            check(a>=0&&a<=b&&b<=1.000001,"Retimed skeleton is continuous and monotonic");
        }
        check(Math.abs(ArenaWorld.slamPosePhase(ArenaWorld.SLAM_CONTACT)-.30)<1e-9,"Impact matches calibrated weapon contact");
        w=fresh();w.configure(w.player,2);enemy=ArenaSwordTest.dummy(w);check(w.cast(1)&&enemy.hp<enemy.maxHp&&w.player.slamTime==0,"Mage retains its current skill behavior");
        System.out.println("PASS: retimed sword slam, delayed area impact, aim/radius/team filtering, cooldown, cancellation, death, pause, attack transition and restart");
    }
}
