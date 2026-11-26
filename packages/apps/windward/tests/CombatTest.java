package com.packages.windward;

/** Behavioral checks for hit timing, counterplay, boundaries and lifecycle. */
public final class CombatTest {
    static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    static void steps(MountainWorld w,int n){for(int i=0;i<n;i++)w.step(0,0,false,0);}
    static MountainWorld duel(){MountainWorld w=new MountainWorld();w.x=MountainCombat.HOME_X;w.z=MountainCombat.HOME_Z+1.6;w.y=w.floor();w.facing=w.visualFacing=Math.PI;return w;}
    public static void main(String[] args){
        MountainWorld w=duel();w.combat.attack(w);check(w.combat.attackTime==0,"Attack begins");steps(w,10);check(w.combat.enemyHealth==3,"No damage before swing contact");steps(w,10);check(w.combat.enemyHealth==2,"Forward melee hits at contact");
        w.combat.attack(w);steps(w,20);check(w.combat.enemyHealth==2,"Held/repeated attack cannot re-hit within swing");
        w=duel();w.facing=0;w.combat.attack(w);steps(w,20);check(w.combat.enemyHealth==3,"Back-facing attack misses");
        w=duel();w.z+=3;w.combat.attack(w);steps(w,20);check(w.combat.enemyHealth==3,"Out of range misses");
        w=duel();w.y+=5;w.grounded=false;w.combat.attack(w);check(w.combat.attackTime<0,"No airborne melee");steps(w,15);check(w.combat.health==100,"Guard cannot hit elevated player");
        w=duel();w.step(0,0,false,0);check(w.combat.state==MountainCombat.State.WINDUP,"Close guard telegraphs attack");steps(w,60);check(w.combat.health==100,"Windup gives reaction time");steps(w,30);check(w.combat.health==75,"Guard strike damages once");steps(w,60);check(w.combat.health==75,"Recovery does not repeatedly hit");
        w=duel();w.step(0,0,false,0);steps(w,65);w.combat.dodge(w,1,0,0);double x=w.x;check(w.stamina==75,"Dodge costs stamina");steps(w,20);check(w.combat.health==100&&w.x>x+1,"Timed side dodge avoids strike and moves");
        w.combat.dodge(w,1,0,0);check(w.stamina==75,"Cannot chain during active dodge");
        w=duel();w.combat.dodge(w,0,0,0);check(w.combat.vulnerable(),"Dodge startup is vulnerable");steps(w,8);check(!w.combat.vulnerable(),"Dodge active window is invulnerable");
        w.paused=true;double timer=w.combat.dodgeTime,enemyTimer=w.combat.timer,stamina=w.stamina;steps(w,120);w.combat.attack(w);check(w.combat.dodgeTime==timer&&w.combat.timer==enemyTimer&&w.stamina==stamina,"Pause freezes both combatants and stamina");
        w=duel();w.stamina=24;w.combat.dodge(w,1,0,0);check(w.combat.dodgeTime<0,"Insufficient stamina blocks dodge");w.climbing=true;w.stamina=100;w.combat.attack(w);w.combat.dodge(w,1,0,0);check(!w.combat.busy(),"No attacks or dodges on wall");
        w=duel();for(int hit=0;hit<3;hit++){w.combat.attack(w);steps(w,62);}check(w.combat.enemyHealth==0&&w.combat.state==MountainCombat.State.DEFEATED,"Three hits defeat guard");int health=w.combat.health;steps(w,600);check(w.combat.health==health,"Defeated guard cannot attack");
        w.reset();check(w.combat.health==100&&w.combat.enemyHealth==3&&!w.combat.busy()&&w.combat.state==MountainCombat.State.IDLE,"Restart resets complete duel");
        w=duel();steps(w,1800);check(w.combat.health==0,"Repeated unavoided strikes exhaust player");double time=w.time,z=w.z;w.step(0,1,true,0);w.jump();w.interact();w.combat.attack(w);w.combat.dodge(w,1,0,0);check(w.time==time&&w.z==z&&!w.combat.busy(),"Downed player cannot move or act");w.reset();check(w.combat.health==100,"R can recover from defeat");
        w=new MountainWorld();w.combat.x+=2;steps(w,600);check(Math.hypot(w.combat.x-MountainCombat.HOME_X,w.combat.z-MountainCombat.HOME_Z)<.11,"Disengaged guard returns home");
        MountainWorld.Obstacle rock=w.rocks.get(0);w.y=MountainWorld.ground(rock.x,rock.z);check(!MountainCombat.clear(w,rock.x-3,rock.z,rock.x+3,rock.z),"Melee cannot pass through rock");
        w.reset();w.x=47.6;w.y=w.floor();w.facing=Math.PI/2;w.combat.dodge(w,0,0,0);steps(w,40);check(w.x>48,"Dodge crosses old map boundary");
        System.out.println("PASS: melee timing/arc/range, telegraph/recovery, dodge cost/iframes/solids, defeat, leash and pause/reset");
    }
}
