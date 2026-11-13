package com.packages.riftarena;
import java.io.*;
import javax.imageio.ImageIO;
public final class ArenaAssetsTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        ArenaAssets assets=new ArenaAssets(ArenaAssetsTest.class.getClassLoader());int triangles=0;
        for(String name:ArenaAssets.MODELS){ArenaAssets.Model model=assets.load(name);
            check(model.triangles>0&&model.height()>0,"Valid model: "+name);check(!model.parts.isEmpty(),"Model parts: "+name);
            int count=0;for(ArenaAssets.Part part:model.parts){check(part.vertices.length%24==0,"Triangle records: "+name);for(float value:part.vertices)check(Float.isFinite(value),"Finite geometry: "+name);count+=part.vertices.length/24;}
            check(count==model.triangles,"All imported faces accounted for: "+name);triangles+=count;
        }
        check(assets.load("sword").triangles==34,"Packaged source sword has all blade and grip faces");
        for(String name:new String[]{"warrior","archer","mage","soldier"}){
            ArenaAssets.Model person=assets.load(name);check(person.maxX-person.minX>person.height()*.15,"Character silhouette: "+name);
        }
        String[] textures={"PolygonNatureBiomes_Meadow_Texture_01.png","leafPatch_01.png","leafPatch_04.png","Grass_Mid_01.png","WildFlowers_01.png","Branches_01.png","butterfly.png","DWF_Units_Blue.png","DWF_Units_Red.png","ground-grass.png","ground-dirt.png","ground-stone.png","ground-gravel.png"};
        for(String texture:textures)try(InputStream in=assets.resource("textures/"+texture)){check(ImageIO.read(in)!=null,"Valid texture: "+texture);}
        try{assets.load("missing-model");throw new AssertionError("Missing resource must fail visibly");}catch(IOException expected){}
        System.out.println("PASS: all "+ArenaAssets.MODELS.length+" packaged FBX models, "+triangles+" triangles, finite geometry, four character silhouettes, landscape and character textures and missing-resource handling");
    }
}
