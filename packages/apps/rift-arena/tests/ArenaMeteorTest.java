package com.packages.riftarena;
/** R release/impact are separate: cancellation before launch, independent flight after launch. */
public final class ArenaMeteorTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static ArenaWorld fresh(){ArenaWorld w=ArenaSwordTest.fresh();ArenaWorld.Unit t=ArenaSwordTest.dummy(w);t.hp=t.maxHp=2000;t.range=0;w.aimX=3;w.aimZ=0;return w;}
 static void tick(ArenaWorld w,int count){ArenaSwordTest.advance(w,count);}
 public static void main(String[] args){
  ArenaWorld w=fresh();ArenaWorld.Unit t=ArenaSwordTest.dummy(w);check(w.cast(2),"R starts");
  check(w.meteors.size()==1&&w.player.meteorTime>0&&w.soundEvents.contains("meteor-charge"),"Immediate cast pose, warning and charge audio");
  check(!w.cast(2)&&w.cooldowns[2]==24,"Cooldown prevents duplicate R");
  tick(w,20);check(!w.meteors.get(0).released()&&t.hp==2000,"No early release or damage");
  tick(w,6);check(w.meteors.get(0).released(),"Meteor releases after windup");
  w.aimX=-14;w.aimZ=12;check(w.meteors.get(0).x==3&&w.meteors.get(0).z==0,"Aim changes cannot drag warning");
  tick(w,60);check(t.hp==2000,"Damage waits until actual landing");tick(w,8);
  check(t.hp==1798&&w.meteors.isEmpty()&&w.effects.stream().anyMatch(e->e.type==4)&&w.soundEvents.contains("meteor-impact"),"One landing event synchronizes damage, wave and audio");
  tick(w,40);check(t.hp==1798,"No repeated impact damage");
  w=fresh();w.cast(2);tick(w,10);w.update(.01,-1,0,false);tick(w,100);
  check(w.meteors.isEmpty()&&ArenaSwordTest.dummy(w).hp==2000&&!w.soundEvents.contains("meteor-impact"),"Fresh movement cancels unlaunched meteor");
  w=fresh();w.cast(2);tick(w,26);w.update(.01,-1,0,false);tick(w,75);
  check(ArenaSwordTest.dummy(w).hp==1798,"Moving after release leaves flight intact");
  for(boolean released:new boolean[]{false,true}){
   w=fresh();w.cast(2);tick(w,released?26:10);w.damage(w.player,10000,1);tick(w,100);
   check(ArenaSwordTest.dummy(w).hp==(released?1798:2000),"Death respects release boundary");
  }
  w=fresh();w.cast(2);tick(w,12);double cast=w.player.meteorTime,time=w.elapsed;w.pause();w.update(.2,0,0,false);
  check(w.player.meteorTime==cast&&w.elapsed==time,"Pause freezes cast and warning");w.resume();tick(w,15);
  double launched=w.meteors.get(0).launchedAt;w.pause();w.update(.2,0,0,false);w.resume();tick(w,70);
  check(launched>=0&&ArenaSwordTest.dummy(w).hp==1798,"Flight survives pause with one impact");
  for(boolean released:new boolean[]{false,true}){
   w=fresh();w.cast(2);tick(w,released?26:10);w.aimX=-10;w.cast(0);tick(w,100);
   check(ArenaSwordTest.dummy(w).hp==(released?1798:2000),"Another skill respects committed meteor");
  }
  w=fresh();w.cast(2);tick(w,10);w.recall();tick(w,100);check(ArenaSwordTest.dummy(w).hp==2000,"Recall cancels windup");
  w=fresh();w.cast(2);tick(w,32);w.requestAttack();tick(w,12);
  check(w.player.attackTime>0,"Late attack tap chains after short recovery");
  w=fresh();w.update(.01,1,0,false);double x=w.player.x;w.cast(2);for(int i=0;i<20;i++)w.update(.01,1,0,false);
  check(w.player.x==x,"Held movement does not cancel cast at startup");for(int i=0;i<10;i++)w.update(.01,1,0,false);
  check(w.player.x>x&&w.meteors.get(0).released(),"Movement resumes as soon as meteor launches");
  w=fresh();t=ArenaSwordTest.dummy(w);t.x=8.01;ArenaWorld.Unit ally=w.spawn(ArenaWorld.Kind.SOLDIER,0,3,0);ally.range=ally.speed=ally.attack=0;
  ArenaWorld.Unit core=w.spawn(ArenaWorld.Kind.CORE,1,3,0);core.attack=0;w.spawn(ArenaWorld.Kind.TOWER,1,35,0).attack=0;w.cast(2);tick(w,95);
  check(t.hp==2000&&ally.hp==ally.maxHp&&core.hp==core.maxHp,"Radius, allies and protected core respected");
  w=fresh();w.player.x=42;w.aimX=100;w.aimZ=100;w.cast(2);ArenaWorld.Meteor m=w.meteors.get(0);
  check(m.x<=43&&m.z<=18&&Math.hypot(m.x-42,m.z)<=14.001,"Landing clamped to range and arena");
  w.reset(0);check(w.meteors.isEmpty()&&w.player.meteorTime==0,"Restart clears cast and flight");
  w=fresh();w.configure(w.player,1);w.cast(2);check(w.meteors.isEmpty()&&w.effects.get(0).type==2,"Other heroes retain existing R");
  System.out.println("PASS: meteor windup/release/impact, fixed aim, single area damage, radius/team/shield filtering, cancellation, death, pause, movement/attack chaining and restart");
 }
}
