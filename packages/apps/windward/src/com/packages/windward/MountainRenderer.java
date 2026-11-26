package com.packages.windward;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL30.*;

/** Hidden GL context; only the framework creates a visible application window. */
final class MountainRenderer implements AutoCloseable {
    static final int WIDTH=900,HEIGHT=600;
    private long window;
    private GLFWErrorCallback errors;
    private int fbo,color,depth;
    private final java.util.List<StaticDraw> scene=new java.util.ArrayList<>();
    private final MountainFrustum frustum=new MountainFrustum();
    boolean culling=true;int sceneDraws;
    private static final class StaticDraw {
        final int id;final double x,y,z,radius;
        StaticDraw(int id,double x,double y,double z,double radius){this.id=id;this.x=x;this.y=y;this.z=z;this.radius=radius;}
    }
    private final java.util.Map<PlanetTerrain.Tile,StaticDraw> terrain=new java.util.LinkedHashMap<>();
    private PlanetTerrain.Tile terrainAnchor;
    int terrainSize(){return terrain.size();}
    int sceneSize(){return scene.size()+terrain.size();}
    private static void surface(double x,double z){glMultMatrixd(MountainPlanet.frame(x,z).surface(x,z));}
    private void cache(double x,double y,double z,double radius,Runnable draw){
        int id=glGenLists(1);if(id==0)throw new IllegalStateException("Cannot cache static scene");
        MountainPlanet.Vec center=MountainPlanet.position(x,y,z);
        scene.add(new StaticDraw(id,center.x,center.y,center.z,radius+.1));
        glNewList(id,GL_COMPILE);glPushMatrix();surface(x,z);draw.run();glPopMatrix();glEndList();
    }
    private void cacheModel(String name,double x,double y,double z,double height,double angle,double radius){
        cache(x,y+height/2,z,art.radius(name,height,radius),()->art.model(name,x,y,z,height,angle,radius));
    }
    private void drawScene(MountainWorld w,MountainCamera camera){
        streamTerrain(w);
        frustum.update(w,camera);sceneDraws=0;
        for(StaticDraw draw:terrain.values())if(!culling||frustum.visible(draw.x,draw.y,draw.z,draw.radius)){glCallList(draw.id);sceneDraws++;}
        for(StaticDraw draw:scene)if(!culling||frustum.visible(draw.x,draw.y,draw.z,draw.radius)){glCallList(draw.id);sceneDraws++;}
        glDisable(GL_TEXTURE_2D);glDisable(GL_ALPHA_TEST);glColor3f(1,1,1);
    }
    private ByteBuffer pixels;
    String device="";
    // Explicit profiling only: glFinish boundaries make GPU work attributable to stages.
    boolean profile;
    double sceneMs,characterMs,propsMs,readMs,copyMs,skinMs;
    private long stage(){if(profile)glFinish();return System.nanoTime();}
    private static double elapsed(long begin){return (System.nanoTime()-begin)/1e6;}
    private MountainArt art;
    void open(MountainWorld world)throws IOException{
        errors=GLFWErrorCallback.createPrint(System.err);errors.set();
        glfwInitHint(GLFW_PLATFORM,GLFW_PLATFORM_X11);
        if(!glfwInit())throw new IOException("OpenGL 初始化失败，请检查 DISPLAY");
        glfwDefaultWindowHints();glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,2);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,1);
        window=glfwCreateWindow(64,64,"windward offscreen",0,0);
        if(window==0)throw new IOException("无法创建 OpenGL 上下文");
        glfwMakeContextCurrent(window);GL.createCapabilities();device=glGetString(GL_RENDERER);
        if(!GL.getCapabilities().OpenGL30&&!GL.getCapabilities().GL_ARB_framebuffer_object)throw new IOException("需要 framebuffer object 支持");
        fbo=glGenFramebuffers();glBindFramebuffer(GL_FRAMEBUFFER,fbo);
        color=glGenTextures();glBindTexture(GL_TEXTURE_2D,color);
        glTexImage2D(GL_TEXTURE_2D,0,GL_RGB,WIDTH,HEIGHT,0,GL_RGB,GL_UNSIGNED_BYTE,(ByteBuffer)null);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
        glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,color,0);
        depth=glGenRenderbuffers();glBindRenderbuffer(GL_RENDERBUFFER,depth);
        glRenderbufferStorage(GL_RENDERBUFFER,GL_DEPTH_COMPONENT24,WIDTH,HEIGHT);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER,GL_DEPTH_ATTACHMENT,GL_RENDERBUFFER,depth);
        if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new IOException("离屏缓冲创建失败");
        pixels=BufferUtils.createByteBuffer(WIDTH*HEIGHT*4);
        glEnable(GL_DEPTH_TEST);glEnable(GL_NORMALIZE);glEnable(GL_COLOR_MATERIAL);
        glColorMaterial(GL_FRONT_AND_BACK,GL_AMBIENT_AND_DIFFUSE);glEnable(GL_LIGHTING);glEnable(GL_LIGHT0);
        glLightModelfv(GL_LIGHT_MODEL_AMBIENT,new float[]{.45f,.48f,.47f,1});
        glLightfv(GL_LIGHT0,GL_DIFFUSE,new float[]{.55f,.53f,.48f,1});
        glEnable(GL_FOG);glFogi(GL_FOG_MODE,GL_LINEAR);glFogfv(GL_FOG_COLOR,new float[]{.65f,.83f,.87f,1});
        glFogf(GL_FOG_START,40);glFogf(GL_FOG_END,125);
        art=new MountainArt();art.open();
        landscape(world);
    }
    BufferedImage render(MountainWorld w,MountainCamera camera){
        camera.update(w);
        glBindFramebuffer(GL_FRAMEBUFFER,fbo);glViewport(0,0,WIDTH,HEIGHT);
        glClearColor(.65f,.83f,.92f,1);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
        glMatrixMode(GL_PROJECTION);glLoadIdentity();
        double top=.1*Math.tan(Math.toRadians(65)/2);glFrustum(-top*WIDTH/HEIGHT,top*WIDTH/HEIGHT,-top,top,.1,camera.farClip());
        glMatrixMode(GL_MODELVIEW);glLoadIdentity();
        // Camera-relative tangent frame keeps render coordinates small and local up radial.
        lookAt(camera.eyeX-w.x,camera.eyeY,camera.eyeZ-w.z,0,camera.targetAltitude(w),0);
        if(camera.globe)glDisable(GL_FOG);else glEnable(GL_FOG);
        glMultMatrixd(MountainPlanet.frame(w.x,w.z).inverse());
        glLightfv(GL_LIGHT0,GL_POSITION,new float[]{-30,65,20,0});
        long begin=profile?stage():0;
        drawScene(w,camera);
        if(profile){glFinish();sceneMs=elapsed(begin);begin=stage();}
        glPushMatrix();surface(w.x,w.z);contactShadow(w);
        art.profile=profile;art.character(w);glPopMatrix();
        if(profile){glFinish();characterMs=elapsed(begin);skinMs=art.skinMs;begin=stage();}
        traversalObjects(w);
        combatObjects(w,art);
        if(MountainPlanet.distance(w.x,w.z,22,-25)<250){glPushMatrix();surface(22,-25);puzzleObjects(w);glPopMatrix();}
        if(profile){glFinish();propsMs=elapsed(begin);begin=stage();}
        glReadPixels(0,0,WIDTH,HEIGHT,GL_BGRA,GL_UNSIGNED_INT_8_8_8_8_REV,pixels);
        if(profile){readMs=elapsed(begin);begin=System.nanoTime();}
        BufferedImage image=new BufferedImage(WIDTH,HEIGHT,BufferedImage.TYPE_INT_RGB);
        int[] dest=((DataBufferInt)image.getRaster().getDataBuffer()).getData();
        java.nio.IntBuffer packed=pixels.asIntBuffer();
        for(int row=0;row<HEIGHT;row++){packed.position((HEIGHT-1-row)*WIDTH);packed.get(dest,row*WIDTH,WIDTH);}
        if(profile)copyMs=elapsed(begin);
        return image;
    }
    private static void puzzleObjects(MountainWorld w){
        MountainPuzzle p=w.puzzle;
        double py=MountainWorld.ground(MountainPuzzle.X,MountainPuzzle.PLATE_Z);
        cylinder(MountainPuzzle.X,py+.03,MountainPuzzle.PLATE_Z,1.25,.12,1.25,p.powered?0x75d9bb:0xc2b471,12);
        box(MountainPuzzle.X,p.stoneY()+.7,p.stoneZ,.7,.7,.7,p.powered?0x90c8b3:0x9eaa9c);
        box(MountainPuzzle.X,p.stoneY()+1.42,p.stoneZ,.3,.025,.3,0xe0d08b);
        double tx=MountainPuzzle.TERMINAL_X,tz=MountainPuzzle.TERMINAL_Z,ty=MountainWorld.ground(tx,tz);
        cylinder(tx,ty+.9,tz,.48,.12,.48,p.powered?0x75d9bb:0x847968,8);
        glDisable(GL_LIGHTING);glLineWidth(3);color(p.powered?0x75d9bb:0x8d886a);glBegin(GL_LINE_STRIP);
        for(int i=0;i<=24;i++){double u=i/24.,x=MountainPuzzle.X+(tx-MountainPuzzle.X)*u,z=MountainPuzzle.PLATE_Z+(tz-MountainPuzzle.PLATE_Z)*u;glVertex3d(x,MountainWorld.ground(x,z)+.18,z);}
        glEnd();glLineWidth(1);
        if(w.journey.complete){
            double towerY=MountainWorld.ground(15,-25);
            glPushMatrix();glTranslated(15,towerY+8.2,-25);glRotated(w.time*65,0,1,0);
            glColor3d(.65,.97,.86);glBegin(GL_LINES);
            for(int i=0;i<4;i++){double angle=i*Math.PI/2;glVertex3d(0,0,0);glVertex3d(Math.cos(angle)*3.1,.5,Math.sin(angle)*3.1);}
            glEnd();glPopMatrix();
            glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);glDepthMask(false);glColor4d(.5,.95,.84,.2);
            glBegin(GL_QUADS);
            for(int i=0;i<12;i++){double a=i*Math.PI/6,b=(i+1)*Math.PI/6;glVertex3d(15+Math.cos(a)*.7,towerY+10.5,-25+Math.sin(a)*.7);glVertex3d(15+Math.cos(b)*.7,towerY+10.5,-25+Math.sin(b)*.7);glVertex3d(15+Math.cos(b)*.7,towerY+24,-25+Math.sin(b)*.7);glVertex3d(15+Math.cos(a)*.7,towerY+24,-25+Math.sin(a)*.7);}
            glEnd();glDepthMask(true);glDisable(GL_BLEND);
        }
        glEnable(GL_LIGHTING);
    }
    private static void puzzleRails(){
        // Narrow rails communicate the allowed push direction, with a bright receiver.
        for(double z=MountainPuzzle.MIN_Z;z<=MountainPuzzle.MAX_Z;z+=.5){
            for(int side:new int[]{-1,1})box(MountainPuzzle.X+side*.9,MountainWorld.ground(MountainPuzzle.X+side*.9,z)+.04,z,.05,.04,.25,0xb4ab8a);
        }
    }
    private static void combatObjects(MountainWorld w,MountainArt art){
        MountainCombat c=w.combat;double floor=MountainWorld.ground(c.x,c.z);
        if(MountainPlanet.distance(w.x,w.z,c.x,c.z)<250){
        glPushMatrix();surface(c.x,c.z);
        if(c.enemyHealth>0){
            glPushMatrix();glTranslated(c.x,floor,c.z);glRotated(Math.toDegrees(c.facing),0,1,0);
            int stone=c.enemyHurt>0?0xffe9b4:0x879d9b;
            double stride=c.state==MountainCombat.State.CHASE||c.state==MountainCombat.State.RETURN?Math.sin(w.time*9)*.13:0;
            box(-.28,.3+stride,0,.22,.3,.28,stone);box(.28,.3-stride,0,.22,.3,.28,stone);
            box(0,1.05,0,.55,.48,.35,stone);box(0,1.83,0,.37,.3,.32,stone);
            box(-.17,1.87,.33,.065,.065,.02,0xf3d16c);box(.17,1.87,.33,.065,.065,.02,0xf3d16c);
            double reach=c.state==MountainCombat.State.WINDUP?-.35:c.state==MountainCombat.State.RECOVERY&&c.timer<.25?.8:.15;
            box(-.73,1.0,reach,.23,.35,.24,stone);box(.73,1.0,reach,.23,.35,.24,stone);
            glPopMatrix();
            if(c.state==MountainCombat.State.WINDUP){
                glDisable(GL_LIGHTING);glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);glDepthMask(false);
                glColor4d(1,.28,.12,.25+c.timer*.3);glBegin(GL_TRIANGLE_FAN);glVertex3d(c.x,floor+.04,c.z);
                for(int i=0;i<=24;i++){double angle=c.facing-1.26+i*2.52/24,px=c.x+Math.sin(angle)*2.35,pz=c.z+Math.cos(angle)*2.35;glVertex3d(px,MountainTraversal.floor(px,pz)+.045,pz);}
                glEnd();glDepthMask(true);glDisable(GL_BLEND);glEnable(GL_LIGHTING);
            }
        }else{cylinder(c.x,floor,c.z,.65,.25,.45,0x879d9b,6);box(c.x+.5,floor+.23,c.z+.3,.25,.2,.25,0x879d9b);}
        glPopMatrix();}
        if(w.grounded&&!w.climbing&&!w.gliding&&!w.puzzle.pushing&&c.health>0){
            glPushMatrix();surface(w.x,w.z);glTranslated(w.x,w.y,w.z);glRotated(Math.toDegrees(w.visualFacing),0,1,0);
            double slash=c.attackTime<0?0:Math.sin(Math.min(1,c.attackTime/.5)*Math.PI);
            glTranslated(art.handX,art.handY,art.handZ);glRotated(-35+slash*140,0,1,0);glRotated(65-slash*55,1,0,0);
            box(0,-.12,0,.055,.16,.055,0x66513a);box(0,.04,0,.19,.04,.06,0xc7ab63);box(0,.6,0,.07,.52,.035,0xd6eff0);
            glPopMatrix();
        }
    }
    private static void traversalObjects(MountainWorld w){
        double y=MountainWorld.ground(MountainJourney.CACHE_X,MountainJourney.CACHE_Z);
        glPushMatrix();surface(MountainJourney.CACHE_X,MountainJourney.CACHE_Z);
        box(MountainJourney.CACHE_X,y+.35,MountainJourney.CACHE_Z,.65,.35,.45,0x82704a);
        box(MountainJourney.CACHE_X,y+(w.journey.treasure?.98:.75),MountainJourney.CACHE_Z,.7,.10,.48,w.journey.treasure?0x738f80:0xe6c56b);
        glPopMatrix();
        if(w.gliding){
            glPushMatrix();surface(w.x,w.z);glTranslated(w.x,w.y,w.z);glRotated(Math.toDegrees(w.visualFacing),0,1,0);
            glDisable(GL_CULL_FACE);
            tri(-2,2.8,.5,0,3.25,-.65,2,2.8,.5,0x58b6b4);
            tri(-2,2.8,.5,2,2.8,.5,0,2.9,1.1,0xe5d6a1);
            glDisable(GL_LIGHTING);glColor3f(.35f,.3f,.22f);glBegin(GL_LINES);
            for(int side:new int[]{-1,1}){glVertex3d(side*1.8,2.8,.5);glVertex3d(side*.45,2.15,0);}
            glEnd();glEnable(GL_LIGHTING);glPopMatrix();
        }
    }
    private static void contactShadow(MountainWorld w){
        double floor=w.floor(),air=Math.max(0,w.y-floor),radius=.42+air*.08;
        glDisable(GL_LIGHTING);glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);glDepthMask(false);
        glColor4d(.08,.14,.12,.24/(1+air));glBegin(GL_TRIANGLE_FAN);glVertex3d(w.x,floor+.025,w.z);
        for(int i=0;i<=24;i++){double a=i*Math.PI/12,dx=Math.cos(a)*radius,dz=Math.sin(a)*radius;
            MountainPlanet.Move p=MountainPlanet.advance(w.x,w.z,dx,dz);
            glColor4d(.08,.14,.12,0);glVertex3d(w.x+dx,w.floorAt(p.x,p.z)+.025,w.z+dz);}
        glEnd();glDepthMask(true);glDisable(GL_BLEND);glEnable(GL_LIGHTING);
    }
    private static void lookAt(double ex,double ey,double ez,double tx,double ty,double tz){
        double fx=tx-ex,fy=ty-ey,fz=tz-ez,n=Math.sqrt(fx*fx+fy*fy+fz*fz);fx/=n;fy/=n;fz/=n;
        double sx=-fz,sz=fx,sn=Math.hypot(sx,sz);sx/=sn;sz/=sn;
        double ux=-sz*fy,uy=sz*fx-sx*fz,uz=sx*fy;
        glMultMatrixd(new double[]{sx,ux,-fx,0,0,uy,-fy,0,sz,uz,-fz,0,0,0,0,1});glTranslated(-ex,-ey,-ez);
    }
    private void streamTerrain(MountainWorld w){
        PlanetTerrain.Tile anchor=PlanetTerrain.leaf(w.x,w.z);
        if(anchor.equals(terrainAnchor))return;
        MountainPlanet.Vec n=PlanetTerrain.direction(anchor.face,anchor.u()+anchor.size()/2,anchor.v()+anchor.size()/2);
        java.util.Set<PlanetTerrain.Tile> wanted=PlanetTerrain.select(MountainPlanet.longitude(n),MountainPlanet.latitude(n));
        java.util.Iterator<java.util.Map.Entry<PlanetTerrain.Tile,StaticDraw>> it=terrain.entrySet().iterator();
        while(it.hasNext()){java.util.Map.Entry<PlanetTerrain.Tile,StaticDraw> e=it.next();if(!wanted.contains(e.getKey())){glDeleteLists(e.getValue().id,1);it.remove();}}
        for(PlanetTerrain.Tile tile:wanted)if(!terrain.containsKey(tile)){
            int id=glGenLists(1);if(id==0)throw new IllegalStateException("Cannot allocate planet patch");
            MountainPlanet.Vec center=tile.center();
            glNewList(id,GL_COMPILE);planetPatch(tile);
            if(tile.level==PlanetTerrain.DEPTH)for(PlanetTerrain.Prop prop:w.terrain.props(tile)){
                glPushMatrix();surface(prop.x,prop.z);
                art.model(prop.rock?"rock":"tree",prop.x,MountainWorld.ground(prop.x,prop.z),prop.z,prop.height,tile.hashCode()%360,prop.rock?prop.r:0);
                glPopMatrix();
            }
            glEndList();terrain.put(tile,new StaticDraw(id,center.x,center.y,center.z,tile.radius()+10));
        }
        terrainAnchor=anchor;
    }
    private void planetPatch(PlanetTerrain.Tile tile){
        int count=PlanetTerrain.SEGMENTS;double step=tile.size()/count;
        MountainPlanet.Vec[][] points=new MountainPlanet.Vec[count+1][count+1];
        for(int z=0;z<=count;z++)for(int x=0;x<=count;x++)points[z][x]=PlanetTerrain.vertex(tile.face,tile.u()+x*step,tile.v()+z*step);
        // The same finest-grid vertices feed collision. Coarser patches use subsets; skirts hide LOD edge cracks.
        for(int material=0;material<2;material++){
            art.groundTexture(material==1);glColor3f(1,1,1);glBegin(GL_TRIANGLES);
            for(int z=0;z<count;z++)for(int x=0;x<count;x++){
                MountainPlanet.Vec n=PlanetTerrain.direction(tile.face,tile.u()+(x+.5)*step,tile.v()+(z+.5)*step);
                double px=MountainPlanet.longitude(n),pz=MountainPlanet.latitude(n);
                boolean path=Math.abs(px)<50&&Math.abs(pz)<50&&(Math.abs(px-MountainWorld.pathX(pz))<1.8||(pz>-11&&pz<-3&&Math.abs(px-(-7*(-3-pz)/8))<1.3));
                if(path!=(material==1))continue;
                planetTriangle(points[z][x],points[z][x+1],points[z+1][x]);
                planetTriangle(points[z+1][x+1],points[z+1][x],points[z][x+1]);
            }
            glEnd();
        }
        art.groundTexture(false);glBegin(GL_TRIANGLES);
        for(int side=0;side<4;side++)for(int i=0;i<count;i++){
            MountainPlanet.Vec a=side==0?points[0][i]:side==1?points[count][i]:side==2?points[i][0]:points[i][count];
            MountainPlanet.Vec b=side==0?points[0][i+1]:side==1?points[count][i+1]:side==2?points[i+1][0]:points[i+1][count];
            MountainPlanet.Vec c=a.mul(1-tile.skirt()/a.length()),d=b.mul(1-tile.skirt()/b.length());
            planetTriangle(a,b,c);planetTriangle(b,d,c);
        }
        glEnd();glDisable(GL_TEXTURE_2D);
    }
    private static void planetTriangle(MountainPlanet.Vec a,MountainPlanet.Vec b,MountainPlanet.Vec c){
        MountainPlanet.Vec normal=b.sub(a).cross(c.sub(a));
        if(normal.dot(a)<0)normal=normal.mul(-1);
        glNormal3d(normal.x,normal.y,normal.z);
        planetVertex(a);planetVertex(b);planetVertex(c);
    }
    private static void planetVertex(MountainPlanet.Vec p){
        // Continuous world-direction texturing; no resource allocation per rendered frame.
        glTexCoord2d((p.x+p.y*.37)/5,(p.z+p.y*.23)/5);glVertex3d(p.x,p.y,p.z);
    }
    private void landscape(MountainWorld w){
        // The authored valley remains attached to its original small spherical region.
        // The rails and terminal pedestal never change during a journey.
        double railZ=(MountainPuzzle.MIN_Z+MountainPuzzle.MAX_Z)/2;
        double railY=MountainWorld.ground(MountainPuzzle.X,railZ),railRadius=0;
        for(double z=MountainPuzzle.MIN_Z;z<=MountainPuzzle.MAX_Z;z+=.5)
            for(int side:new int[]{-1,1}){
                double x=MountainPuzzle.X+side*.9,y=MountainWorld.ground(x,z)+.04;
                railRadius=Math.max(railRadius,Math.sqrt(.9*.9+(y-railY)*(y-railY)+(z-railZ)*(z-railZ))+.3);
            }
        cache(MountainPuzzle.X,railY,railZ,railRadius,MountainRenderer::puzzleRails);
        double terminalY=MountainWorld.ground(MountainPuzzle.TERMINAL_X,MountainPuzzle.TERMINAL_Z);
        cache(MountainPuzzle.TERMINAL_X,terminalY+.45,MountainPuzzle.TERMINAL_Z,.8,
            ()->cylinder(MountainPuzzle.TERMINAL_X,terminalY,MountainPuzzle.TERMINAL_Z,.55,.9,.4,0xb4b49b,8));
        int treeIndex=0;
        for(MountainWorld.Obstacle t:w.trees){
            String name=treeIndex++%3==0?"birch":"tree";
            cacheModel(name,t.x,MountainWorld.ground(t.x,t.z),t.z,t.height+2,treeIndex*43,0);
        }
        int rockIndex=0;
        for(MountainWorld.Obstacle r:w.rocks)cacheModel(rockIndex++%2==0?"rock":"roundrock",r.x,MountainWorld.ground(r.x,r.z),r.z,r.height,rockIndex*29,r.r);
        cacheModel("tent",w.tent.x,MountainWorld.ground(w.tent.x,w.tent.z),w.tent.z,w.tent.height,25,w.tent.r);
        cacheModel("runestone",w.rune.x,MountainWorld.ground(w.rune.x,w.rune.z),w.rune.z,w.rune.height,15,w.rune.r);
        double base=MountainWorld.ground(-7,-14)-2,top=MountainTraversal.TOP;
        cache(-7,(base+top)/2,-14,8,()->{
            box(-7,(base+top)/2,-14,3,(top-base)/2,3,0x969888);
            box(-7,top-.12,-14,3,.12,3,0x99ab78);
            for(int row=0;row<6;row++)for(int side:new int[]{-1,1})box(-7+side*(.45+row%2*.2),top-4.7+row*.72,MountainTraversal.FRONT+.04,.24,.08,.09,0xe7d4a1);
        });
        for(int i=1;i<MountainJourney.ROUTE.length;i++){
            double px=MountainJourney.ROUTE[i][0]-2.5,pz=MountainJourney.ROUTE[i][1],py=MountainWorld.ground(px,pz);
            cache(px+.35,py+.9,pz,1.5,()->{cylinder(px,py,pz,.12,1.8,.12,0x847258,6);box(px+.35,py+1.5,pz,.65,.19,.13,0x4697a2);});
        }
        double ty=MountainWorld.ground(15,-25);
        cache(15,ty+5.25,-25,6,()->{
            cylinder(15,ty,-25,2.3,1,2.3,0xb8b7a0,8);
            cylinder(15,ty+1,-25,1.35,7,1.1,0xe3d9b8,8);
            cylinder(15,ty+8,-25,2.1,.5,2.1,0xb2b5a2,8);
            cylinder(15,ty+8.5,-25,2.5,2,0,0x4697a2,8);
        });
        cache(-3,MountainWorld.ground(-3,14),14,3,()->{
            for(int i=0;i<8;i++){double a=i*Math.PI/4,x=-3+Math.cos(a),z=14+Math.sin(a);cylinder(x,MountainWorld.ground(x,z),z,.26,.25,.2,0xaba797,6);}
        });
        for(int i=0;i<95;i++){
            double x=-44+i*19%88,z=-42+i*31%84;
            if(Math.abs(x-MountainWorld.pathX(z))<2||Math.hypot(x,z-12)<4)continue;
            cacheModel(i%9==0?"mushrooms":i%4==0?"flowers":"grass",x,MountainWorld.ground(x,z),z,i%9==0?.45:i%4==0?.18:.55,i*67,i%9==0?.35:i%4==0?0:.7);
        }
    }

    private static void color(int c){glColor3d((c>>16&255)/255.,(c>>8&255)/255.,(c&255)/255.);}
    private static void tri(double ax,double ay,double az,double bx,double by,double bz,double cx,double cy,double cz,int c){
        glBegin(GL_TRIANGLES);emitTri(ax,ay,az,bx,by,bz,cx,cy,cz,c);glEnd();
    }
    private static void emitTri(double ax,double ay,double az,double bx,double by,double bz,double cx,double cy,double cz,int c){
        double ux=bx-ax,uy=by-ay,uz=bz-az,vx=cx-ax,vy=cy-ay,vz=cz-az;
        color(c);glNormal3d(uy*vz-uz*vy,uz*vx-ux*vz,ux*vy-uy*vx);
        glTexCoord2d(ax/5,az/5);glVertex3d(ax,ay,az);glTexCoord2d(bx/5,bz/5);glVertex3d(bx,by,bz);glTexCoord2d(cx/5,cz/5);glVertex3d(cx,cy,cz);
    }
    private static void cylinder(double x,double y,double z,double bottom,double h,double top,int c,int sides){
        for(int i=0;i<sides;i++){
            double a=i*Math.PI*2/sides,b=(i+1)*Math.PI*2/sides;
            double ax=x+Math.cos(a)*bottom,az=z+Math.sin(a)*bottom,bx=x+Math.cos(b)*bottom,bz=z+Math.sin(b)*bottom;
            double cx=x+Math.cos(a)*top,cz=z+Math.sin(a)*top,dx=x+Math.cos(b)*top,dz=z+Math.sin(b)*top;
            tri(ax,y,az,cx,y+h,cz,bx,y,bz,c);tri(bx,y,bz,cx,y+h,cz,dx,y+h,dz,c);
            tri(x,y+h,z,dx,y+h,dz,cx,y+h,cz,c);
        }
    }
    private static void box(double x,double y,double z,double a,double b,double c,int tint){
        double[][] p={{x-a,y-b,z-c},{x+a,y-b,z-c},{x+a,y+b,z-c},{x-a,y+b,z-c},{x-a,y-b,z+c},{x+a,y-b,z+c},{x+a,y+b,z+c},{x-a,y+b,z+c}};
        int[][] faces={{0,3,2,1},{4,5,6,7},{0,4,7,3},{1,2,6,5},{3,7,6,2},{0,1,5,4}};
        for(int[] f:faces){double[] u=p[f[0]],v=p[f[1]],w=p[f[2]],t=p[f[3]];tri(u[0],u[1],u[2],v[0],v[1],v[2],w[0],w[1],w[2],tint);tri(u[0],u[1],u[2],w[0],w[1],w[2],t[0],t[1],t[2],tint);}
    }
    @Override public void close(){
        if(window!=0){
            glfwMakeContextCurrent(window);
            for(StaticDraw draw:terrain.values())glDeleteLists(draw.id,1);terrain.clear();terrainAnchor=null;
            for(StaticDraw draw:scene)glDeleteLists(draw.id,1);scene.clear();if(art!=null)art.close();if(depth!=0)glDeleteRenderbuffers(depth);if(color!=0)glDeleteTextures(color);if(fbo!=0)glDeleteFramebuffers(fbo);
            GL.setCapabilities(null);glfwDestroyWindow(window);window=0;
        }
        glfwTerminate();if(errors!=null){glfwSetErrorCallback(null);errors.free();errors=null;}
    }
}
