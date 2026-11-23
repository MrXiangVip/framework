package com.packages.riftarena;

public final class ArenaRetreatTest {
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
    static ArenaWorld fresh(){ArenaWorld w=ArenaSwordTest.fresh();w.configure(w.player,1);w.aimX=7;w.aimZ=0;ArenaSwordTest.dummy(w).x=7;return w;}
    static void tick(ArenaWorld w,int n){ArenaSwordTest.advance(w,n);}
    public static void main(String[] args){
        ArenaWorld w=fresh();ArenaWorld.Unit enemy=ArenaSwordTest.dummy(w);double hp=enemy.hp;
        check(w.cast(0)&&w.player.x==0&&w.player.dashTime>0,"Retreat starts without teleport");
        tick(w,8);check(w.player.x<0&&w.player.x>-4&&enemy.hp==hp,"Continuous backward travel, no path damage");
        w.aimX=-10;tick(w,18);check(Math.abs(w.player.x+4)<.001&&w.player.retreatShot&&!w.arrows.get(0).released,"Retreat finishes into bow draw");
        check(w.arrows.get(0).target==enemy,"Original direction retained despite cursor movement");
        tick(w,19);check(w.arrows.get(0).released&&enemy.hp==hp,"No damage until strengthened arrow arrives");
        tick(w,70);check(enemy.hp==hp-122&&w.arrows.isEmpty(),"Q arrow hits once with skill damage beyond basic range");
        check(!w.cast(0)&&w.cooldowns[0]>0,"Six second cooldown preserved");
        w=fresh();w.cast(0);tick(w,8);double x=w.player.x;w.pause();w.update(.2,0,0,false);check(w.player.x==x,"Pause freezes retreat");w.resume();tick(w,50);check(w.player.dashTime==0,"Resume completes action");
        for(int cancel=0;cancel<3;cancel++){
            w=fresh();w.cast(0);tick(w,8);enemy=ArenaSwordTest.dummy(w);
            if(cancel==0)w.cast(1);else if(cancel==1)w.recall();else w.damage(w.player,10000,1);
            tick(w,90);check(w.arrows.isEmpty()&&!w.player.retreatShot&&enemy.hp==enemy.maxHp-(cancel==0?150:0),"E replaces Q damage; recall and death cancel pending Q arrow");
        }
        w=fresh();w.cast(0);tick(w,27);w.update(.01,0,1,false);tick(w,90);
        check(w.arrows.isEmpty()&&ArenaSwordTest.dummy(w).hp==210,"Fresh movement can cancel bow draw");
        w=fresh();w.cast(0);for(int i=0;i<50;i++)w.update(.01,0,1,false);
        check(w.player.z>0&&!w.player.retreatShot&&w.arrows.get(0).released,"Held movement waits through draw and resumes after release");
        w=fresh();w.cast(0);tick(w,46);w.damage(w.player,10000,1);tick(w,70);
        check(ArenaSwordTest.dummy(w).hp==88,"Released Q arrow survives caster death");
        w=fresh();w.player.x=-ArenaWorld.LIMIT_X+1;w.aimX=w.player.x+12;w.cast(0);tick(w,26);check(w.player.x==-ArenaWorld.LIMIT_X,"Boundary clamps backward endpoint");
        w=fresh();w.units.remove(1);w.cast(0);tick(w,130);check(w.arrows.isEmpty()&&w.impacts.isEmpty(),"Empty Q arrow expires without damage");
        w=fresh();w.cast(0);tick(w,26);w.reset(1);check(w.arrows.isEmpty()&&!w.player.retreatShot&&w.player.dashTime==0,"Restart clears action");
        System.out.println("PASS: archer retreat, saved aim, delayed skill arrow, cancellation, held movement, cooldown, pause, death, bounds and restart");
    }
}
