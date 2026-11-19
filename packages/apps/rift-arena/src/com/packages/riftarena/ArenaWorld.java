package com.packages.riftarena;

import java.util.*;

/** Deterministic simulation, owned by the renderer thread; independent of graphics. */
final class ArenaWorld {
    enum Screen { TITLE, PLAYING, PAUSED, WON, LOST }
    enum Kind { HERO, SOLDIER, TOWER, CORE }
    static final double LIMIT_X=43, LIMIT_Z=18;
    static final double ATTACK_VISUAL_SECONDS=.46;
    static final double HURT_SECONDS=.26;
    static final double DASH_MOVE=.18,DASH_SECONDS=.24,DASH_RADIUS=1.6;
    static final double METEOR_CAST=.40,METEOR_RELEASE=.24,METEOR_FLIGHT=.66,METEOR_RADIUS=5;
    static double meteorPosePhase(Unit u){double age=METEOR_CAST-u.meteorTime;return age<METEOR_RELEASE?.10+.14*clamp(age/METEOR_RELEASE,0,1):.24+.76*clamp((age-METEOR_RELEASE)/(METEOR_CAST-METEOR_RELEASE),0,1);}
    static final double SLAM_SECONDS=.74,SLAM_WINDUP=.23,SLAM_CONTACT=.32,SLAM_RADIUS=6;
    static double slamAge(Unit unit){return SLAM_SECONDS-unit.slamTime;}
    /** Retime the existing overhead chop: slow raise, fast descent, full recovery. */
    static double slamPosePhase(double age){
        if(age<=SLAM_WINDUP)return clamp(age/SLAM_WINDUP,0,1)*.24;
        if(age<=SLAM_CONTACT)return .24+(age-SLAM_WINDUP)/(SLAM_CONTACT-SLAM_WINDUP)*.06;
        return .30+clamp((age-SLAM_CONTACT)/(SLAM_SECONDS-SLAM_CONTACT),0,1)*.70;
    }
    static final double SWORD_ATTACK_SECONDS=.66,SWORD_HIT_PHASE=.30,SWORD_SWING_PHASE=.22;
    static boolean swordsman(Unit unit){return unit.kind==Kind.HERO&&unit.build==0;}
    static final double ARROW_RELEASE=.18,ARROW_SPEED=28;
    static boolean archer(Unit unit){return unit.kind==Kind.HERO&&unit.build==1;}
    static double attackDuration(Unit unit){return swordsman(unit)?SWORD_ATTACK_SECONDS:ATTACK_VISUAL_SECONDS;}
    static final String[] HEROES={"破晓剑士", "逐星游侠", "苍穹法师"};
    static final String[] SKILLS={"疾风突进", "震荡斩", "星陨"};
    private static final String[][] SKILL_NAMES={{"突进","震荡斩","星陨"},{"后撤射击","扇形散射","星陨"},{"奥术闪现","奥术新星","星陨"}};
    static String skillName(int hero,int skill){return SKILL_NAMES[hero][skill];}
    boolean chargingMeteor(){if(!player.alive())return false;if(player.meteorTime>METEOR_CAST-METEOR_RELEASE)return true;if(hero==2)for(Effect e:effects)if(e.team==player.team&&e.type==2)return true;return false;}

