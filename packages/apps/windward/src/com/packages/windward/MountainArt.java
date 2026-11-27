package com.packages.windward;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.lwjgl.BufferUtils;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;

/** App-owned forest display lists, texture mapping and Fantasy skeleton presentation. */
final class MountainArt implements AutoCloseable {
    private final MountainAssets assets=new MountainAssets(MountainArt.class.getClassLoader());
    private final Map<String,Integer> textures=new HashMap<>(),lists=new HashMap<>();
    private final Map<String,MountainAssets.Model> models=new HashMap<>();
    TravellerRig traveller;
    double handX,handY,handZ;
    boolean profile;double skinMs;
    private static final class SkinBuffer {
        final float[] vertices;final FloatBuffer buffer;final TravellerRig.Skinning work;
        SkinBuffer(TravellerRig.Mesh mesh){vertices=new float[mesh.vertices.length];buffer=BufferUtils.createFloatBuffer(vertices.length);work=new TravellerRig.Skinning(mesh);}
    }
    private final Map<TravellerRig.Mesh,SkinBuffer> skinBuffers=new IdentityHashMap<>();
    int modelTriangles;
    void open()throws IOException{
        for(String name:MountainAssets.MODELS){
            MountainAssets.Model model=assets.load(name);models.put(name,model);modelTriangles+=model.triangles;
            List<Integer> ids=new ArrayList<>();for(MountainAssets.Part part:model.parts)ids.add(texture(forestTexture(name,part)));
            int id=glGenLists(1);if(id==0)throw new IOException("Cannot cache forest model: "+name);lists.put(name,id);
            glNewList(id,GL_COMPILE);glEnable(GL_TEXTURE_2D);glEnable(GL_ALPHA_TEST);glAlphaFunc(GL_GREATER,.35f);glColor3f(1,1,1);
            int index=0;
            for(MountainAssets.Part part:model.parts){
                glBindTexture(GL_TEXTURE_2D,ids.get(index++));glBegin(GL_TRIANGLES);
                for(int i=0;i<part.vertices.length;i+=8){float[] v=part.vertices;glNormal3f(v[i+3],v[i+4],v[i+5]);glTexCoord2f(v[i+6],v[i+7]);glVertex3f(v[i],v[i+1],v[i+2]);}
                glEnd();
            }
            glDisable(GL_ALPHA_TEST);glDisable(GL_TEXTURE_2D);glEndList();
        }
        texture("ground-grass");texture("ground-dirt");texture("butterfly");texture("bird-wings");
        traveller=TravellerRig.load(assets);
        for(TravellerRig.Mesh mesh:traveller.meshes){texture(characterTexture(mesh.material));skinBuffers.put(mesh,new SkinBuffer(mesh));}
        System.out.println("Windward: loaded "+models.size()+" Meadow models ("+modelTriangles+" triangles), Fantasy "+traveller.meshes.size()+" meshes / "+traveller.boneCount+" bone bindings");
    }
    private static String forestTexture(String name,MountainAssets.Part part){
        if(name.equals("pine")||name.equals("fern")||name.equals("lilypad"))return "generic-palette";
        if(part.leaves)return name.equals("birch")?"birch-leaves":"leaves";
        if(name.equals("grass"))return "grass-blades";
        if(name.equals("flowers"))return "flowers";
        if(part.name.contains("Branches"))return "branches";
        return "palette";
    }
    static String characterTexture(String material)throws IOException{
        if(material.equals("M_Hair"))return "traveller-hair";
        if(material.equals("M_Body"))return "traveller-body";
        if(material.equals("M_Clothes")||material.equals("M_Raincoat"))return "traveller-clothes";
        throw new IOException("Unmapped Fantasy material: "+material);
    }
    private int texture(String name)throws IOException{
        Integer existing=textures.get(name);if(existing!=null)return existing;
        BufferedImage image;try(InputStream input=assets.resource("textures/"+name+".png")){image=ImageIO.read(input);}
        if(image==null)throw new IOException("Invalid texture: "+name);
        // Bound GPU memory for the original 4K character maps; full-size sources stay in APK.
        if(Math.max(image.getWidth(),image.getHeight())>1024){
            double scale=1024./Math.max(image.getWidth(),image.getHeight());
            BufferedImage resized=new BufferedImage(Math.max(1,(int)(image.getWidth()*scale)),Math.max(1,(int)(image.getHeight()*scale)),BufferedImage.TYPE_INT_ARGB);
            Graphics2D g=resized.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);g.drawImage(image,0,0,resized.getWidth(),resized.getHeight(),null);g.dispose();image=resized;
        }
        ByteBuffer pixels=BufferUtils.createByteBuffer(image.getWidth()*image.getHeight()*4);
        for(int y=image.getHeight()-1;y>=0;y--)for(int x=0;x<image.getWidth();x++){int rgb=image.getRGB(x,y);pixels.put((byte)(rgb>>16)).put((byte)(rgb>>8)).put((byte)rgb).put((byte)(rgb>>24));}pixels.flip();
        int id=glGenTextures();textures.put(name,id);glBindTexture(GL_TEXTURE_2D,id);
        glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,image.getWidth(),image.getHeight(),0,GL_RGBA,GL_UNSIGNED_BYTE,pixels);glGenerateMipmap(GL_TEXTURE_2D);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR_MIPMAP_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_REPEAT);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_REPEAT);
        return id;
    }
    void groundTexture(boolean path){glEnable(GL_TEXTURE_2D);glBindTexture(GL_TEXTURE_2D,textures.get(path?"ground-dirt":"ground-grass"));glColor3f(1,1,1);}
    PlantedMesh plant(String name,double x,double y,double z,double height,double angle,double radius){
        return PlantedMesh.supports(name)?new PlantedMesh(models.get(name),x,y,z,height,angle,radius):null;
    }
    void planted(String name,PlantedMesh mesh){
        glEnable(GL_TEXTURE_2D);glEnable(GL_ALPHA_TEST);glAlphaFunc(GL_GREATER,.35f);glColor3f(1,1,1);
        MountainAssets.Model model=models.get(name);
        for(int p=0;p<mesh.parts.size();p++){
            glBindTexture(GL_TEXTURE_2D,textures.get(forestTexture(name,model.parts.get(p))));glBegin(GL_TRIANGLES);
            float[] v=mesh.parts.get(p);
            for(int i=0;i<v.length;i+=8){glNormal3f(v[i+3],v[i+4],v[i+5]);glTexCoord2f(v[i+6],v[i+7]);glVertex3f(v[i],v[i+1],v[i+2]);}
            glEnd();
        }
        glDisable(GL_ALPHA_TEST);glDisable(GL_TEXTURE_2D);
    }
    double radius(String name,double height,double horizontalRadius){
        MountainAssets.Model model=models.get(name);
        if(horizontalRadius<=0){
            double dx=(model.maxX-model.minX)/2,dz=(model.maxZ-model.minZ)/2;
            horizontalRadius=Math.hypot(dx,dz)*height/model.height();
        }
        return Math.hypot(horizontalRadius,height/2)+.05;
    }
    void model(String name,double x,double y,double z,double height,double angle,double radius){
        MountainAssets.Model model=models.get(name);double scale=height/model.height();
        double cx=(model.minX+model.maxX)/2,cz=(model.minZ+model.maxZ)/2,horizontal=scale;
        if(radius>0){double maxRadius=0;for(MountainAssets.Part part:model.parts)for(int i=0;i<part.vertices.length;i+=8)maxRadius=Math.max(maxRadius,Math.hypot(part.vertices[i]-cx,part.vertices[i+2]-cz));horizontal=radius/maxRadius;}
        glPushMatrix();glTranslated(x,y,z);glRotated(angle,0,1,0);glScaled(horizontal,scale,horizontal);glTranslated(-cx,-model.minY,-cz);glCallList(lists.get(name));glPopMatrix();
    }
    /** Two hinged wings use the first atlas frame; motion is local, independent of Unity shaders. */
    void flying(boolean bird,double flap){
        glDisable(GL_LIGHTING);glEnable(GL_TEXTURE_2D);glEnable(GL_ALPHA_TEST);glAlphaFunc(GL_GREATER,.35f);
        glBindTexture(GL_TEXTURE_2D,textures.get(bird?"bird-wings":"butterfly"));glColor3f(1,1,1);
        double span=bird?.9:.24,length=bird?.52:.3,angle=Math.sin(flap)*(bird?.55:1.05)+.2;
        glBegin(GL_QUADS);
        for(int side=-1;side<=1;side+=2){
            double x=side*Math.cos(angle)*span,y=Math.sin(angle)*span;
            glTexCoord2d(.035,.999);glVertex3d(0,0,-length/2);
            glTexCoord2d(.2,.999);glVertex3d(x,y,-length/2);
            glTexCoord2d(.2,.875);glVertex3d(x,y,length/2);
            glTexCoord2d(.035,.875);glVertex3d(0,0,length/2);
        }
        glEnd();glDisable(GL_ALPHA_TEST);glDisable(GL_TEXTURE_2D);
        glColor3d(.17,.15,.12);glBegin(GL_TRIANGLES);
        double body=bird?.6:.25,width=bird?.065:.018;
        glVertex3d(0,.03,-body);glVertex3d(-width,0,body/2);glVertex3d(width,0,body/2);
        glVertex3d(0,-.03,-body);glVertex3d(width,0,body/2);glVertex3d(-width,0,body/2);
        glEnd();glEnable(GL_LIGHTING);glColor3f(1,1,1);
    }
    void character(MountainWorld world){
        long poseBegin=profile?System.nanoTime():0;skinMs=0;
        double[][] globals=traveller.pose(world.gait,world.locomotion,world.runBlend,world.airBlend,world.landing,world.time,world.vy,world.climbing,world.gliding,world.combat.attackTime,world.combat.dodgeTime,world.puzzle.pushing,world.swimBlend,world.swimPhase);
        if(profile)skinMs=(System.nanoTime()-poseBegin)/1e6;
        glPushMatrix();glTranslated(world.x,world.y,world.z);glRotated(Math.toDegrees(world.visualFacing),0,1,0);
        double scale=1.85/traveller.height();
        double[] hand=globals[traveller.nodeIds.get("hand_r")];
        handX=(hand[3]-(traveller.minX+traveller.maxX)/2)*scale;handY=(hand[7]-traveller.minY)*scale;handZ=(hand[11]-(traveller.minZ+traveller.maxZ)/2)*scale;
        glScaled(scale,scale,scale);glTranslated(-(traveller.minX+traveller.maxX)/2,-traveller.minY,-(traveller.minZ+traveller.maxZ)/2);
        glColor3f(1,world.combat.hurt>0?.55f:1,world.combat.hurt>0?.5f:1);glEnable(GL_TEXTURE_2D);glEnable(GL_ALPHA_TEST);glAlphaFunc(GL_GREATER,.35f);
        glEnableClientState(GL_VERTEX_ARRAY);glEnableClientState(GL_NORMAL_ARRAY);glEnableClientState(GL_TEXTURE_COORD_ARRAY);
        for(TravellerRig.Mesh mesh:traveller.meshes){
            SkinBuffer skin=skinBuffers.get(mesh);long skinBegin=profile?System.nanoTime():0;traveller.skin(mesh,globals,skin.vertices,skin.work);if(profile)skinMs+=(System.nanoTime()-skinBegin)/1e6;skin.buffer.clear();skin.buffer.put(skin.vertices);skin.buffer.flip();
            glBindTexture(GL_TEXTURE_2D,textures.get(textureName(mesh.material)));
            skin.buffer.position(0);glVertexPointer(3,GL_FLOAT,32,skin.buffer);skin.buffer.position(3);glNormalPointer(GL_FLOAT,32,skin.buffer);skin.buffer.position(6);glTexCoordPointer(2,GL_FLOAT,32,skin.buffer);
            glDrawElements(GL_TRIANGLES,mesh.indexBuffer);
        }
        glDisableClientState(GL_VERTEX_ARRAY);glDisableClientState(GL_NORMAL_ARRAY);glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        glDisable(GL_ALPHA_TEST);glDisable(GL_TEXTURE_2D);glPopMatrix();
    }
    private static String textureName(String material){try{return characterTexture(material);}catch(IOException impossible){throw new IllegalStateException(impossible);}}
    @Override public void close(){for(int id:lists.values())glDeleteLists(id,1);for(int id:textures.values())glDeleteTextures(id);lists.clear();textures.clear();skinBuffers.clear();}
}
