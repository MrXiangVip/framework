package com.packages.riftarena;

import java.io.*;
import java.nio.*;
import java.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.assimp.*;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.assimp.Assimp.*;

/** Copies FBX skin, hierarchy and animation data; no native Assimp pointers survive import. */
final class ArenaRig {
    static final String[] ACTIONS={"idle","run","attack","hurt","death"};
    static final class Node {
        final String name;final int parent;final double[] bind;
        Node(String name,int parent,double[] bind){this.name=name;this.parent=parent;this.bind=bind;}
    }
    static final class Bone {
        final int node;final double[] offset;
        Bone(int node,double[] offset){this.node=node;this.offset=offset;}
    }
    static final class Track {
        final double[] times;final double[][] values;
        Track(double[] times,double[][] values){this.times=times;this.values=values;}
        void sample(double time,double[] target,int offset,boolean rotation){
            int low=0,high=times.length-1;
            while(low<high){int mid=(low+high+1)/2;if(times[mid]<=time)low=mid;else high=mid-1;}
            int next=Math.min(low+1,times.length-1);
            double span=times[next]-times[low],alpha=span<=0?0:ArenaWorld.clamp((time-times[low])/span,0,1);
            if(rotation)slerp(values[low],0,values[next],0,alpha,target,offset);
            else for(int i=0;i<3;i++)target[offset+i]=values[low][i]+(values[next][i]-values[low][i])*alpha;
        }
    }
    static final class Channel {
        final Track position,rotation,scale;
        Channel(AINodeAnim channel){position=vectors(channel.mPositionKeys());rotation=rotations(channel.mRotationKeys());scale=vectors(channel.mScalingKeys());}
        void sample(double time,double[] out){if(position.times.length>0)position.sample(time,out,0,false);if(rotation.times.length>0)rotation.sample(time,out,3,true);if(scale.times.length>0)scale.sample(time,out,7,false);}
    }
    static final class Clip {
        final double duration;final Channel[] channels;
        Clip(double duration,Channel[] channels){this.duration=duration;this.channels=channels;}
    }
    static final class Mesh {
        final float[] vertices;final int[] indices;final int[][] bones;final float[][] weights;final Bone[] palette;
        final int node;final IntBuffer indexBuffer;
        Mesh(float[] vertices,int[] indices,int[][] bones,float[][] weights,Bone[] palette,int node){
            this.vertices=vertices;this.indices=indices;this.bones=bones;this.weights=weights;this.palette=palette;this.node=node;
            indexBuffer=BufferUtils.createIntBuffer(indices.length);indexBuffer.put(indices).flip();
        }
    }
    final List<Node> nodes=new ArrayList<>();final List<Mesh> meshes=new ArrayList<>();
    final Map<String,Integer> nodeIds=new HashMap<>();final Map<String,Clip> clips=new HashMap<>();
    double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=-minX,maxY=-minX,maxZ=-minX;
    int boneCount;
    double[][] weaponPath,weaponPath2;
    Mesh swordMesh;int removedWeaponTriangles;
    static ArenaRig load(ArenaAssets assets,String name)throws IOException{
        ArenaRig rig=new ArenaRig();
        try(Scene source=open(assets,"models/"+name+".fbx")){
            rig.readNodes(source.scene.mRootNode(),-1);
            for(int i=0;i<source.scene.mNumMeshes();i++)rig.readMesh(AIMesh.create(source.scene.mMeshes().get(i)),findMeshNode(source.scene.mRootNode(),i,rig.nodeIds));
        }
        if(rig.meshes.isEmpty()||rig.boneCount==0)throw new IOException("Character has no skinned geometry: "+name);
        double[][] bind=rig.bindPose();double[][] globals=rig.globals(bind);
        for(Mesh mesh:rig.meshes){float[] out=new float[mesh.vertices.length];rig.skin(mesh,globals,out);for(int i=0;i<out.length;i+=8){rig.minX=Math.min(rig.minX,out[i]);rig.maxX=Math.max(rig.maxX,out[i]);rig.minY=Math.min(rig.minY,out[i+1]);rig.maxY=Math.max(rig.maxY,out[i+1]);rig.minZ=Math.min(rig.minZ,out[i+2]);rig.maxZ=Math.max(rig.maxZ,out[i+2]);}}
        if(!Double.isFinite(rig.height())||rig.height()<=0)throw new IOException("Invalid character bind bounds: "+name);
        String group=name.equals("archer")?"archer":name.equals("mage")?"mage":"infantry";
        for(String action:ACTIONS)try(Scene source=open(assets,"animations/"+group+"-"+action+".fbx")){rig.readClip(source.scene,action);}
        if(name.equals("archer"))try(Scene source=open(assets,"animations/archer-retreat.fbx")){rig.readClip(source.scene,"retreat");}
        if(name.equals("warrior")){
            rig.replaceWeapon(assets);
            try(Scene source=open(assets,"animations/infantry-charge.fbx")){rig.readClip(source.scene,"charge");}
            try(Scene source=open(assets,"animations/infantry-attack2.fbx")){rig.readClip(source.scene,"attack2");}
            for(String action:new String[]{"combat-idle","combat-walk"})try(Scene source=open(assets,"animations/infantry-"+action+".fbx")){rig.readClip(source.scene,action);}
            rig.weaponPath=rig.prepareWeaponTrail("attack");rig.weaponPath2=rig.prepareWeaponTrail("attack2");
        }
        return rig;
    }
    private static int component(int[] parents,int vertex){
        while(parents[vertex]!=vertex){parents[vertex]=parents[parents[vertex]];vertex=parents[vertex];}return vertex;
    }
    private static void connect(int[] parents,int a,int b){parents[component(parents,a)]=component(parents,b);}
    /** Remove only disconnected, fully right-hand-weighted parts, never the hand itself. */
    private void replaceWeapon(ArenaAssets assets)throws IOException{
        Integer hand=nodeIds.get("Bip001 R Hand");if(hand==null)throw new IOException("Missing sword hand");
        for(int mi=0;mi<meshes.size();mi++){
            Mesh mesh=meshes.get(mi);int count=mesh.bones.length;int[] parents=new int[count];
            Map<String,Integer> welded=new HashMap<>();
            for(int i=0;i<count;i++){
                parents[i]=i;int o=i*8;
                String key=Math.round(mesh.vertices[o]*1000)+","+Math.round(mesh.vertices[o+1]*1000)+","+Math.round(mesh.vertices[o+2]*1000);
                Integer other=welded.put(key,i);if(other!=null)connect(parents,i,other);
            }
            for(int i=0;i<mesh.indices.length;i+=3){connect(parents,mesh.indices[i],mesh.indices[i+1]);connect(parents,mesh.indices[i],mesh.indices[i+2]);}
            boolean[] rigid=new boolean[count];Arrays.fill(rigid,true);
            for(int i=0;i<count;i++)if(mesh.bones[i].length!=1||mesh.palette[mesh.bones[i][0]].node!=hand)rigid[component(parents,i)]=false;
            List<Integer> kept=new ArrayList<>();
            for(int i=0;i<mesh.indices.length;i+=3){
                if(rigid[component(parents,mesh.indices[i])]){removedWeaponTriangles++;continue;}
                for(int j=0;j<3;j++)kept.add(mesh.indices[i+j]);
            }
            int[] remap=new int[count];Arrays.fill(remap,-1);int vertices=0;
            for(int index:kept)if(remap[index]<0)remap[index]=vertices++;
            float[] v=new float[vertices*8];int[][] bones=new int[vertices][];float[][] weights=new float[vertices][];
            for(int i=0;i<count;i++)if(remap[i]>=0){System.arraycopy(mesh.vertices,i*8,v,remap[i]*8,8);bones[remap[i]]=mesh.bones[i];weights[remap[i]]=mesh.weights[i];}
            int[] indices=new int[kept.size()];for(int i=0;i<indices.length;i++)indices[i]=remap[kept.get(i)];
            meshes.set(mi,new Mesh(v,indices,bones,weights,mesh.palette,mesh.node));
        }
        if(removedWeaponTriangles!=102)throw new IOException("Unexpected warrior axe topology: "+removedWeaponTriangles);
        try(Scene source=open(assets,"models/sword.fbx")){
            if(source.scene.mNumMeshes()!=1)throw new IOException("Expected one sword mesh");
            AIMesh mesh=AIMesh.create(source.scene.mMeshes().get(0));int count=mesh.mNumVertices();float[] v=new float[count*8];
            int[][] bones=new int[count][1];float[][] weights=new float[count][1];
            for(int i=0;i<count;i++){
                AIVector3D p=mesh.mVertices().get(i),n=mesh.mNormals().get(i),uv=mesh.mTextureCoords(0).get(i);int o=i*8;
                // Sword's long X axis becomes hand-local Z. Grip midpoint X=-3.5 sits in the palm.
                v[o]=-p.z();v[o+1]=p.y();v[o+2]=p.x()+3.5f;
                v[o+3]=-n.z();v[o+4]=n.y();v[o+5]=n.x();v[o+6]=uv.x();v[o+7]=uv.y();weights[i][0]=1;
            }
            int[] indices=new int[mesh.mNumFaces()*3];for(int i=0;i<mesh.mNumFaces();i++)for(int j=0;j<3;j++)indices[i*3+j]=mesh.mFaces().get(i).mIndices().get(j);
            double[] identity={1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};
            swordMesh=new Mesh(v,indices,bones,weights,new Bone[]{new Bone(hand,identity)},hand);meshes.add(swordMesh);boneCount++;
        }
    }
    private double[][] prepareWeaponTrail(String action)throws IOException{
        Integer hand=nodeIds.get("Bip001 R Hand");if(hand==null)throw new IOException("Warrior has no weapon hand");
        double[][] bind=globals(bindPose());double[] wrist=bind[hand];int selectedMesh=-1,selectedVertex=-1;double farthest=-1;
        for(int m=0;m<meshes.size();m++){
            Mesh mesh=meshes.get(m);if(mesh!=swordMesh)continue;float[] out=new float[mesh.vertices.length];skin(mesh,bind,out);
            for(int v=0;v<mesh.bones.length;v++)for(int j=0;j<mesh.bones[v].length;j++){
                if(mesh.palette[mesh.bones[v][j]].node!=hand||mesh.weights[v][j]<.75)continue;
                double dx=out[v*8]-wrist[3],dy=out[v*8+1]-wrist[7],dz=out[v*8+2]-wrist[11],d=dx*dx+dy*dy+dz*dz;
                if(d>farthest){farthest=d;selectedMesh=m;selectedVertex=v;}
            }
        }
        if(selectedMesh<0)throw new IOException("Warrior weapon has no rigid hand weights");
        double[][] path=new double[121][6];Mesh mesh=meshes.get(selectedMesh);float[] out=new float[mesh.vertices.length];double[][] pose=bindPose();
        for(int frame=0;frame<path.length;frame++){
            sample(action,clips.get(action).duration*frame/(path.length-1),false,pose);
            double[][] global=globals(pose);skin(mesh,global,out);double[] handMatrix=global[hand];int offset=selectedVertex*8;
            for(int axis=0;axis<3;axis++){
                double tip=out[offset+axis],base=handMatrix[axis*4+3];
                path[frame][axis]=base+(tip-base)*.55;path[frame][axis+3]=tip;
            }
        }
        return path;
    }
    void weaponPoint(double phase,double[] out){
        weaponPoint(phase,out,false);
    }
    void weaponPoint(double phase,double[] out,boolean second){
        double[][] path=second?weaponPath2:weaponPath;
        double frame=ArenaWorld.clamp(phase,0,1)*(path.length-1);int first=(int)frame,next=Math.min(first+1,path.length-1);double alpha=frame-first;
        for(int i=0;i<6;i++)out[i]=path[first][i]+(path[next][i]-path[first][i])*alpha;
    }
    double height(){return maxY-minY;}
    private int readNodes(AINode source,int parent){
        int index=nodes.size();String name=source.mName().dataString();nodeIds.put(name,index);
        try(AIVector3D scale=AIVector3D.calloc();AIVector3D position=AIVector3D.calloc();AIQuaternion rotation=AIQuaternion.calloc()){
            aiDecomposeMatrix(source.mTransformation(),scale,rotation,position);
            nodes.add(new Node(name,parent,new double[]{position.x(),position.y(),position.z(),rotation.x(),rotation.y(),rotation.z(),rotation.w(),scale.x(),scale.y(),scale.z()}));
        }
        for(int i=0;i<source.mNumChildren();i++)readNodes(AINode.create(source.mChildren().get(i)),index);return index;
    }
    private static int findMeshNode(AINode node,int mesh,Map<String,Integer> ids){
        for(int i=0;i<node.mNumMeshes();i++)if(node.mMeshes().get(i)==mesh)return ids.get(node.mName().dataString());
        for(int i=0;i<node.mNumChildren();i++){int found=findMeshNode(AINode.create(node.mChildren().get(i)),mesh,ids);if(found>=0)return found;}return -1;
    }
    private void readMesh(AIMesh mesh,int node)throws IOException{
        if(node<0)throw new IOException("Mesh has no node");
        int count=mesh.mNumVertices();float[] v=new float[count*8];
        for(int i=0;i<count;i++){
            AIVector3D p=mesh.mVertices().get(i),n=mesh.mNormals().get(i),uv=mesh.mTextureCoords(0)==null?null:mesh.mTextureCoords(0).get(i);
            int o=i*8;v[o]=p.x();v[o+1]=p.y();v[o+2]=p.z();v[o+3]=n.x();v[o+4]=n.y();v[o+5]=n.z();v[o+6]=uv==null?0:uv.x();v[o+7]=uv==null?0:uv.y();
        }
        int[] index=new int[mesh.mNumFaces()*3];for(int i=0;i<mesh.mNumFaces();i++){AIFace face=mesh.mFaces().get(i);if(face.mNumIndices()!=3)throw new IOException("Non-triangle character");for(int j=0;j<3;j++)index[i*3+j]=face.mIndices().get(j);}
        Bone[] palette=new Bone[mesh.mNumBones()];int[] counts=new int[count];
        for(int b=0;b<palette.length;b++){
            AIBone bone=AIBone.create(mesh.mBones().get(b));Integer id=nodeIds.get(bone.mName().dataString());if(id==null)throw new IOException("Bone not found: "+bone.mName().dataString());
            palette[b]=new Bone(id,matrix(bone.mOffsetMatrix()));
            for(int j=0;j<bone.mNumWeights();j++)if(bone.mWeights().get(j).mWeight()>0)counts[bone.mWeights().get(j).mVertexId()]++;
        }
        int[][] bones=new int[count][];float[][] weights=new float[count][];
        for(int i=0;i<count;i++){bones[i]=new int[counts[i]];weights[i]=new float[counts[i]];}Arrays.fill(counts,0);
        for(int b=0;b<palette.length;b++){
            AIBone bone=AIBone.create(mesh.mBones().get(b));for(int j=0;j<bone.mNumWeights();j++){
                AIVertexWeight weight=bone.mWeights().get(j);if(weight.mWeight()<=0)continue;
                int vertex=weight.mVertexId(),slot=counts[vertex]++;bones[vertex][slot]=b;weights[vertex][slot]=weight.mWeight();
            }
        }
        for(int i=0;i<count;i++){double sum=0;for(float weight:weights[i])sum+=weight;for(int j=0;j<weights[i].length;j++)weights[i][j]/=sum;}
        meshes.add(new Mesh(v,index,bones,weights,palette,node));boneCount+=palette.length;
    }
    private void readClip(AIScene source,String action)throws IOException{
        if(source.mNumAnimations()!=1)throw new IOException("Expected one animation: "+action);
        AIAnimation animation=AIAnimation.create(source.mAnimations().get(0));double ticks=animation.mTicksPerSecond()>0?animation.mTicksPerSecond():30;
        Channel[] channels=new Channel[nodes.size()];int matched=0;
        for(int i=0;i<animation.mNumChannels();i++){
            AINodeAnim channel=AINodeAnim.create(animation.mChannels().get(i));Integer id=nodeIds.get(channel.mNodeName().dataString());
            if(id==null)throw new IOException("Incompatible animation node: "+channel.mNodeName().dataString());
            channels[id]=new Channel(channel);matched++;
            for(Track track:new Track[]{channels[id].position,channels[id].rotation,channels[id].scale})for(int j=0;j<track.times.length;j++)track.times[j]/=ticks;
        }
        if(matched==0||animation.mDuration()<=0)throw new IOException("Empty animation: "+action);
        clips.put(action,new Clip(animation.mDuration()/ticks,channels));
    }
    double[][] bindPose(){double[][] pose=new double[nodes.size()][];for(int i=0;i<pose.length;i++)pose[i]=nodes.get(i).bind.clone();return pose;}
    void sample(String action,double seconds,boolean loop,double[][] pose){
        Clip clip=clips.get(action);double time=loop?((seconds%clip.duration)+clip.duration)%clip.duration:ArenaWorld.clamp(seconds,0,clip.duration);
        for(int i=0;i<nodes.size();i++){
            System.arraycopy(nodes.get(i).bind,0,pose[i],0,10);
            if(clip.channels[i]!=null)clip.channels[i].sample(time,pose[i]);
            // Root translation would otherwise move the mesh separately from game movement.
            if(nodes.get(i).name.equals("Bip001_$AssimpFbx$_Translation")){
                pose[i][0]=nodes.get(i).bind[0];pose[i][2]=nodes.get(i).bind[2];
            }
        }
    }
    double[][] globals(double[][] pose){double[][] out=new double[nodes.size()][16];globals(pose,out,new double[16]);return out;}
    void globals(double[][] pose,double[][] out,double[] local){
        for(int i=0;i<nodes.size();i++){compose(pose[i],local);int parent=nodes.get(i).parent;if(parent<0)System.arraycopy(local,0,out[i],0,16);else multiply(out[parent],local,out[i]);}
    }
    static final class Skinning {
        final double[][] matrices,normals;
        Skinning(Mesh mesh){matrices=new double[mesh.palette.length+1][16];normals=new double[mesh.palette.length+1][9];}
    }
    void skin(Mesh mesh,double[][] globals,float[] out){skin(mesh,globals,out,new Skinning(mesh));}
    void skin(Mesh mesh,double[][] globals,float[] out,Skinning work){
        int unweighted=mesh.palette.length;
        for(int b=0;b<unweighted;b++)multiply(globals[mesh.palette[b].node],mesh.palette[b].offset,work.matrices[b]);
        System.arraycopy(globals[mesh.node],0,work.matrices[unweighted],0,16);
        // Inverse-transpose once per bone, rather than once per vertex influence.
        for(int i=0;i<work.matrices.length;i++){
            double[] m=work.matrices[i],n=work.normals[i];
            double a=m[5]*m[10]-m[6]*m[9],b=m[6]*m[8]-m[4]*m[10],c=m[4]*m[9]-m[5]*m[8],det=m[0]*a+m[1]*b+m[2]*c;
            if(Math.abs(det)<1e-12){Arrays.fill(n,0);continue;}
            n[0]=a/det;n[1]=b/det;n[2]=c/det;
            n[3]=(m[2]*m[9]-m[1]*m[10])/det;n[4]=(m[0]*m[10]-m[2]*m[8])/det;n[5]=(m[1]*m[8]-m[0]*m[9])/det;
            n[6]=(m[1]*m[6]-m[2]*m[5])/det;n[7]=(m[2]*m[4]-m[0]*m[6])/det;n[8]=(m[0]*m[5]-m[1]*m[4])/det;
        }
        for(int vertex=0;vertex<mesh.bones.length;vertex++){
            int o=vertex*8;double px=0,py=0,pz=0,nx=0,ny=0,nz=0;float[] v=mesh.vertices;
            int influences=Math.max(1,mesh.bones[vertex].length);
            for(int j=0;j<influences;j++){
                int bone=mesh.bones[vertex].length==0?unweighted:mesh.bones[vertex][j];double[] m=work.matrices[bone],n=work.normals[bone];
                double w=mesh.bones[vertex].length==0?1:mesh.weights[vertex][j];
                px+=(m[0]*v[o]+m[1]*v[o+1]+m[2]*v[o+2]+m[3])*w;py+=(m[4]*v[o]+m[5]*v[o+1]+m[6]*v[o+2]+m[7])*w;pz+=(m[8]*v[o]+m[9]*v[o+1]+m[10]*v[o+2]+m[11])*w;
                nx+=(n[0]*v[o+3]+n[1]*v[o+4]+n[2]*v[o+5])*w;ny+=(n[3]*v[o+3]+n[4]*v[o+4]+n[5]*v[o+5])*w;nz+=(n[6]*v[o+3]+n[7]*v[o+4]+n[8]*v[o+5])*w;
            }
            double length=Math.sqrt(nx*nx+ny*ny+nz*nz);if(length<1e-12)length=1;
            out[o]=(float)px;out[o+1]=(float)py;out[o+2]=(float)pz;out[o+3]=(float)(nx/length);out[o+4]=(float)(ny/length);out[o+5]=(float)(nz/length);out[o+6]=v[o+6];out[o+7]=v[o+7];
        }
    }
    static void blend(double[][] previous,double[][] next,double alpha){
        for(int i=0;i<next.length;i++){
            slerp(previous[i],3,next[i],3,alpha,next[i],3);
            for(int j=0;j<10;j++)if(j<3||j>6)next[i][j]=previous[i][j]+(next[i][j]-previous[i][j])*alpha;
        }
    }
    private static Track vectors(AIVectorKey.Buffer keys){
        int count=keys==null?0:keys.remaining();double[] times=new double[count];double[][] values=new double[count][3];
        for(int i=0;i<count;i++){AIVectorKey k=keys.get(i);times[i]=k.mTime();values[i]=new double[]{k.mValue().x(),k.mValue().y(),k.mValue().z()};}return new Track(times,values);
    }
    private static Track rotations(AIQuatKey.Buffer keys){
        int count=keys==null?0:keys.remaining();double[] times=new double[count];double[][] values=new double[count][4];
        for(int i=0;i<count;i++){AIQuatKey k=keys.get(i);times[i]=k.mTime();values[i]=new double[]{k.mValue().x(),k.mValue().y(),k.mValue().z(),k.mValue().w()};}return new Track(times,values);
    }
    static void slerp(double[] a,int ao,double[] b,int bo,double t,double[] out,int offset){
        double dot=0;for(int i=0;i<4;i++)dot+=a[ao+i]*b[bo+i];double sign=dot<0?-1:1;dot=Math.min(1,Math.abs(dot));
        double x=1-t,y=t;if(dot<.9995){double theta=Math.acos(dot),sin=Math.sin(theta);x=Math.sin((1-t)*theta)/sin;y=Math.sin(t*theta)/sin;}
        double q0=a[ao]*x+b[bo]*y*sign,q1=a[ao+1]*x+b[bo+1]*y*sign,q2=a[ao+2]*x+b[bo+2]*y*sign,q3=a[ao+3]*x+b[bo+3]*y*sign;
        double length=Math.sqrt(q0*q0+q1*q1+q2*q2+q3*q3);out[offset]=q0/length;out[offset+1]=q1/length;out[offset+2]=q2/length;out[offset+3]=q3/length;
    }
    static double[] compose(double[] p){double[] out=new double[16];compose(p,out);return out;}
    static void compose(double[] p,double[] out){
        double x=p[3],y=p[4],z=p[5],w=p[6],sx=p[7],sy=p[8],sz=p[9];
        out[0]=(1-2*y*y-2*z*z)*sx;out[1]=(2*x*y-2*z*w)*sy;out[2]=(2*x*z+2*y*w)*sz;out[3]=p[0];
        out[4]=(2*x*y+2*z*w)*sx;out[5]=(1-2*x*x-2*z*z)*sy;out[6]=(2*y*z-2*x*w)*sz;out[7]=p[1];
        out[8]=(2*x*z-2*y*w)*sx;out[9]=(2*y*z+2*x*w)*sy;out[10]=(1-2*x*x-2*y*y)*sz;out[11]=p[2];out[12]=out[13]=out[14]=0;out[15]=1;
    }
    static double[] matrix(AIMatrix4x4 m){return new double[]{m.a1(),m.a2(),m.a3(),m.a4(),m.b1(),m.b2(),m.b3(),m.b4(),m.c1(),m.c2(),m.c3(),m.c4(),m.d1(),m.d2(),m.d3(),m.d4()};}
    static double[] multiply(double[] a,double[] b){double[] out=new double[16];multiply(a,b,out);return out;}
    static void multiply(double[] a,double[] b,double[] out){for(int row=0;row<4;row++)for(int col=0;col<4;col++){double value=0;for(int k=0;k<4;k++)value+=a[row*4+k]*b[k*4+col];out[row*4+col]=value;}}
    private static final class Scene implements AutoCloseable {
        final AIScene scene;final ByteBuffer bytes;
        Scene(AIScene scene,ByteBuffer bytes){this.scene=scene;this.bytes=bytes;}
        public void close(){aiReleaseImport(scene);MemoryUtil.memFree(bytes);}
    }
    private static Scene open(ArenaAssets assets,String path)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=assets.resource(path)){byte[] block=new byte[65536];int n;while((n=in.read(block))!=-1)bytes.write(block,0,n);}
        byte[] data=bytes.toByteArray();ByteBuffer buffer=MemoryUtil.memAlloc(data.length);buffer.put(data).flip();
        AIScene scene=aiImportFileFromMemory(buffer,aiProcess_Triangulate|aiProcess_GenNormals|aiProcess_JoinIdenticalVertices,"fbx");
        if(scene==null){MemoryUtil.memFree(buffer);throw new IOException("Rig import failed: "+path+": "+aiGetErrorString());}return new Scene(scene,buffer);
    }
}
