package com.packages.windward;

/** One authored climbable ledge, shared by collision, camera and drawing. */
final class MountainTraversal {
    static final double LEFT=-10,RIGHT=-4,BACK=-17,FRONT=-11;
    static final double TOP=MountainWorld.ground(-7,-14)+5;
    static boolean inside(double x,double z,double margin){return x>LEFT-margin&&x<RIGHT+margin&&z>BACK-margin&&z<FRONT+margin;}
    static double floor(double x,double z){return inside(x,z,0)?Math.max(TOP,MountainWorld.ground(x,z)):MountainWorld.ground(x,z);}
    static boolean nearFace(MountainWorld w){return w.x>LEFT+.5&&w.x<RIGHT-.5&&Math.abs(w.z-(FRONT+MountainWorld.RADIUS+.04))<.9&&w.y<TOP-.2;}
}
