package com.packages.riftarena;

import java.util.*;

/** Interpolate the last fixed step for rendering; restore simulation state before continuing. */
final class ArenaPresentation {
    private static final class Motion {
        double x,z,travel,currentX,currentZ,currentTravel;
        boolean alive,applied;
    }
    private final IdentityHashMap<ArenaWorld.Unit,Motion> motion=new IdentityHashMap<>();
    private int generation=-1;
    private double time=Double.NaN;
    void capture(ArenaWorld world){
        if(generation!=world.generation){motion.clear();generation=world.generation;}
        for(Motion state:motion.values())state.alive=false;
        for(ArenaWorld.Unit unit:world.units){
            Motion state=motion.get(unit);if(state==null){state=new Motion();motion.put(unit,state);}
            state.x=unit.x;state.z=unit.z;state.travel=unit.travel;state.alive=unit.alive();
        }
        motion.entrySet().removeIf(entry->!entry.getValue().alive);
        time=world.elapsed;
    }
    void apply(ArenaWorld world,double alpha){
        if(world.screen!=ArenaWorld.Screen.PLAYING||generation!=world.generation||Math.abs(world.elapsed-time-ArenaClock.STEP)>1e-8)return;
        alpha=ArenaWorld.clamp(alpha,0,1);
        for(Map.Entry<ArenaWorld.Unit,Motion> entry:motion.entrySet()){
            ArenaWorld.Unit unit=entry.getKey();Motion state=entry.getValue();
            // Death, respawn, recall and blink must snap rather than slide across the map.
            double dx=unit.x-state.x,dz=unit.z-state.z;
            if(!unit.alive()||dx*dx+dz*dz>4||unit.travel<state.travel)continue;
            state.currentX=unit.x;state.currentZ=unit.z;state.currentTravel=unit.travel;state.applied=true;
            unit.x=state.x+dx*alpha;unit.z=state.z+dz*alpha;
            unit.travel=state.travel+(state.currentTravel-state.travel)*alpha;
        }
    }
    void restore(){
        for(Map.Entry<ArenaWorld.Unit,Motion> entry:motion.entrySet()){
            Motion state=entry.getValue();if(!state.applied)continue;
            ArenaWorld.Unit unit=entry.getKey();unit.x=state.currentX;unit.z=state.currentZ;unit.travel=state.currentTravel;state.applied=false;
        }
    }
}
