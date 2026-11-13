package com.packages.riftarena;

/** Straight swept arrows, first contact and shared per-cast damage filtering. */
public final class ArenaScatterTest {
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
    static ArenaWorld fresh(){ArenaWorld w=ArenaSwordTest.fresh();w.configure(w.player,1);w.units.remove(1);w.aimX=12;w.aimZ=0;return w;}
    static ArenaWorld.Unit dummy(ArenaWorld w,double x,double z){ArenaWorld.Unit u=w.spawn(ArenaWorld.Kind.SOLDIER,1,x,z);u.speed=u.attack=u.range=0;u.hp=u.maxHp=1000;return u;}
    static void tick(ArenaWorld w,int n){ArenaSwordTest.advance(w,n);}
    public static void main(String[] args){
        ArenaWorld w=fresh();ArenaWorld.Unit[] targets=new ArenaWorld.Unit[3];
        for(int i=-1;i<=1;i++){double a=Math.toRadians(i*18);targets[i+1]=dummy(w,7*Math.cos(a),7*Math.sin(a));}
        check(w.cast(1)&&w.fanArrows.isEmpty()&&w.player.scatterDraw,"E starts bow draw");tick(w,14);
        for(ArenaWorld.Unit u:targets)check(u.hp==1000,"No early area damage");
        w.aimX=-12;tick(w,5);check(w.fanArrows.size()==3&&!w.player.scatterDraw&&w.soundEvents.contains("scatter-release"),"Release exactly three arrows with original aim");
        double x=w.fanArrows.get(1).x;tick(w,3);check(w.fanArrows.get(1).x>x,"Continuous flight");
        tick(w,70);for(ArenaWorld.Unit u:targets)check(u.hp==850,"Three distinct targets take one skill hit");
        check(w.fanArrows.isEmpty()&&!w.cast(1)&&w.cooldowns[1]>0,"Nine second cooldown and cleanup");
        w=fresh();ArenaWorld.Unit wide=w.spawn(ArenaWorld.Kind.TOWER,1,1.6,0);wide.hp=wide.maxHp=1000;wide.attack=0;w.cast(1);tick(w,80);
        check(wide.hp==850,"Overlapping three arrows cannot triple damage");
        w=fresh();ArenaWorld.Unit front=dummy(w,4,0),back=dummy(w,8,0);w.cast(1);tick(w,90);
        check(front.hp==850&&back.hp==1000,"First enemy blocks center arrow");
        w=fresh();ArenaWorld.Unit near=dummy(w,.1,0);w.cast(1);tick(w,50);check(near.hp==850,"Point blank enemy can be hit once");
        w=fresh();ArenaWorld.Unit ally=w.spawn(ArenaWorld.Kind.SOLDIER,0,3,0);ally.speed=ally.attack=ally.range=0;back=dummy(w,7,0);w.cast(1);tick(w,90);
        check(ally.hp==ally.maxHp&&back.hp==850,"Friendly units do not block or take damage");
        for(int cancel=0;cancel<4;cancel++){
            w=fresh();back=dummy(w,6,0);w.cast(1);tick(w,5);
            if(cancel==0)w.update(.01,0,1,false);else if(cancel==1)w.cast(0);else if(cancel==2)w.recall();else w.damage(w.player,10000,1);
            tick(w,8);check(w.scatterCasts.isEmpty()&&!w.player.scatterDraw&&w.fanArrows.isEmpty(),"Movement, Q, recall or death cancels draw");
        }
        w=fresh();back=dummy(w,7,0);w.cast(1);tick(w,23);x=w.fanArrows.get(1).x;w.pause();w.update(.2,0,0,false);
        check(w.fanArrows.get(1).x==x,"Pause freezes flight");w.resume();w.damage(w.player,10000,1);tick(w,80);check(back.hp==850,"Released scatter survives caster death");
        w=fresh();back=dummy(w,7,0);w.update(.01,0,1,false);w.cast(1);for(int i=0;i<30;i++)w.update(.01,0,1,false);
        check(w.player.z>0&&!w.player.scatterDraw&&!w.fanArrows.isEmpty(),"Held movement resumes after release");
        w=fresh();back=dummy(w,8,0);w.cast(0);tick(w,7);w.cast(1);tick(w,100);check(back.hp==850&&w.arrows.isEmpty(),"E interrupts Q and emits only scatter");
        w=fresh();ArenaWorld.Unit core=w.spawn(ArenaWorld.Kind.CORE,1,3,0);w.spawn(ArenaWorld.Kind.TOWER,1,30,0);back=dummy(w,7,0);w.cast(1);tick(w,80);
        check(core.hp==core.maxHp&&back.hp==1000,"Shielded core absorbs arrow without damage");
        w=fresh();back=dummy(w,6,0);w.cast(1);tick(w,21);back.z=5;tick(w,80);check(back.hp==1000,"Straight arrow never homes after release");
        w=fresh();w.cast(1);w.update(.2,0,0,false);w.update(.2,0,0,false);check(!w.fanArrows.isEmpty(),"Long simulation step retains swept flight");tick(w,70);check(w.fanArrows.isEmpty(),"Range cleanup");
        w=fresh();w.cast(1);tick(w,21);w.reset(1);check(w.fanArrows.isEmpty()&&w.scatterCasts.isEmpty()&&!w.player.scatterDraw,"Restart clears pending and released arrows");
        System.out.println("PASS: scatter windup, three straight arrows, swept first-hit collision, shared hit filter, team/shield rules, cancellation, Q chaining, movement, pause, death, range and restart");
    }
}
