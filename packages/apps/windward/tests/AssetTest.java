package com.packages.windward;
import java.io.InputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Validate actual packaged meshes, texture decode, skeleton and procedural skinning. */
public final class AssetTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void checkPlant(MountainAssets.Model model)throws Exception{
        for(double[] site:new double[][]{{15,-25},{40,-30},{130,-90},{Math.PI*1000-1,400},{800,Math.PI*500-1}})
            for(double angle:new double[]{0,67,191}){
                double x=site[0],z=site[1],base=PlanetTerrain.ground(x,z),height=.55;
                PlantedMesh plant=new PlantedMesh(model,x,base,z,height,angle,0);
                MountainPlanet.Frame frame=MountainPlanet.frame(x,z);
                for(int p=0;p<model.parts.size();p++){
                    float[] source=model.parts.get(p).vertices,baked=plant.parts.get(p);
                    for(int i=0;i<baked.length;i+=8){
                        MountainPlanet.Vec world=frame.point(baked[i]-x,baked[i+1],baked[i+2]-z);
                        double ground=PlanetTerrain.ground(MountainPlanet.longitude(world),MountainPlanet.latitude(world));
                        double stem=(source[i+1]-model.minY)*height/model.height();
                        check(Math.abs(world.length()-MountainPlanet.RADIUS-ground-stem+.015)<.003,"Plant follows terrain at every vertex, including seam/pole");
                        check(Math.sqrt(Math.pow(baked[i]-x,2)+Math.pow(baked[i+1]-base-height/2,2)+Math.pow(baked[i+2]-z,2))<=plant.radius,"Conservative planted mesh culling bound");
                        check(baked[i+6]==source[i+6]&&baked[i+7]==source[i+7],"Preserve plant texture coordinates");
                    }
                }
            }
    }
    public static void main(String[] args)throws Exception{
        MountainAssets assets=new MountainAssets(AssetTest.class.getClassLoader());
        int triangles=0;
        for(String name:MountainAssets.MODELS){MountainAssets.Model model=assets.load(name);check(model.triangles>0&&model.height()>0,"Valid forest geometry: "+name);triangles+=model.triangles;for(MountainAssets.Part part:model.parts)for(float v:part.vertices)check(Float.isFinite(v),"Finite model geometry");if(PlantedMesh.supports(name))checkPlant(model);}
        TravellerRig rig=TravellerRig.load(assets);check(rig.meshes.size()==15&&rig.boneCount>80,"Complete skinned Fantasy character");
        double[][] idle=rig.pose(0,false,false),walk=rig.pose(Math.PI/2,true,false),jump=rig.pose(0,false,true);
        double changed=0,jumped=0;
        for(TravellerRig.Mesh mesh:rig.meshes){
            MountainArt.characterTexture(mesh.material);
            float[] a=new float[mesh.vertices.length],b=a.clone(),c=a.clone();rig.skin(mesh,idle,a);rig.skin(mesh,walk,b);rig.skin(mesh,jump,c);
            for(int i=0;i<a.length;i++){check(Float.isFinite(a[i])&&Float.isFinite(b[i])&&Float.isFinite(c[i]),"Finite deformed character");if(i%8<3){changed+=Math.abs(a[i]-b[i]);jumped+=Math.abs(a[i]-c[i]);}}
            for(boolean climb:new boolean[]{true,false}){
                rig.skin(mesh,rig.pose(1,0,0,1,0,2,-2,climb,!climb),c);
                for(float v:c)check(Float.isFinite(v),"Finite climb/glide skinning");
            }
            for(double[] action:new double[][]{{.2,-1},{-1,.16}}){
                rig.skin(mesh,rig.pose(1,0,0,0,0,2,0,false,false,action[0],action[1]),c);
                for(float v:c)check(Float.isFinite(v),"Finite melee/dodge skinning");
            }
            rig.skin(mesh,rig.pose(1,1,0,0,0,2,0,false,false,-1,-1,true),c);
            for(float v:c)check(Float.isFinite(v),"Finite pushing skinning");
            for(float[] weights:mesh.weights){double sum=0;for(float weight:weights)sum+=weight;check(weights.length==0||Math.abs(sum-1)<1e-5,"Normalized skin weights");}
        }
        check(changed>rig.height()&&jumped>rig.height(),"Walk/jump change actual skeleton vertices");
        for(String name:new String[]{"palette","leaves","birch-leaves","branches","grass-blades","flowers","ground-grass","ground-dirt","generic-palette","butterfly","bird-wings","traveller-body","traveller-clothes","traveller-hair"}){
            try(InputStream input=assets.resource("textures/"+name+".png")){BufferedImage image=ImageIO.read(input);check(image!=null&&image.getWidth()>0,"Real PNG decode: "+name);}
        }
        System.out.println("PASS: "+MountainAssets.MODELS.length+" real forest models / "+triangles+" triangles, 15 Fantasy meshes, material maps, normalized skin weights and distinct idle/walk/jump poses");
    }
}
