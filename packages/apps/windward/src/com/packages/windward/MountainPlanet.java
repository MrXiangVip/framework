package com.packages.windward;

/** A finite spherical world. Chart x/z are longitude/latitude times radius, y is radial altitude. */
final class MountainPlanet {
    static final double RADIUS=1000, CIRCUMFERENCE=2*Math.PI*RADIUS;
    static final double WATER=-2;
    private static final Vec CAMP_LAKE=normal(150,-130);
    static final class Vec {
        final double x,y,z;
        Vec(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
        Vec add(Vec b){return new Vec(x+b.x,y+b.y,z+b.z);}
        Vec sub(Vec b){return new Vec(x-b.x,y-b.y,z-b.z);}
        Vec mul(double s){return new Vec(x*s,y*s,z*s);}
        double dot(Vec b){return x*b.x+y*b.y+z*b.z;}
        Vec cross(Vec b){return new Vec(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x);}
        double length(){return Math.sqrt(dot(this));}
        Vec unit(){return mul(1/length());}
    }
    static final class Frame {
        final Vec east,up,south,origin;
        Frame(double x,double z){
            double lon=x/RADIUS,lat=z/RADIUS,sl=Math.sin(lon),cl=Math.cos(lon),s=Math.sin(lat),c=Math.cos(lat);
            east=new Vec(cl,-sl,0);up=new Vec(c*sl,c*cl,s);south=new Vec(-s*sl,-s*cl,c);origin=up.mul(RADIUS);
        }
        Vec point(double x,double altitude,double z){return origin.add(east.mul(x)).add(up.mul(altitude)).add(south.mul(z));}
        double[] inverse(){return new double[]{east.x,up.x,south.x,0,east.y,up.y,south.y,0,east.z,up.z,south.z,0,-east.dot(origin),-up.dot(origin),-south.dot(origin),1};}
        // Transforms legacy local-area geometry at chart coordinates into an upright spherical object.
        double[] surface(double x,double z){
            Vec t=origin.sub(east.mul(x)).sub(south.mul(z));
            return new double[]{east.x,east.y,east.z,0,up.x,up.y,up.z,0,south.x,south.y,south.z,0,t.x,t.y,t.z,1};
        }
    }
    static Frame frame(double x,double z){return new Frame(x,z);}
    static Vec normal(double x,double z){return frame(x,z).up;}
    static Vec position(double x,double altitude,double z){return normal(x,z).mul(RADIUS+altitude);}
    static double longitude(Vec p){return Math.atan2(p.x,p.y)*RADIUS;}
    static double latitude(Vec p){return Math.atan2(p.z,Math.hypot(p.x,p.y))*RADIUS;}
    static double wrap(double x){return x-CIRCUMFERENCE*Math.floor((x+CIRCUMFERENCE/2)/CIRCUMFERENCE);}
    static double distance(double ax,double az,double bx,double bz){
        // atan2 remains accurate both for millimetre steps and near-antipodal points.
        Vec a=normal(ax,az),b=normal(bx,bz);
        return RADIUS*Math.atan2(a.cross(b).length(),a.dot(b));
    }
    static final class Move {
        final double x,z,turn;
        Move(double x,double z,double turn){this.x=x;this.z=z;this.turn=turn;}
    }
    /** Exponential-map step, including parallel transport of the local heading across poles/seams. */
    static Move advance(double x,double z,double east,double south){
        double length=Math.hypot(east,south);
        if(length<1e-14)return new Move(wrap(x),z,0);
        Frame old=frame(x,z);Vec tangent=old.east.mul(east/length).add(old.south.mul(south/length));
        double angle=length/RADIUS,c=Math.cos(angle),s=Math.sin(angle);
        Vec next=old.up.mul(c).add(tangent.mul(s)).unit();
        double nx=longitude(next),nz=latitude(next);
        Vec axis=old.up.cross(tangent);
        Vec transported=old.south.mul(c).add(axis.cross(old.south).mul(s)).add(axis.mul(axis.dot(old.south)*(1-c)));
        Frame f=frame(nx,nz);
        return new Move(nx,nz,Math.atan2(transported.dot(f.east),transported.dot(f.south)));
    }
    static double height(Vec n){
        double x=longitude(n),z=latitude(n),d=Math.hypot(x,z);
        double authored=.9*Math.sin(x*.075)*Math.cos(z*.065)+9*Math.exp(-((x-15)*(x-15)+(z+25)*(z+25))/290);
        // Noise uses the 3D unit direction, so both longitude seams and poles share the same values.
        double continental=Math.sin(n.x*8+n.y*3)*Math.cos(n.z*7-n.y*2);
        double ridge=Math.max(0,Math.sin(n.x*17-n.z*11+n.y*5));
        double terrain=9+18*continental+25*ridge*ridge
                +3*Math.sin(n.x*43-n.z*29+n.y*11)+.7*Math.cos(n.x*93+n.y*71+n.z*57);
        // A reachable lake beyond the first valley, plus low continental basins worldwide.
        double lakeDistance=n.sub(CAMP_LAKE).length()*RADIUS/95;
        terrain-=42*Math.exp(-Math.pow(lakeDistance,4));
        double blend=MountainCamera.clamp((d-60)/50,0,1);blend=blend*blend*(3-2*blend);
        return authored*(1-blend)+terrain*blend;
    }
    static double moisture(Vec n){return .5+.5*Math.sin(n.x*15+n.z*11+n.y*4);}
    static String region(double x,double z){
        double h=PlanetTerrain.ground(x,z);
        if(h<WATER-.9)return "碧水湖域";
        if(h<WATER+2.5)return "浅滩沙岸";
        if(Math.hypot(x,z)<65)return "风塔山谷";
        if(Math.abs(normal(x,z).z)>.82)return "霜色高地";
        if(h>27)return "风蚀岩岭";
        return moisture(normal(x,z))>.55?"白桦林地":"晴风草甸";
    }
}
