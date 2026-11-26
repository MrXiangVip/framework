package com.packages.windward;

import java.util.*;

/** Sphere topology, chart seams, metric movement, terrain continuity and bounded streaming selection. */
public final class PlanetTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void near(double a,double b,double eps,String message){check(Math.abs(a-b)<=eps,message+": "+a+" / "+b);}
    static void topology(){
        double r=MountainPlanet.RADIUS,x=0,z=0,heading=Math.PI/2;
        int steps=12000;double length=MountainPlanet.CIRCUMFERENCE/steps;
        for(int i=0;i<steps;i++){
            MountainPlanet.Move next=MountainPlanet.advance(x,z,Math.sin(heading)*length,Math.cos(heading)*length);
            heading+=next.turn;x=next.x;z=next.z;
        }
        near(MountainPlanet.distance(0,0,x,z),0,1e-6,"Full equatorial circuit returns to its start");
        // A meridian crosses both poles; parallel-transported headings prevent reflecting back.
        x=0;z=0;heading=0;
        for(int i=0;i<steps;i++){
            MountainPlanet.Move next=MountainPlanet.advance(x,z,Math.sin(heading)*length,Math.cos(heading)*length);
            heading+=next.turn;x=next.x;z=next.z;
            check(Double.isFinite(x)&&Double.isFinite(z)&&Double.isFinite(heading),"Pole crossing stays finite");
        }
        near(MountainPlanet.distance(0,0,x,z),0,1e-5,"Full meridian circuit crosses poles and returns");
        for(double latitude:new double[]{0,.8,Math.PI/2-1e-7,-Math.PI/2+1e-7}){
            MountainPlanet.Move p=MountainPlanet.advance(3.14*r,latitude*r,7,0);
            near(MountainPlanet.distance(3.14*r,latitude*r,p.x,p.z),7,1e-7,"East step retains metric speed at all latitudes");
            MountainPlanet.Frame f=MountainPlanet.frame(p.x,p.z);
            near(f.east.dot(f.up),0,1e-12,"Local east perpendicular to gravity");
            near(f.east.cross(f.up).dot(f.south),1,1e-12,"Local frame is right-handed");
            near(MountainPlanet.position(p.x,12,p.z).length(),r+12,1e-9,"Altitude is radial");
        }
    }
    static void terrain(){
        // Each face boundary is sampled from identical 3D directions, including poles and dateline.
        for(int face=0;face<6;face++)for(int side=0;side<4;side++)for(int i=0;i<=16;i++){
            double t=-1+i/8.,u=side<2?(side==0?-1:1):t,v=side<2?t:(side==2?-1:1);
            MountainPlanet.Vec n=PlanetTerrain.direction(face,u,v);PlanetTerrain.Address a=new PlanetTerrain.Address(n);
            MountainPlanet.Vec p=PlanetTerrain.vertex(face,u,v),q=PlanetTerrain.vertex(a.face,a.u,a.v);
            near(p.sub(q).length(),0,1e-8,"Cube face edge vertices agree");
            near(MountainWorld.ground(MountainPlanet.longitude(n),MountainPlanet.latitude(n)),p.length()-MountainPlanet.RADIUS,1e-7,"Collision agrees at shared grid vertices");
        }
        near(MountainWorld.ground(Math.PI*MountainPlanet.RADIUS,50),MountainWorld.ground(-Math.PI*MountainPlanet.RADIUS,50),1e-8,"Dateline terrain closes");
        Random random=new Random(574);
        for(int i=0;i<100;i++){
            int face=random.nextInt(6),gx=random.nextInt(PlanetTerrain.GRID),gz=random.nextInt(PlanetTerrain.GRID);
            double step=2.0/PlanetTerrain.GRID,u=-1+gx*step,v=-1+gz*step;
            MountainPlanet.Vec a=PlanetTerrain.vertex(face,u,v),b=PlanetTerrain.vertex(face,u+step,v),c=PlanetTerrain.vertex(face,u,v+step);
            MountainPlanet.Vec p=a.mul(.2).add(b.mul(.3)).add(c.mul(.5));
            near(MountainWorld.ground(MountainPlanet.longitude(p),MountainPlanet.latitude(p)),p.length()-MountainPlanet.RADIUS,1e-7,"Collision is exact radial intersection with rendered triangle");
        }
        PlanetTerrain terrain=new PlanetTerrain();int largest=0;
        for(int i=0;i<80;i++){
            double x=(random.nextDouble()*2-1)*Math.PI*MountainPlanet.RADIUS,z=(random.nextDouble()-0.5)*Math.PI*MountainPlanet.RADIUS;
            Set<PlanetTerrain.Tile> selected=PlanetTerrain.select(x,z);largest=Math.max(largest,selected.size());
            PlanetTerrain.Tile leaf=PlanetTerrain.leaf(x,z);
            check(selected.contains(leaf),"Player collision tile always finest LOD");
            // Disjoint quadtree leaves cover each complete face without holes or overlap.
            for(int face=0;face<6;face++){
                double area=0;for(PlanetTerrain.Tile t:selected)if(t.face==face)area+=t.size()*t.size();
                near(area,4,1e-12,"LOD tiles cover face exactly");
            }
            for(PlanetTerrain.Tile t:selected)if(t.level==PlanetTerrain.DEPTH)terrain.props(t);
            terrain.nearby(x,z);check(terrain.cachedPropTiles()<=128,"Prop cache evicts distant entries");
        }
        check(largest<700,"LOD selection bounded: "+largest);
        PlanetTerrain.Tile tile=PlanetTerrain.leaf(1600,700);
        List<PlanetTerrain.Prop> first=terrain.props(tile);
        for(int i=0;i<160;i++)terrain.props(new PlanetTerrain.Tile(i%6,PlanetTerrain.DEPTH,i%128,(i*7)%128));
        List<PlanetTerrain.Prop> again=terrain.props(tile);
        check(first.size()==again.size(),"Regenerated prop count stable");
        for(int i=0;i<first.size();i++)near(first.get(i).x,again.get(i).x,0,"Regenerated props retain locations");
        System.out.println("Planet patches max="+largest+", prop cache="+terrain.cachedPropTiles());
    }
    static void controller(){
        MountainWorld w=new MountainWorld();MountainCamera camera=new MountainCamera();
        w.x=0;w.z=-Math.PI*MountainPlanet.RADIUS/2+.02;w.y=w.floor();
        double startX=w.x,startZ=w.z;
        w.step(0,1,false,camera.yaw);camera.transport(w);
        check(Math.abs(w.x)>3000,"Controller crosses pole instead of hitting a boundary");
        near(MountainPlanet.distance(startX,startZ,w.x,w.z),4.5*MountainWorld.STEP,1e-7,"Controller pole-crossing speed");
        camera.update(w);
        MountainPlanet.Frame f=MountainPlanet.frame(w.x,w.z);
        MountainPlanet.Vec eye=f.point(camera.eyeX-w.x,camera.eyeY,camera.eyeZ-w.z);
        check(eye.length()>MountainPlanet.RADIUS+MountainWorld.ground(MountainPlanet.longitude(eye),MountainPlanet.latitude(eye)),"Polar camera remains above planet");
        w.jump();double y=w.y;for(int i=0;i<20;i++)w.step(0,0,false,0);check(w.y>y&&!w.grounded,"Polar jump rises radially against gravity");
        for(int i=0;i<150;i++)w.step(0,0,false,0);near(w.y,w.floor(),1e-8,"Polar gravity lands on spherical terrain");
        w.reset();camera.reset();camera.globe=true;camera.update(w);
        near(MountainPlanet.frame(w.x,w.z).point(camera.eyeX-w.x,camera.eyeY,camera.eyeZ-w.z).length(),3000,1e-8,"Overview camera orbits sphere centre");
        camera.globe=false;camera.update(w);check(camera.eyeY<20,"Return to normal surface camera");
    }
    public static void main(String[] args){topology();terrain();controller();System.out.println("PASS: spherical circuits, poles, radial gravity, collision seams and bounded deterministic terrain");}
}
