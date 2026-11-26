package com.packages.windward;

import java.util.*;

/** Six cube-sphere faces, distance-selected quadtree patches, deterministic bounded prop cache. */
final class PlanetTerrain {
    static final int DEPTH=7, SEGMENTS=8, GRID=(1<<DEPTH)*SEGMENTS;
    static final class Tile {
        final int face,level,x,z;
        Tile(int face,int level,int x,int z){this.face=face;this.level=level;this.x=x;this.z=z;}
        double size(){return 2.0/(1<<level);}
        double u(){return -1+x*size();} double v(){return -1+z*size();}
        MountainPlanet.Vec center(){return vertex(face,u()+size()/2,v()+size()/2);}
        double radius(){
            MountainPlanet.Vec c=center();double radius=0;
            for(int i=0;i<2;i++)for(int j=0;j<2;j++)radius=Math.max(radius,vertex(face,u()+i*size(),v()+j*size()).sub(c).length());
            return radius+skirt()+2;
        }
        double skirt(){double step=MountainPlanet.RADIUS*size()/SEGMENTS;return 1+step*step/MountainPlanet.RADIUS;}
        @Override public boolean equals(Object o){if(!(o instanceof Tile))return false;Tile b=(Tile)o;return face==b.face&&level==b.level&&x==b.x&&z==b.z;}
        @Override public int hashCode(){return ((face*31+level)*257+x)*257+z;}
    }
    static MountainPlanet.Vec direction(int face,double u,double v){
        switch(face){
            case 0:return new MountainPlanet.Vec(u,1,v).unit();
            case 1:return new MountainPlanet.Vec(u,-1,v).unit();
            case 2:return new MountainPlanet.Vec(1,u,v).unit();
            case 3:return new MountainPlanet.Vec(-1,u,v).unit();
            case 4:return new MountainPlanet.Vec(u,v,1).unit();
            case 5:return new MountainPlanet.Vec(u,v,-1).unit();
            default:throw new IllegalArgumentException("Cube face");
        }
    }
    static MountainPlanet.Vec vertex(int face,double u,double v){
        MountainPlanet.Vec n=direction(face,u,v);return n.mul(MountainPlanet.RADIUS+MountainPlanet.height(n));
    }
    static final class Address {
        final int face;final double u,v;
        Address(MountainPlanet.Vec n){
            double ax=Math.abs(n.x),ay=Math.abs(n.y),az=Math.abs(n.z);
            if(ay>=ax&&ay>=az){face=n.y>=0?0:1;u=n.x/ay;v=n.z/ay;}
            else if(ax>=az){face=n.x>=0?2:3;u=n.y/ax;v=n.z/ax;}
            else{face=n.z>=0?4:5;u=n.x/az;v=n.y/az;}
        }
        Tile leaf(){int n=1<<DEPTH;return new Tile(face,DEPTH,index(u,n),index(v,n));}
    }
    private static int index(double u,int n){return Math.max(0,Math.min(n-1,(int)Math.floor((u+1)*n/2)));}
    static Tile leaf(double x,double z){return new Address(MountainPlanet.normal(x,z)).leaf();}
    /** Radial intersection with the same finest-grid triangle used by the rendered near terrain. */
    static double ground(double x,double z){
        MountainPlanet.Vec ray=MountainPlanet.normal(x,z);Address a=new Address(ray);
        int ix=index(a.u,GRID),iz=index(a.v,GRID);double step=2.0/GRID,u=-1+ix*step,v=-1+iz*step;
        double fx=(a.u-u)/step,fz=(a.v-v)/step;
        MountainPlanet.Vec p,q,r;
        if(fx+fz<=1){p=vertex(a.face,u,v);q=vertex(a.face,u+step,v);r=vertex(a.face,u,v+step);}
        else{p=vertex(a.face,u+step,v+step);q=vertex(a.face,u,v+step);r=vertex(a.face,u+step,v);}
        MountainPlanet.Vec normal=q.sub(p).cross(r.sub(p));
        return normal.dot(p)/normal.dot(ray)-MountainPlanet.RADIUS;
    }
    static Set<Tile> select(double x,double z){
        Set<Tile> result=new LinkedHashSet<>();MountainPlanet.Vec player=MountainPlanet.position(x,0,z);
        for(int face=0;face<6;face++)select(new Tile(face,0,0,0),player,result);
        return result;
    }
    private static void select(Tile t,MountainPlanet.Vec player,Set<Tile> result){
        double width=MountainPlanet.RADIUS*t.size();
        if(t.level<DEPTH&&t.center().sub(player).length()<width*1.6+20){
            for(int z=0;z<2;z++)for(int x=0;x<2;x++)select(new Tile(t.face,t.level+1,t.x*2+x,t.z*2+z),player,result);
        }else result.add(t);
    }
    static final class Prop {
        final double x,z,r,height,angle;final String model;
        Prop(double x,double z,double r,double height,String model,double angle){
            this.x=x;this.z=z;this.r=r;this.height=height;this.model=model;this.angle=angle;
        }
    }
    private final Map<Tile,List<Prop>> props=new LinkedHashMap<Tile,List<Prop>>(128,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<Tile,List<Prop>> e){return size()>128;}
    };
    List<Prop> props(Tile tile){
        List<Prop> cached=props.get(tile);if(cached!=null)return cached;
        List<Prop> result=new ArrayList<>();
        long seed=0x57494e4457415244L+tile.face*73856093L+tile.x*19349663L+tile.z*83492791L;
        Random random=new Random(seed);
        // Vary both density and species by habitat, leaving meadows open and grouping woodland.
        int count=1+random.nextInt(4);
        for(int i=0;i<count;i++){
            MountainPlanet.Vec n=direction(tile.face,tile.u()+tile.size()*(.15+.7*random.nextDouble()),tile.v()+tile.size()*(.15+.7*random.nextDouble()));
            double x=MountainPlanet.longitude(n),z=MountainPlanet.latitude(n),h=ground(x,z),wet=MountainPlanet.moisture(n);
            if(MountainPlanet.distance(0,0,x,z)<75)continue;
            if(h<MountainPlanet.WATER+.35){
                if(h<MountainPlanet.WATER-.2&&h>MountainPlanet.WATER-2.5&&random.nextDouble()<.65)
                    result.add(new Prop(x,z,0,.15+random.nextDouble()*.12,"lilypad",random.nextDouble()*360));
                continue;
            }
            boolean shore=h<MountainPlanet.WATER+3,high=h>27||Math.abs(n.z)>.82;
            if(shore||high){
                if(high&&h<42&&wet>.45&&random.nextDouble()<.35)
                    result.add(new Prop(x,z,.5,6+random.nextDouble()*4,"pine",random.nextDouble()*360));
                else if(i==0||random.nextDouble()<.25)result.add(new Prop(x,z,.7+random.nextDouble(),.8+random.nextDouble()*2.5,random.nextBoolean()?"rock":"roundrock",random.nextDouble()*360));
            }else if(random.nextDouble()<wet*.85){
                result.add(new Prop(x,z,.45+random.nextDouble()*.2,4+random.nextDouble()*5,wet>.65?"birch":"tree",random.nextDouble()*360));
            }else{
                String model=wet>.7?"fern":wet>.45&&random.nextInt(5)==0?"mushrooms":random.nextBoolean()?"flowers":"grass";
                result.add(new Prop(x,z,0,model.equals("fern")?.65:model.equals("grass")?.45:model.equals("flowers")?.3:.35,model,random.nextDouble()*360));
            }
        }
        cached=Collections.unmodifiableList(result);props.put(tile,cached);return cached;
    }
    private final Map<Tile,List<Prop>> neighbours=new LinkedHashMap<Tile,List<Prop>>(128,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<Tile,List<Prop>> e){return size()>128;}
    };
    List<Prop> nearby(double x,double z){
        Tile leaf=leaf(x,z);List<Prop> cached=neighbours.get(leaf);if(cached!=null)return cached;
        double step=leaf.size(),u=leaf.u()+step/2,v=leaf.v()+step/2;
        Set<Tile> tiles=new LinkedHashSet<>();
        // Sampling beyond a face edge automatically addresses its neighbouring cube face.
        for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++)tiles.add(new Address(direction(leaf.face,u+dx*step,v+dz*step)).leaf());
        List<Prop> result=new ArrayList<>();for(Tile tile:tiles)result.addAll(props(tile));cached=Collections.unmodifiableList(result);neighbours.put(leaf,cached);return cached;
    }
    int cachedPropTiles(){return props.size();}
}