    static final double[] COOLDOWNS={6,9,24};
    static final class Unit {
        final Kind kind; final int team; final double homeX,homeZ;
        double x,z,hp,maxHp,attack,range,speed,cooldown,respawn,facing,travel,hitFlash,attackFacing,attackTime;
        double lastMovedAt=-100,deathAt=-1,hitStop,hitPauseTotal,hurtTime,hitDX,hitDZ;
        int hitSerial,slamSerial,dashSerial,meteorSerial;
        double meteorTime,meteorFacing;
        boolean retreatShot,scatterDraw;double retreatDamage;
        double dashTime,dashFacing,dashStartX,dashStartZ,dashEndX,dashEndZ,dashDamage;
        final Set<Unit> dashHits=Collections.newSetFromMap(new IdentityHashMap<Unit,Boolean>());
        double slamTime,slamFacing,slamDamage;boolean slamLanded;
        double comboWindow,combatTime;int comboStep;
        boolean player,retreating; int build,attackSerial;
        Unit(Kind k,int t,double x,double z){kind=k;team=t;this.x=homeX=x;this.z=homeZ=z;}
        boolean alive(){return hp>0;}
        double distance(Unit b){return Math.hypot(x-b.x,z-b.z);}
    }
    static final class Effect {
        final double x,z,radius; final int team,type; double life; final double duration;
        Effect(double x,double z,double radius,int team,int type,double life){this.x=x;this.z=z;this.radius=radius;this.team=team;this.type=type;this.life=duration=life;}
    }
    static final class Shot {
        final double x,z,tx,tz;final Unit source; final int team; final boolean melee;final double duration;double life;
        Shot(Unit from,Unit to){this(from,to.x,to.z);}
        Shot(Unit from,double tx,double tz){source=from;x=from.x;z=from.z;this.tx=tx;this.tz=tz;team=from.team;melee=swordsman(from);duration=attackDuration(from);life=duration;
            if(melee){from.combatTime=2.4;from.comboStep=from.comboWindow>0?1-from.comboStep:0;from.comboWindow=1.15;}
            from.attackSerial++;from.attackFacing=Math.toDegrees(Math.atan2(tx-x,tz-z));from.attackTime=duration;}
    }
    static final class Arrow {
        final Unit source,target;final int serial,team;final double amount,aimX,aimZ,range;
        double x,y,z,dx,dz,life=1.5;boolean released;
        Arrow(Shot shot,Unit target){this(shot,target,shot.source.attack,shot.source.range);}
        Arrow(Shot shot,Unit target,double amount,double range){source=shot.source;this.target=target;serial=source.attackSerial;team=source.team;this.amount=amount;this.range=range;aimX=shot.tx;aimZ=shot.tz;}
    }
    static final double SCATTER_RANGE=12,SCATTER_ANGLE=18;
    static final class ScatterCast {
        final Unit source;final int serial;final double damage,facing;
        ScatterCast(Unit source,double damage){this.source=source;serial=source.attackSerial;this.damage=damage;facing=source.attackFacing;}
    }
    static final class FanArrow {
        final Unit source;final int team;final double damage,dx,dz;final Set<Unit> hits;
        double x,z,travel;final double y=1.8;
        FanArrow(ScatterCast cast,double offset,Set<Unit> hits){source=cast.source;team=source.team;damage=cast.damage;this.hits=hits;
            double angle=Math.toRadians(cast.facing+offset);dx=Math.sin(angle);dz=Math.cos(angle);x=source.x+dx*.35;z=source.z+dz*.35;}
    }
    final List<ScatterCast> scatterCasts=new ArrayList<>();
    final List<FanArrow> fanArrows=new ArrayList<>();
    private void advanceScatter(double dt){
        for(Iterator<FanArrow> it=fanArrows.iterator();it.hasNext();){
            FanArrow arrow=it.next();double step=Math.min(ARROW_SPEED*dt,SCATTER_RANGE-arrow.travel);
            Unit first=null;double nearest=step+1;
            for(Unit target:units)if(target.alive()&&target.team!=arrow.team){
                double radius=target.kind==Kind.SOLDIER?.42:target.kind==Kind.HERO?.7:1.3;
                double vx=target.x-arrow.x,vz=target.z-arrow.z,along=vx*arrow.dx+vz*arrow.dz,side=vx*arrow.dz-vz*arrow.dx;
                if(Math.abs(side)>radius)continue;
                double half=Math.sqrt(Math.max(0,radius*radius-side*side));
                if(along+half<0)continue;double contact=Math.max(0,along-half);
                if(contact<=step&&contact<nearest){nearest=contact;first=target;}
            }
            if(first!=null){
                // Even an already-hit enemy or shielded core stops this arrow.
                if(!shielded(first)&&arrow.hits.add(first)){
                    Unit incoming=new Unit(Kind.HERO,arrow.team,arrow.x-arrow.dx,arrow.z-arrow.dz);
                    damage(first,arrow.damage,arrow.team,incoming);
                    if(arrow.source.player)sound(first.kind==Kind.TOWER||first.kind==Kind.CORE?"stone-hit":"arrow-hit");
                }
                it.remove();if(screen!=Screen.PLAYING)break;
            }else{
                arrow.x+=arrow.dx*step;arrow.z+=arrow.dz*step;arrow.travel+=step;
                if(arrow.travel>=SCATTER_RANGE-.000001||Math.abs(arrow.x)>LIMIT_X||Math.abs(arrow.z)>LIMIT_Z)it.remove();
            }
        }
        if(screen!=Screen.PLAYING)return;
        for(Iterator<ScatterCast> it=scatterCasts.iterator();it.hasNext();){
            ScatterCast cast=it.next();Unit source=cast.source;
            if(!source.alive()||source.attackSerial!=cast.serial){source.scatterDraw=false;it.remove();continue;}
            if(attackDuration(source)-source.attackTime<ARROW_RELEASE)continue;
            Set<Unit> hits=Collections.newSetFromMap(new IdentityHashMap<Unit,Boolean>());
            for(int i=-1;i<=1;i++)fanArrows.add(new FanArrow(cast,i*SCATTER_ANGLE,hits));
            source.scatterDraw=false;if(source.player)sound("scatter-release");it.remove();
        }
    }
    final List<Arrow> arrows=new ArrayList<>();
    private void attackShot(Unit source,Unit target,double x,double z){
        Shot shot=new Shot(source,x,z);
        if(archer(source))arrows.add(new Arrow(shot,target));else shots.add(shot);
    }
    private void cancelArrowWindup(Unit unit){
        unit.retreatShot=unit.scatterDraw=false;
        scatterCasts.removeIf(cast->cast.source==unit);
        if(archer(unit))arrows.removeIf(arrow->arrow.source==unit&&!arrow.released);
    }
    private void advanceArrows(double dt){
        for(Iterator<Arrow> it=arrows.iterator();it.hasNext();){
            Arrow arrow=it.next();Unit source=arrow.source,target=arrow.target;
            if(!arrow.released){
                if(!source.alive()||source.attackSerial!=arrow.serial||(target!=null&&(!target.alive()||shielded(target)))){it.remove();continue;}
                if(attackDuration(source)-source.attackTime<ARROW_RELEASE)continue;
                // Recheck range at release; once airborne an arrow can follow an escaping target.
                if(target!=null&&source.distance(target)>arrow.range+.35){it.remove();continue;}
                double angle=Math.toRadians(source.attackFacing);
                arrow.dx=Math.sin(angle);arrow.dz=Math.cos(angle);
                arrow.x=source.x+arrow.dx*.75+arrow.dz*.25;arrow.z=source.z+arrow.dz*.75-arrow.dx*.25;arrow.y=2.0;
                arrow.released=true;source.retreatShot=false;if(source.player)sound("bow-release");continue;
            }
            arrow.life-=dt;
            if(arrow.life<=0||target!=null&&(!target.alive()||shielded(target))){it.remove();continue;}
            double tx=target==null?arrow.aimX:target.x,tz=target==null?arrow.aimZ:target.z;
            double ty=target==null?1.7:target.kind==Kind.SOLDIER?1.25:target.kind==Kind.HERO?2.2:2.3;
            double vx=tx-arrow.x,vy=ty-arrow.y,vz=tz-arrow.z,d=Math.sqrt(vx*vx+vy*vy+vz*vz),step=ARROW_SPEED*dt;
            if(d<=step){
                if(target!=null&&target.team!=arrow.team){
                    // Impact direction comes from the incoming arrow, not the shooter's current position.
                    Unit incoming=new Unit(Kind.HERO,arrow.team,arrow.x-arrow.dx,arrow.z-arrow.dz);
                    damage(target,arrow.amount,arrow.team,incoming);
                    if(source.player)sound(target.kind==Kind.TOWER||target.kind==Kind.CORE?"stone-hit":"arrow-hit");
                }
                it.remove();if(screen!=Screen.PLAYING)break;
            }else{
                arrow.x+=vx/d*step;arrow.y+=vy/d*step;arrow.z+=vz/d*step;
                double horizontal=Math.hypot(vx,vz);if(horizontal>.001){arrow.dx=vx/horizontal;arrow.dz=vz/horizontal;}
            }
        }
        if(player.retreatShot){boolean pending=false;for(Arrow arrow:arrows)if(arrow.source==player&&!arrow.released)pending=true;if(!pending)player.retreatShot=false;}
    }
    static final class SwordStrike {
        final Unit attacker,target;final int serial;final double amount;double age;boolean swung;
        SwordStrike(Unit attacker,Unit target){this.attacker=attacker;this.target=target;serial=attacker.attackSerial;amount=attacker.attack;}
    }
    final List<SwordStrike> swordStrikes=new ArrayList<>();
    final ArrayDeque<String> soundEvents=new ArrayDeque<>();
    void sound(String name){if(soundEvents.size()>=16)soundEvents.removeFirst();soundEvents.addLast(name);}
    private void cancelSwordWindup(Unit unit){
        if(swordsman(unit)&&unit.attackTime>SWORD_ATTACK_SECONDS*(1-SWORD_HIT_PHASE)){
            unit.attackSerial++;unit.attackTime=0;
            shots.removeIf(shot->shot.melee&&shot.source==unit);
        }
    }
    static final class Impact {
        final double x,z,amount,contactX,contactZ,height,dx,dz; final int team;final boolean building,lethal; double life=.65;
        Impact(Unit target,double amount,int team,Unit source){
            x=target.x;z=target.z;this.amount=amount;this.team=team;
            building=target.kind==Kind.TOWER||target.kind==Kind.CORE;lethal=amount>=target.hp;
            double length=source==null?0:target.distance(source);
            dx=length>.001?(target.x-source.x)/length:0;dz=length>.001?(target.z-source.z)/length:1;
            double radius=building?1.2:target.kind==Kind.HERO?.6:.35;
            contactX=x-dx*radius;contactZ=z-dz*radius;height=building?2.3:target.kind==Kind.HERO?2.2:1.25;
        }
    }
    static final class DashGhost {
        final Unit source;final double x,z,facing;double life=.10;
        DashGhost(Unit source){this.source=source;x=source.x;z=source.z;facing=source.dashFacing;}
    }
    static final class Meteor {
        final Unit caster;final int serial,team;final double x,z,damage;
        double launchedAt=-1;
        Meteor(Unit caster,double x,double z,double damage){this.caster=caster;serial=caster.meteorSerial;team=caster.team;this.x=x;this.z=z;this.damage=damage;}
        boolean released(){return launchedAt>=0;}
        double progress(double now){return clamp((released()?METEOR_RELEASE+now-launchedAt:METEOR_CAST-caster.meteorTime)/(METEOR_RELEASE+METEOR_FLIGHT),0,1);}
    }
    final List<Meteor> meteors=new ArrayList<>();
    final List<DashGhost> dashGhosts=new ArrayList<>();
    final List<Impact> impacts=new ArrayList<>();
    final List<Shot> shots=new ArrayList<>();
    final List<Unit> units=new ArrayList<>();
    final List<Effect> effects=new ArrayList<>();
    final double[] cooldowns=new double[3];
    Screen screen=Screen.TITLE;
    Unit player,rival,selected;
    int hero,level=1,xp,gold=200,kills,deaths,wave,weapon,armor;
    double elapsed,waveTimer=3,noticeTime,recall,attackBuffer,aimX,aimZ,destX,destZ;
    boolean moving,attackRequested,slamJustStarted;
    double inputX,inputZ;
    double cameraZoom=1;
    int generation;
    String notice="跟随兵线推塔，摧毁敌方水晶";
    ArenaWorld(){reset(0);screen=Screen.TITLE;}
    void reset(int hero){
        generation++;
        this.hero=Math.floorMod(hero,3);scatterCasts.clear();fanArrows.clear();arrows.clear();meteors.clear();dashGhosts.clear();swordStrikes.clear();soundEvents.clear();impacts.clear();units.clear();effects.clear();shots.clear();Arrays.fill(cooldowns,0);
        elapsed=0;waveTimer=3;wave=0;level=1;xp=0;gold=200;kills=deaths=weapon=armor=0;recall=0;attackBuffer=0;inputX=inputZ=0;slamJustStarted=false;moving=false;selected=null;attackRequested=false;
        spawn(Kind.CORE,0,-38,0);spawn(Kind.CORE,1,38,0);
        spawn(Kind.TOWER,0,-22,0);spawn(Kind.TOWER,1,22,0);
        player=spawn(Kind.HERO,0,-33,0);player.player=true;configure(player,this.hero);
        rival=spawn(Kind.HERO,1,33,0);configure(rival,(this.hero+1)%3);
        aimX=player.x+10;aimZ=0;screen=Screen.PLAYING;tell("跟随兵线拆塔，再摧毁敌方水晶！");
    }
    void configure(Unit u,int build){u.build=build;u.maxHp=build==0?900:build==1?650:700;u.hp=u.maxHp;u.range=build==0?3.4:build==1?8:6.5;u.attack=build==0?62:build==1?48:53;u.speed=build==0?8:7.5;}
    Unit spawn(Kind kind,int team,double x,double z){
        Unit u=new Unit(kind,team,x,z);
        if(kind==Kind.HERO){u.maxHp=800;u.attack=55;u.range=4;u.speed=8;}
        if(kind==Kind.SOLDIER){u.maxHp=210+wave*8;u.attack=23+wave*1.5;u.range=2.4;u.speed=3.7;}
        if(kind==Kind.TOWER){u.maxHp=1600;u.attack=95;u.range=11;}
        if(kind==Kind.CORE){u.maxHp=2200;u.attack=60;u.range=9;}
        u.hp=u.maxHp;units.add(u);return u;
    }
    void tell(String text){notice=text;noticeTime=4;}
    void pause(){attackRequested=false;attackBuffer=0;if(screen==Screen.PLAYING)screen=Screen.PAUSED;}
    void resume(){if(screen==Screen.PAUSED)screen=Screen.PLAYING;}
    boolean shielded(Unit u){if(u.kind!=Kind.CORE)return false;for(Unit t:units)if(t.kind==Kind.TOWER&&t.team==u.team&&t.alive())return true;return false;}
    void command(double x,double z){
        if(screen!=Screen.PLAYING||!player.alive())return;
        attackBuffer=0;attackRequested=false;recall=0;destX=clamp(x,-LIMIT_X,LIMIT_X);destZ=clamp(z,-LIMIT_Z,LIMIT_Z);moving=true;selected=null;
        double best=2.5;for(Unit u:units)if(u.team!=0&&u.alive()){double d=Math.hypot(x-u.x,z-u.z);if(d<best){best=d;selected=u;}}
        if(selected!=null)moving=false;
    }
    static double clamp(double x,double min,double max){return Math.max(min,Math.min(max,x));}
    void requestAttack(){if(screen==Screen.PLAYING&&player.alive()){attackRequested=true;attackBuffer=.14;}}
    void basicAttack(boolean explicitPress){
        if(screen!=Screen.PLAYING||!player.alive()||player.slamTime>0||player.dashTime>0||player.meteorTime>0)return;
        recall=0;moving=false;selected=null;
        if(player.cooldown>0)return;
        attackBuffer=0;
        Unit enemy=target(player);
        if(enemy!=null){strike(player,enemy);return;}
        double dx=explicitPress?aimX-player.x:Math.sin(Math.toRadians(player.facing));
        double dz=explicitPress?aimZ-player.z:Math.cos(Math.toRadians(player.facing)),d=Math.hypot(dx,dz);
        if(d<.001){dx=Math.sin(Math.toRadians(player.facing));dz=Math.cos(Math.toRadians(player.facing));d=1;}
        player.facing=Math.toDegrees(Math.atan2(dx,dz));player.cooldown=.72;
        attackShot(player,null,player.x+dx/d*player.range,player.z+dz/d*player.range);
        if(swordsman(player))swordStrikes.add(new SwordStrike(player,null));
        tell("射程内无敌人：靠近敌人后普攻可造成伤害");
    }
    void update(double seconds,double dx,double dz,boolean attack){
        if(screen!=Screen.PLAYING)return;
        // A long stalled frame must not let units teleport or skip attacks.
        double remaining=clamp(seconds,0,.2);
        while(remaining>0&&screen==Screen.PLAYING){double dt=Math.min(.025,remaining);step(dt,dx,dz,attack);remaining-=dt;}
    }
    private void step(double dt,double dx,double dz,boolean attack){
        double inputLength=Math.hypot(dx,dz),nx=inputLength>0?dx/inputLength:0,nz=inputLength>0?dz/inputLength:0;
        boolean newMovement=inputLength>0&&(Math.abs(nx-inputX)>.001||Math.abs(nz-inputZ)>.001);
        inputX=nx;inputZ=nz;boolean justCast=slamJustStarted;slamJustStarted=false;
        for(Iterator<DashGhost> it=dashGhosts.iterator();it.hasNext();){DashGhost ghost=it.next();ghost.life-=dt;if(ghost.life<=0||!ghost.source.alive())it.remove();}
        for(Iterator<Shot> it=shots.iterator();it.hasNext();){Shot shot=it.next();shot.life-=Math.max(0,dt-shot.source.hitStop);if(shot.life<=0)it.remove();}
        for(Iterator<Impact> it=impacts.iterator();it.hasNext();){Impact impact=it.next();impact.life-=dt;if(impact.life<=0)it.remove();}
        attackBuffer=Math.max(0,attackBuffer-dt);
        elapsed+=dt;noticeTime=Math.max(0,noticeTime-dt);
        for(int i=0;i<3;i++)cooldowns[i]=Math.max(0,cooldowns[i]-dt);
        for(Iterator<Effect> it=effects.iterator();it.hasNext();){Effect e=it.next();e.life-=dt;if(e.life<=0){
            if(e.type==2)area(e.team,e.x,e.z,e.radius,180+level*22);
            it.remove();
        }}
        if(screen!=Screen.PLAYING)return;
        waveTimer-=dt;if(waveTimer<=0){waveTimer+=14;wave++;for(int team=0;team<2;team++)for(int i=0;i<4;i++)spawn(Kind.SOLDIER,team,team==0?-34:34,(i-1.5)*1.5);}
        for(Unit u:units){
            double stopped=u.alive()?Math.min(dt,u.hitStop):0;u.hitStop=Math.max(0,u.hitStop-dt);u.hitPauseTotal+=stopped;
            double actionDt=dt-stopped;
            u.combatTime=Math.max(0,u.combatTime-actionDt);u.comboWindow=Math.max(0,u.comboWindow-actionDt);u.cooldown=Math.max(0,u.cooldown-dt);u.hitFlash=Math.max(0,u.hitFlash-dt);u.attackTime=Math.max(0,u.attackTime-actionDt);u.hurtTime=Math.max(0,u.hurtTime-actionDt);u.slamTime=Math.max(0,u.slamTime-actionDt);
            if(u.meteorTime>0){u.meteorTime=Math.max(0,u.meteorTime-actionDt);if(u.alive()&&u.meteorTime<=METEOR_CAST-METEOR_RELEASE)for(Meteor meteor:meteors)if(meteor.caster==u&&meteor.serial==u.meteorSerial&&!meteor.released())meteor.launchedAt=elapsed;}
            if(!u.alive()){
                if(u.kind==Kind.HERO){u.respawn-=dt;if(u.respawn<=0){u.combatTime=0;u.comboWindow=0;u.hp=u.maxHp;u.x=u.homeX;u.z=u.homeZ;u.cooldown=0;u.attackTime=0;u.deathAt=-1;u.hurtTime=u.hitStop=u.slamTime=u.dashTime=u.meteorTime=0;if(u.player){moving=false;selected=null;recall=0;}}}
                continue;
            }
            if(u.kind==Kind.HERO&&Math.hypot(u.x-u.homeX,u.z-u.homeZ)<8)u.hp=Math.min(u.maxHp,u.hp+u.maxHp*.13*dt);
            if(u.player){
                if(u.dashTime>0){
                    if(attack||attackRequested)attackBuffer=.14;attackRequested=false;
                    advanceDash(u,dt);continue;
                }
                double len=Math.hypot(dx,dz);
                if((u.retreatShot||u.scatterDraw)&&newMovement)cancelArrowWindup(u);
                if(len>0&&!u.retreatShot&&!u.scatterDraw){recall=0;moving=false;selected=null;if(((u.slamTime<=0||u.slamLanded)&&u.meteorTime<=METEOR_CAST-METEOR_RELEASE)||newMovement&&!justCast)move(u,u.x+dx/len*10,u.z+dz/len*10,dt,newMovement);}
                if(selected!=null&&!selected.alive()){selected=null;moving=false;}
                if(selected!=null){recall=0;if(u.distance(selected)>u.range)move(u,selected.x,selected.z,dt);else strike(u,selected);}
                else if(moving){move(u,destX,destZ,dt);if(Math.hypot(u.x-destX,u.z-destZ)<.15)moving=false;}
                if(u.meteorTime>0&&(attack||attackRequested)){attackBuffer=.14;attackRequested=false;}
                if(attack||attackRequested||attackBuffer>0){boolean explicitPress=attackRequested||attackBuffer>0;attackRequested=false;basicAttack(explicitPress);}
                if(recall>0){recall-=dt;if(recall<=0){u.x=u.homeX;u.z=u.homeZ;moving=false;selected=null;tell("已回到泉水，正在恢复生命");}}
            }else{
                Unit t=target(u);
                if(u.kind==Kind.HERO){
                    if(u.hp<u.maxHp*.24)u.retreating=true;
                    if(u.hp>=u.maxHp*.72)u.retreating=false;
                    if(u.retreating)move(u,u.homeX,u.homeZ,dt);
                    else if(!avoidDanger(u,dt)){
                        if(t!=null)strike(u,t);
                        else{Unit goal=nearest(u,17);double gx=goal==null?(u.team==0?30:-30):goal.x,gz=goal==null?0:goal.z;
                            if(safeAdvance(u,gx,gz,dt))move(u,gx,gz,dt);
                        }
                    }
                }else{if(t!=null)strike(u,t);else if(u.kind==Kind.SOLDIER){Unit goal=nearest(u,12);if(goal==null)move(u,u.team==0?38:-38,0,dt);else move(u,goal.x,goal.z,dt);}}
            }
            if(screen!=Screen.PLAYING)break;
        }
        if(screen!=Screen.PLAYING)return;
        advanceScatter(dt);
        if(screen!=Screen.PLAYING)return;
        advanceArrows(dt);
        if(screen!=Screen.PLAYING)return;
        for(Iterator<SwordStrike> it=swordStrikes.iterator();it.hasNext();){
            SwordStrike strike=it.next();Unit attacker=strike.attacker,target=strike.target;
            if(!attacker.alive()||attacker.attackSerial!=strike.serial){it.remove();continue;}
            strike.age=SWORD_ATTACK_SECONDS-attacker.attackTime;
            if(!strike.swung&&strike.age>=SWORD_ATTACK_SECONDS*SWORD_SWING_PHASE){
                strike.swung=true;if(attacker.player)sound("sword-swing");
            }
            if(strike.age>=SWORD_ATTACK_SECONDS*SWORD_HIT_PHASE){
                if(target!=null&&target.alive()&&target.team!=attacker.team&&!shielded(target)&&attacker.distance(target)<=attacker.range+.35){
                    double hitX=target.x-attacker.x,hitZ=target.z-attacker.z,d=Math.hypot(hitX,hitZ),yaw=Math.toRadians(attacker.attackFacing);
                    if(d<.001||(hitX*Math.sin(yaw)+hitZ*Math.cos(yaw))/d>=.5){
                        damage(target,strike.amount,attacker.team,attacker);
                        attacker.hitStop=Math.max(attacker.hitStop,.03);
                        if(attacker.player)sound(target.kind==Kind.TOWER||target.kind==Kind.CORE?"stone-hit":"sword-hit");
                    }
                }
                it.remove();
            }
            if(screen!=Screen.PLAYING)break;
        }
        // Resolve after movement/deaths, on the same action clock as the retimed skeleton.
        if(screen==Screen.PLAYING&&player.alive()&&player.slamTime>0&&!player.slamLanded&&slamAge(player)>=SLAM_CONTACT){
            player.slamLanded=true;
            effects.add(new Effect(player.x,player.z,SLAM_RADIUS,player.team,3,.48));
            sound("slam-impact");player.hitStop=Math.max(player.hitStop,.04);
            for(Unit target:units){
                if(target.team!=player.team&&target.alive()&&player.distance(target)<=SLAM_RADIUS)damage(target,player.slamDamage,player.team,player);
                if(screen!=Screen.PLAYING)break;
            }
        }
        for(Iterator<Meteor> it=meteors.iterator();it.hasNext();){
            Meteor meteor=it.next();
            if(!meteor.released()){
                if(!meteor.caster.alive()||meteor.caster.meteorTime<=0||meteor.caster.meteorSerial!=meteor.serial)it.remove();
                continue;
            }
            if(elapsed-meteor.launchedAt+1e-9<METEOR_FLIGHT)continue;
            it.remove();effects.add(new Effect(meteor.x,meteor.z,METEOR_RADIUS,meteor.team,4,.65));sound("meteor-impact");
            Unit center=new Unit(Kind.HERO,meteor.team,meteor.x,meteor.z);
            for(Unit target:units){
                if(target.team!=meteor.team&&target.alive()&&Math.hypot(target.x-meteor.x,target.z-meteor.z)<=METEOR_RADIUS)damage(target,meteor.damage,meteor.team,center);
                if(screen!=Screen.PLAYING)break;
            }
            if(screen!=Screen.PLAYING)break;
        }
        units.removeIf(u->u.kind==Kind.SOLDIER&&!u.alive()&&elapsed-u.deathAt>=2);
    }
    private boolean avoidDanger(Unit u,double dt){
        for(Effect e:effects)if(e.team!=u.team&&e.type==2&&e.life>.08&&Math.hypot(u.x-e.x,u.z-e.z)<e.radius+.8){evade(u,e.x,e.z,dt);return true;}
        for(Meteor m:meteors)if(m.team!=u.team&&Math.hypot(u.x-m.x,u.z-m.z)<METEOR_RADIUS+.8){evade(u,m.x,m.z,dt);return true;}
        return false;
    }
    private void evade(Unit u,double x,double z,double dt){double dx=u.x-x,dz=u.z-z;if(Math.hypot(dx,dz)<.01){dx=0;dz=u.z>=0?1:-1;}double length=Math.hypot(dx,dz);move(u,u.x+dx/length*10,u.z+dz/length*10,dt);}
    private boolean safeAdvance(Unit u,double x,double z,double dt){
        double dx=x-u.x,dz=z-u.z,length=Math.hypot(dx,dz);if(length<.001)return true;
        double nx=u.x+dx/length*u.speed*dt,nz=u.z+dz/length*u.speed*dt;
        for(Unit tower:units)if(tower.kind==Kind.TOWER&&tower.team!=u.team&&tower.alive()&&Math.hypot(nx-tower.x,nz-tower.z)<tower.range+1){
            boolean tank=false;for(Unit ally:units)if(ally.kind==Kind.SOLDIER&&ally.team==u.team&&ally.alive()&&ally.distance(tower)<tower.range){tank=true;break;}
            if(!tank)return false;
        }
        return true;
    }
    Unit nearest(Unit u,double radius){Unit result=null;double best=radius;for(Unit t:units)if(t.team!=u.team&&t.alive()&&!shielded(t)){double d=u.distance(t);if(d<best){best=d;result=t;}}return result;}
    Unit target(Unit u){
        Unit result=null;double score=Double.MAX_VALUE;
        for(Unit t:units)if(t.team!=u.team&&t.alive()&&!shielded(t)&&u.distance(t)<=u.range){
            double s=u.distance(t)+(u.kind==Kind.TOWER&&t.kind!=Kind.SOLDIER?100:0);
            if(s<score){score=s;result=t;}
        }return result;
    }
    static double visualFacing(Unit u){return u.dashTime>0?u.dashFacing:u.meteorTime>0?u.meteorFacing:u.slamTime>0?u.slamFacing:u.attackTime>0?u.attackFacing:u.facing;}
    void move(Unit u,double x,double z,double dt){move(u,x,z,dt,true);}
    void move(Unit u,double x,double z,double dt,boolean newIntent){double dx=x-u.x,dz=z-u.z,d=Math.hypot(dx,dz);if(d<.001||u.speed<=0)return;
        if(newIntent){u.meteorTime=0;u.comboWindow=0;u.slamTime=0;cancelSwordWindup(u);}
        if(u.player){
            // A fresh movement command takes precedence over visual recovery and queued taps.
            if(newIntent)attackBuffer=0;u.hitStop=0;u.hurtTime=0;
            // Held WASD exits recovery after contact, but never cancels its own windup.
            if(swordsman(u)&&u.slamLanded)u.slamTime=0;
            boolean swordRecovery=swordsman(u)&&u.attackTime>0
                &&swordStrikes.stream().noneMatch(strike->strike.attacker==u&&strike.serial==u.attackSerial);
            if((newIntent||swordRecovery)&&u.attackTime>0){u.attackTime=0;u.attackSerial++;shots.removeIf(shot->shot.source==u&&shot.melee);}
        }
        double step=Math.min(d,u.speed*dt);u.travel+=step;u.lastMovedAt=elapsed;u.x=clamp(u.x+dx/d*step,-LIMIT_X,LIMIT_X);u.z=clamp(u.z+dz/d*step,-LIMIT_Z,LIMIT_Z);u.facing=Math.toDegrees(Math.atan2(dx,dz));}
    void strike(Unit u,Unit target){if(u.cooldown>0||u.slamTime>0||u.meteorTime>0||!u.alive()||!target.alive())return;u.cooldown=u.kind==Kind.TOWER?1:0.72;u.facing=Math.toDegrees(Math.atan2(target.x-u.x,target.z-u.z));attackShot(u,target,target.x,target.z);if(swordsman(u))swordStrikes.add(new SwordStrike(u,target));else if(!archer(u))damage(target,u.attack,u.team,u);if(u.player)recall=0;}
    void damage(Unit u,double amount,int team){damage(u,amount,team,null);}
    void damage(Unit u,double amount,int team,Unit source){
        if(amount<=0||u.team==team||!u.alive()||shielded(u)||screen!=Screen.PLAYING)return;
        if(swordsman(u))u.combatTime=2.4;
        Impact impact=new Impact(u,Math.min(u.hp,amount),team,source);impacts.add(impact);u.hitFlash=.12;
        if(!impact.building){
            u.hurtTime=HURT_SECONDS;u.hitSerial++;u.hitDX=impact.dx;u.hitDZ=impact.dz;u.hitStop=Math.max(u.hitStop,.035);
        }
        u.hp=Math.max(0,u.hp-amount);if(u.player)recall=0;
        if(u.hp>0)return;
        u.deathAt=elapsed;u.retreatShot=u.scatterDraw=false;u.hitStop=u.hurtTime=u.slamTime=u.dashTime=u.meteorTime=0;
        if(team==0&&player.alive()&&player.distance(u)<15)sound("kill-hit");
        if(u.kind==Kind.HERO){u.respawn=7+Math.min(10,elapsed/90);if(u.player){deaths++;attackBuffer=0;attackRequested=false;moving=false;selected=null;}else kills++;}
        if(team==0&&player.alive()&&player.distance(u)<23){
            gold+=u.kind==Kind.HERO?180:u.kind==Kind.TOWER?220:u.kind==Kind.SOLDIER?28:0;
            xp+=u.kind==Kind.HERO?100:u.kind==Kind.TOWER?100:20;
            while(xp>=level*100&&level<15){xp-=level*100;level++;player.maxHp+=70;player.hp=Math.min(player.maxHp,player.hp+140);player.attack+=6;tell("等级提升！当前 Lv."+level);}
        }
        if(u.kind==Kind.TOWER)tell(u.team==1?"敌方防御塔已摧毁，水晶护盾解除！":"我方防御塔被摧毁，保护水晶！");
        if(u.kind==Kind.CORE){screen=u.team==1?Screen.WON:Screen.LOST;moving=false;recall=0;}
    }
    void area(int team,double x,double z,double radius,double amount){for(Unit u:units)if(u.team!=team&&Math.hypot(x-u.x,z-u.z)<=radius)damage(u,amount,team);}
    private void advanceDash(Unit u,double dt){
        if(archer(u)){advanceRetreat(u,dt);return;}
        double sx=u.x,sz=u.z;
        u.dashTime=Math.max(0,u.dashTime-dt);u.hitStop=u.hurtTime=0;
        double age=DASH_SECONDS-u.dashTime,t=clamp(age/DASH_MOVE,0,1),progress=1-(1-t)*(1-t);
        double x=u.dashStartX+(u.dashEndX-u.dashStartX)*progress,z=u.dashStartZ+(u.dashEndZ-u.dashStartZ)*progress;
        double vx=x-sx,vz=z-sz,length=vx*vx+vz*vz;
        if(length>1e-10){dashGhosts.add(new DashGhost(u));u.travel+=Math.sqrt(length);u.lastMovedAt=elapsed;}
        u.x=x;u.z=z;
        if(age<=DASH_MOVE+dt){
            for(Unit target:units){
                if(target.team==u.team||!target.alive()||shielded(target)||u.dashHits.contains(target))continue;
                double along=length<1e-10?0:clamp(((target.x-sx)*vx+(target.z-sz)*vz)/length,0,1);
                if(Math.hypot(target.x-sx-vx*along,target.z-sz-vz*along)<=DASH_RADIUS){
                    u.dashHits.add(target);damage(target,u.dashDamage,u.team,u);
                    if(screen!=Screen.PLAYING)break;
                }
            }
        }
    }
    private void advanceRetreat(Unit u,double dt){
        double sx=u.x,sz=u.z;u.dashTime=Math.max(0,u.dashTime-dt);u.hitStop=u.hurtTime=0;
        double t=clamp(1-u.dashTime/.24,0,1),progress=1-(1-t)*(1-t);
        u.x=u.dashStartX+(u.dashEndX-u.dashStartX)*progress;u.z=u.dashStartZ+(u.dashEndZ-u.dashStartZ)*progress;
        double distance=Math.hypot(u.x-sx,u.z-sz);
        if(distance>.00001){dashGhosts.add(new DashGhost(u));u.travel+=distance;u.lastMovedAt=elapsed;}
        if(u.dashTime<=0){
            double angle=Math.toRadians(u.dashFacing),dx=Math.sin(angle),dz=Math.cos(angle);
            Unit target=null;double best=14;
            for(Unit enemy:units)if(enemy.team!=u.team&&enemy.alive()&&!shielded(enemy)){
                double vx=enemy.x-u.x,vz=enemy.z-u.z,along=vx*dx+vz*dz;
                if(along>0&&along<best&&Math.abs(vx*dz-vz*dx)<=1.6){best=along;target=enemy;}
            }
            double tx=target==null?clamp(u.x+dx*14,-LIMIT_X,LIMIT_X):target.x;
            double tz=target==null?clamp(u.z+dz*14,-LIMIT_Z,LIMIT_Z):target.z;
            Shot shot=new Shot(u,tx,tz);arrows.add(new Arrow(shot,target,u.retreatDamage,14));
            u.retreatShot=true;u.cooldown=Math.max(u.cooldown,.72);
        }
    }
    boolean cast(int index){
        if(index<0||index>2||screen!=Screen.PLAYING||!player.alive()||cooldowns[index]>0)return false;
        if(swordsman(player))player.combatTime=2.4;
        cancelArrowWindup(player);player.comboWindow=0;cancelSwordWindup(player);player.dashTime=0;player.slamTime=0;player.meteorTime=0;attackBuffer=0;recall=0;cooldowns[index]=COOLDOWNS[index];
        double dx=aimX-player.x,dz=aimZ-player.z,d=Math.hypot(dx,dz);if(d<.01){dx=1;dz=0;d=1;}
        if(index==0&&swordsman(player)){
            player.attackTime=0;player.attackSerial++;shots.removeIf(shot->shot.source==player&&shot.melee);
            player.dashHits.clear();player.dashTime=DASH_SECONDS;player.dashSerial++;
            player.dashFacing=player.facing=Math.toDegrees(Math.atan2(dx,dz));
            player.dashStartX=player.x;player.dashStartZ=player.z;
            player.dashEndX=clamp(player.x+dx/d*7,-LIMIT_X,LIMIT_X);player.dashEndZ=clamp(player.z+dz/d*7,-LIMIT_Z,LIMIT_Z);
            player.dashDamage=110+level*12;player.hitStop=player.hurtTime=0;
            moving=false;selected=null;attackRequested=false;sound("dash-whoosh");
        }else if(index==0&&archer(player)){
            player.attackTime=0;player.attackSerial++;player.dashTime=.24;player.dashSerial++;
            player.dashFacing=player.facing=Math.toDegrees(Math.atan2(dx,dz));
            player.dashStartX=player.x;player.dashStartZ=player.z;
            player.dashEndX=clamp(player.x-dx/d*4,-LIMIT_X,LIMIT_X);player.dashEndZ=clamp(player.z-dz/d*4,-LIMIT_Z,LIMIT_Z);
            player.retreatDamage=110+level*12;player.hitStop=player.hurtTime=0;
            moving=false;selected=null;attackRequested=false;sound("dash-whoosh");
        }else if(index==0){
            double sx=player.x,sz=player.z;player.x=clamp(sx+dx/d*7,-LIMIT_X,LIMIT_X);player.z=clamp(sz+dz/d*7,-LIMIT_Z,LIMIT_Z);
            double vx=player.x-sx,vz=player.z-sz,length=vx*vx+vz*vz;
            for(Unit u:units)if(u.team==1){double t=length==0?0:clamp(((u.x-sx)*vx+(u.z-sz)*vz)/length,0,1);if(Math.hypot(u.x-sx-vx*t,u.z-sz-vz*t)<3)damage(u,110+level*12,0);}
            effects.add(new Effect(sx,sz,3,0,0,.25));effects.add(new Effect(player.x,player.z,3,0,0,.4));sound("dash-whoosh");moving=false;selected=null;
        }else if(index==1){
            if(swordsman(player)){
                player.attackTime=0;player.attackSerial++;shots.removeIf(shot->shot.source==player&&shot.melee);
                player.slamTime=SLAM_SECONDS;slamJustStarted=true;player.slamSerial++;player.slamLanded=false;player.slamDamage=135+level*15;
                player.slamFacing=player.facing=Math.toDegrees(Math.atan2(dx,dz));
                moving=false;selected=null;attackRequested=false;sound("slam-charge");
            }else if(archer(player)){
                double tx=player.x+dx/d*SCATTER_RANGE,tz=player.z+dz/d*SCATTER_RANGE;
                new Shot(player,tx,tz);player.facing=player.attackFacing;
                player.scatterDraw=true;scatterCasts.add(new ScatterCast(player,135+level*15));
                player.cooldown=Math.max(player.cooldown,.72);player.hitStop=player.hurtTime=0;
                moving=false;selected=null;attackRequested=false;
            }else{new Shot(player,player.x+dx,player.z+dz);area(0,player.x,player.z,6,135+level*15);effects.add(new Effect(player.x,player.z,6,0,1,.5));sound("meteor-impact");}
        }
        else if(swordsman(player)){
            player.attackTime=0;player.attackSerial++;shots.removeIf(shot->shot.source==player&&shot.melee);
            player.meteorTime=METEOR_CAST;player.meteorSerial++;slamJustStarted=true;
            player.meteorFacing=player.facing=Math.toDegrees(Math.atan2(dx,dz));
            double range=Math.min(14,d);
            meteors.add(new Meteor(player,clamp(player.x+dx/d*range,-LIMIT_X,LIMIT_X),clamp(player.z+dz/d*range,-LIMIT_Z,LIMIT_Z),180+level*22));
            player.hitStop=player.hurtTime=0;moving=false;selected=null;attackRequested=false;sound("meteor-charge");
        }else{if(hero==2)new Shot(player,aimX,aimZ);double range=Math.min(14,d);effects.add(new Effect(player.x+dx/d*range,player.z+dz/d*range,5,0,2,1.1));sound("meteor-charge");}
        return true;
    }
    void recall(){if(screen==Screen.PLAYING&&player.alive()){cancelArrowWindup(player);player.comboWindow=0;player.dashTime=0;player.slamTime=0;player.meteorTime=0;attackBuffer=0;recall=4;moving=false;selected=null;tell("回城引导中，移动或受伤将打断");}}
    boolean buy(int item){
        if(screen!=Screen.PLAYING||!player.alive())return false;
        if(Math.hypot(player.x-player.homeX,player.z-player.homeZ)>8){tell("请回到泉水购买装备");return false;}
        int cost=item==0?100+weapon*60:100+armor*60;
        if(gold<cost){tell("金币不足，需要 "+cost);return false;}gold-=cost;
        if(item==0){weapon++;player.attack+=18;}else{armor++;player.maxHp+=180;player.hp+=180;}tell(item==0?"已购买锋刃：攻击 +18":"已购买护甲：生命 +180");return true;
    }
}
