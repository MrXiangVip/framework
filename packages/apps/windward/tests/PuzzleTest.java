package com.packages.windward;

/** Stone mechanics and completion requirements through the real fixed-step simulation. */
public final class PuzzleTest {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void steps(MountainWorld w,int n,double forward){for(int i=0;i<n;i++)w.step(0,forward,false,0);}
    static void place(MountainWorld w,double x,double z){w.x=x;w.z=z;w.y=w.floor();w.grounded=true;}
    static void atHandle(MountainWorld w){place(w,MountainPuzzle.X,w.puzzle.stoneZ+1.15);}
    static void atTerminal(MountainWorld w){place(w,MountainPuzzle.TERMINAL_X,MountainPuzzle.TERMINAL_Z+1.4);}
    static void solve(MountainWorld w){
        atHandle(w);w.interact();check(w.puzzle.pushing,"E attaches to south handle");
        for(int i=0;i<1000&&w.puzzle.stoneZ>MountainPuzzle.PLATE_Z+.1;i++)w.step(0,1,false,0);
        check(Math.abs(w.puzzle.stoneZ-MountainPuzzle.PLATE_Z)<.2,"Push stone onto receiver");
        steps(w,125,0);check(w.puzzle.powered&&!w.puzzle.pushing,"Receiver charges, fixes stone and releases hands");
    }
    static void walk(MountainWorld w,double tx,double tz){
        for(int i=0;i<3000&&Math.hypot(w.x-tx,w.z-tz)>.16;i++){
            double dx=tx-w.x,dz=tz-w.z,len=Math.hypot(dx,dz);w.step(dx/len,-dz/len,false,0);
        }
        check(Math.hypot(w.x-tx,w.z-tz)<.16,"Walkable complete route to "+tx+","+tz);
    }
    static void playthrough(){
        MountainWorld w=new MountainWorld();walk(w,1,-2);walk(w,3,-10.4);
        for(int hit=0;hit<3;hit++){w.facing=Math.atan2(w.combat.x-w.x,w.combat.z-w.z);w.combat.attack(w);steps(w,62,0);}
        check(w.combat.enemyHealth==0,"Camp playthrough defeats guard");
        walk(w,4,-21);walk(w,15,-25);check(w.journey.arrived(),"Camp playthrough discovers tower");
        walk(w,18,-18);walk(w,22,MountainPuzzle.START_Z+1.15);w.interact();check(w.puzzle.pushing,"Camp playthrough grabs stone");
        while(w.puzzle.stoneZ>MountainPuzzle.PLATE_Z+.1&&w.time<180)w.step(0,1,false,0);
        steps(w,125,0);check(w.puzzle.powered,"Camp playthrough powers plate");
        walk(w,MountainPuzzle.TERMINAL_X,MountainPuzzle.TERMINAL_Z+1.4);w.interact();
        check(w.journey.complete&&w.combat.health>0,"Camp-to-completion without teleport or injected prerequisites");
    }
    public static void main(String[] args){
        playthrough();
        MountainWorld w=new MountainWorld();w.interact();check(!w.puzzle.pushing,"Distant E cannot move stone");
        atHandle(w);MountainCamera camera=new MountainCamera();camera.yaw=Math.PI;camera.pitch=.12;camera.update(w);check(camera.eyeZ>w.puzzle.stoneZ+.7,"Camera retracts before puzzle stone");
        w.journey.stage=MountainJourney.ROUTE.length;w.combat.enemyHealth=0;check(w.journey.guideZ(w)==w.puzzle.stoneZ,"Objective guides to stone before pushing");w.reset();
        atHandle(w);w.grounded=false;w.interact();check(!w.puzzle.pushing,"Cannot grab in air");w.grounded=true;w.paused=true;w.interact();check(!w.puzzle.pushing,"Cannot grab while paused");w.paused=false;
        w.combat.attack(w);w.interact();check(!w.puzzle.pushing,"Cannot grab during attack");steps(w,65,0);
        w.interact();check(w.puzzle.pushing,"Can grab after attack");double z=w.puzzle.stoneZ;w.jump();w.combat.attack(w);w.combat.dodge(w,1,0,0);check(w.grounded&&!w.combat.busy(),"Push excludes jump, melee and dodge");
        w.paused=true;steps(w,120,1);check(w.puzzle.stoneZ==z,"Pause freezes push");w.paused=false;
        steps(w,90,1);check(w.puzzle.stoneZ<z-.9&&w.z>w.puzzle.stoneZ+1,"Stone and player move together without overlap");
        w.interact();check(!w.puzzle.pushing,"E releases stone");z=w.puzzle.stoneZ;steps(w,100,0);check(w.puzzle.stoneZ==z,"Released stone stays put");
        w.interact();steps(w,1200,1);check(w.puzzle.stoneZ==MountainPuzzle.MIN_Z&&!w.puzzle.powered,"Overshoot remains in rail and can be recovered");steps(w,1200,-1);check(w.puzzle.stoneZ==MountainPuzzle.MAX_Z,"Pull back to start bound");
        w.reset();atHandle(w);steps(w,120,1);check(w.z>=w.puzzle.stoneZ+.7+MountainWorld.RADIUS,"Walking cannot pass through stone");
        place(w,MountainPuzzle.X,w.puzzle.stoneZ);w.y=w.puzzle.stoneY()+4;w.grounded=false;steps(w,180,0);check(w.grounded&&Math.abs(w.y-w.puzzle.stoneY()-1.4)<1e-8,"Stone top supports landing");
        w.reset();w.puzzle.stoneZ=MountainPuzzle.PLATE_Z;steps(w,60,0);check(w.puzzle.charge>0&&!w.puzzle.powered,"Receiver requires sustained weight");double charge=w.puzzle.charge;w.paused=true;steps(w,120,0);check(w.puzzle.charge==charge,"Pause freezes receiver charging");w.paused=false;w.puzzle.stoneZ=MountainPuzzle.START_Z;steps(w,1,0);check(w.puzzle.charge==0,"Leaving receiver clears partial charge");
        w.reset();atTerminal(w);w.interact();check(!w.journey.complete,"Live guard blocks tower activation");w.combat.enemyHealth=0;w.interact();check(!w.journey.complete,"Unpowered receiver blocks tower activation");
        w.reset();solve(w);atTerminal(w);w.interact();check(!w.journey.complete,"Powered receiver alone cannot bypass guard");
        // Defeat the guard using real swings, then walk back to the terminal.
        place(w,MountainCombat.HOME_X,MountainCombat.HOME_Z+1.6);w.facing=Math.PI;
        for(int hit=0;hit<3;hit++){w.combat.attack(w);steps(w,62,0);}
        check(w.combat.enemyHealth==0,"Guard prerequisite earned in combat");
        atTerminal(w);w.paused=true;w.interact();check(!w.journey.complete,"Paused terminal ignored");w.paused=false;w.interact();
        check(w.journey.complete&&w.journey.summaryOpen&&!w.journey.treasure,"Main completion does not require optional relic");
        double time=w.time;steps(w,120,1);w.combat.attack(w);w.jump();check(w.time==time&&!w.combat.busy(),"Summary freezes world and actions");
        w.journey.summaryOpen=false;steps(w,10,0);check(w.time>time,"Continue resumes free exploration");double completed=w.journey.completedAt;w.interact();check(w.journey.completedAt==completed,"Activation is idempotent");
        w.reset();check(!w.puzzle.powered&&!w.puzzle.pushing&&w.puzzle.charge==0&&w.puzzle.stoneZ==MountainPuzzle.START_Z&&!w.journey.complete&&!w.journey.summaryOpen,"Restart resets puzzle and completion");
        System.out.println("PASS: stone push/pull/collision/support, plate timing, guard gate, completion/continue and pause/reset");
    }
}
