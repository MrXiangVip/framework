package com.packages.riftarena;

/** Input intent must win over cosmetic recovery and a tap near cooldown end must not disappear. */
public final class ArenaResponseTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        ArenaWorld w=ArenaSwordTest.fresh();ArenaWorld.Unit target=ArenaSwordTest.dummy(w);target.range=0;
        w.requestAttack();w.update(.01,0,0,false);ArenaSwordTest.advance(w,22);
        double hp=target.hp,x=w.player.x;check(w.player.attackTime>0,"Attack is recovering");
        w.update(.01,-1,0,false);
        check(w.player.x<x&&w.player.attackTime==0&&ArenaWorld.visualFacing(w.player)==w.player.facing,"Movement and facing respond in first simulation step");
        check(target.hp==hp&&w.swordStrikes.isEmpty(),"Exiting recovery cannot repeat damage");
        w.damage(w.player,1,1,target);w.update(.01,1,0,false);
        check(w.player.hitStop==0&&w.player.hurtTime==0,"Moving player is not held in hurt animation");
        w=ArenaSwordTest.fresh();w.player.cooldown=.08;w.requestAttack();w.update(.01,0,0,false);
        check(w.shots.isEmpty()&&w.attackBuffer>0,"Short cooldown buffers an early tap");
        ArenaSwordTest.advance(w,10);check(w.shots.size()==1&&w.attackBuffer==0,"Buffered tap executes when cooldown ends");
        w=ArenaSwordTest.fresh();w.player.cooldown=.5;w.requestAttack();w.update(.01,0,0,false);ArenaSwordTest.advance(w,60);
        check(w.shots.isEmpty()&&w.attackBuffer==0,"Old taps cannot fire unexpectedly later");
        w=ArenaSwordTest.fresh();w.player.cooldown=.08;w.requestAttack();w.update(.01,0,0,false);w.update(.01,1,0,false);ArenaSwordTest.advance(w,10);
        check(w.shots.isEmpty(),"New movement cancels buffered attack intent");
        w=ArenaSwordTest.fresh();w.requestAttack();w.update(.01,1,0,false);check(!w.shots.isEmpty(),"Fresh attack tap still works while moving");
        w=ArenaSwordTest.fresh();w.player.cooldown=.08;w.requestAttack();w.update(.01,0,0,false);w.pause();w.resume();ArenaSwordTest.advance(w,15);
        check(w.shots.isEmpty(),"Pause discards stale buffered input");
        w=ArenaSwordTest.fresh();w.cast(1);ArenaSwordTest.advance(w,34);check(w.player.slamLanded,"Slam responds within 340ms");
        w=ArenaSwordTest.fresh();target=ArenaSwordTest.dummy(w);target.range=0;w.update(.01,1,0,false);
        w.requestAttack();w.update(.01,1,0,false);for(int i=0;i<21;i++)w.update(.01,1,0,false);
        check(target.hp<target.maxHp,"Held movement cannot cancel every attack windup");
        check(w.player.attackTime==0&&ArenaWorld.visualFacing(w.player)==w.player.facing,"Held WASD exits attack recovery after contact");
        double heldX=w.player.x;w.update(.01,-1,0,false);
        check(w.player.x<heldX,"Direction reversal moves on the first step");
        heldX=w.player.x;w.update(.01,0,0,false);
        check(w.player.x==heldX,"Releasing WASD stops without drift");
        w=ArenaSwordTest.fresh();double diagonalX=w.player.x,diagonalZ=w.player.z;
        w.update(.01,1,1,false);
        check(Math.abs(Math.hypot(w.player.x-diagonalX,w.player.z-diagonalZ)-w.player.speed*.01)<.000001,"Diagonal speed stays normalized");
        w=ArenaSwordTest.fresh();w.update(.01,1,0,false);w.cast(1);double startX=w.player.x;
        for(int i=0;i<34;i++)w.update(.01,1,0,false);
        check(w.player.slamLanded&&w.player.x>startX&&w.player.slamTime==0,"Held movement resumes immediately after slam contact");
        w.update(.01,0,1,false);check(w.player.slamTime==0,"Fresh direction can cancel planted recovery immediately");
        w=ArenaSwordTest.fresh();w.requestAttack();w.update(.01,0,0,false);
        check(w.player.combatTime>2,"Attack enters combat stance");
        double combat=w.player.combatTime;w.pause();w.update(.2,0,0,false);
        check(w.player.combatTime==combat,"Pause freezes combat stance timeout");w.resume();
        w.units.remove(1);ArenaSwordTest.advance(w,260);check(w.player.combatTime==0,"Quiet interval leaves combat stance");
        w.damage(w.player,1,1);check(w.player.combatTime>2,"Taking damage reenters combat stance");
        w.player.hp=0;w.player.respawn=.01;w.update(.02,0,0,false);
        check(w.player.combatTime==0,"Respawn clears combat stance");
        System.out.println("PASS: immediate movement/facing recovery, hurt interruption, short attack input buffer, cancellation and faster slam");
    }
}
