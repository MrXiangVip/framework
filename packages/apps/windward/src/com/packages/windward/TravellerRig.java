package com.packages.windward;
import java.io.*;
import java.nio.*;
import java.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.assimp.*;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.assimp.Assimp.*;

/** Fantasy skeleton and CPU skinning. Source model has no animation clips. */
final class TravellerRig {
    static final class Node {
        final String name;final int parent;final double[] bind;
        Node(String name,int parent,double[] bind){this.name=name;this.parent=parent;this.bind=bind;}
    }
    static final class Bone {
        final int node;final double[] offset;
        Bone(int node,double[] offset){this.node=node;this.offset=offset;}
    }
    static final class Mesh {
        final float[] vertices;final int[] indices;final int[][] bones;final float[][] weights;final Bone[] palette;
        String material; final int node;final IntBuffer indexBuffer;
        Mesh(float[] vertices,int[] indices,int[][] bones,float[][] weights,Bone[] palette,int node){
            this.vertices=vertices;this.indices=indices;this.bones=bones;this.weights=weights;this.palette=palette;this.node=node;
            indexBuffer=BufferUtils.createIntBuffer(indices.length);indexBuffer.put(indices).flip();
        }
    }
    final List<Node> nodes=new ArrayList<>();final List<Mesh> meshes=new ArrayList<>();
    final Map<String,Integer> nodeIds=new HashMap<>();
    double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=-minX,maxY=-minX,maxZ=-minX;
    int boneCount; final Map<String,double[]> restDirections=new HashMap<>(),armLocalDirections=new HashMap<>();
    static TravellerRig load(MountainAssets assets)throws IOException{
        TravellerRig rig=new TravellerRig();
        try(Scene source=open(assets,"models/traveller.fbx")){
            rig.readNodes(source.scene.mRootNode(),-1);
            for(int i=0;i<source.scene.mNumMeshes();i++){
                AIMesh mesh=AIMesh.create(source.scene.mMeshes().get(i));
                if(mesh.mName().dataString().toLowerCase(Locale.ROOT).contains("sword"))continue;
                rig.readMesh(mesh,findMeshNode(source.scene.mRootNode(),i,rig.nodeIds));
                try(AIString label=AIString.calloc()){
                    aiGetMaterialString(AIMaterial.create(source.scene.mMaterials().get(mesh.mMaterialIndex())),AI_MATKEY_NAME,0,0,label);
                    rig.meshes.get(rig.meshes.size()-1).material=label.dataString();
                }
            }
            if(source.scene.mNumAnimations()!=0)throw new IOException("Expected animation-free Fantasy source");
        }
        double[][] globals=rig.globals(rig.bindPose());
        for(Mesh mesh:rig.meshes){float[] out=new float[mesh.vertices.length];rig.skin(mesh,globals,out);for(int i=0;i<out.length;i+=8){rig.minX=Math.min(rig.minX,out[i]);rig.maxX=Math.max(rig.maxX,out[i]);rig.minY=Math.min(rig.minY,out[i+1]);rig.maxY=Math.max(rig.maxY,out[i+1]);rig.minZ=Math.min(rig.minZ,out[i+2]);rig.maxZ=Math.max(rig.maxZ,out[i+2]);}}
        if(rig.boneCount==0||!Double.isFinite(rig.height())||rig.height()<=0)throw new IOException("Invalid Fantasy character skeleton");
        for(String side:new String[]{"l","r"}){
            int shoulder=rig.required("upperarm_"+side),hand=rig.required("hand_"+side);
            rig.restDirections.put(side,new double[]{globals[hand][3]-globals[shoulder][3],globals[hand][7]-globals[shoulder][7],globals[hand][11]-globals[shoulder][11]});
            rig.armLocalDirections.put(side,inverseDirection(globals[shoulder],rig.restDirections.get(side)));
            rig.required("thigh_"+side);rig.required("calf_"+side);
        }
        return rig;
    }
    int required(String name)throws IOException{Integer id=nodeIds.get(name);if(id==null)throw new IOException("Missing Fantasy joint: "+name);return id;}
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
    double[][] bindPose(){double[][] pose=new double[nodes.size()][];for(int i=0;i<pose.length;i++)pose[i]=nodes.get(i).bind.clone();return pose;}
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
    /** Continuous gait with a planted stance and a flexed recovery leg. */
    double[][] pose(double phase,boolean moving,boolean airborne){
        return pose(phase,moving?1:0,0,airborne?1:0,0,0,0);
    }
    double[][] pose(double phase,double move,double run,double air,double landing,double time,double velocityY){
        return pose(phase,move,run,air,landing,time,velocityY,false,false);
    }
    double[][] pose(double phase,double move,double run,double air,double landing,double time,double velocityY,boolean climbing,boolean gliding){
        return pose(phase,move,run,air,landing,time,velocityY,climbing,gliding,-1,-1);
    }
    double[][] pose(double phase,double move,double run,double air,double landing,double time,double velocityY,boolean climbing,boolean gliding,double attack,double dodge){
        return pose(phase,move,run,air,landing,time,velocityY,climbing,gliding,attack,dodge,false);
    }
    double[][] pose(double phase,double move,double run,double air,double landing,double time,double velocityY,boolean climbing,boolean gliding,double attack,double dodge,boolean pushing){
        return pose(phase,move,run,air,landing,time,velocityY,climbing,gliding,attack,dodge,pushing,0,0);
    }
    double[][] pose(double phase,double move,double run,double air,double landing,double time,double velocityY,boolean climbing,boolean gliding,double attack,double dodge,boolean pushing,double swim,double swimPhase){
        double slash=attack<0?0:Math.sin(Math.min(1,attack/.5)*Math.PI);
        double evade=dodge<0?0:Math.sin(Math.min(1,dodge/.32)*Math.PI);
        double[][] out=new double[nodes.size()][16];double[] local=new double[16];
        double stride=move*(1-air)*(1-swim),wave=Math.sin(phase);
        double breath=Math.sin(time*2.1)*(1-move)*.012;
        double crouch=landing*.28+evade*.5;
        for(int i=0;i<nodes.size();i++){
            Node node=nodes.get(i);compose(node.bind,local);
            if(node.parent<0)System.arraycopy(local,0,out[i],0,16);else multiply(out[node.parent],local,out[i]);
            if(node.name.equals("pelvis")){
                out[i][7]+=height()*(breath*.15+stride*(.012+run*.012)*(1-Math.cos(phase*2))-landing*.035+swim*.008*Math.sin(swimPhase*2));
                rotateWorld(out[i],axisX(stride*run*.08+swim*(.2+move*.95)));
                rotateWorld(out[i],axisY(wave*stride*.045));
                rotateWorld(out[i],axisZ(Math.sin(swimPhase)*swim*move*.09));
            }
            if(node.name.equals("spine_01")){
                rotateWorld(out[i],axisX(stride*(.045+run*.16)+crouch*.28+breath));
                rotateWorld(out[i],axisY(-wave*stride*.09-slash*.45));
            }
            if(node.name.equals("head"))rotateWorld(out[i],axisX(-swim*(.1+move*.5)));
            for(String side:new String[]{"l","r"}){
                double sign=side.equals("l")?1:-1;
                double cycle=phase+(sign<0?Math.PI:0),leg=Math.sin(cycle);
                double recovery=Math.pow(Math.max(0,-leg),1.3);
                double thigh=leg*stride*(.48+run*.42)+air*(velocityY>0?.38:.18)+crouch;
                if(climbing)thigh=.35+.25*Math.sin(cycle);
                if(gliding)thigh=.12;
                double knee=-recovery*stride*(.85+run*.8)-air*(velocityY>0?.95:.45)-crouch*1.7;
                if(climbing)knee=-.7-.25*Math.sin(cycle);
                if(gliding)knee=-.35;
                double stroke=swimPhase+(sign<0?Math.PI:0),kick=Math.sin(swimPhase*2+(sign<0?Math.PI:0));
                thigh=thigh*(1-swim)+swim*(.12+kick*(.15+move*.08));
                knee=knee*(1-swim)-swim*(.18+Math.max(0,-kick)*.35);
                if(node.name.equals("upperarm_"+side)){
                    double[] target=pushing?new double[]{sign*.35,-.1,.9}:attack>=0&&side.equals("r")?new double[]{-.45+slash*.9,-.25,.55+slash*.5}:climbing?new double[]{sign*.35,.65+.2*Math.sin(cycle),.45}:gliding?new double[]{sign*.55,.9,0}:new double[]{sign*(.13+air*.22),-1+run*stride*.25,leg*stride*(.42+run*.5)-air*.28};
                    double[] water={sign*(.7*(1-move)+move*.35),-.2*(1-move)+move*.1+(.15+move*.3)*Math.sin(stroke),(.25+move*.65)*Math.cos(stroke)};
                    for(int axis=0;axis<3;axis++)target[axis]=target[axis]*(1-swim)+water[axis]*swim;
                    // Aim from the current inherited arm direction, including the swimming torso tilt.
                    rotateWorld(out[i],align(direction(out[i],armLocalDirections.get(side)),target));
                }
                if(node.name.equals("lowerarm_"+side))rotateWorld(out[i],axisX((.18+stride*(.2+run*.65)+air*.45)*(1-swim)+swim*(.25+.45*Math.max(0,Math.sin(stroke)))));
                if(node.name.equals("hand_"+side))rotateWorld(out[i],axisX(-.08-stride*.06-swim*.1));
                if(node.name.equals("thigh_"+side))rotateWorld(out[i],axisX(thigh));
                if(node.name.equals("calf_"+side))rotateWorld(out[i],axisX(knee));
                if(node.name.equals("foot_"+side))rotateWorld(out[i],axisX((-thigh-knee*.65-air*.18)*(1-swim)-swim*.3));
            }
        }
        return out;
    }
    private static double[] axisY(double a){double c=Math.cos(a),s=Math.sin(a);return new double[]{c,0,s,0,0,1,0,0,-s,0,c,0,0,0,0,1};}
    private static double[] axisX(double a){double c=Math.cos(a),s=Math.sin(a);return new double[]{1,0,0,0,0,c,-s,0,0,s,c,0,0,0,0,1};}
    private static double[] axisZ(double a){double c=Math.cos(a),s=Math.sin(a);return new double[]{c,-s,0,0,s,c,0,0,0,0,1,0,0,0,0,1};}
    private static double[] direction(double[] m,double[] v){return new double[]{m[0]*v[0]+m[1]*v[1]+m[2]*v[2],m[4]*v[0]+m[5]*v[1]+m[6]*v[2],m[8]*v[0]+m[9]*v[1]+m[10]*v[2]};}
    private static double[] inverseDirection(double[] m,double[] v){
        double a=m[5]*m[10]-m[6]*m[9],b=m[6]*m[8]-m[4]*m[10],c=m[4]*m[9]-m[5]*m[8],d=m[0]*a+m[1]*b+m[2]*c;
        return new double[]{(a*v[0]+(m[2]*m[9]-m[1]*m[10])*v[1]+(m[1]*m[6]-m[2]*m[5])*v[2])/d,
            (b*v[0]+(m[0]*m[10]-m[2]*m[8])*v[1]+(m[2]*m[4]-m[0]*m[6])*v[2])/d,
            (c*v[0]+(m[1]*m[8]-m[0]*m[9])*v[1]+(m[0]*m[5]-m[1]*m[4])*v[2])/d};
    }
    private static double[] align(double[] from,double[] to){
        double al=Math.sqrt(from[0]*from[0]+from[1]*from[1]+from[2]*from[2]),bl=Math.sqrt(to[0]*to[0]+to[1]*to[1]+to[2]*to[2]);
        double ax=from[0]/al,ay=from[1]/al,az=from[2]/al,bx=to[0]/bl,by=to[1]/bl,bz=to[2]/bl;
        double x=ay*bz-az*by,y=az*bx-ax*bz,z=ax*by-ay*bx,w=1+ax*bx+ay*by+az*bz;
        double l=Math.sqrt(x*x+y*y+z*z+w*w);if(l<1e-8)return axisX(Math.PI);
        return compose(new double[]{0,0,0,x/l,y/l,z/l,w/l,1,1,1});
    }
    private static void rotateWorld(double[] transform,double[] rotation){
        double x=transform[3],y=transform[7],z=transform[11];double[] copy=transform.clone();multiply(rotation,copy,transform);transform[3]=x;transform[7]=y;transform[11]=z;
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
    private static Scene open(MountainAssets assets,String path)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=assets.resource(path)){byte[] block=new byte[65536];int n;while((n=in.read(block))!=-1)bytes.write(block,0,n);}
        byte[] data=bytes.toByteArray();ByteBuffer buffer=MemoryUtil.memAlloc(data.length);buffer.put(data).flip();
        AIScene scene=aiImportFileFromMemory(buffer,aiProcess_Triangulate|aiProcess_GenNormals|aiProcess_JoinIdenticalVertices,"fbx");
        if(scene==null){MemoryUtil.memFree(buffer);throw new IOException("Rig import failed: "+path+": "+aiGetErrorString());}return new Scene(scene,buffer);
    }
}
