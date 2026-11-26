package com.packages.windward;

import com.android.content.Context;
import com.android.graphics.Canvas;
import com.android.os.Handler;
import com.android.view.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/** Worker owns simulation and GL; UI only sees completed, immutable frame images. */
public final class MountainView extends View implements AutoCloseable {
    private final MountainWorld world=new MountainWorld();
    private final MountainCamera camera=new MountainCamera();
    private final MountainInput input=new MountainInput();
    private final ConcurrentLinkedQueue<Runnable> commands=new ConcurrentLinkedQueue<>();
    private final AtomicBoolean pending=new AtomicBoolean();
    private volatile boolean closed;
    private volatile Frame frame;
    private volatile String error="";
    private Thread worker;
    private int state,lastX,lastY;
    private boolean dragging;
    private final Handler ui=new Handler(m->{pending.set(false);if(!closed)invalidate();return true;});
    static final class Frame {
        final BufferedImage image;
        final boolean paused,nearRune,runeRead,climbing,gliding;
        final double x,z,y,time,renderMs,targetDistance,stamina;
        final int terrainPatches;
        final String device,objective,notice,traversalHint,enemyState;
        final int health,enemyHealth;
        final boolean enemyNearby,attacking,dodging,complete,summaryOpen,pushing,powered,treasure;
        final double completedAt,stoneZ;
        Frame(BufferedImage i,MountainWorld w,double ms,String d,int patches){
            terrainPatches=patches;
            image=i;paused=w.paused;x=w.x;z=w.z;y=w.y;time=w.time;renderMs=ms;device=d;
            complete=w.journey.complete;summaryOpen=w.journey.summaryOpen;completedAt=w.journey.completedAt;treasure=w.journey.treasure;
            pushing=w.puzzle.pushing;powered=w.puzzle.powered;stoneZ=w.puzzle.stoneZ;
            health=w.combat.health;enemyHealth=w.combat.enemyHealth;enemyState=w.combat.state.name();
            enemyNearby=Math.hypot(w.x-w.combat.x,w.z-w.combat.z)<10;
            attacking=w.combat.attackTime>=0;dodging=w.combat.dodgeTime>=0;
            objective=w.journey.objective(w);notice=w.time<w.journey.noticeUntil?w.journey.notice:"";
            nearRune=w.journey.nearRune(w);runeRead=w.journey.runeRead;climbing=w.climbing;gliding=w.gliding;stamina=w.stamina;
            traversalHint=hint(w);targetDistance=MountainPlanet.distance(w.x,w.z,w.journey.guideX(w),w.journey.guideZ(w));
        }
        private static String hint(MountainWorld w){
            String puzzle=w.puzzle.hint(w);if(!puzzle.isEmpty())return puzzle;
            if(w.climbing)return "攀爬中 · W/S 上下  A/D 横移  E 松手";
            if(w.gliding)return "滑翔中 · 方向键控制  空格收起";
            if(w.journey.nearCache(w))return w.journey.treasure?"遗物已收集":"E 打开遗物匣";
            return MountainTraversal.nearFace(w)?"E 抓住浅色岩面 · W 向上攀爬":"";
        }
    }
    public MountainView(Context context){super(context);setFocusable(true);}
    private void start(){
        if(worker!=null||closed)return;
        worker=new Thread(()->{
            MountainRenderer renderer=new MountainRenderer();
            try{
                renderer.open(world);long last=System.nanoTime();double accumulated=0;
                while(!closed&&!Thread.currentThread().isInterrupted()){
                    long begin=System.nanoTime();double dt=Math.min(.2,(begin-last)/1e9);last=begin;
                    Runnable command;while((command=commands.poll())!=null)command.run();
                    if(world.paused||world.journey.summaryOpen)accumulated=0;
                    else{
                        accumulated+=dt;
                        while(accumulated>=MountainWorld.STEP){
                            world.step(((state&8)!=0?1:0)-((state&4)!=0?1:0),((state&1)!=0?1:0)-((state&2)!=0?1:0),(state&16)!=0,camera.yaw);
                            camera.transport(world);
                            accumulated-=MountainWorld.STEP;
                        }
                    }
                    long renderBegin=System.nanoTime();BufferedImage image=renderer.render(world,camera);
                    frame=new Frame(image,world,(System.nanoTime()-renderBegin)/1e6,renderer.device,renderer.terrainSize());
                    if(pending.compareAndSet(false,true))ui.sendMessage(ui.obtainMessage(0,null));
                    LockSupport.parkNanos(Math.max(0,(world.paused?100000000L:16666667L)-(System.nanoTime()-begin)));
                }
            }catch(Throwable failure){error="场景启动失败："+failure;failure.printStackTrace();ui.sendMessage(ui.obtainMessage(0,null));}
            finally{renderer.close();}
        },"windward-render");worker.setDaemon(true);worker.start();
    }
    private void queue(Runnable command){if(closed)return;commands.add(command);if(worker!=null)LockSupport.unpark(worker);}
    private void updateKeys(){final int next=input.state();queue(()->state=next);}
    public void pause(){input.clear();dragging=false;queue(()->{state=0;world.paused=true;});}
    @Override protected void onFocusChanged(boolean focused){if(!focused)pause();}
    @Override public void close(){closed=true;input.clear();commands.clear();if(worker!=null)worker.interrupt();}
    @Override protected void onMeasure(int w,int h){setMeasuredDimension(resolveSize(1200,w),resolveSize(800,h));}
    @Override protected void onDraw(Canvas canvas){
        start();canvas.drawColor(0xff203f42);
        int width=getWidth(),height=getHeight();double scale=Math.min(width/1200.,height/800.);
        int ox=(int)((width-1200*scale)/2),oy=(int)((height-800*scale)/2);
        Frame f=frame;
        if(f!=null)canvas.drawImage(f.image,ox,oy,(int)(1200*scale),(int)(800*scale));
        canvas.save();canvas.translate(ox,oy);
        // HUD uses window-scaled positions and text so resized windows remain usable.
        panel(canvas,scale,24,24,420,102,0xe6234140);
        text(canvas,scale,"风起山野",44,64,28,0xffffedbd);
        text(canvas,scale,f==null?"山谷初行 · 寻找远处的风塔":f.objective,44,98,16,0xffd7e6d6);
        panel(canvas,scale,24,696,1152,80,0xe6234140);
        text(canvas,scale,"WASD / 方向键 移动    Shift 奔跑    空格 跳跃    按住鼠标右键 转动镜头    滚轮 缩放",44,726,17,0xfff2edda);
        text(canvas,scale,"J / 左键 攻击    K 闪避    E 互动 / 攀爬    空中空格 滑翔    G 星球视角    Esc 暂停    R 重开",44,754,15,0xffb9d3c4);
        if(f==null)text(canvas,scale,error.isEmpty()?"正在打开山谷…":error,44,180,20,0xffffedbd);
        else{
            panel(canvas,scale,932,24,244,76,0xd9234140);
            text(canvas,scale,"球面探索 · 半径 1 km",948,55,18,0xffe4ead7);
            text(canvas,scale,String.format(java.util.Locale.ROOT,"目标距离 %.0f m",f.targetDistance),948,81,14,0xffb9d3c4);
            text(canvas,scale,String.format(java.util.Locale.ROOT,"经度 %.1f°  纬度 %.1f°",Math.toDegrees(f.x/MountainPlanet.RADIUS),-Math.toDegrees(f.z/MountainPlanet.RADIUS)),948,121,13,0xffe4ead7);
            if(Boolean.getBoolean("framework.windward.profile"))text(canvas,scale,String.format(java.util.Locale.ROOT,"GL / 读回 %.1f ms",f.renderMs),948,146,14,0xffe4ead7);
            if(!f.notice.isEmpty()){panel(canvas,scale,260,154,680,58,0xe6234140);text(canvas,scale,f.notice,280,191,18,0xffffedbd);}
            panel(canvas,scale,24,144,196,38,0xd9234140);text(canvas,scale,String.format(java.util.Locale.ROOT,"体力 %.0f / 100",f.stamina),40,170,17,0xffffedbd);
            if(!f.traversalHint.isEmpty()&&!f.paused){panel(canvas,scale,320,610,560,48,0xe6234140);text(canvas,scale,f.traversalHint,340,642,18,0xffffedbd);}
            else if(f.nearRune&&!f.paused){panel(canvas,scale,420,610,360,48,0xe6234140);text(canvas,scale,f.runeRead?"E 重读古老符文":"E 阅读古老符文",455,642,20,0xffffedbd);}
            panel(canvas,scale,24,190,196,38,0xd9234140);text(canvas,scale,"生命 "+f.health+" / 100",40,216,17,0xffffd0b8);
            if(f.enemyNearby&&f.enemyHealth>0){panel(canvas,scale,780,244,396,68,0xe6234140);text(canvas,scale,"山谷守卫  "+f.enemyHealth+" / 3",800,272,19,0xffffd0b8);text(canvas,scale,f.enemyState.equals("WINDUP")?"即将挥击！K 闪避":"J / 鼠标左键 攻击    K 闪避",800,298,16,0xffffedbd);}
            if(f.summaryOpen){
                panel(canvas,scale,300,260,600,270,0xf0234140);text(canvas,scale,"山谷初行 · 完成",420,318,32,0xffffedbd);
                text(canvas,scale,"守卫已平息 · 风塔已苏醒",398,363,23,0xffe4ead7);
                text(canvas,scale,String.format(java.util.Locale.ROOT,"旅程用时 %d:%02d   隐藏遗物 %s",(int)f.completedAt/60,(int)f.completedAt%60,f.treasure?"1 / 1":"0 / 1"),388,408,20,0xffe4ead7);
                text(canvas,scale,"Enter 继续探索    R 重新出发",392,474,20,0xffffedbd);
            }
            else if(f.health<=0){panel(canvas,scale,390,290,420,152,0xf0234140);text(canvas,scale,"暂时力竭",500,345,32,0xffffd0b8);text(canvas,scale,"按 R 重新出发",500,395,20,0xffe4ead7);}
            else if(f.paused){panel(canvas,scale,390,290,420,152,0xf0234140);text(canvas,scale,"山风暂歇",500,345,32,0xffffedbd);text(canvas,scale,"按 Enter 或 Esc 继续探索",455,395,20,0xffe4ead7);}
            if(!error.isEmpty())text(canvas,scale,error,44,180,18,0xffffb8a0);
        }
        canvas.restore();
    }
    private static void panel(Canvas c,double s,int x,int y,int w,int h,int color){c.fillRect((int)(x*s),(int)(y*s),(int)(w*s),(int)(h*s),color);}
    private static void text(Canvas c,double s,String value,int x,int y,int size,int color){c.drawText(value,(int)(x*s),(int)(y*s),Math.max(10,(int)(size*s)),color);}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(event.getType()==KeyEvent.TEXT)return false;
        int key=MountainInput.normalize(event.getValue());
        if(event.getType()==KeyEvent.UP){input.release(key);updateKeys();return true;}
        if(event.getType()!=KeyEvent.DOWN)return false;
        if(!input.press(key))return true;
        if(key==KeyEvent.ESCAPE||key==KeyEvent.ENTER){input.clear();input.press(key);queue(()->{state=0;if(key==KeyEvent.ENTER&&world.journey.summaryOpen){world.journey.summaryOpen=false;world.paused=false;}else world.paused=!world.paused;});}
        else if(key=='g')queue(()->camera.globe=!camera.globe);
        else if(key=='r'){input.clear();input.press(key);queue(()->{state=0;world.reset();camera.reset();});}
        else if(key==' ')queue(world::jump);
        else if(key=='j')queue(()->world.combat.attack(world));
        else if(key=='k')queue(()->world.combat.dodge(world,((state&8)!=0?1:0)-((state&4)!=0?1:0),((state&1)!=0?1:0)-((state&2)!=0?1:0),camera.yaw));
        else if(key=='e')queue(()->world.interact());
        updateKeys();return true;
    }
    @Override protected boolean onTouchEvent(MotionEvent event){
        if(event.getAction()==MotionEvent.ACTION_CANCEL){pause();return true;}
        if(event.getAction()==MotionEvent.ACTION_DOWN){requestFocus();lastX=event.getX();lastY=event.getY();dragging=event.getButton()==3;if(event.getButton()==1)queue(()->world.combat.attack(world));return true;}
        if(event.getAction()==MotionEvent.ACTION_UP){dragging=false;return true;}
        if(event.getAction()==MotionEvent.ACTION_MOVE&&dragging){
            double scale=Math.max(.1,Math.min(getWidth()/1200.,getHeight()/800.));
            final double dx=(event.getX()-lastX)/scale,dy=(event.getY()-lastY)/scale;lastX=event.getX();lastY=event.getY();
            queue(()->{if(!world.paused)camera.orbit(dx,dy);});return true;
        }
        if(event.getAction()==MotionEvent.ACTION_SCROLL){final int delta=event.getScrollDelta();queue(()->{if(!world.paused)camera.zoom(delta);});return true;}
        return true;
    }
}
