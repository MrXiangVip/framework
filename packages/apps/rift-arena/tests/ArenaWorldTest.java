package com.packages.riftarena;

public final class ArenaWorldTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static ArenaWorld fresh(){ArenaWorld w=new ArenaWorld();w.reset(0);return w;}
    static ArenaWorld.Unit find(ArenaWorld w,ArenaWorld.Kind k,int team){for(ArenaWorld.Unit u:w.units)if(u.kind==k&&u.team==team)return u;throw new AssertionError("Missing unit");}
    static void advance(ArenaWorld w,double seconds){for(int i=0;i<(int)(seconds*100);i++)w.update(.01,0,0,false);}
    public static void main(String[] args){
        ArenaWorld w=fresh();double x=w.player.x;w.update(.1,1,1,false);check(Math.abs(Math.hypot(w.player.x-x,w.player.z)-.8)<.001,"Normalized diagonal movement");
        w.player.x=42.9;w.update(.2,1,0,false);check(w.player.x<=43,"Map bounds");
        w=fresh();w.pause();double time=w.elapsed;w.update(.1,1,0,true);check(w.elapsed==time,"Pause freezes simulation");w.resume();w.update(.1,0,0,false);check(w.elapsed>time,"Resume");
        w=fresh();advance(w,3.1);check(w.wave==1&&w.units.stream().filter(u->u.kind==ArenaWorld.Kind.SOLDIER).count()==8,"Both sides spawn four soldiers");
        ArenaWorld.Unit tower=find(w,ArenaWorld.Kind.TOWER,1),core=find(w,ArenaWorld.Kind.CORE,1);double hp=core.hp;w.damage(core,100000,0);check(core.hp==hp,"Core protected while tower alive");
        w.player.x=19;w.player.z=0;ArenaWorld.Unit soldier=w.spawn(ArenaWorld.Kind.SOLDIER,0,13,0);check(w.target(tower)==soldier,"Tower prioritizes minions over nearer hero");
        w=fresh();w.player.x=0;ArenaWorld.Unit enemy=w.spawn(ArenaWorld.Kind.SOLDIER,1,3,0),ally=w.spawn(ArenaWorld.Kind.SOLDIER,0,3,0);hp=ally.hp;enemy.attack=ally.attack=enemy.speed=ally.speed=0;
        w.aimX=7;w.aimZ=0;check(w.cast(0)&&enemy.hp==enemy.maxHp,"Dash does not hit before moving");advance(w,.25);check(enemy.hp<enemy.maxHp&&ally.hp==hp,"Dash damages path enemies only");check(!w.cast(0),"Skill cooldown enforced");
        w.player.x=0;w.player.z=0;enemy.hp=enemy.maxHp;check(w.cast(1)&&enemy.hp==enemy.maxHp,"Slam waits for impact");advance(w,.6);check(enemy.hp<enemy.maxHp,"Slam area damage on impact");
        w.aimX=3;w.aimZ=0;enemy.hp=enemy.maxHp;enemy.speed=0;enemy.attack=0;check(w.cast(2)&&enemy.hp==enemy.maxHp,"Meteor has warning delay");advance(w,1.2);check(enemy.hp==Math.max(0,enemy.maxHp-180-w.level*22),"Delayed meteor resolves");
        w=fresh();double attack=w.player.attack;check(w.buy(0)&&w.player.attack==attack+18&&w.gold==100,"Attack purchase");double max=w.player.maxHp;check(w.buy(1)&&w.player.maxHp==max+180&&w.gold==0,"Health purchase");check(!w.buy(0),"No spending missing gold");w.gold=999;w.player.x=0;check(!w.buy(0),"Shop limited to fountain");
        w=fresh();w.player.x=0;w.recall();w.update(.1,1,0,false);check(w.recall==0,"Movement cancels recall");w.recall();w.damage(w.player,1,1);check(w.recall==0,"Damage cancels recall");w.rival.speed=0;w.waveTimer=100;w.recall();advance(w,4.1);check(Math.abs(w.player.x+33)<.01,"Recall returns to fountain");
        w=fresh();w.player.x=0;int gold=w.gold;for(int i=0;i<6;i++){enemy=w.spawn(ArenaWorld.Kind.SOLDIER,1,1,0);w.damage(enemy,10000,0);}check(w.gold>gold&&w.level==2,"Gold XP and level rewards");
        w.damage(w.player,10000,1);check(!w.player.alive()&&w.deaths==1,"Hero death counted");advance(w,8);check(w.player.alive()&&w.player.hp==w.player.maxHp&&w.player.x==-33,"Hero respawns at home");
        w=fresh();tower=find(w,ArenaWorld.Kind.TOWER,1);core=find(w,ArenaWorld.Kind.CORE,1);w.damage(tower,10000,0);check(!w.shielded(core),"Tower destruction removes shield");w.damage(core,10000,0);check(w.screen==ArenaWorld.Screen.WON,"Victory");w.reset(2);check(w.screen==ArenaWorld.Screen.PLAYING&&w.gold==200&&w.hero==2&&w.cooldowns[2]==0&&w.effects.isEmpty(),"Clean restart with selected hero");
        w.damage(find(w,ArenaWorld.Kind.TOWER,0),10000,1);w.damage(find(w,ArenaWorld.Kind.CORE,0),10000,1);check(w.screen==ArenaWorld.Screen.LOST,"Defeat");
        // Tap requests survive release before the next frame; empty attacks cannot damage distant enemies.
        w=fresh();hp=w.rival.hp;w.requestAttack();w.update(.01,0,0,false);
        check(!w.shots.isEmpty()&&w.player.cooldown>0&&w.rival.hp==hp,"Empty tap gives feedback without remote damage");
        int shotCount=w.shots.size();w.requestAttack();w.update(.01,0,0,false);
        check(w.shots.size()==shotCount,"Attack cooldown prevents repeated taps bypassing rate");
        w=fresh();w.player.x=0;enemy=w.spawn(ArenaWorld.Kind.SOLDIER,1,2,0);enemy.speed=0;enemy.attack=0;hp=enemy.hp;
        w.requestAttack();w.update(.01,0,0,false);check(enemy.hp==hp,"Sword windup does not damage early");advance(w,.21);check(enemy.hp==hp-w.player.attack,"Sword damages nearby enemy on contact");
        check(!w.impacts.isEmpty()&&enemy.hitFlash>0,"Hit feedback accompanies actual damage");
        hp=enemy.hp;w.update(.2,0,0,true);check(enemy.hp==hp,"Held attack respects cooldown");
        w.pause();w.requestAttack();w.resume();w.update(.01,0,0,false);check(!w.attackRequested,"Paused press is ignored");
        w=fresh();w.player.x=0;w.player.z=0;w.rival.speed=0;w.waveTimer=100;
        enemy=w.spawn(ArenaWorld.Kind.SOLDIER,1,2,0);enemy.speed=0;enemy.attack=0;
        w.requestAttack();w.update(.01,0,0,false);double attackFacing=w.player.facing;
        check(Math.abs(attackFacing-90)<.001,"Attack faces enemy");
        advance(w,.9);check(Math.abs(w.player.facing-attackFacing)<.001,"Attack recovery preserves facing");
        w.update(.01,-1,0,false);check(Math.abs(w.player.facing+90)<.001,"Movement can change facing after attack");
        // Killing a right-click target must not resume the stale ground destination.
        w=fresh();w.player.x=0;w.player.z=0;w.rival.speed=0;w.waveTimer=100;
        enemy=w.spawn(ArenaWorld.Kind.SOLDIER,1,2,0);enemy.hp=1;enemy.speed=0;enemy.attack=0;
        w.command(enemy.x,enemy.z);w.update(.01,0,0,false);
        double afterKillX=w.player.x,afterKillFacing=w.player.facing;
        advance(w,.9);check(w.player.x==afterKillX&&w.player.facing==afterKillFacing,"Dead chase target cannot resume old movement");
        // Held auto-attacks keep their direction if the victim disappears, even with the mouse behind.
        w.aimX=-10;w.aimZ=0;
        for(int i=0;i<100;i++)w.update(.01,0,0,true);
        check(w.player.facing==afterKillFacing,"Held empty attacks preserve last attack facing");
        advance(w,.8);w.requestAttack();w.update(.01,0,0,false);
        check(Math.abs(w.player.facing+90)<.001,"Explicit empty attack can aim toward mouse");
        w=fresh();w.player.x=0;w.player.z=0;w.waveTimer=100;w.rival.speed=0;
        w.command(10,0);w.requestAttack();w.update(.01,0,0,false);double cancelX=w.player.x;
        advance(w,.9);check(w.player.x==cancelX&&!w.moving,"Manual attack cancels queued ground movement");
        // Every cardinal attack shares the same direction for its mesh pose and shot.
        for(int direction=0;direction<4;direction++){
            w=fresh();w.player.x=0;w.player.z=0;w.rival.speed=0;w.waveTimer=100;
            double radians=Math.toRadians(direction*90);
            enemy=w.spawn(ArenaWorld.Kind.SOLDIER,1,Math.sin(radians)*2,Math.cos(radians)*2);enemy.speed=0;enemy.attack=0;
            w.requestAttack();w.update(.01,0,0,false);
            ArenaWorld.Shot shot=w.shots.get(0);
            double shotYaw=Math.toDegrees(Math.atan2(shot.tx-shot.x,shot.tz-shot.z));
            check(Math.abs(ArenaWorld.visualFacing(w.player)-shotYaw)<.001,"Face and shot share cardinal direction");
            advance(w,.21);w.update(.1,-1,0,false);
            check(ArenaWorld.visualFacing(w.player)==w.player.facing&&w.player.attackTime==0,"Movement immediately exits attack recovery");
            for(ArenaWorld.Shot active:w.shots)check(active.source!=w.player||!active.melee,"Movement removes old melee trail");
            advance(w,.5);check(ArenaWorld.visualFacing(w.player)==w.player.facing,"Finished pose returns to current movement facing");
        }
        w=fresh();w.waveTimer=100;enemy=w.spawn(ArenaWorld.Kind.SOLDIER,1,0,10);
        w.damage(enemy,10000,0);check(w.units.contains(enemy)&&!enemy.alive()&&enemy.deathAt==w.elapsed,"Dead soldier remains briefly for death animation");
        advance(w,2.1);check(!w.units.contains(enemy),"Death animation corpses are removed after two seconds");
        // Play an entire deterministic match through ordinary movement, attacks, skills and shopping.
        w=fresh();w.reset(1);int ticks=0;
        while(w.screen==ArenaWorld.Screen.PLAYING&&ticks++<24000){
            if(w.player.alive()){
                if(Math.hypot(w.player.x+33,w.player.z)<8){w.buy(0);w.buy(1);}
                boolean retreat=w.player.hp<w.player.maxHp*.35;
                ArenaWorld.Unit target=w.nearest(w.player,100);
                if(retreat)w.command(-33,0);else if(target!=null)w.command(target.x,target.z);
                if(target!=null&&!retreat){w.aimX=target.x;w.aimZ=target.z;if(w.player.distance(target)<5)w.cast(1);if(w.player.distance(target)<14)w.cast(2);}
            }
            w.update(.1,0,0,true);
        }
        check(w.screen!=ArenaWorld.Screen.PLAYING,"Full playable match must reach a result");
        System.out.println("PASS: movement, waves, targeting, all skills, shields, shop, XP, recall, death, respawn, victory/defeat, restart; full match "+w.screen+" after "+(int)w.elapsed+"s");
    }
}
