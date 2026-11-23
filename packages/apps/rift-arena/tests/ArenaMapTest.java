package com.packages.riftarena;

/** Real lane routing and map boundaries, independent of rendering and wall-clock timing. */
public final class ArenaMapTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        ArenaWorld world=new ArenaWorld();world.reset(0);
        check(world.units.stream().filter(u->u.kind==ArenaWorld.Kind.TOWER).count()==6,"Three towers per team");
        for(int lane=-1;lane<=1;lane++)for(int team=0;team<2;team++){
            boolean found=false;
            for(ArenaWorld.Unit u:world.units)if(u.kind==ArenaWorld.Kind.TOWER&&u.team==team&&u.lane==lane){
                check(u.z==lane*ArenaWorld.LANE_Z&&Math.abs(u.x)==ArenaWorld.TOWER_X,"Tower on its own lane");found=true;
            }
            check(found,"Both teams defend every lane");
        }
        for(int i=0;i<31;i++)world.update(.1,0,0,false);
        for(int lane=-1;lane<=1;lane++)for(int team=0;team<2;team++){
            int count=0;
            for(ArenaWorld.Unit u:world.units)if(u.kind==ArenaWorld.Kind.SOLDIER&&u.team==team&&u.lane==lane)count++;
            check(count==4,"Each team sends four minions down every lane");
        }
        for(int lane=-1;lane<=1;lane++)for(int team=0;team<2;team++){
            ArenaWorld w=new ArenaWorld();w.reset(0);w.units.clear();w.waveTimer=1000;
            ArenaWorld.Unit soldier=w.spawn(ArenaWorld.Kind.SOLDIER,team,team==0?-ArenaWorld.WAVE_X:ArenaWorld.WAVE_X,0);
            soldier.lane=lane;boolean crossed=false;
            for(int tick=0;tick<700;tick++){
                w.update(.1,0,0,false);
                if(Math.abs(soldier.x)<1){
                    check(Math.abs(soldier.z-lane*ArenaWorld.LANE_Z)<.2,"Minion crosses its own river bridge");crossed=true;
                }
            }
            check(crossed,"Every lane reaches the river");
            check(Math.hypot(soldier.x-(team==0?ArenaWorld.CORE_X:-ArenaWorld.CORE_X),soldier.z)<.01,"Lane merges at the enemy core, beyond the old map");
        }
        world.reset(0);world.waveTimer=1000;world.player.x=45;world.player.z=30;
        for(int i=0;i<10;i++)world.update(.1,1,0,false);
        check(world.player.x>52&&world.player.z==30,"Expanded flanks are actually traversable");
        ArenaWorld.Unit core=null,tower=null;
        for(ArenaWorld.Unit u:world.units){if(u.kind==ArenaWorld.Kind.CORE&&u.team==1)core=u;if(u.kind==ArenaWorld.Kind.TOWER&&u.team==1&&u.lane==-1)tower=u;}
        check(world.shielded(core),"Unbroken lanes shield the core");world.damage(tower,10000,0);
        check(!world.shielded(core),"Breaking a side lane opens the core while the other lanes remain defended");
        System.out.println("PASS: expanded movement, three-lane waves, symmetric towers, six full minion routes and side-lane breakthrough");
    }
}
