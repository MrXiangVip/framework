package com.packages.riftarena;

import java.io.*;
import java.nio.*;
import java.util.*;
import org.lwjgl.assimp.*;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.assimp.Assimp.*;

/** Imports actual user-supplied FBX meshes. Arrays contain position, normal and UV. */
final class ArenaAssets {
    static final String[] MODELS={"warrior","archer","mage","soldier","swordstone","butterfly","tree","birch","rock","roundrock","grass","flowers","mushrooms","cabin","bridge","cliff","rockpile","watchpillar","runestone","well"};
    static final class Part {
        final String material,name;final float[] vertices;final boolean leaves;
        Part(String material,String name,float[] vertices,boolean leaves){this.material=material;this.name=name;this.vertices=vertices;this.leaves=leaves;}
    }
    static final class Model {
        final List<Part> parts=new ArrayList<>();
        float minX=Float.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=-minX,maxY=-minX,maxZ=-minX;
        int triangles;
        float height(){return maxY-minY;}
    }
    final ClassLoader loader;
    ArenaAssets(ClassLoader loader){this.loader=loader;}
    InputStream resource(String name)throws IOException{
        InputStream in=loader.getResourceAsStream("res/arena/"+name);
        if(in==null)throw new IOException("缺少竞技场模型资源："+name);return in;
    }
    Model load(String name)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(InputStream in=resource("models/"+name+".fbx")){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)bytes.write(b,0,n);}
        byte[] raw=bytes.toByteArray();ByteBuffer buffer=MemoryUtil.memAlloc(raw.length);buffer.put(raw).flip();
        AIScene scene=null;
        try{
            AIPropertyStore properties=aiCreatePropertyStore();
            try{
                aiSetImportPropertyInteger(properties,AI_CONFIG_PP_PTV_KEEP_HIERARCHY,1);
                scene=aiImportFileFromMemoryWithProperties(buffer,aiProcess_Triangulate|aiProcess_GenNormals|aiProcess_PreTransformVertices|aiProcess_JoinIdenticalVertices,"fbx",properties);
            }finally{aiReleasePropertyStore(properties);}
            if(scene==null)throw new IOException("模型读取失败 "+name+": "+aiGetErrorString());
            Model model=new Model();
            for(int m=0;m<scene.mNumMeshes();m++){
                AIMesh mesh=AIMesh.create(scene.mMeshes().get(m));
                String meshName=mesh.mName().dataString();
                String lower=meshName.toLowerCase(Locale.ROOT);
                int wanted=(name.equals("tree")||name.equals("birch")||name.equals("fruit")||name.equals("flowers"))?1:0;
                java.util.regex.Matcher lod=java.util.regex.Pattern.compile("lod[_ ]?([0-9]+)").matcher(lower);
                if(lod.find()&&Integer.parseInt(lod.group(1))!=wanted)continue;
                AIMaterial material=AIMaterial.create(scene.mMaterials().get(mesh.mMaterialIndex()));
                AIString label=AIString.calloc();String mat;
                try{aiGetMaterialString(material,AI_MATKEY_NAME,0,0,label);mat=label.dataString();}finally{label.free();}
                int count=0;for(int f=0;f<mesh.mNumFaces();f++)if(mesh.mFaces().get(f).mNumIndices()==3)count+=3;
                float[] vertices=new float[count*8];boolean[] leaves=new boolean[count/3];int o=0;
                AIVector3D.Buffer positions=mesh.mVertices(),normals=mesh.mNormals(),uv=mesh.mTextureCoords(0);
                for(int f=0;f<mesh.mNumFaces();f++){
                    AIFace face=mesh.mFaces().get(f);if(face.mNumIndices()!=3)continue;
                    if((name.equals("tree")||name.equals("birch")||name.equals("fruit"))&&mesh.mColors(0)!=null)
                        leaves[o/24]=mesh.mColors(0).get(face.mIndices().get(0)).b()>.5;
                    for(int j=0;j<3;j++){
                        int i=face.mIndices().get(j);AIVector3D p=positions.get(i),n=normals==null?null:normals.get(i),t=uv==null?null:uv.get(i);
                        vertices[o++]=p.x();vertices[o++]=p.y();vertices[o++]=p.z();
                        vertices[o++]=n==null?0:n.x();vertices[o++]=n==null?1:n.y();vertices[o++]=n==null?0:n.z();
                        vertices[o++]=t==null?0:t.x();vertices[o++]=t==null?0:t.y();
                        model.minX=Math.min(model.minX,p.x());model.maxX=Math.max(model.maxX,p.x());
                        model.minY=Math.min(model.minY,p.y());model.maxY=Math.max(model.maxY,p.y());
                        model.minZ=Math.min(model.minZ,p.z());model.maxZ=Math.max(model.maxZ,p.z());
                    }
                }
                int leafCount=0;for(boolean leaf:leaves)if(leaf)leafCount++;
                float[] trunk=new float[vertices.length-leafCount*24],foliage=new float[leafCount*24];int ti=0,li=0;
                for(int f=0;f<leaves.length;f++){if(leaves[f]){System.arraycopy(vertices,f*24,foliage,li,24);li+=24;}else{System.arraycopy(vertices,f*24,trunk,ti,24);ti+=24;}}
                if(trunk.length>0)model.parts.add(new Part(mat,meshName,trunk,false));
                if(foliage.length>0)model.parts.add(new Part(mat,meshName,foliage,true));
                model.triangles+=count/3;
            }
            if(model.triangles==0||!Float.isFinite(model.height())||model.height()<=0)throw new IOException("模型没有有效几何："+name);
            return model;
        }finally{if(scene!=null)aiReleaseImport(scene);MemoryUtil.memFree(buffer);}
    }
}
