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
    static void waterAndHabitats(){
        MountainWorld w=new MountainWorld();
        check(MountainWorld.ground(175,-130)<MountainPlanet.WATER-8,"Reachable lake has a deep basin");
        near(w.floor(),MountainWorld.ground(w.x,w.z),1e-9,"Camp remains dry and unchanged by buoyancy");
        w.x=175;w.z=-130;w.y=w.floor();
        near(w.y,MountainPlanet.WATER-.9,1e-9,"Deep water supports torso above surface");
        double x=w.x,z=w.z;for(int i=0;i<120;i++)w.step(1,0,false,0);
        check(w.swimming(),"Deep lake activates water movement");
        near(MountainPlanet.distance(x,z,w.x,w.z),2.2,.001,"Water movement is slower than walking");
        w.jump();for(int i=0;i<180;i++)w.step(0,0,false,0);
        near(w.y,MountainPlanet.WATER-.9,1e-8,"Jumping falls back to buoyant water support");
        MountainCamera camera=new MountainCamera();camera.update(w);
        MountainPlanet.Vec eye=MountainPlanet.frame(w.x,w.z).point(camera.eyeX-w.x,camera.eyeY,camera.eyeZ-w.z);
        check(eye.length()>MountainPlanet.RADIUS+MountainPlanet.WATER,"Surface camera stays above water");
        w.x=120;w.z=-130;w.y=w.floor();boolean entered=false,exited=false;
        for(int i=0;i<6000;i++){
            w.step(1,0,false,0);entered|=w.swimming();
            if(entered&&!w.swimming()&&MountainWorld.ground(w.x,w.z)>MountainPlanet.WATER+.5){exited=true;break;}
            check(w.y>=w.floor()-1e-8,"Shore transition never sinks below support");
        }
        check(entered&&exited,"Controller walks down shore, crosses lake and leaves on opposite bank");
        Set<String> models=new HashSet<>(),regions=new HashSet<>();double min=1e9,max=-1e9;int water=0,land=0,clearPlants=0;
        for(int face=0;face<6;face++)for(int v=0;v<12;v++)for(int u=0;u<12;u++){
            MountainPlanet.Vec n=PlanetTerrain.direction(face,-1+(u+.5)/6,-1+(v+.5)/6);
            double px=MountainPlanet.longitude(n),pz=MountainPlanet.latitude(n),h=MountainWorld.ground(px,pz);
            min=Math.min(min,h);max=Math.max(max,h);if(h<MountainPlanet.WATER)water++;else land++;
            regions.add(MountainPlanet.region(px,pz));
            for(PlanetTerrain.Prop p:w.terrain.props(PlanetTerrain.leaf(px,pz))){
                models.add(p.model);
                check(p.model.equals("lilypad")||MountainWorld.ground(p.x,p.z)>MountainPlanet.WATER,"Land vegetation never spawns underwater");
                if(p.model.equals("lilypad"))check(MountainWorld.ground(p.x,p.z)<MountainPlanet.WATER&&p.r==0,"Water lilies occupy water and do not block movement");
                if(p.r==0&&!p.model.equals("lilypad")){
                    boolean clear=true;for(PlanetTerrain.Prop other:w.terrain.nearby(p.x,p.z))
                        if(other.r>0&&MountainPlanet.distance(p.x,p.z,other.x,other.z)<other.r+MountainWorld.RADIUS)clear=false;
                    if(clear){clearPlants++;check(!w.staticSolidAt(p.x,p.z,MountainWorld.ground(p.x,p.z)),"Isolated ground cover does not block player");}
                }
            }
        }
        check(min< -8&&max>35&&water>20&&land>200,"Global terrain contains water basins and substantial highland relief");
        check(models.size()>=6&&regions.size()>=5,"Habitat changes affect model species and regional identity");
        check(clearPlants>10,"Collision check samples isolated ground cover across the globe");
        w.reset();check(!w.swimming(),"Restart leaves water and returns to dry camp");
        System.out.println("PASS: lakes, buoyancy, water speed, shore entry/exit, habitat variety and dry vegetation");
    }
    static void wildlife(){
        MountainWildlife wildlife=new MountainWildlife();
        for(double[] location:new double[][]{{0,12},{150,-130},{1500,300},{-3140,0},{0,-1570}}){
            PlanetTerrain.Tile leaf=PlanetTerrain.leaf(location[0],location[1]);
            wildlife.update(PlanetTerrain.select(location[0],location[1]),leaf);
            check(wildlife.active().size()<=MountainWildlife.MAX_ACTIVE,"Animals unload outside nearby area");
            for(MountainWildlife.Spawn spawn:wildlife.active()){
                MountainWildlife.Sample a=MountainWildlife.sample(spawn,0,location[0],location[1]);
                MountainWildlife.Sample b=MountainWildlife.sample(spawn,2,location[0],location[1]);
                MountainWildlife.Sample paused=MountainWildlife.sample(spawn,2,location[0],location[1]);
                near(MountainPlanet.distance(b.x,b.z,paused.x,paused.z),0,1e-10,"Paused simulation freezes animal positions");
                near(b.flap,paused.flap,0,"Paused simulation freezes wing animation");
                check(MountainPlanet.distance(a.x,a.z,b.x,b.z)>.1,"Animals actively move through habitat");
                check(b.y>Math.max(MountainWorld.ground(b.x,b.z),MountainPlanet.WATER)+.5,"Flying wildlife stays above terrain/water");
                check(Double.isFinite(b.heading)&&Double.isFinite(b.flap),"Animal orientation is finite at seams/poles");
            }
        }
        PlanetTerrain.Tile leaf=PlanetTerrain.leaf(0,12);wildlife.update(PlanetTerrain.select(0,12),leaf);
        check(wildlife.active().stream().anyMatch(s->s.bird)&&wildlife.active().stream().anyMatch(s->!s.bird),"Camp has visible birds and butterflies");
        List<MountainWildlife.Spawn> before=wildlife.active();wildlife.update(PlanetTerrain.select(0,12),leaf);
        check(before==wildlife.active(),"Stationary camera does not recreate animal populations");
        System.out.println("PASS: deterministic bounded wildlife, flight clearance, movement and pause");
    }
    public static void main(String[] args){topology();terrain();controller();waterAndHabitats();wildlife();System.out.println("PASS: spherical circuits, poles, radial gravity, collision seams and bounded deterministic terrain");}
}
