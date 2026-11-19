package com.packages.riftarena;

import java.io.IOException;
import java.awt.image.*;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.FloatBuffer;
import java.io.InputStream;
import java.util.*;
import javax.imageio.ImageIO;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_BGRA;
import static org.lwjgl.opengl.GL12.GL_UNSIGNED_INT_8_8_8_8_REV;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL14.*;

/** Offscreen OpenGL renderer. The framework owns the visible window and all input. */
final class ArenaRenderer implements AutoCloseable {
    private long window;
    private GLFWErrorCallback errors;
    private int fbo,color,depth,width,height,landscape;
    private int bareFbo,bareColor,bareDepth;
    private int sceneFbo,sceneColor,sceneDepth,sceneWidth,sceneHeight,sceneMarginX,sceneMarginY;
    private boolean sceneReady;
    boolean cachedScene=true,culling=true;
    int drawnUnits,culledUnits;
    private ByteBuffer pixels;
    private IntBuffer packedPixels;
    private ArenaAssets assets;
    private final Map<String,Integer> models=new HashMap<>(),textures=new HashMap<>();
    private final Map<String,ArenaRig> rigs=new HashMap<>();
    private final Map<ArenaWorld.Unit,ArenaAnimator> animators=new IdentityHashMap<>();
    private final Map<ArenaWorld.Unit,double[]> healthFeedback=new IdentityHashMap<>();
    double trailingHealth(ArenaWorld.Unit u,double time){
        double[] state=healthFeedback.get(u);
        if(state==null){state=new double[]{u.hp,u.hp,time,time};healthFeedback.put(u,state);}
        double dt=Math.max(0,time-state[2]);
        if(time<state[2]||u.hp>state[0])state[1]=u.hp;
        else if(u.hp<state[0])state[3]=time;
        else if(time-state[3]>.18)state[1]=Math.max(u.hp,state[1]-u.maxHp*dt*1.6);
        state[0]=u.hp;state[2]=time;return Math.max(u.hp,state[1]);
    }
    static boolean towerThreat(ArenaWorld w,ArenaWorld.Unit u){
        return w.player.alive()&&u.alive()&&u.kind==ArenaWorld.Kind.TOWER&&u.team!=w.player.team&&w.target(u)==w.player;
    }
    int modelTriangles;
    boolean profile;long sceneNanos,unitNanos,effectNanos,readbackNanos;
    double cameraX,cameraZ,zoom=1;
    String device="";
    static final double VIEW_HEIGHT=34,HUD_SCALE=800.0/VIEW_HEIGHT;
    static final class Canopy {
        final double x,z,height,angle;final String model;double alpha=1,minX,maxX,minY,maxY;
        Canopy(int i){x=-42+(i*17%85);z=(i%2==0?-1:1)*(12+(i*7%5)*1.35);height=5.5+(i%4)*.65;angle=i*43;model=i%3==0?"birch":"tree";}
    }
    final List<Canopy> canopies=new ArrayList<>();double canopyTime=Double.NaN;int fadedTrees;
    static final double TILT=55, SIN=Math.sin(Math.toRadians(TILT)),COS=Math.cos(Math.toRadians(TILT));
    void open(ClassLoader loader,int w,int h)throws IOException{
        errors=GLFWErrorCallback.createPrint(System.err);errors.set();
        glfwInitHint(GLFW_PLATFORM,GLFW_PLATFORM_X11);
        if(!glfwInit())throw new IOException("无法初始化 OpenGL，请检查 Linux DISPLAY");
        glfwDefaultWindowHints();glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,2);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,1);
        window=glfwCreateWindow(64,64,"rift-arena offscreen",0,0);
        if(window==0)throw new IOException("无法创建 OpenGL 上下文");
        glfwMakeContextCurrent(window);GL.createCapabilities();device=glGetString(GL_RENDERER);
        if(!GL.getCapabilities().OpenGL30&&!GL.getCapabilities().GL_ARB_framebuffer_object)throw new IOException("需要 framebuffer object 支持");
        width=w;height=h;fbo=glGenFramebuffers();glBindFramebuffer(GL_FRAMEBUFFER,fbo);
        color=glGenTextures();glBindTexture(GL_TEXTURE_2D,color);glTexImage2D(GL_TEXTURE_2D,0,GL_RGB,w,h,0,GL_RGB,GL_UNSIGNED_BYTE,(ByteBuffer)null);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
        glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,color,0);
        depth=glGenRenderbuffers();glBindRenderbuffer(GL_RENDERBUFFER,depth);glRenderbufferStorage(GL_RENDERBUFFER,GL_DEPTH_COMPONENT24,w,h);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER,GL_DEPTH_ATTACHMENT,GL_RENDERBUFFER,depth);
        if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new IOException("无法创建渲染缓冲区");
        pixels=BufferUtils.createByteBuffer(w*h*4);packedPixels=pixels.asIntBuffer();glPixelStorei(GL_PACK_ALIGNMENT,1);
        glEnable(GL_DEPTH_TEST);glEnable(GL_NORMALIZE);glEnable(GL_COLOR_MATERIAL);glColorMaterial(GL_FRONT_AND_BACK,GL_AMBIENT_AND_DIFFUSE);
        glEnable(GL_LIGHTING);glEnable(GL_LIGHT0);glLightModelfv(GL_LIGHT_MODEL_AMBIENT,new float[]{.48f,.55f,.64f,1});glLightfv(GL_LIGHT0,GL_DIFFUSE,new float[]{.8f,.79f,.68f,1});
        for(int i=0;i<36;i++){Canopy tree=new Canopy(i);if(Math.abs(tree.x)>=7)canopies.add(tree);}
        assets=new ArenaAssets(loader);
        for(String name:ArenaAssets.MODELS){
            if(isCharacter(name)){
                ArenaRig rig=ArenaRig.load(assets,name);rigs.put(name,rig);
                for(ArenaRig.Mesh mesh:rig.meshes)modelTriangles+=mesh.indices.length/3;
            }else compile(name,assets.load(name),-1);
        }
        loadTexture("textures/DWF_Units_Blue.png");loadTexture("textures/DWF_Units_Red.png");
        glLightModeli(GL_LIGHT_MODEL_TWO_SIDE,GL_TRUE);
        for(String surface:new String[]{"grass","dirt","stone","gravel"})loadTexture("textures/ground-"+surface+".png");
        landscape=glGenLists(1);glNewList(landscape,GL_COMPILE);world(0);glEndList();
        // Cover every legal camera position at the same world-to-pixel scale, once.
        sceneMarginX=(int)Math.ceil(17*height/VIEW_HEIGHT*1.25);sceneMarginY=(int)Math.ceil(5*SIN*height/VIEW_HEIGHT*1.25);
        sceneWidth=width+sceneMarginX*2;sceneHeight=height+sceneMarginY*2;
        sceneFbo=glGenFramebuffers();glBindFramebuffer(GL_FRAMEBUFFER,sceneFbo);
        sceneColor=glGenRenderbuffers();glBindRenderbuffer(GL_RENDERBUFFER,sceneColor);glRenderbufferStorage(GL_RENDERBUFFER,GL_RGB8,sceneWidth,sceneHeight);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_RENDERBUFFER,sceneColor);
        sceneDepth=glGenRenderbuffers();glBindRenderbuffer(GL_RENDERBUFFER,sceneDepth);glRenderbufferStorage(GL_RENDERBUFFER,GL_DEPTH_COMPONENT24,sceneWidth,sceneHeight);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER,GL_DEPTH_ATTACHMENT,GL_RENDERBUFFER,sceneDepth);
        if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new IOException("无法创建场景缓存");
        bareFbo=glGenFramebuffers();glBindFramebuffer(GL_FRAMEBUFFER,bareFbo);
        bareColor=glGenRenderbuffers();glBindRenderbuffer(GL_RENDERBUFFER,bareColor);glRenderbufferStorage(GL_RENDERBUFFER,GL_RGB8,sceneWidth,sceneHeight);glFramebufferRenderbuffer(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_RENDERBUFFER,bareColor);
        bareDepth=glGenRenderbuffers();glBindRenderbuffer(GL_RENDERBUFFER,bareDepth);glRenderbufferStorage(GL_RENDERBUFFER,GL_DEPTH_COMPONENT24,sceneWidth,sceneHeight);glFramebufferRenderbuffer(GL_FRAMEBUFFER,GL_DEPTH_ATTACHMENT,GL_RENDERBUFFER,bareDepth);
        if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new IOException("无法创建树木遮挡缓存");
        glBindFramebuffer(GL_FRAMEBUFFER,fbo);
    }
    // Snapshot API for image-comparison tests; production supplies a leased pool image.
    BufferedImage render(ArenaWorld world){return render(world,new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB));}
    BufferedImage render(ArenaWorld world,BufferedImage image){
        if(image.getWidth()!=width||image.getHeight()!=height||image.getType()!=BufferedImage.TYPE_INT_RGB)throw new IllegalArgumentException("Unexpected frame buffer");
        double wantedZoom=ArenaWorld.clamp(world.cameraZoom,.85,1.25);if(wantedZoom!=zoom){zoom=wantedZoom;sceneReady=false;}
        cameraX=world.screen==ArenaWorld.Screen.TITLE?0:ArenaWorld.clamp(world.player.x,-17,17);
        cameraZ=world.screen==ArenaWorld.Screen.TITLE?0:ArenaWorld.clamp(world.player.z,-5,5);
        // Integer pixel scrolling keeps cached depth, scene and dynamic geometry aligned.
        double scale=height/VIEW_HEIGHT*zoom;
        cameraX=Math.rint(cameraX*scale)/scale;cameraZ=Math.rint(cameraZ*scale*SIN)/(scale*SIN);
        glBindFramebuffer(GL_FRAMEBUFFER,fbo);glViewport(0,0,width,height);glClearColor(.025f,.055f,.085f,1);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
        glMatrixMode(GL_PROJECTION);glLoadIdentity();double aspect=width/(double)height;glOrtho(-(VIEW_HEIGHT/zoom/2)*aspect,(VIEW_HEIGHT/zoom/2)*aspect,-VIEW_HEIGHT/zoom/2,VIEW_HEIGHT/zoom/2,-100,100);
        glMatrixMode(GL_MODELVIEW);glLoadIdentity();glTranslated(0,0,cameraZ*COS);glRotated(TILT,1,0,0);glTranslated(-cameraX,0,-cameraZ);
        glLightfv(GL_LIGHT0,GL_POSITION,new float[]{-20,50,25,0});
        if(profile)glFinish();long stage=profile?System.nanoTime():0;
        updateCanopies(world);
        if(cachedScene)copyScene();else glCallList(landscape);
        waterMotion(world.elapsed);
        drawCanopies(false);
        if(profile){glFinish();sceneNanos=System.nanoTime()-stage;stage=System.nanoTime();}
        animators.keySet().retainAll(world.units);healthFeedback.keySet().retainAll(world.units);
        drawnUnits=culledUnits=0;
        for(ArenaWorld.Unit u:world.units){
            if(culling&&!visible(u.x,u.z,u.kind==ArenaWorld.Kind.HERO?5:3.5,8)){culledUnits++;continue;}
            unit(u,world.elapsed);drawnUnits++;
        }
        sanctuaryMotion(world.elapsed);
        for(ArenaWorld.DashGhost ghost:world.dashGhosts)if(!culling||visible(ghost.x,ghost.z,5,8))dashGhost(ghost);
        for(int i=0;i<8;i++){double x=-24+i*7+Math.sin(world.elapsed*.7+i)*1.5,z=(i%2==0?-1:1)*9+Math.cos(world.elapsed+i)*.8;
            if(!culling||visible(x,z,1,3))drawModel("butterfly",x,1.1+Math.sin(world.elapsed*3+i)*.25,z,.65,world.elapsed*23+i*40,0xffffff);}

        drawCanopies(true);
        if(profile){glFinish();unitNanos=System.nanoTime()-stage;stage=System.nanoTime();}
        glDisable(GL_LIGHTING);
        glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE);glDepthMask(false);
        for(ArenaWorld.FanArrow arrow:world.fanArrows)if(!culling||visible(arrow.x,arrow.z,2,4)){
            glColor4f(.4f,.9f,1,.32f);glLineWidth(6);glBegin(GL_LINES);
            glVertex3d(arrow.x-arrow.dx*1.25,arrow.y,arrow.z-arrow.dz*1.25);glVertex3d(arrow.x,arrow.y,arrow.z);glEnd();
            glColor4f(1,.9f,.6f,1);glLineWidth(2);glBegin(GL_LINES);
            glVertex3d(arrow.x-arrow.dx*.8,arrow.y,arrow.z-arrow.dz*.8);glVertex3d(arrow.x,arrow.y,arrow.z);
            for(int side=-1;side<=1;side+=2){glVertex3d(arrow.x,arrow.y,arrow.z);glVertex3d(arrow.x-arrow.dx*.25+arrow.dz*.13*side,arrow.y,arrow.z-arrow.dz*.25-arrow.dx*.13*side);}glEnd();
        }
        for(ArenaWorld.Arrow arrow:world.arrows)if(arrow.released&&(!culling||visible(arrow.x,arrow.z,2,4))){
            double x=arrow.x,y=arrow.y,z=arrow.z,dx=arrow.dx,dz=arrow.dz;
            glColor4f(arrow.team==0?.35f:1,.75f,arrow.team==0?1:.4f,.28f);glLineWidth(5);glBegin(GL_LINES);
            glVertex3d(x-dx*1.4,y,z-dz*1.4);glVertex3d(x,y,z);glEnd();
            glColor4f(1,.89f,.65f,1);glLineWidth(2);glBegin(GL_LINES);
            glVertex3d(x-dx*.85,y,z-dz*.85);glVertex3d(x,y,z);
            glVertex3d(x,y,z);glVertex3d(x-dx*.24+dz*.14,y,z-dz*.24-dx*.14);
            glVertex3d(x,y,z);glVertex3d(x-dx*.24-dz*.14,y,z-dz*.24+dx*.14);glEnd();
        }
        for(ArenaWorld.Shot shot:world.shots){
            double progress=1-shot.life/shot.duration;
            float fade=(float)Math.min(1,(1-progress)*2);
            if(shot.melee){
                // The warrior's blade trail is drawn from its actual skinned weapon path below.
                continue;
            }else{
                double head=Math.min(1,progress*1.7),tail=Math.max(0,head-.45);
                double x=shot.x+(shot.tx-shot.x)*head,z=shot.z+(shot.tz-shot.z)*head;
                glColor4f(shot.team==0?.3f:1,.7f,1,fade*.5f);glLineWidth(9);glBegin(GL_LINES);
                glVertex3d(shot.x+(shot.tx-shot.x)*tail,1.8,shot.z+(shot.tz-shot.z)*tail);glVertex3d(x,1.8,z);glEnd();
                glColor4f(1,1,.85f,fade);glLineWidth(3);glBegin(GL_LINES);
                glVertex3d(shot.x+(shot.tx-shot.x)*tail,1.8,shot.z+(shot.tz-shot.z)*tail);glVertex3d(x,1.8,z);glEnd();
                spark(x,1.8,z,.35,fade);
            }
        }
        for(ArenaWorld.Impact hit:world.impacts){
            if(culling&&!visible(hit.contactX,hit.contactZ,2.5,hit.height+2))continue;
            double age=.65-hit.life;
            if(age<.18){
                float fade=(float)(1-age/.18);
                spark(hit.contactX,hit.height,hit.contactZ,(hit.lethal?.65:.35)+age*3,fade);
                glLineWidth(hit.building?3:2);glBegin(GL_LINES);
                for(int i=0;i<7;i++){
                    double angle=i*Math.PI*2/7,spread=(hit.building?.8:1.2)*age*5;
                    double vx=Math.cos(angle)*spread+hit.dx*age*3,vz=Math.sin(angle)*spread+hit.dz*age*3;
                    glColor4f(1,hit.building?.65f:.9f,hit.building?.3f:.65f,fade);
                    glVertex3d(hit.contactX+vx*.45,hit.height+age*2,hit.contactZ+vz*.45);
                    glColor4f(1,.4f,.12f,0);glVertex3d(hit.contactX+vx,hit.height+age*2+Math.sin(angle)*spread*.6,hit.contactZ+vz);
                }
                glEnd();
            }
        }
        glDepthMask(true);glDisable(GL_BLEND);glEnable(GL_LIGHTING);
        for(ArenaWorld.Effect e:world.effects){
            if(culling&&!visible(e.x,e.z,e.radius+1,13))continue;
            if(e.type==3||e.type==4){glPushMatrix();if(e.type==4)glTranslated(0,meteorGround(e.x,e.z)-.16,0);slamWave(e);if(e.type==4)meteorDebris(e);glPopMatrix();continue;}
            ring(e.x,markerGround(e.x,e.z),e.z,e.radius,e.team==1?0xff4e62:e.type==2?0xffbf68:0x64e9ef);
            if(e.type==2&&e.team!=world.player.team){markerCorners(e.x,markerGround(e.x,e.z)+.01,e.z,e.radius,0xff4e62);}
            if(e.type==2){double t=1-e.life/e.duration;ring(e.x,.2,e.z,e.radius*(1-t),0xffe5ac);cube(e.x,12*(1-t),e.z,.5,.8,.5,0xffdc90);}
        }
        for(ArenaWorld.Meteor meteor:world.meteors)if(!culling||visible(meteor.x,meteor.z,ArenaWorld.METEOR_RADIUS+2,22))drawMeteor(meteor,world.elapsed);
        if(world.player.alive()&&world.screen!=ArenaWorld.Screen.TITLE){
            double markerY=Math.abs(world.player.x)<5.5&&Math.abs(world.player.z)<4.5?.67:.08;
            ring(world.player.x,markerY,world.player.z,1.35,0xffe6a1);
            ring(world.player.x,markerY+.006,world.player.z,1.17,0x4ccfe0);
            if(ArenaWorld.archer(world.player)&&(world.cooldowns[1]<=0||world.player.scatterDraw)){
                double angle=world.player.scatterDraw?Math.toRadians(world.player.attackFacing):Math.atan2(world.aimX-world.player.x,world.aimZ-world.player.z);
                glDisable(GL_LIGHTING);glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);glDepthMask(false);
                glColor4f(.4f,.85f,.95f,world.player.scatterDraw?.52f:.20f);glLineWidth(1);glBegin(GL_LINES);
                for(int i=-1;i<=1;i++){double a=angle+Math.toRadians(i*ArenaWorld.SCATTER_ANGLE);glVertex3d(world.player.x,markerY,world.player.z);glVertex3d(world.player.x+Math.sin(a)*ArenaWorld.SCATTER_RANGE,markerY,world.player.z+Math.cos(a)*ArenaWorld.SCATTER_RANGE);}
                glEnd();glBegin(GL_LINE_STRIP);
                for(int i=0;i<=24;i++){double a=angle+Math.toRadians(-ArenaWorld.SCATTER_ANGLE+i*2*ArenaWorld.SCATTER_ANGLE/24);glVertex3d(world.player.x+Math.sin(a)*ArenaWorld.SCATTER_RANGE,markerY,world.player.z+Math.cos(a)*ArenaWorld.SCATTER_RANGE);}glEnd();
                glDepthMask(true);glDisable(GL_BLEND);glEnable(GL_LIGHTING);
            }
            if(world.player.slamTime>0&&!world.player.slamLanded){
                double charge=ArenaWorld.clamp(ArenaWorld.slamAge(world.player)/ArenaWorld.SLAM_CONTACT,0,1);
                ring(world.player.x,.13,world.player.z,ArenaWorld.SLAM_RADIUS,0xbca475);
                ring(world.player.x,.16,world.player.z,.9+charge*1.1,0xffd687);
            }
            if(world.selected!=null&&world.selected.alive()){
                ArenaWorld.Unit selected=world.selected;
                double y=markerGround(selected.x,selected.z);
                double radius=selected.kind==ArenaWorld.Kind.CORE?3:selected.kind==ArenaWorld.Kind.TOWER?2.2:selected.kind==ArenaWorld.Kind.HERO?1.5:.9;
                ring(selected.x,y,selected.z,radius,0xff6675);
                markerCorners(selected.x,y+.015,selected.z,radius+.24,0xffbd9a);
            }
            markerCorners(world.player.x,markerY+.018,world.player.z,1.65,0xffe6a1);
            for(ArenaWorld.Unit tower:world.units)if(towerThreat(world,tower)){
                glDisable(GL_LIGHTING);glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);glDepthMask(false);
                glColor4f(1,.23f,.16f,(float)(.65+.2*Math.sin(world.elapsed*8)));glLineWidth(3);glBegin(GL_LINES);
                glVertex3d(tower.x,4.3,tower.z);glVertex3d(world.player.x,1.7,world.player.z);glEnd();
                glDepthMask(true);glDisable(GL_BLEND);glEnable(GL_LIGHTING);
                markerCorners(world.player.x,markerY+.025,world.player.z,2.05,0xff6552);
            }
            if(world.moving)ring(world.destX,.12,world.destZ,.5,0xf1d28d);
            if(world.recall>0)ring(world.player.x,.13,world.player.z,2+(4-world.recall)*.5,0x85d9ff);
            ring(world.aimX,.09,world.aimZ,.38,0xded7aa);
        }
        glEnable(GL_LIGHTING);
        if(profile){glFinish();effectNanos=System.nanoTime()-stage;stage=System.nanoTime();}
        glReadBuffer(GL_COLOR_ATTACHMENT0);pixels.clear();glReadPixels(0,0,width,height,GL_BGRA,GL_UNSIGNED_INT_8_8_8_8_REV,pixels);
        int[] rgb=((DataBufferInt)image.getRaster().getDataBuffer()).getData();
        // Native packed ARGB matches TYPE_INT_RGB storage; only the row order needs flipping.
        for(int y=0;y<height;y++){packedPixels.position((height-1-y)*width);packedPixels.get(rgb,y*width,width);}
        int err=glGetError();if(err!=GL_NO_ERROR)throw new IllegalStateException("OpenGL error: "+err);if(profile)readbackNanos=System.nanoTime()-stage;return image;
    }
    private void updateCanopies(ArenaWorld world){
        double dt=Double.isNaN(canopyTime)?-1:world.elapsed-canopyTime;fadedTrees=0;
        for(Canopy tree:canopies){
            double playerY=-world.player.z*SIN+2*COS,treeY=-tree.z*SIN+tree.height*.62*COS;
            boolean blocks=world.player.alive()&&world.screen!=ArenaWorld.Screen.TITLE&&Math.abs(tree.x-world.player.x)<tree.height*.43+.65&&Math.abs(treeY-playerY)<tree.height*.32+1;
            double target=blocks?.18:1;
            if(dt<0||dt>.5)tree.alpha=target;
            else tree.alpha+=ArenaWorld.clamp(target-tree.alpha,-dt*3.4,dt*3.4);
            if(tree.alpha<.999)fadedTrees++;
        }
        canopyTime=world.elapsed;
    }
    private int[] canopyPatch(Canopy tree){
        double scale=height/VIEW_HEIGHT*zoom,cy=-cameraZ*SIN;
        return new int[]{Math.max(0,(int)Math.floor(width/2.+(tree.minX-cameraX)*scale)-2),Math.max(0,(int)Math.floor(height/2.+(tree.minY-cy)*scale)-2),Math.min(width,(int)Math.ceil(width/2.+(tree.maxX-cameraX)*scale)+2),Math.min(height,(int)Math.ceil(height/2.+(tree.maxY-cy)*scale)+2)};
    }
    private boolean overlapsPatch(Canopy tree){
        if(!cachedScene)return true;if(fadedTrees==0)return false;
        for(Canopy faded:canopies)if(faded.alpha<.999&&tree.maxX>=faded.minX-.2&&tree.minX<=faded.maxX+.2&&tree.maxY>=faded.minY-.2&&tree.minY<=faded.maxY+.2)return true;
        return false;
    }
    private void drawCanopies(boolean translucent){
        if(translucent){
            // Resolve the nearest leaf first: overlapping crowns must not accumulate to opaque.
            glColorMask(false,false,false,false);glDepthMask(true);
            for(Canopy tree:canopies)if(tree.alpha<.999&&(!culling||visible(tree.x,tree.z,tree.height*.65,tree.height)))drawModel(tree.model,tree.x,0,tree.z,tree.height,tree.angle,0xffffff);
            glColorMask(true,true,true,true);glDepthFunc(GL_EQUAL);glEnable(GL_BLEND);glBlendColor(1,1,1,1);glBlendFunc(GL_CONSTANT_ALPHA,GL_ONE_MINUS_CONSTANT_ALPHA);glDepthMask(false);
        }
        for(Canopy tree:canopies){
            if(!translucent&&!overlapsPatch(tree))continue;
            if((tree.alpha<.999)!=translucent||culling&&!visible(tree.x,tree.z,tree.height*.65,tree.height))continue;
            if(translucent)glBlendColor(1,1,1,(float)tree.alpha);
            drawModel(tree.model,tree.x,0,tree.z,tree.height,tree.angle,0xffffff);
        }
        if(translucent){glDepthFunc(GL_LESS);glDepthMask(true);glDisable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);}
    }
    boolean visible(double x,double z,double radius,double tall){
        double halfWidth=VIEW_HEIGHT/zoom/2*width/(double)height,sy=-(z-cameraZ)*SIN;
        return x+radius>=cameraX-halfWidth&&x-radius<=cameraX+halfWidth&&sy+radius*SIN+tall*COS>=-VIEW_HEIGHT/zoom/2&&sy-radius*SIN-.5<=VIEW_HEIGHT/zoom/2;
    }
    private void copyScene(){
        if(!sceneReady){
            glBindFramebuffer(GL_FRAMEBUFFER,sceneFbo);glViewport(0,0,sceneWidth,sceneHeight);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
            glMatrixMode(GL_PROJECTION);glPushMatrix();glLoadIdentity();double scale=height/VIEW_HEIGHT*zoom;
            glOrtho(-sceneWidth/(2*scale),sceneWidth/(2*scale),-sceneHeight/(2*scale),sceneHeight/(2*scale),-100,100);
            glMatrixMode(GL_MODELVIEW);glPushMatrix();glLoadIdentity();glRotated(TILT,1,0,0);
            glLightfv(GL_LIGHT0,GL_POSITION,new float[]{-20,50,25,0});glCallList(landscape);
            glBindFramebuffer(GL_READ_FRAMEBUFFER,sceneFbo);glBindFramebuffer(GL_DRAW_FRAMEBUFFER,bareFbo);
            glBlitFramebuffer(0,0,sceneWidth,sceneHeight,0,0,sceneWidth,sceneHeight,GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT,GL_NEAREST);
            glBindFramebuffer(GL_FRAMEBUFFER,sceneFbo);
            for(Canopy tree:canopies)drawModel(tree.model,tree.x,0,tree.z,tree.height,tree.angle,0xffffff);
            glPopMatrix();glMatrixMode(GL_PROJECTION);glPopMatrix();glMatrixMode(GL_MODELVIEW);
            sceneReady=true;
        }
        glBindFramebuffer(GL_READ_FRAMEBUFFER,sceneFbo);glBindFramebuffer(GL_DRAW_FRAMEBUFFER,fbo);
        int x=sceneMarginX+(int)Math.rint(cameraX*height/VIEW_HEIGHT*zoom),y=sceneMarginY-(int)Math.rint(cameraZ*SIN*height/VIEW_HEIGHT*zoom);
        glBlitFramebuffer(x,y,x+width,y+height,0,0,width,height,GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT,GL_NEAREST);
        if(fadedTrees>0){
            glBindFramebuffer(GL_READ_FRAMEBUFFER,bareFbo);
            for(Canopy tree:canopies)if(tree.alpha<.999){
                int[] patch=canopyPatch(tree);if(patch[2]<=patch[0]||patch[3]<=patch[1])continue;
                glBlitFramebuffer(x+patch[0],y+patch[1],x+patch[2],y+patch[3],patch[0],patch[1],patch[2],patch[3],GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT,GL_NEAREST);
            }
        }
        glBindFramebuffer(GL_FRAMEBUFFER,fbo);glViewport(0,0,width,height);
        // Light was transformed under the cache camera on its first draw; restore the live view.
        glLightfv(GL_LIGHT0,GL_POSITION,new float[]{-20,50,25,0});
    }
    private void world(double time){
        cube(0,-2.1,0,91,3,43,0x394b38);
        // A continuous textured surface, with irregular dirt shoulders instead of road markings.
        surface("grass",-45,-4.6,-21,21,0,5,0xc5d2a3);
        surface("grass",4.6,45,-21,21,0,5,0xc5d2a3);
        for(int x=-45;x<45;x++){
            if(x>=-5&&x<5)continue;
            roadRibbon("dirt",x,x+1,-shoulder(x),-shoulder(x+1),shoulder(x),shoulder(x+1),.012,4,0xc6bd94,false);
            roadRibbon("stone",x,x+1,-roadEdge(x),-roadEdge(x+1),roadEdge(x),roadEdge(x+1),.025,5,0xd8d5be,false);
            for(int side=-1;side<=1;side+=2){
                roadRibbon("stone",x,x+1,side*roadEdge(x),side*roadEdge(x+1),side*(roadEdge(x)+.85),side*(roadEdge(x+1)+.85),.028,5,0xd8d5be,true);
                roadRibbon("dirt",x,x+1,side*shoulder(x),side*shoulder(x+1),side*(shoulder(x)+.95),side*(shoulder(x+1)+.95),.018,4,0xc6bd94,true);
            }
        }
        // Lowered river bed, sloped textured banks, and a shallow-to-deep water gradient.
        surface("gravel",-4.6,4.6,-21,21,-.85,4,0x82978a);
        for(int side=-1;side<=1;side+=2){
            bank(side);
            for(int i=0;i<6;i++){
                double z=-18+i*7.1;if(Math.abs(z)<6)continue;
                shadow(side*5.25,.035,z,.8,1.2);
                drawModel("cliff",side*5.1,-.65,z,1.6+(i%2)*.3,side<0?90:270,0xd3d7bc);
                drawModel("grass",side*6.4,0,z+1.6,.7,i*47,0xffffff);
            }
        }
        riverSurface();
        for(int side=-1;side<=1;side+=2){
            // Small stones and reeds break the bank silhouette without entering the lane.
            for(int i=0;i<19;i++){
                double z=-19+i*2.05;if(Math.abs(z)<5.5)continue;
                double edge=bankEdge(z,side);
                if(i%3==0){
                    drawModel("roundrock",side*(edge-.65),-.40,z,.35+(i%4)*.09,i*67,0xaaa994);
                    drawModel("grass",side*(edge+.18),0,z+.3,.42+(i%3)*.1,i*137,0xdce4bd);
                }
            }
            // Low stone abutments support the bridge ends; the playable deck stays clear.
            for(int end=-1;end<=1;end+=2){
                cube(side*5.0,-.34,end*3.8,1.4,.72,.9,0x827f69);
                drawModel("rock",side*5.4,-.1,end*4.45,.58,end*side*57,0xc2bca1);
            }
            surface("gravel",side<0?-6.3:4.7,side<0?-4.7:6.3,-3.6,3.6,.031,3,0xc4bea4);
        }
        drawModel("bridge",0,-.65,0,1,0,0xffffff);
        for(int side=-1;side<=1;side+=2){
            int team=side<0?0x45cbd2:0xe16c72;
            cylinder(side*35,.10,0,6,.20,0x7b806b,24);
            cylinder(side*35,.23,0,5.65,.12,0x566b65,24);
            ring(side*35,.30,0,5.35,0xb1a77b);ring(side*35,.31,0,4.95,team);
            drawModel("well",side*38,.3,-3,2.6,side*90,0xffffff);
            for(int i=0;i<8;i++){double a=i*Math.PI/4;
                drawModel("runestone",side*35+Math.cos(a)*5.6,.2,Math.sin(a)*5.6,.7,i*45,0xcbd1b2);
            }
            for(int i=-1;i<=1;i+=2){cube(side*40,1.4,i*7,1,2.8,1,0x4c6365);crystal(side*40,3.3,i*7,.6,1,team,time*18);}
        }
        // Model decorations are compiled once. The playable lane and both fountains stay clear.
        for(int i=0;i<36;i++){
            double x=-42+(i*17%85),z=(i%2==0?-1:1)*(12+(i*7%5)*1.35);if(Math.abs(x)<7)continue;
            shadow(x+1.2,.032,z-1.1,3.5,2.2,.18);
            shadow(x,.038,z,.85,.65,.44);
            // Crowns are drawn separately so only obstructing trees fade.
        }
        for(int i=0;i<28;i++){
            double x=-41+i*3.1,z=(i%2==0?-1:1)*(7.5+(i%3)*.65);
            if(Math.abs(x)<7)continue;
            double rockSize=1+(i%4)*.3;
            shadow(x+.3,.035,z-.2,rockSize*.9,rockSize*.65,.23);
            shadow(x,.045,z,rockSize*.53,rockSize*.4,.43);
            drawModel(i%3==0?"rockpile":i%3==1?"roundrock":"rock",x,-.12,z,1+(i%4)*.3,i*37,0xffffff);
            shadow(x+.8,.04,z+.6,.35,.25,.3);
            drawModel("rock",x+.8,-.1,z+.6,.45,i*71,0xe2e0c9);
            drawModel(i%3==0?"flowers":i%3==1?"grass":"mushrooms",x+.8,0,z+Math.signum(z)*1.3,i%3==0?.75:i%3==1?.6:.5,i*29,0xffffff);
        }
        // Sparse roadside clumps and gravel sit outside the clear combat lane.
        for(int i=0;i<48;i++){
            double x=-42+i*1.79;if(Math.abs(x)<7||Math.abs(x)>39)continue;
            int side=i%2==0?-1:1;double z=side*(shoulder(x)+.65+(i%3)*.24);
            if(i%3==0){shadow(x,.04,z,.3,.2,.27);drawModel("roundrock",x,-.04,z,.23+(i%4)*.06,i*71,0xc9c6ae);}
            else drawModel(i%7==0?"flowers":"grass",x,0,z,i%7==0?.42:.33+(i%4)*.06,i*137,0xe1e6cf);
        }
        shadow(-30,.035,-15,3.1,2.1,.27);shadow(32,.035,15,3.1,2.1,.27);
        shadow(-12,.04,9.5,1.2,.8,.36);shadow(12,.04,-9.5,1.2,.8,.36);
        drawModel("cabin",-31,0,-15,5.2,30,0xffffff);
        drawModel("cabin",31,0,15,5.2,210,0xffffff);
        drawModel("swordstone",-12,0,9.5,2.5,-30,0xffffff);
        drawModel("swordstone",12,0,-9.5,2.5,150,0xffffff);
    }
    /** Forest-pack stonework with procedural faction metalwork and energy. */
    private void building(ArenaWorld.Unit u,double time,int team){
        boolean core=u.kind==ArenaWorld.Kind.CORE;
        double r=core?2.65:1.85;
        cylinder(u.x,.22,u.z,r,.40,0x727b69,8);
        cylinder(u.x,.48,u.z,r*.87,.18,0xb5ad87,8);
        if(!u.alive()){
            for(int i=0;i<6;i++){double a=i*2.399;
                drawModel("roundrock",u.x+Math.cos(a)*(r*.6),.45,u.z+Math.sin(a)*(r*.6),.45+(i%3)*.22,i*63,0x999889);
            }
            double age=Math.max(0,time-u.deathAt);
            if(age<.9){glDisable(GL_LIGHTING);glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE);glDepthMask(false);
                spark(u.x,.8+age*1.5,u.z,1+age*4,(float)(1-age/.9));
                glDepthMask(true);glDisable(GL_BLEND);glEnable(GL_LIGHTING);}
            return;
        }
        int stone=u.hitFlash>0?0xffddb0:0xffffff;
        if(core){
            cylinder(u.x,.85,u.z,1.8,.65,0x51665e,8);
            for(int i=0;i<3;i++){double a=i*Math.PI*2/3;
                drawModel("runestone",u.x+Math.cos(a)*2,.5,u.z+Math.sin(a)*2,1.9,i*120,stone);
            }
        }else{
            drawModel("watchpillar",u.x,.52,u.z,3.25,0,stone);
            cylinder(u.x,3.50,u.z,1.32,.25,0xa69b6c,8);
            for(int i=0;i<4;i++){double a=i*Math.PI/2+Math.PI/4;
                cube(u.x+Math.cos(a)*1.15,3.9,u.z+Math.sin(a)*1.15,.40,.75,.40,0x7c8469);
            }
        }
        double y=core?3.05:4.05;
        double pulse=1+.055*Math.sin(time*2.5+u.team);
        // Tower recoil and brighter energy follow its existing attack cooldown.
        double firing=core?0:Math.max(0,(u.cooldown-.75)/.25);
        crystal(u.x,y+Math.sin(time*2)*.12-firing*.14,u.z,(core?1.05:.62)*pulse,core?1.9:1.1,u.hitFlash>0?0xffecc4:team,time*(core?18:10));
        ring(u.x,core?1.25:3.67,u.z,core?1.65:1.02,team);
        if(u.hp<u.maxHp*.5){
            // Broken gold bands and dark fissures communicate damage without changing collision.
            glDisable(GL_LIGHTING);color(0x302f29);glLineWidth(3);glBegin(GL_LINE_STRIP);
            glVertex3d(u.x-.65,.6,u.z+r*.7);glVertex3d(u.x-.2,1.05,u.z+r*.6);glVertex3d(u.x-.45,1.5,u.z+r*.4);glEnd();glEnable(GL_LIGHTING);
        }
        glDisable(GL_LIGHTING);glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE);glDepthMask(false);
        for(int i=0;i<5;i++){double a=time*.65+i*Math.PI*2/5;
            spark(u.x+Math.cos(a)*(core?1.5:.85),y+.35+Math.sin(a*2)*.25,u.z+Math.sin(a)*(core?1.5:.85),.07+firing*.08,.45f);
        }
        if(firing>0)spark(u.x,y,u.z,.65+firing*.35,(float)firing);
        glDepthMask(true);glDisable(GL_BLEND);glEnable(GL_LIGHTING);
    }
    private void sanctuaryMotion(double time){
        glDisable(GL_LIGHTING);glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE);glDepthMask(false);
        for(int side=-1;side<=1;side+=2)for(int i=0;i<12;i++){
            double phase=(time*.23+i/12.0)%1,a=i*2.399+time*.12;
            glColor4f(side<0?.25f:1,.65f,side<0?1:.4f,(float)(Math.sin(phase*Math.PI)*.38));
            double x=side*35+Math.cos(a)*4.7,z=Math.sin(a)*4.7,y=.4+phase*1.7;
            glBegin(GL_LINES);glVertex3d(x,y,z);glVertex3d(x,y+.18,z);glEnd();
        }
        glDepthMask(true);glDisable(GL_BLEND);glEnable(GL_LIGHTING);
    }
    private double markerGround(double x,double z){return Math.abs(x)<5.5&&Math.abs(z)<4.5?.68:.085;}
    private void markerCorners(double x,double y,double z,double radius,int tint){
        glDisable(GL_LIGHTING);color(tint);glLineWidth(3);glBegin(GL_LINES);
        for(int i=0;i<4;i++){double a=i*Math.PI/2,dx=Math.cos(a),dz=Math.sin(a);
            glVertex3d(x+dx*radius,y,z+dz*radius);glVertex3d(x+dx*(radius+.32)-dz*.22,y,z+dz*(radius+.32)+dx*.22);
            glVertex3d(x+dx*radius,y,z+dz*radius);glVertex3d(x+dx*(radius+.32)+dz*.22,y,z+dz*(radius+.32)-dx*.22);
        }glEnd();glEnable(GL_LIGHTING);
    }
    private void unit(ArenaWorld.Unit u,double time){
        int team=u.team==0?0x54d9e1:0xf1777e;
        double ground=Math.abs(u.x)<5.5&&Math.abs(u.z)<4.5?.64:.055;
        boolean mobile=u.kind==ArenaWorld.Kind.HERO||u.kind==ArenaWorld.Kind.SOLDIER;
        double radius=u.kind==ArenaWorld.Kind.HERO?1.1:u.kind==ArenaWorld.Kind.SOLDIER?.62:2.5;
        if(u.alive()||!mobile){
            shadow(u.x+.28,ground,u.z-.22,radius*1.25,radius*.65,.19);
            shadow(u.x,ground+.002,u.z,radius*.6,radius*.38,.40);
        }
        if(u.alive()&&u.kind==ArenaWorld.Kind.HERO){ring(u.x,ground+.012,u.z,1.28,u.team==0?0x4ddcff:0xff5b70);markerCorners(u.x,ground+.015,u.z,1.52,u.team==0?0x66efff:0xff6b78);}
        if(!mobile){building(u,time,team);return;}
        double size=u.kind==ArenaWorld.Kind.HERO?4:2.3;
        String character=u.kind==ArenaWorld.Kind.SOLDIER?"soldier":u.build==0?"warrior":u.build==1?"archer":"mage";
        ArenaRig rig=rigs.get(character);
        ArenaAnimator animator=animators.get(u);
        if(animator==null||animator.rig!=rig){animator=new ArenaAnimator(rig);animators.put(u,animator);}
        animator.update(u,time);
        double recoil=u.alive()?Math.sin(Math.PI*ArenaWorld.clamp(1-u.hurtTime/ArenaWorld.HURT_SECONDS,0,1))*.18:0;
        glPushMatrix();glTranslated(u.x+u.hitDX*recoil,.03,u.z+u.hitDZ*recoil);glRotated(animator.renderFacing,0,1,0);
        if(u.dashTime>0)glRotated((ArenaWorld.archer(u)?-8:10)*Math.min(1,u.dashTime/.06),1,0,0);
        glScaled(size/rig.height(),size/rig.height(),size/rig.height());
        glTranslated(-(rig.minX+rig.maxX)/2,-rig.minY,-(rig.minZ+rig.maxZ)/2);
        color(u.hitFlash>0?0xffd5a0:0xffffff);if(u.hitFlash>0)glDisable(GL_LIGHTING);
        glEnable(GL_TEXTURE_2D);glBindTexture(GL_TEXTURE_2D,textures.get("textures/DWF_Units_"+(u.team==0?"Blue":"Red")+".png"));
        glEnableClientState(GL_VERTEX_ARRAY);glEnableClientState(GL_NORMAL_ARRAY);glEnableClientState(GL_TEXTURE_COORD_ARRAY);
        for(int i=0;i<rig.meshes.size();i++){
            FloatBuffer buffer=animator.buffers.get(i);
            buffer.position(0);glVertexPointer(3,GL_FLOAT,32,buffer);
            buffer.position(3);glNormalPointer(GL_FLOAT,32,buffer);
            buffer.position(6);glTexCoordPointer(2,GL_FLOAT,32,buffer);
            glDrawElements(GL_TRIANGLES,rig.meshes.get(i).indexBuffer);buffer.position(0);
        }
        glDisableClientState(GL_TEXTURE_COORD_ARRAY);glDisableClientState(GL_NORMAL_ARRAY);glDisableClientState(GL_VERTEX_ARRAY);
        glDisable(GL_TEXTURE_2D);glEnable(GL_LIGHTING);
        if(u.alive()&&ArenaWorld.swordsman(u)&&(u.attackTime>0||u.slamTime>0))drawSwordTrail(rig,u);
        glPopMatrix();
        if(u.alive())ring(u.x,.08,u.z,u.kind==ArenaWorld.Kind.HERO?1.05:.55,team);
    }

    private void dashGhost(ArenaWorld.DashGhost ghost){
        ArenaAnimator animator=animators.get(ghost.source);if(animator==null)return;
        ArenaRig rig=animator.rig;double size=ghost.source.kind==ArenaWorld.Kind.HERO?4:2.3;
        glPushMatrix();glTranslated(ghost.x,.03,ghost.z);glRotated(ghost.facing,0,1,0);glRotated(ArenaWorld.archer(ghost.source)?-8:10,1,0,0);
        glScaled(size/rig.height(),size/rig.height(),size/rig.height());glTranslated(-(rig.minX+rig.maxX)/2,-rig.minY,-(rig.minZ+rig.maxZ)/2);
        glDisable(GL_LIGHTING);glDisable(GL_TEXTURE_2D);glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);glDepthMask(false);
        glColor4f(.25f,.8f,1,(float)(ghost.life/.10*.07));glEnableClientState(GL_VERTEX_ARRAY);
        for(int i=0;i<rig.meshes.size();i++){
            FloatBuffer buffer=animator.buffers.get(i);buffer.position(0);glVertexPointer(3,GL_FLOAT,32,buffer);
            glDrawElements(GL_TRIANGLES,rig.meshes.get(i).indexBuffer);
        }
        glDisableClientState(GL_VERTEX_ARRAY);glDepthMask(true);glDisable(GL_BLEND);glEnable(GL_LIGHTING);glPopMatrix();
    }
    private void drawSwordTrail(ArenaRig rig,ArenaWorld.Unit unit){
        double phase=unit.slamTime>0?ArenaWorld.slamPosePhase(ArenaWorld.slamAge(unit)):1-unit.attackTime/ArenaWorld.SWORD_ATTACK_SECONDS;
        if(rig.weaponPath==null||phase<.22||phase>.46)return;
        double end=Math.min(.36,phase),start=Math.max(.22,end-.12),fade=phase>.36?Math.max(0,1-(phase-.36)/.1):1;
        double[] point=new double[6];
        glDisable(GL_LIGHTING);glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE);glDepthMask(false);
        glBegin(GL_QUAD_STRIP);
        for(int i=0;i<=16;i++){
            double p=start+(end-start)*i/16;rig.weaponPoint(p,point,unit.slamTime<=0&&unit.comboStep==1);float alpha=(float)(i/16.0*fade);
            glColor4f(.35f,.75f,1,alpha*.15f);glVertex3d(point[0],point[1],point[2]);
            glColor4f(1,.9f,.55f,alpha*.85f);glVertex3d(point[3],point[4],point[5]);
        }
        glEnd();glLineWidth(2);glBegin(GL_LINE_STRIP);
        for(int i=0;i<=16;i++){rig.weaponPoint(start+(end-start)*i/16,point,unit.slamTime<=0&&unit.comboStep==1);glColor4f(1,1,.85f,(float)(i/16.0*fade));glVertex3d(point[3],point[4],point[5]);}
        glEnd();glDepthMask(true);glDisable(GL_BLEND);glEnable(GL_LIGHTING);
    }
    /** Expanding ground wave and short dust rays, all generated geometry. */
    private double meteorGround(double x,double z){return Math.abs(x)<5.5&&Math.abs(z)<4.5?.72:.16;}
    private void meteorGlow(double x,double y,double z,double radius,float alpha){
        glDisable(GL_LIGHTING);glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE);glDepthMask(false);
        spark(x,y,z,radius,alpha);
        glDepthMask(true);glDisable(GL_BLEND);glEnable(GL_LIGHTING);glLineWidth(2);
    }
    private void drawMeteor(ArenaWorld.Meteor meteor,double now){
        double p=meteor.progress(now),radius=ArenaWorld.METEOR_RADIUS;
        // Exact damage boundary remains visible throughout; the arc counts down to impact.
        glPushMatrix();glTranslated(0,meteorGround(meteor.x,meteor.z)-.16,0);
        ring(meteor.x,.16,meteor.z,radius,0xff934d);
        glDisable(GL_LIGHTING);glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE);glDepthMask(false);
        glBegin(GL_TRIANGLE_FAN);glColor4f(1,.28f,.04f,(float)(.05+.09*p));glVertex3d(meteor.x,.14,meteor.z);
        for(int i=0;i<=64;i++){double a=i*Math.PI/32;glColor4f(1,.35f,.06f,(float)(.06+.10*p));glVertex3d(meteor.x+Math.cos(a)*radius,.14,meteor.z+Math.sin(a)*radius);}glEnd();
        glLineWidth(4);glColor4f(1,.85f,.35f,.95f);glBegin(GL_LINE_STRIP);
        for(int i=0;i<=64;i++){double a=-Math.PI/2+2*Math.PI*p*i/64;glVertex3d(meteor.x+Math.cos(a)*(radius+.16),.19,meteor.z+Math.sin(a)*(radius+.16));}glEnd();
        glDepthMask(true);glDisable(GL_BLEND);glEnable(GL_LIGHTING);glLineWidth(2);
        glPopMatrix();
        if(!meteor.released())return;
        double flight=ArenaWorld.clamp((now-meteor.launchedAt)/ArenaWorld.METEOR_FLIGHT,0,1),y=.8+18*(1-flight*flight);
        shadow(meteor.x,.12,meteor.z,.7+flight*1.2,.7+flight*1.2);
        for(int i=4;i>=1;i--)meteorGlow(meteor.x-.10*i,y+i*1.1,meteor.z,.5+.10*i,(float)(.18*(1-i/5.0)));
        glPushMatrix();glTranslated(meteor.x,y,meteor.z);glRotated(now*180,1,.4,.2);
        cone(0,0,0,1,1.3,0xd26429,7);glRotated(180,1,0,0);cone(0,0,0,1,.9,0x763c26,7);glPopMatrix();
        meteorGlow(meteor.x,y,meteor.z,1.35,.32f);
    }
    private void meteorDebris(ArenaWorld.Effect effect){
        double t=1-effect.life/effect.duration;
        for(int i=0;i<14;i++){
            double a=i*2.39996,reach=(.4+t*4)*(1+(i%3)*.15),size=(1-t)*(.16+(i%3)*.08);
            double x=effect.x+Math.cos(a)*reach,z=effect.z+Math.sin(a)*reach,y=.2+Math.sin(Math.PI*t)*(1+(i%4)*.35);
            glPushMatrix();glTranslated(x,y,z);glRotated(i*41+t*300,1,0,1);cube(0,0,0,size,size*.8,size,0x947354);glPopMatrix();
        }
        if(t<.32)meteorGlow(effect.x,.5,effect.z,1+t*8,(float)(1-t/.32));
    }
    private void slamWave(ArenaWorld.Effect effect){
        double age=effect.duration-effect.life,t=age/effect.duration;
        double radius=effect.radius*Math.min(1,age/.22),width=.3+.65*t;
        glDisable(GL_LIGHTING);glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE);glDepthMask(false);
        glBegin(GL_QUAD_STRIP);
        for(int i=0;i<=64;i++){
            double a=i*Math.PI/32,c=Math.cos(a),sn=Math.sin(a);
            glColor4f(1,.72f,.3f,(float)((1-t)*.65));glVertex3d(effect.x+c*radius,.17,effect.z+sn*radius);
            glColor4f(1,.45f,.12f,0);glVertex3d(effect.x+c*Math.max(0,radius-width),.15,effect.z+sn*Math.max(0,radius-width));
        }
        glEnd();
        for(int i=0;i<12;i++){
            double a=i*Math.PI/6,reach=effect.radius*(.25+.75*t),x=effect.x+Math.cos(a)*reach,z=effect.z+Math.sin(a)*reach;
            double y=.2+Math.sin(Math.PI*t)*(.3+(i%3)*.18),size=(1-t)*.18;
            glColor4f(1,.76f,.38f,(float)((1-t)*.7));glBegin(GL_TRIANGLES);
            glVertex3d(x-size,y,z);glVertex3d(x+size,y,z);glVertex3d(x,y+size*2,z);glEnd();
        }
        if(age<.12)spark(effect.x,.3,effect.z,.4+age*8,(float)(1-age/.12));
        glDepthMask(true);glDisable(GL_BLEND);glEnable(GL_LIGHTING);
    }
    private static boolean isCharacter(String name){
        return name.equals("warrior")||name.equals("archer")||name.equals("mage")||name.equals("soldier");
    }
    private String texture(String model,ArenaAssets.Part part,int team){
        if(isCharacter(model))return "textures/DWF_Units_"+(team==0?"Blue":"Red")+".png";
        if(model.equals("butterfly"))return "textures/butterfly.png";
        if(part.leaves)return model.equals("birch")?"textures/leafPatch_01.png":"textures/leafPatch_04.png";
        if(model.equals("grass"))return "textures/Grass_Mid_01.png";
        if(model.equals("flowers"))return "textures/WildFlowers_01.png";
        if(part.name.contains("Branches"))return "textures/Branches_01.png";
        return "textures/PolygonNatureBiomes_Meadow_Texture_01.png";
    }
    private int loadTexture(String path)throws IOException{
        Integer old=textures.get(path);if(old!=null)return old;
        BufferedImage image;try(InputStream in=assets.resource(path)){image=ImageIO.read(in);}if(image==null)throw new IOException("无法读取模型贴图："+path);
        ByteBuffer data=BufferUtils.createByteBuffer(image.getWidth()*image.getHeight()*4);
        for(int y=image.getHeight()-1;y>=0;y--)for(int x=0;x<image.getWidth();x++){int rgb=image.getRGB(x,y);data.put((byte)(rgb>>16)).put((byte)(rgb>>8)).put((byte)rgb).put((byte)(rgb>>24));}data.flip();
        int id=glGenTextures();textures.put(path,id);glBindTexture(GL_TEXTURE_2D,id);
        glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,image.getWidth(),image.getHeight(),0,GL_RGBA,GL_UNSIGNED_BYTE,data);
        glGenerateMipmap(GL_TEXTURE_2D);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR_MIPMAP_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_REPEAT);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_REPEAT);return id;
    }
    private void compile(String name,ArenaAssets.Model model,int team)throws IOException{
        java.util.List<Integer> ids=new ArrayList<>();for(ArenaAssets.Part part:model.parts)ids.add(loadTexture(texture(name,part,team)));
        int id=glGenLists(1);if(id==0)throw new IOException("无法缓存人物模型："+name);models.put(team<0?name:name+"_"+team,id);modelTriangles+=model.triangles;
        double dimension=model.height();
        if(name.equals("butterfly"))dimension=Math.max(dimension,Math.max(model.maxX-model.minX,model.maxZ-model.minZ));
        if(name.equals("flowers"))dimension=Math.max(model.maxX-model.minX,model.maxZ-model.minZ)/3;
        for(Canopy tree:canopies)if(tree.model.equals(name)){
            tree.minX=tree.minY=Double.POSITIVE_INFINITY;tree.maxX=tree.maxY=Double.NEGATIVE_INFINITY;
            double a=Math.toRadians(tree.angle),scale=tree.height/model.height();
            for(ArenaAssets.Part part:model.parts)for(int i=0;i<part.vertices.length;i+=8){
                double px=(part.vertices[i]-(model.minX+model.maxX)/2)*scale,py=(part.vertices[i+1]-model.minY)*scale,pz=(part.vertices[i+2]-(model.minZ+model.maxZ)/2)*scale;
                double wx=tree.x+px*Math.cos(a)+pz*Math.sin(a),wz=tree.z-px*Math.sin(a)+pz*Math.cos(a),sy=-wz*SIN+py*COS;
                tree.minX=Math.min(tree.minX,wx);tree.maxX=Math.max(tree.maxX,wx);tree.minY=Math.min(tree.minY,sy);tree.maxY=Math.max(tree.maxY,sy);
            }
        }
        glNewList(id,GL_COMPILE);glPushMatrix();
        if(name.equals("bridge")){
            // Fit the bridge footprint to the lane; preserve the source mesh and material UVs.
            boolean alongX=model.maxX-model.minX>=model.maxZ-model.minZ;
            if(!alongX)glRotated(90,0,1,0);
            glScaled((alongX?11:9)/(model.maxX-model.minX),1.25/dimension,(alongX?9:11)/(model.maxZ-model.minZ));
        }else glScaled(1/dimension,1/dimension,1/dimension);glTranslated(-(model.minX+model.maxX)/2,-model.minY,-(model.minZ+model.maxZ)/2);
        glEnable(GL_TEXTURE_2D);glEnable(GL_ALPHA_TEST);glAlphaFunc(GL_GREATER,.4f);
        int index=0;for(ArenaAssets.Part part:model.parts){glBindTexture(GL_TEXTURE_2D,ids.get(index++));glBegin(GL_TRIANGLES);
            for(int i=0;i<part.vertices.length;i+=8){float[] v=part.vertices;glNormal3f(v[i+3],v[i+4],v[i+5]);glTexCoord2f(v[i+6],v[i+7]);glVertex3f(v[i],v[i+1],v[i+2]);}glEnd();}
        glDisable(GL_ALPHA_TEST);glDisable(GL_TEXTURE_2D);glPopMatrix();glEndList();
    }
    private void drawModel(String name,double x,double y,double z,double height,double angle,int tint){
        glPushMatrix();glTranslated(x,y,z);glRotated(angle,0,1,0);glScaled(height,height,height);color(tint);glCallList(models.get(name));glPopMatrix();
    }

    private void spark(double x,double y,double z,double radius,float alpha){
        glColor4f(1,.86f,.45f,alpha);glLineWidth(3);glBegin(GL_LINES);
        for(int i=0;i<8;i++){
            double a=i*Math.PI/4;
            glVertex3d(x+Math.cos(a)*radius*.2,y,z+Math.sin(a)*radius*.2);
            glVertex3d(x+Math.cos(a)*radius,y+((i%2)*.5),z+Math.sin(a)*radius);
        }glEnd();
        glBegin(GL_TRIANGLE_FAN);glColor4f(1,1,.85f,alpha);glVertex3d(x,y,z);
        for(int i=0;i<=16;i++){double a=i*Math.PI/8;glColor4f(1,.65f,.2f,0);glVertex3d(x+Math.cos(a)*radius*.65,y,z+Math.sin(a)*radius*.65);}glEnd();
    }
    private void surface(String texture,double x0,double x1,double z0,double z1,double y,double tile,int tint){
        glEnable(GL_TEXTURE_2D);glBindTexture(GL_TEXTURE_2D,textures.get("textures/ground-"+texture+".png"));
        color(tint);glNormal3d(0,1,0);glBegin(GL_QUADS);
        groundVertex(x0,y,z0,tile);groundVertex(x0,y,z1,tile);groundVertex(x1,y,z1,tile);groundVertex(x1,y,z0,tile);
        glEnd();glDisable(GL_TEXTURE_2D);
    }
    private void groundVertex(double x,double y,double z,double tile){glTexCoord2d(x/tile,z/tile);glVertex3d(x,y,z);}
    private double roadEdge(double x){return 3.55+.20*Math.sin(x*.61)+.12*Math.sin(x*1.73);}
    private double shoulder(double x){return 5.1+.28*Math.sin(x*.73)+.14*Math.sin(x*1.91);}
    /** Matching endpoints avoid seams; fading fringe keeps the dirt and stone textures visible. */
    private void roadRibbon(String texture,double x0,double x1,double inner0,double inner1,double outer0,double outer1,double y,double tile,int tint,boolean fade){
        glEnable(GL_TEXTURE_2D);glBindTexture(GL_TEXTURE_2D,textures.get("textures/ground-"+texture+".png"));
        if(fade){glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);glDepthMask(false);}
        float r=(tint>>16&255)/255f,g=(tint>>8&255)/255f,b=(tint&255)/255f;
        // Keep upward winding on both shoulders so two-sided lighting does not darken the road.
        if(outer0<inner0){double swap=x0;x0=x1;x1=swap;swap=inner0;inner0=inner1;inner1=swap;swap=outer0;outer0=outer1;outer1=swap;}
        glNormal3d(0,1,0);glBegin(GL_QUADS);glColor4f(r,g,b,1);groundVertex(x0,y,inner0,tile);
        glColor4f(r,g,b,fade?0:1);groundVertex(x0,y,outer0,tile);groundVertex(x1,y,outer1,tile);
        glColor4f(r,g,b,1);groundVertex(x1,y,inner1,tile);
        glEnd();glDepthMask(true);glDisable(GL_BLEND);glDisable(GL_TEXTURE_2D);
    }
    private double bankEdge(double z,int side){return 5.2+.26*Math.sin(z*.53+side*.7)+.13*Math.sin(z*1.27);}
    private double bankToe(double z,int side){return 3.15+.18*Math.sin(z*.61+side);}
    private double waterEdge(double z,int side){return bankEdge(z,side)+(bankToe(z,side)-bankEdge(z,side))*(.36/.75);}
    private void bank(int side){
        glEnable(GL_TEXTURE_2D);glBindTexture(GL_TEXTURE_2D,textures.get("textures/ground-gravel.png"));
        color(0xaaa78b);glNormal3d(-side*.36,1,0);glBegin(GL_QUADS);
        for(int i=0;i<84;i++){
            double z0=-21+i*.5,z1=z0+.5;
            if(side>0){groundVertex(side*bankEdge(z0,side),.004,z0,3);groundVertex(side*bankToe(z0,side),-.75,z0,3);groundVertex(side*bankToe(z1,side),-.75,z1,3);groundVertex(side*bankEdge(z1,side),.004,z1,3);}
            else{groundVertex(side*bankEdge(z1,side),.004,z1,3);groundVertex(side*bankToe(z1,side),-.75,z1,3);groundVertex(side*bankToe(z0,side),-.75,z0,3);groundVertex(side*bankEdge(z0,side),.004,z0,3);}
        }
        glEnd();glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);glDepthMask(false);
        glBegin(GL_QUADS);
        for(int i=0;i<84;i++){
            double z0=-21+i*.5,z1=z0+.5;if(side<0){double swap=z0;z0=z1;z1=swap;}
            glColor4f(.67f,.66f,.54f,.8f);groundVertex(side*bankEdge(z0,side),.009,z0,3);groundVertex(side*bankEdge(z1,side),.009,z1,3);
            glColor4f(.67f,.66f,.54f,0);groundVertex(side*(bankEdge(z1,side)+.55),.009,z1,3);groundVertex(side*(bankEdge(z0,side)+.55),.009,z0,3);
        }
        glEnd();glDepthMask(true);glDisable(GL_BLEND);glDisable(GL_TEXTURE_2D);
    }
    private void riverSurface(){
        glDisable(GL_LIGHTING);glBegin(GL_QUADS);
        for(int row=0;row<84;row++)for(int col=0;col<16;col++){
            double z=-21+row*.5,u=col/16.,v=(col+1)/16.;
            waterVertex(z,u);waterVertex(z+.5,u);waterVertex(z+.5,v);waterVertex(z,v);
        }
        glEnd();glEnable(GL_LIGHTING);
    }
    private void waterVertex(double z,double u){
        double left=-waterEdge(z,-1),right=waterEdge(z,1),edge=Math.pow(Math.abs(u*2-1),2.5);
        glColor3d(.15+.16*edge,.37+.20*edge,.42+.12*edge);glVertex3d(left+(right-left)*u,-.36,z);
    }
    /** Only the small translucent overlay moves; terrain and depth remain cached. */
    private void waterMotion(double time){
        glDisable(GL_LIGHTING);glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);glDepthMask(false);glLineWidth(1);
        for(int i=0;i<72;i++){
            double z=-21+((i*.593+time*.65)%42),x=Math.sin(i*8.31)*3.45,length=.22+(i%5)*.12;
            if(!visible(x,z,1,1))continue;
            glBegin(GL_LINE_STRIP);
            for(int j=0;j<=6;j++){
                double px=x+(j/6.-.5)*length,pz=z+.045*Math.sin(j*.9+time*1.6+i);
                if(Math.abs(px)>waterEdge(pz,px<0?-1:1)-.12)continue;
                glColor4f(.65f,.85f,.78f,(float)(.07+.09*Math.sin(Math.PI*j/6)));glVertex3d(px,-.345,pz);
            }
            glEnd();
        }
        for(int side=-1;side<=1;side+=2)for(int end=-1;end<=1;end+=2)for(int n=0;n<2;n++){
            double t=(time*.55+n*.5)%1,r=.18+t*.85,x=side*3.7,z=end*4.1+t*.4;
            glColor4f(.72f,.86f,.76f,(float)((1-t)*.19));glBegin(GL_LINES);
            for(int j=0;j<24;j++){
                double a=j*Math.PI/12,b=(j+1)*Math.PI/12;
                double ax=x+Math.cos(a)*r,bx=x+Math.cos(b)*r,az=z+Math.sin(a)*r*.55,bz=z+Math.sin(b)*r*.55;
                if(Math.abs(ax)>waterEdge(az,side)-.08||Math.abs(bx)>waterEdge(bz,side)-.08)continue;
                glVertex3d(ax,-.342,az);glVertex3d(bx,-.342,bz);
            }glEnd();
        }
        glLineWidth(2);glDepthMask(true);glDisable(GL_BLEND);glEnable(GL_LIGHTING);
    }
    private void shadow(double x,double y,double z,double rx,double rz){shadow(x,y,z,rx,rz,.34);}
    private void shadow(double x,double y,double z,double rx,double rz,double opacity){
        glDisable(GL_LIGHTING);glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);glDepthMask(false);
        glBegin(GL_TRIANGLE_FAN);glColor4f(.045f,.065f,.04f,(float)opacity);glVertex3d(x,y,z);
        for(int i=0;i<=32;i++){double a=i*Math.PI/16;glColor4f(.06f,.10f,.08f,0);glVertex3d(x+Math.cos(a)*rx,y,z+Math.sin(a)*rz);}
        glEnd();glDepthMask(true);glDisable(GL_BLEND);glEnable(GL_LIGHTING);
    }

    private void color(int rgb){glColor3f((rgb>>16&255)/255f,(rgb>>8&255)/255f,(rgb&255)/255f);}
    private void cube(double x,double y,double z,double w,double h,double d,int rgb){
        glPushMatrix();glTranslated(x,y,z);glScaled(w/2,h/2,d/2);color(rgb);
        double[][][] faces={{{0,1,0},{-1,1,-1},{-1,1,1},{1,1,1},{1,1,-1}},{{0,-1,0},{-1,-1,1},{-1,-1,-1},{1,-1,-1},{1,-1,1}},{{0,0,1},{-1,-1,1},{1,-1,1},{1,1,1},{-1,1,1}},{{0,0,-1},{1,-1,-1},{-1,-1,-1},{-1,1,-1},{1,1,-1}},{{1,0,0},{1,-1,1},{1,-1,-1},{1,1,-1},{1,1,1}},{{-1,0,0},{-1,-1,-1},{-1,-1,1},{-1,1,1},{-1,1,-1}}};
        glBegin(GL_QUADS);for(double[][] f:faces){glNormal3d(f[0][0],f[0][1],f[0][2]);for(int i=1;i<5;i++)glVertex3d(f[i][0],f[i][1],f[i][2]);}glEnd();glPopMatrix();
    }
    private void cylinder(double x,double y,double z,double r,double h,int rgb,int sides){
        color(rgb);glBegin(GL_QUADS);for(int i=0;i<sides;i++){double a=i*2*Math.PI/sides,b=(i+1)*2*Math.PI/sides;glNormal3d(Math.cos((a+b)/2),0,Math.sin((a+b)/2));glVertex3d(x+Math.cos(a)*r,y-h/2,z+Math.sin(a)*r);glVertex3d(x+Math.cos(b)*r,y-h/2,z+Math.sin(b)*r);glVertex3d(x+Math.cos(b)*r,y+h/2,z+Math.sin(b)*r);glVertex3d(x+Math.cos(a)*r,y+h/2,z+Math.sin(a)*r);}glEnd();disk(x,y+h/2,z,r,rgb);
    }
    private void cone(double x,double y,double z,double r,double h,int rgb,int sides){color(rgb);glBegin(GL_TRIANGLES);for(int i=0;i<sides;i++){double a=i*2*Math.PI/sides,b=(i+1)*2*Math.PI/sides;glNormal3d(Math.cos((a+b)/2),r/h,Math.sin((a+b)/2));glVertex3d(x+Math.cos(a)*r,y,z+Math.sin(a)*r);glVertex3d(x+Math.cos(b)*r,y,z+Math.sin(b)*r);glVertex3d(x,y+h,z);}glEnd();}
    private void crystal(double x,double y,double z,double r,double h,int rgb,double angle){glPushMatrix();glTranslated(x,y,z);glRotated(angle,0,1,0);cone(0,0,0,r,h,rgb,4);glRotated(180,1,0,0);cone(0,0,0,r,h*.65,rgb,4);glPopMatrix();}
    private void disk(double x,double y,double z,double r,int rgb){color(rgb);glNormal3d(0,1,0);glBegin(GL_TRIANGLE_FAN);glVertex3d(x,y,z);for(int i=0;i<=24;i++){double a=i*Math.PI/12;glVertex3d(x+Math.cos(a)*r,y,z+Math.sin(a)*r);}glEnd();}
    private void ring(double x,double y,double z,double r,int rgb){glDisable(GL_LIGHTING);color(rgb);glLineWidth(2);glBegin(GL_LINE_LOOP);for(int i=0;i<48;i++){double a=i*Math.PI/24;glVertex3d(x+Math.cos(a)*r,y,z+Math.sin(a)*r);}glEnd();glEnable(GL_LIGHTING);}
    @Override public void close(){if(window!=0){if(landscape!=0)glDeleteLists(landscape,1);if(sceneFbo!=0){glDeleteRenderbuffers(sceneColor);glDeleteRenderbuffers(sceneDepth);glDeleteFramebuffers(sceneFbo);sceneFbo=0;}if(bareFbo!=0){glDeleteRenderbuffers(bareColor);glDeleteRenderbuffers(bareDepth);glDeleteFramebuffers(bareFbo);bareFbo=0;}for(int id:models.values())glDeleteLists(id,1);models.clear();animators.clear();healthFeedback.clear();rigs.clear();for(int id:textures.values())glDeleteTextures(id);textures.clear();glDeleteRenderbuffers(depth);glDeleteTextures(color);glDeleteFramebuffers(fbo);glfwMakeContextCurrent(0);GL.setCapabilities(null);glfwDestroyWindow(window);window=0;}glfwTerminate();if(errors!=null){glfwSetErrorCallback(null);errors.free();errors=null;}}
}
