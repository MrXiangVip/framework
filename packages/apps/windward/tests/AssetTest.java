package com.packages.windward;
import java.io.InputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Validate actual packaged meshes, texture decode, skeleton and procedural skinning. */
public final class AssetTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        MountainAssets assets=new MountainAssets(AssetTest.class.getClassLoader());
        int triangles=0;
        for(String name:MountainAssets.MODELS){MountainAssets.Model model=assets.load(name);check(model.triangles>0&&model.height()>0,"Valid forest geometry: "+name);triangles+=model.triangles;for(MountainAssets.Part part:model.parts)for(float v:part.vertices)check(Float.isFinite(v),"Finite model geometry");}
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
