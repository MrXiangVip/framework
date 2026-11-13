package com.packages.riftarena;

/** Bow release, travel and contact must be separate deterministic events. */
public final class ArenaArrowTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static ArenaWorld fresh(){ArenaWorld w=ArenaSwordTest.fresh();w.configure(w.player,1);ArenaSwordTest.dummy(w).x=7;return w;}
    static void tick(ArenaWorld w,int count){ArenaSwordTest.advance(w,count);}
    static void begin(ArenaWorld w){ArenaSwordTest.begin(w);}
    public static void main(String[] args){
        ArenaWorld w=fresh();ArenaWorld.Unit target=ArenaSwordTest.dummy(w);double hp=target.hp;begin(w);tick(w,14);
        check(target.hp==hp&&w.arrows.size()==1&&!w.arrows.get(0).released&&w.soundEvents.isEmpty(),"Windup cannot damage or emit release sound");
        tick(w,5);check(w.arrows.get(0).released&&target.hp==hp&&w.soundEvents.contains("bow-release"),"Release starts flight without damage");
        double x=w.arrows.get(0).x;tick(w,4);check(w.arrows.get(0).x>x&&target.hp==hp,"Arrow advances continuously");
        w.player.attack+=100;tick(w,40);check(target.hp==hp-48&&w.arrows.isEmpty()&&w.soundEvents.contains("arrow-hit"),"Arrival applies captured damage once");
        tick(w,30);check(target.hp==hp-48,"No duplicate contact");
        w=fresh();target=ArenaSwordTest.dummy(w);begin(w);tick(w,5);w.update(.01,-1,0,false);tick(w,70);
        check(target.hp==target.maxHp&&w.arrows.isEmpty()&&!w.soundEvents.contains("bow-release"),"Movement cancels draw");
        for(int cancel=0;cancel<3;cancel++){
            w=fresh();target=ArenaSwordTest.dummy(w);begin(w);tick(w,5);
            if(cancel==0)w.cast(2);else if(cancel==1)w.recall();else w.damage(w.player,10000,1);
            tick(w,70);check(target.hp==target.maxHp&&w.arrows.isEmpty(),"Skill, recall and death cancel draw");
        }
        w=fresh();target=ArenaSwordTest.dummy(w);begin(w);tick(w,20);w.damage(w.player,10000,1);tick(w,40);
        check(target.hp==target.maxHp-48,"Released arrow survives shooter death");
        w=fresh();target=ArenaSwordTest.dummy(w);begin(w);tick(w,20);target.x=11;tick(w,70);
        check(target.hp==target.maxHp-48,"Airborne arrow follows target outside original range");
        w=fresh();target=ArenaSwordTest.dummy(w);begin(w);target.x=12;tick(w,70);
        check(target.hp==target.maxHp&&w.arrows.isEmpty(),"Out of range before release cancels shot");
        w=fresh();target=ArenaSwordTest.dummy(w);begin(w);tick(w,20);w.damage(target,10000,0);int gold=w.gold;tick(w,60);
        check(w.arrows.isEmpty()&&w.gold==gold&&!w.soundEvents.contains("arrow-hit"),"Dead target cannot be hit or rewarded twice");
        w=fresh();begin(w);tick(w,22);x=w.arrows.get(0).x;w.pause();w.update(.2,0,0,false);
        check(w.arrows.get(0).x==x,"Pause freezes flight");w.resume();tick(w,50);check(w.arrows.isEmpty(),"Resume completes flight");
        w=fresh();w.units.remove(1);w.aimX=8;w.aimZ=0;begin(w);tick(w,80);
        check(w.impacts.isEmpty()&&w.arrows.isEmpty()&&w.soundEvents.contains("bow-release"),"Empty shot has visual and sound, no damage");
        w=fresh();begin(w);tick(w,22);w.reset(1);check(w.arrows.isEmpty(),"Restart clears arrows");
        w=fresh();target=ArenaSwordTest.dummy(w);w.units.remove(target);target=w.spawn(ArenaWorld.Kind.CORE,1,7,0);w.spawn(ArenaWorld.Kind.TOWER,1,30,0);
        begin(w);tick(w,80);check(target.hp==target.maxHp,"Shielded core cannot be damaged");
        w=fresh();target=ArenaSwordTest.dummy(w);w.units.remove(target);target=w.spawn(ArenaWorld.Kind.TOWER,1,7,0);target.attack=0;
        begin(w);tick(w,80);check(target.hp==target.maxHp-48&&w.soundEvents.contains("stone-hit"),"Building arrival uses stone sound");
        System.out.println("PASS: bow windup, release, continuous flight, captured single damage, cancellation, independent projectile, range, death, pause, shield and building feedback");
    }
}
