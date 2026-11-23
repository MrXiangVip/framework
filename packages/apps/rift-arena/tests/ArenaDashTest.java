package com.packages.riftarena;

public final class ArenaDashTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static ArenaWorld fresh(){ArenaWorld w=ArenaSwordTest.fresh();ArenaWorld.Unit target=ArenaSwordTest.dummy(w);target.x=6.8;target.range=0;w.aimX=10;w.aimZ=0;return w;}
    public static void main(String[] args){
        ArenaWorld w=fresh();ArenaWorld.Unit target=ArenaSwordTest.dummy(w);double hp=target.hp;
        ArenaWorld.Unit ally=w.spawn(ArenaWorld.Kind.SOLDIER,0,3,0);ally.speed=ally.range=ally.attack=0;double allyHp=ally.hp;
        ArenaWorld.Unit side=w.spawn(ArenaWorld.Kind.SOLDIER,1,4,3);side.speed=side.range=side.attack=0;
        check(w.cast(0)&&w.player.x==0&&target.hp==hp&&w.soundEvents.contains("dash-whoosh"),"Q starts animation and sound without teleport or remote damage");
        w.update(.01,0,0,false);check(w.player.x>0&&w.player.x<1&&target.hp==hp,"First frame visibly advances part of path");
        ArenaSwordTest.advance(w,4);check(target.hp==hp&&!w.dashGhosts.isEmpty(),"Far target not hit ahead of actual path");
        ArenaSwordTest.advance(w,8);check(target.hp==hp-122,"Swept path damages target on arrival");
        ArenaSwordTest.advance(w,5);check(Math.abs(w.player.x-7)<1e-8&&w.player.dashTime>0,"Travel ends at 180ms then brakes");
        ArenaSwordTest.advance(w,20);check(w.player.dashTime==0&&target.hp==hp-122&&w.dashGhosts.isEmpty(),"Single hit per target and fading ghosts expire");
        check(ally.hp==allyHp&&side.hp==side.maxHp&&!w.cast(0),"Allies, side targets and cooldown respected");
        w=fresh();w.cast(0);ArenaSwordTest.advance(w,5);double x=w.player.x,time=w.player.dashTime;w.pause();w.update(.2,0,0,false);
        check(w.player.x==x&&w.player.dashTime==time,"Pause freezes dash");w.resume();ArenaSwordTest.advance(w,25);check(Math.abs(w.player.x-7)<1e-8,"Resume finishes same path");
        w=fresh();target=ArenaSwordTest.dummy(w);hp=target.hp;w.cast(0);ArenaSwordTest.advance(w,2);x=w.player.x;w.damage(w.player,10000,1);ArenaSwordTest.advance(w,25);
        check(w.player.x==x&&w.player.dashTime==0&&target.hp==hp,"Death cancels remaining motion and hits");
        w=fresh();w.cast(0);ArenaSwordTest.advance(w,3);x=w.player.x;w.aimX=x;w.aimZ=5;w.cast(1);ArenaSwordTest.advance(w,10);
        check(w.player.x==x&&w.player.dashTime==0&&w.player.slamTime>0,"E interrupts dash at actual current position");
        w=fresh();w.player.x=ArenaWorld.LIMIT_X-.5;w.aimX=ArenaWorld.LIMIT_X+10;w.cast(0);ArenaSwordTest.advance(w,30);check(w.player.x==ArenaWorld.LIMIT_X,"Dash clamps expanded map boundary");
        w=fresh();w.cast(0);ArenaSwordTest.advance(w,14);w.requestAttack();w.update(.01,0,0,false);ArenaSwordTest.advance(w,12);
        check(w.player.attackTime>0,"Late attack tap chains after braking");
        w=fresh();w.cast(0);for(int i=0;i<28;i++)w.update(.01,0,1,false);
        check(w.player.x==7&&w.player.z>0&&w.player.dashTime==0,"Held movement resumes immediately after dash");
        w=fresh();w.cast(0);w.update(.2,0,0,false);check(w.player.x==7&&ArenaSwordTest.dummy(w).hp<210,"Large update cannot skip swept contact");
        w.reset(0);check(w.player.dashTime==0&&w.dashGhosts.isEmpty(),"Restart clears dash state");
        w=fresh();w.configure(w.player,2);check(w.cast(0)&&w.player.x==7&&w.player.dashTime==0,"Mage retains existing Q behavior");
        System.out.println("PASS: continuous dash, swept single-hit damage, charge/brake, ghosts, input chaining, death, skill interruption, pause and bounds");
    }
}
