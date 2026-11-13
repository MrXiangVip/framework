package com.packages.riftarena;

/** Feedback changes must not disable input, change facing, or apply physics knockback. */
public final class ArenaHitTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        ArenaWorld w=ArenaSwordTest.fresh();ArenaWorld.Unit target=ArenaSwordTest.dummy(w);target.facing=37;
        w.requestAttack();w.update(.01,0,0,false);ArenaSwordTest.advance(w,20);
        check(target.hitStop>0&&w.player.hitStop>0,"Contact pauses both participants");
        check(target.hurtTime>target.hitFlash&&target.hitSerial==1,"Hurt action outlasts brief highlight");
        double attackTime=w.player.attackTime,x=target.x,z=target.z,cooldown=w.player.cooldown,facing=target.facing;
        w.update(.02,0,0,false);
        check(Math.abs(w.player.attackTime-attackTime)<1e-9&&w.player.cooldown<cooldown,"Hit stop holds pose, not attack cooldown");
        check(target.x==x&&target.z==z&&target.facing==facing,"Recoil never changes physics position or facing");
        ArenaWorld.Impact hit=w.impacts.get(0);
        check(!hit.building&&!hit.lethal&&hit.contactX<hit.x&&hit.height>1,"Contact lies on incoming side of target");
        double px=w.player.x;w.update(.01,-1,0,false);check(w.player.x<px,"Movement remains responsive during hit stop");
        w.pause();double stop=target.hitStop;w.update(.2,0,0,false);check(target.hitStop==stop,"Pause freezes feedback");w.resume();
        ArenaSwordTest.advance(w,40);check(target.hurtTime==0&&target.hitStop==0&&target.hitFlash==0,"Feedback expires");
        w.damage(target,1,0,w.player);w.damage(target,1,0,w.player);check(target.hitSerial==3&&target.hitStop<=.055,"Repeated hits restart reaction without accumulating stun");
        w.damage(target,10000,0,w.player);check(target.hurtTime==0&&target.hitStop==0&&w.soundEvents.contains("kill-hit"),"Lethal hit immediately releases death animation and gives kill cue");
        w=ArenaSwordTest.fresh();w.units.remove(1);ArenaWorld.Unit tower=w.spawn(ArenaWorld.Kind.TOWER,1,3,0);tower.attack=0;
        w.requestAttack();w.update(.01,0,0,false);ArenaSwordTest.advance(w,21);
        check(w.soundEvents.contains("stone-hit")&&!w.soundEvents.contains("sword-hit"),"Buildings have stone impact sound");
        check(tower.hurtTime==0&&tower.hitStop==0&&w.impacts.get(0).building,"Buildings never flinch");
        int impacts=w.impacts.size();w.damage(tower,0,0);w.damage(tower,10,1);check(w.impacts.size()==impacts,"Invalid damage cannot produce feedback");
        System.out.println("PASS: local hit stop, responsive movement, directional contact, short flash, bounded hurt reaction, death priority and building feedback");
    }
}
