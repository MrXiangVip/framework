package com.packages.riftarena;

/** Contact events must follow animation time, survive pauses and never survive cancellation. */
public final class ArenaSwordTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static ArenaWorld fresh(){
        ArenaWorld w=new ArenaWorld();w.reset(0);w.units.clear();w.units.add(w.player);w.player.x=w.player.z=0;w.waveTimer=1000;
        ArenaWorld.Unit dummy=w.spawn(ArenaWorld.Kind.SOLDIER,1,3,0);dummy.speed=0;dummy.attack=0;return w;
    }
    static ArenaWorld.Unit dummy(ArenaWorld w){return w.units.get(1);}
    static void advance(ArenaWorld w,int steps){for(int i=0;i<steps;i++)w.update(.01,0,0,false);}
    static void begin(ArenaWorld w){w.requestAttack();w.update(.01,0,0,false);}
    public static void main(String[] args){
        ArenaWorld w=fresh();ArenaWorld.Unit target=dummy(w);double hp=target.hp;begin(w);
        advance(w,14);check(target.hp==hp&&w.soundEvents.isEmpty(),"No damage or swing sound in early windup");
        advance(w,2);check(target.hp==hp&&w.soundEvents.size()==1&&w.soundEvents.peek().equals("sword-swing"),"Swing precedes contact");
        advance(w,5);check(target.hp==hp-w.player.attack&&w.soundEvents.size()==2&&w.soundEvents.peekLast().equals("sword-hit"),"Contact damage and audio fire once");
        advance(w,30);check(target.hp==hp-w.player.attack&&w.soundEvents.size()==2,"Recovery cannot repeat damage");
        w=fresh();target=dummy(w);hp=target.hp;begin(w);advance(w,7);w.update(.01,-1,0,false);advance(w,30);
        check(target.hp==hp&&w.player.attackTime==0&&w.swordStrikes.isEmpty()&&w.soundEvents.isEmpty(),"Moving cancels windup and all pending feedback");
        w.requestAttack();w.update(.01,0,0,false);check(w.swordStrikes.isEmpty(),"Cancelling cannot bypass attack cooldown");
        for(boolean behind:new boolean[]{false,true}){
            w=fresh();target=dummy(w);hp=target.hp;begin(w);target.x=behind?-3:10;advance(w,22);
            check(target.hp==hp&&w.soundEvents.size()==1,"Escaped or rear target cannot be hit");
        }
        w=fresh();target=dummy(w);hp=target.hp;begin(w);w.damage(w.player,10000,1);advance(w,25);
        check(target.hp==hp&&w.swordStrikes.isEmpty()&&w.soundEvents.isEmpty(),"Dead attacker cannot finish swing");
        w=fresh();target=dummy(w);begin(w);w.damage(target,10000,0);int gold=w.gold;advance(w,25);
        check(w.gold==gold&&w.soundEvents.contains("sword-swing")&&!w.soundEvents.contains("sword-hit"),"Dead target cannot award gold or hit audio twice");
        w=fresh();target=dummy(w);hp=target.hp;begin(w);advance(w,10);double remaining=w.player.attackTime;
        w.pause();w.update(.2,0,0,false);check(w.player.attackTime==remaining&&target.hp==hp,"Pause freezes pending contact");
        w.resume();advance(w,12);check(target.hp==hp-w.player.attack,"Resume completes pending contact once");
        w=fresh();target=dummy(w);begin(w);w.aimX=0;w.aimZ=6;check(w.cast(0),"Dash starts");advance(w,25);
        check(target.hp==target.maxHp&&w.swordStrikes.isEmpty(),"Skill cancels sword windup without phantom hit");
        w=fresh();w.units.remove(1);begin(w);advance(w,25);
        check(w.impacts.isEmpty()&&w.soundEvents.size()==1,"Empty swing produces whoosh only");
        w=fresh();target=dummy(w);begin(w);w.update(.2,0,0,false);
        check(target.hp==target.maxHp-w.player.attack,"Long frame cannot skip contact");
        w.reset(0);check(w.swordStrikes.isEmpty()&&w.soundEvents.isEmpty(),"Restart clears pending events");
        w=fresh();w.configure(w.player,2);target=dummy(w);begin(w);
        check(target.hp==target.maxHp-w.player.attack&&w.swordStrikes.isEmpty(),"Mage damage unchanged");
        // Real input paths: held attacks alternate, release expires, movement resets.
        w=fresh();target=dummy(w);target.hp=target.maxHp=10000;begin(w);
        check(w.player.comboStep==0,"First attack starts A");
        int serial=w.player.attackSerial;
        for(int i=0;i<90&&w.player.attackSerial==serial;i++)w.update(.01,0,0,true);
        check(w.player.attackSerial==serial+1&&w.player.comboStep==1,"Held attack chains into B");
        hp=target.hp;advance(w,23);
        check(target.hp==hp-w.player.attack,"Second attack deals exactly one hit at contact");
        serial=w.player.attackSerial;
        for(int i=0;i<90&&w.player.attackSerial==serial;i++)w.update(.01,0,0,true);
        check(w.player.comboStep==0,"Continuous combo returns to A");
        advance(w,130);begin(w);check(w.player.comboStep==0,"Idle gap resets combo");
        advance(w,75);w.update(.01,-1,0,false);begin(w);
        check(w.player.comboStep==0,"Movement resets combo without slowing input");
        w=fresh();target=dummy(w);target.hp=target.maxHp=10000;begin(w);advance(w,65);
        w.requestAttack();advance(w,15);
        check(w.player.comboStep==1&&w.player.attackTime>0,"Late tap buffers second attack");
        hp=target.hp;w.update(.01,-1,0,false);advance(w,25);
        check(target.hp==hp&&w.swordStrikes.isEmpty(),"Cancelling B prevents phantom damage");
        System.out.println("PASS: sword windup/contact/recovery, sound timing, movement/skill/death cancellation, range/facing checks, pause, empty swings and restart");
    }
}
