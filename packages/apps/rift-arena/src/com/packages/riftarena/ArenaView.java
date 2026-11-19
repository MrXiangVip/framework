package com.packages.riftarena;

import com.android.content.Context;
import com.android.graphics.Canvas;
import com.android.graphics.FontManager;
import com.android.os.Handler;
import com.android.view.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/** UI snapshots cross threads; simulation and GL stay on their owned worker. */
public final class ArenaView extends View implements AutoCloseable {
    private final ArenaWorld world=new ArenaWorld();
    private final ArenaMusic music=new ArenaMusic(ArenaView.class.getClassLoader());
    private final ArenaInput keys=new ArenaInput();
    private final ConcurrentLinkedQueue<Runnable> commands=new ConcurrentLinkedQueue<>();
    private final AtomicBoolean pending=new AtomicBoolean();
    private volatile boolean closed;
    private volatile Frame frame;
    private final ArenaFramePool frames=new ArenaFramePool(900,600);
    private final ArenaMetrics metrics=new ArenaMetrics();
    private static final class Aim {final double x,z;final int generation;Aim(double x,double z,int generation){this.x=x;this.z=z;this.generation=generation;}}
    private final java.util.concurrent.atomic.AtomicReference<Aim> latestAim=new java.util.concurrent.atomic.AtomicReference<>();
    private final java.util.concurrent.atomic.AtomicLong inputAt=new java.util.concurrent.atomic.AtomicLong();
    private volatile String error="";
    private volatile Thread worker;
    private BufferedImage uiBuffer;
    private int choice,downX,downY;
    private final java.util.List<Hit> hits=new ArrayList<>();
    private final Handler ui=new Handler(m->{pending.set(false);if(!closed)invalidate();return true;});
    private static final class Hit {final Rectangle box;final Runnable action;Hit(int x,int y,int w,int h,Runnable r){box=new Rectangle(x,y,w,h);action=r;}}
    static final class Dot {
        final double x,z,hp,maxHp,trail;final ArenaWorld.Kind kind;final int team;final boolean player,shield,selected,threat;
        Dot(ArenaWorld.Unit u,ArenaWorld w,ArenaRenderer r){trail=r.trailingHealth(u,w.elapsed);selected=w.selected==u;threat=ArenaRenderer.towerThreat(w,u);x=u.x;z=u.z;hp=u.hp;maxHp=u.maxHp;kind=u.kind;team=u.team;player=u.player;shield=w.shielded(u);}
    }
    static final class DamageText {
        final double x,z,amount,life;final int team;
        DamageText(ArenaWorld.Impact hit){x=hit.x;z=hit.z;amount=hit.amount;life=hit.life;team=hit.team;}
    }
    static final class Frame {
        final long producedAt=System.nanoTime();
        final BufferedImage image;final ArenaWorld.Screen screen;
        final double scale,zoom,cameraX,cameraZ,hp,maxHp,attack,time,respawn,recall,x,z,slamTime,dashTime,meteorTime;final boolean slamLanded,inFountain,retreatShot,scatterDraw;
        final double[] cooldowns;final int generation,gold,level,xp,kills,deaths,hero,wave,weapon,armor;
        final java.util.List<DamageText> damageTexts=new ArrayList<>();
        final String notice;final java.util.List<Dot> dots=new ArrayList<>();
        Frame(BufferedImage image,ArenaWorld w,ArenaRenderer r){
            this.image=image;generation=w.generation;zoom=r.zoom;scale=ArenaRenderer.HUD_SCALE*zoom;scatterDraw=w.player.scatterDraw;retreatShot=w.player.retreatShot;meteorTime=w.player.meteorTime;inFountain=Math.hypot(w.player.x-w.player.homeX,w.player.z-w.player.homeZ)<=8;dashTime=w.player.dashTime;slamTime=w.player.slamTime;slamLanded=w.player.slamLanded;screen=w.screen;cameraX=r.cameraX;cameraZ=r.cameraZ;hp=w.player.hp;maxHp=w.player.maxHp;attack=w.player.attack;time=w.elapsed;respawn=w.player.respawn;recall=w.recall;x=w.player.x;z=w.player.z;
            cooldowns=w.cooldowns.clone();gold=w.gold;level=w.level;xp=w.xp;kills=w.kills;deaths=w.deaths;hero=w.hero;wave=w.wave;weapon=w.weapon;armor=w.armor;notice=w.noticeTime>0?w.notice:"";
            for(ArenaWorld.Unit u:w.units)if(u.alive())dots.add(new Dot(u,w,r));
            for(ArenaWorld.Impact hit:w.impacts)damageTexts.add(new DamageText(hit));
        }
    }
    public ArenaView(Context c){super(c);setFocusable(true);}
    private void start(){
        if(worker!=null||closed)return;
        worker=new Thread(()->{
            ArenaRenderer renderer=new ArenaRenderer();
            try{
                renderer.open(ArenaView.class.getClassLoader(),900,600);long last=System.nanoTime(),nextRender=last;ArenaClock clock=new ArenaClock();ArenaPresentation presentation=new ArenaPresentation();boolean wasCharging=false,wasMeteorCharging=false;
                while(!closed){long begin=System.nanoTime();double dt=(begin-last)/1e9;last=begin;
                    applyAim();
                    long input=inputAt.getAndSet(0);if(input!=0)metrics.record(4,begin-input);
                    Runnable r;while((r=commands.poll())!=null)r.run();
                    applyAim();
                    long simulationBegin=System.nanoTime();
                    clock.advance(dt,world.screen==ArenaWorld.Screen.PLAYING,()->{int state=keys.snapshot();presentation.capture(world);world.update(ArenaClock.STEP,ArenaInput.x(state),ArenaInput.z(state),(state&16)!=0);});
                    metrics.record(0,System.nanoTime()-simulationBegin);
                    music.update(world.screen);
                    boolean charging=world.screen==ArenaWorld.Screen.PLAYING&&world.player.slamTime>0&&!world.player.slamLanded;
                    boolean meteorCharging=world.screen==ArenaWorld.Screen.PLAYING&&world.chargingMeteor();
                    while(!world.soundEvents.isEmpty()){
                        String sound=world.soundEvents.removeFirst();if((!sound.equals("slam-charge")||charging)&&(!sound.equals("meteor-charge")||meteorCharging))music.playEffect(sound);
                    }
                    if(wasCharging&&!charging)music.stopEffect("slam-charge");wasCharging=charging;
                    if(wasMeteorCharging&&!meteorCharging)music.stopEffect("meteor-charge");wasMeteorCharging=meteorCharging;
                    if(begin>=nextRender||frame==null||frame.screen!=world.screen){
                        BufferedImage target=frames.beginWrite();
                        if(target!=null){long renderBegin=System.nanoTime();Frame next;
                            presentation.apply(world,clock.alpha());
                            try{renderer.render(world,target);next=new Frame(target,world,renderer);}finally{presentation.restore();}
                            metrics.record(1,System.nanoTime()-renderBegin);
                            synchronized(frames){frames.publish(target);frame=next;}
                            if(pending.compareAndSet(false,true))ui.sendMessage(ui.obtainMessage(0,null));
                        }
                        long interval=world.screen==ArenaWorld.Screen.PLAYING?16666667L:100000000L;
                        nextRender=Math.max(nextRender+interval,System.nanoTime());
                    }
                    metrics.report();
                    long wait=Math.min(world.screen==ArenaWorld.Screen.PLAYING?8333333L:100000000L,Math.max(0,nextRender-System.nanoTime()));
                    if(wait>0)LockSupport.parkNanos(wait);
                    if(Thread.currentThread().isInterrupted())break;
                }
            }catch(Throwable failure){error="3D 场景加载失败："+(failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage());failure.printStackTrace();ui.sendMessage(ui.obtainMessage(0,null));}
            finally{music.close();renderer.close();}
        },"rift-arena-render");worker.setDaemon(true);worker.start();
    }
    private void wakeRenderer(){inputAt.compareAndSet(0,System.nanoTime());Thread active=worker;if(active!=null)LockSupport.unpark(active);}
    public void pause(){keys.clear();music.update(ArenaWorld.Screen.PAUSED);commands.add(world::pause);wakeRenderer();}
    @Override protected void onFocusChanged(boolean focused){if(!focused)pause();}
    @Override public void close(){music.close();closed=true;keys.clear();commands.clear();if(worker!=null)worker.interrupt();}
    @Override protected void onMeasure(int w,int h){setMeasuredDimension(resolveSize(1200,w),resolveSize(800,h));}
    @Override protected void onDraw(Canvas canvas){
        if(getWidth()<1||getHeight()<1)return;long composeBegin=System.nanoTime();start();
        if(uiBuffer==null||uiBuffer.getWidth()!=getWidth()||uiBuffer.getHeight()!=getHeight())
            uiBuffer=new BufferedImage(getWidth(),getHeight(),BufferedImage.TYPE_INT_RGB);
        BufferedImage image=uiBuffer;Graphics2D g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(new Color(0x081522));g.fillRect(0,0,getWidth(),getHeight());
        double scale=Math.min(getWidth()/1200.0,getHeight()/800.0);g.translate((getWidth()-1200*scale)/2,(getHeight()-800*scale)/2);g.scale(scale,scale);hits.clear();Frame f;
        synchronized(frames){f=frame;if(f!=null)frames.retain(f.image);}
        if(f!=null)try{g.drawImage(f.image,0,0,1200,800,null);metrics.record(3,System.nanoTime()-f.producedAt);}finally{frames.release(f.image);}
        if(!error.isEmpty()){
            veil(g,.9f);text(g,"竞技场暂时无法打开",90,280,38,0xf2dfb2);wrap(g,error,90,340,55,19);text(g,"请检查 Linux 显示服务与 OpenGL 驱动，然后关闭窗口重试。",90,480,20,0xafc4cf);
        }else if(f==null){text(g,"RIFT ARENA",90,330,64,0xe8d3a0);text(g,"正在唤醒竞技场…",94,385,24,0xb8cdd0);}
        else if(f.screen==ArenaWorld.Screen.TITLE)title(g);
        else{
            healthBars(g,f);damageNumbers(g,f);hud(g,f);
            if(f.screen==ArenaWorld.Screen.PAUSED){veil(g,.78f);text(g,"战场暂歇",466,245,46,0xf5e4b9);text(g,"准备好了，就继续这场战斗。",435,290,21,0xa8c1c9);
                button(g,"继续对战  →",450,340,300,55,()->commands.add(world::resume),true);
                button(g,"重新开始",450,413,300,50,()->commands.add(()->world.reset(f.hero)),false);
                button(g,"返回选角",450,480,300,50,()->commands.add(()->world.screen=ArenaWorld.Screen.TITLE),false);
            }else if(f.screen==ArenaWorld.Screen.WON||f.screen==ArenaWorld.Screen.LOST){
                veil(g,.78f);boolean win=f.screen==ArenaWorld.Screen.WON;text(g,win?"VICTORY":"DEFEAT",405,250,72,win?0xf0d497:0xf29398);text(g,win?"敌方水晶已摧毁":"我方水晶已被摧毁",475,300,26,0xd6e5df);
                text(g,"击杀 "+f.kills+"    阵亡 "+f.deaths+"    用时 "+time(f.time),414,357,22,0xadc4ca);
                button(g,"再战一局",450,405,300,55,()->commands.add(()->world.reset(f.hero)),true);
                button(g,"返回选角",450,480,300,50,()->commands.add(()->world.screen=ArenaWorld.Screen.TITLE),false);
            }
        }
        if(f!=null&&error.isEmpty())button(g,!music.isAvailable()?"声音不可用":music.isMuted()?"声音：关 [M]":"声音：开 [M]",947,93,158,42,()->{music.toggleMuted();invalidate();},false);
        g.dispose();canvas.drawImage(image,0,0,getWidth(),getHeight());metrics.record(2,System.nanoTime()-composeBegin);
    }
    private void title(Graphics2D g){
        veil(g,.68f);text(g,"R I F T   A R E N A",65,90,20,0x8ecacc);text(g,"星隙竞技场",60,167,61,0xf0dfb1);
        text(g,"守住你的水晶，带领兵线夺下胜利。",65,215,23,0xb6cbcf);text(g,"3D 单路人机对战   /   选择你的英雄",67,258,18,0x89aaa9);
        String[] roles={"近战战士 / 更高生命","远程射手 / 更远射程","中程法师 / 均衡输出"};String[] stats={"生命 900   攻击 62   射程 3.4","生命 650   攻击 48   射程 8.0","生命 700   攻击 53   射程 6.5"};
        for(int i=0;i<3;i++){final int hero=i;int x=65+i*360;boolean active=choice==i;
            panel(g,x,300,340,243,active?0x24484e:0x122b36);g.setColor(new Color(active?0xe3c88f:0x45616b));g.drawRoundRect(x,300,340,243,14,14);
            text(g,"0"+(i+1),x+24,348,20,0x8ba9ab);g.setColor(new Color(i==0?0xb4dcd1:i==1?0xd7bd80:0x9aace3));g.fillPolygon(new int[]{x+276,x+300,x+276,x+252},new int[]{326,362,398,362},4);
            text(g,ArenaWorld.HEROES[i],x+24,415,30,0xf1dfb0);text(g,roles[i],x+24,451,18,0xb2c9c8);text(g,stats[i],x+24,486,16,0x92b1b7);text(g,active?"已选择  /  READY":"点击选择",x+24,521,17,active?0xe8ce94:0x75989f);
            hits.add(new Hit(x,300,340,243,()->{choice=hero;invalidate();}));
        }
        button(g,"进入竞技场   →",65,586,330,62,()->begin(),true);
        text(g,"WASD 移动  ·  右键移动 / 选敌  ·  空格普攻",65,702,19,0xc6d6d2);
        text(g,"Q / E / R 技能   ·   B 回城   ·   1 / 2 购买装备   ·   Esc 暂停",65,738,18,0x91b0b5);
        text(g,"目标：先拆防御塔，再摧毁敌方水晶。泉水可恢复生命与购买装备。",445,620,17,0xb0c3be);
    }
    private void applyAim(){Aim aim=latestAim.get();if(aim!=null&&aim.generation==world.generation){world.aimX=aim.x;world.aimZ=aim.z;}}
    private void queueCast(int skill){final Aim aim=latestAim.get();commands.add(()->{double x=world.aimX,z=world.aimZ;if(aim!=null&&aim.generation==world.generation){world.aimX=aim.x;world.aimZ=aim.z;}world.cast(skill);world.aimX=x;world.aimZ=z;});}
    private void begin(){final int selected=choice;keys.clear();commands.add(()->world.reset(selected));requestFocus();}
    private void hud(Graphics2D g,Frame f){
        panel(g,22,18,244,44,0x102835);text(g,"星隙竞技场",38,46,20,0xe7d6a8);
        text(g,"第 "+f.wave+" 波",179,45,14,0x8eb0b8);
        panel(g,466,18,268,44,0x102835);text(g,"蓝 "+f.kills+" : "+f.deaths+" 红",484,46,19,0xdbdfc9);text(g,time(f.time),650,45,15,0x8fb4b8);
        panel(g,1007,18,170,44,0x102835);text(g,"金币  "+f.gold,1023,47,21,0xe6c782);
        button(g,"Ⅱ",1117,93,60,42,()->commands.add(world::pause),false);
        if(!f.notice.isEmpty()){
            // A fixed feedback slot keeps purchases, cancellation and combat messages predictable.
            panel(g,300,92,600,38,0x172e38);text(g,f.notice,318,117,17,0xf0d99f);
        }
        if(f.hp>0&&f.hp/f.maxHp<=.25){
            panel(g,22,78,244,35,0x542d2d);text(g,"生命危急 · B 回城补给",37,101,16,0xffb89e);
        }
        miniMap(g,f);
        panel(g,223,680,954,98,0x102835);
        text(g,ArenaWorld.HEROES[f.hero]+"  Lv."+f.level,244,709,22,0xe9d8a6);bar(g,244,724,255,12,f.hp/f.maxHp,0x59cba6);text(g,(int)f.hp+" / "+(int)f.maxHp+"    攻击 "+(int)f.attack,244,759,16,0xb4cccb);
        bar(g,244,742,255,3,f.xp/(f.level*100.0),0xd9c481);
        for(int i=0;i<3;i++)skillButton(g,f,i,527+i*100);
        button(g,f.recall>0?"B 引导中":"B 回城",835,701,92,55,()->commands.add(world::recall),false);
        text(g,f.recall>0?"移动 / 受伤打断":"4 秒引导",837,771,12,0x9dbbbd);
        shopButton(g,f,0,941,100);shopButton(g,f,1,1051,109);
        text(g,"WASD 移动   ·   右键追击   ·   按住空格普攻   ·   Q/E/R 技能   ·   滚轮缩放   ·   Esc 暂停",245,795,14,0x9cb8bd);
        if(f.hp<=0){panel(g,410,300,380,86,0x122b36);text(g,"英雄阵亡",520,334,27,0xf2c4bd);text(g,"距离复活  "+(int)Math.ceil(f.respawn)+" 秒",510,370,22,0xd7ddd1);}
        if(f.recall>0){panel(g,440,610,320,45,0x122b36);bar(g,457,638,286,6,1-f.recall/4,0x83d5ea);text(g,"正在回城… "+String.format(Locale.ROOT,"%.1f",f.recall),505,631,18,0xe4dfbd);}
    }
    static String skillState(Frame f,int skill){
        if(f.hp<=0)return "阵亡";
        if(skill==0&&f.dashTime>0)return f.hero==1?"后撤":"冲刺";
        if(skill==0&&f.retreatShot)return "拉弓";
        if(skill==1&&f.scatterDraw)return "拉弓";
        if(skill==1&&f.slamTime>0)return f.slamLanded?"收招":"蓄力";
        if(skill==2&&f.meteorTime>0)return f.meteorTime>ArenaWorld.METEOR_CAST-ArenaWorld.METEOR_RELEASE?"蓄力":"收招";
        return f.cooldowns[skill]>0?String.format(Locale.ROOT,"%.1f s",f.cooldowns[skill]):"就绪";
    }
    private void skillButton(Graphics2D g,Frame f,int skill,int x){
        String state=skillState(f,skill);
        boolean active=state.equals("拉弓")||state.equals("后撤")||state.equals("冲刺")||state.equals("蓄力")||state.equals("收招");
        boolean ready=f.hp>0&&f.cooldowns[skill]<=0;
        int tint=skill==0?0x79dce1:skill==1?0xefc77e:0xe8a18a;
        panel(g,x,690,88,68,ready?0x294b4d:0x172e38);
        g.setColor(new Color(active?0xffe1a0:ready?tint:0x52656a));g.drawRoundRect(x,690,88,68,10,10);
        Graphics2D icon=(Graphics2D)g.create();icon.translate(x+44,720);icon.setColor(new Color(tint));icon.setStroke(new BasicStroke(2.5f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        if(f.hero==2&&skill<2){
            if(skill==0){icon.drawOval(-15,-15,30,30);icon.drawLine(-23,0,23,0);icon.drawLine(0,-23,0,23);icon.drawOval(-5,-5,10,10);}
            else{for(int i=0;i<8;i++){double a=i*Math.PI/4;icon.drawLine((int)(Math.cos(a)*8),(int)(Math.sin(a)*8),(int)(Math.cos(a)*23),(int)(Math.sin(a)*23));}icon.drawOval(-10,-10,20,20);}
        }else if(skill==0){
            if(f.hero==1)icon.rotate(Math.PI);
            for(int i=0;i<2;i++){int dx=i*13-9;icon.drawLine(dx-10,-11,dx+1,0);icon.drawLine(dx+1,0,dx-10,11);}
            icon.drawLine(-25,-6,-16,-6);icon.drawLine(-25,6,-16,6);
        }else if(skill==1&&f.hero==1){
            for(int i=-1;i<=1;i++){int dx=i*15;icon.drawLine(0,14,dx,-14);icon.drawLine(dx,-14,dx-4,-6);icon.drawLine(dx,-14,dx+4,-6);}
        }else if(skill==1){
            icon.drawLine(-12,-16,5,5);icon.drawLine(-4,-12,0,-16);icon.drawLine(-9,-6,0,-13);
            icon.drawArc(-23,-2,46,23,195,150);icon.drawArc(-15,3,30,13,190,160);icon.drawLine(4,8,8,14);
        }else{
            icon.fillPolygon(new int[]{-3,9,13,4,-7,-11},new int[]{-7,-10,1,11,8,0},6);
            icon.drawLine(-7,-11,-17,-22);icon.drawLine(0,-14,-6,-23);icon.drawLine(-14,-3,-23,-11);
            icon.drawArc(-21,8,43,12,180,180);
        }
        icon.dispose();
        if(!ready&&!active){
            g.setColor(new Color(6,17,25,175));
            double fraction=f.hp<=0?1:ArenaWorld.clamp(f.cooldowns[skill]/ArenaWorld.COOLDOWNS[skill],0,1);
            g.fillArc(x+20,696,48,48,90,-(int)Math.ceil(fraction*360));
        }
        text(g,new String[]{"Q","E","R"}[skill],x+7,708,13,0xf0e0b8);
        text(g,ArenaWorld.skillName(f.hero,skill),x+27-(f.hero==2&&skill<2?12:skill==1?(f.hero==1?12:7):skill==0&&f.hero==1?12:0),753,14,0xd4dfd7);
        text(g,state,x+19,774,13,active?0xffdc91:ready?0xa8e4d4:0xb6c7cb);
        final int selected=skill;
        hits.add(new Hit(x,690,88,68,()->queueCast(selected)));
    }
    static String purchaseState(Frame f,int item){
        int cost=100+(item==0?f.weapon:f.armor)*60;
        return f.hp<=0?"阵亡不可购买":!f.inFountain?"需回泉水":f.gold<cost?"金币不足":"可购买";
    }
    private void shopButton(Graphics2D g,Frame f,int item,int x,int width){
        int cost=100+(item==0?f.weapon:f.armor)*60;String status=purchaseState(f,item);
        boolean enabled=status.equals("可购买");
        button(g,item==0?"1 锋刃":"2 护甲",x,690,width,35,()->commands.add(()->world.buy(item)),enabled);
        text(g,cost+" 金 / "+(item==0?"+18攻":"+180血"),x+3,744,12,0xc4c9b6);
        text(g,status,x+3,766,12,enabled?0x87d7ba:0xaab9b9);
    }
    private void miniMap(Graphics2D g,Frame f){
        panel(g,22,588,180,190,0x102835);text(g,"战场态势",40,614,16,0xbcd1ca);
        g.setColor(new Color(0x385b58));g.fillRoundRect(36,631,152,127,5,5);g.setColor(new Color(0x8a8262));g.fillRect(38,691,148,7);g.setColor(new Color(0x3d8596));g.fillRect(106,633,8,122);
        for(Dot d:f.dots){int x=112+(int)(d.x*1.65),y=695+(int)(d.z*2.7);g.setColor(new Color(d.player?0xffe6a6:d.team==0?0x64e0df:0xfa8790));int n=d.kind==ArenaWorld.Kind.HERO?7:d.kind==ArenaWorld.Kind.SOLDIER?3:9;if(d.kind==ArenaWorld.Kind.TOWER||d.kind==ArenaWorld.Kind.CORE)g.fillRect(x-n/2,y-n/2,n,n);else g.fillOval(x-n/2,y-n/2,n,n);}
        g.setColor(new Color(0xd2ddc1));int vx=112+(int)(f.cameraX*1.65),vy=695+(int)(f.cameraZ*2.7);int halfW=(int)(49/f.zoom),halfH=(int)(56/f.zoom);g.drawRect(vx-halfW,vy-halfH,halfW*2,halfH*2);
    }
    private void damageNumbers(Graphics2D g,Frame f){
        for(DamageText hit:f.damageTexts){
            double age=.65-hit.life;
            int x=(int)(600+(hit.x-f.cameraX)*f.scale)-12;
            int y=(int)(400+(hit.z-f.cameraZ)*ArenaRenderer.SIN*f.scale-58-age*58);
            if(x<0||x>1200||y<80||y>670)continue;
            Composite old=g.getComposite();g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,(float)Math.min(1,hit.life/.2)));
            String label="-"+(int)Math.ceil(hit.amount);
            text(g,label,x+2,y+2,26,0x10202a);text(g,label,x,y,26,hit.team==0?0xffe49a:0xff8e83);g.setComposite(old);
        }
    }
    private void healthBars(Graphics2D g,Frame f){
        for(Dot d:f.dots){double tall=d.kind==ArenaWorld.Kind.TOWER?6:d.kind==ArenaWorld.Kind.CORE?7:d.kind==ArenaWorld.Kind.HERO?4.5:2.9;
            int x=(int)(600+(d.x-f.cameraX)*f.scale),y=(int)(400+(d.z-f.cameraZ)*ArenaRenderer.SIN*f.scale-tall*ArenaRenderer.COS*f.scale);
            boolean soldier=d.kind==ArenaWorld.Kind.SOLDIER,hero=d.kind==ArenaWorld.Kind.HERO;
            int w=soldier?32:hero?78:94,h=soldier?4:hero?7:9;
            if(x<0||x>1200||y<85||y>660)continue;
            int left=x-w/2;
            g.setColor(new Color(d.selected?0xff8c78:d.player?0xffe0a0:0x14282e));g.fillRect(left-2,y-2,w+4,h+4);
            g.setColor(new Color(0x091820));g.fillRect(left,y,w,h);
            g.setColor(new Color(0xeab780));g.fillRect(left,y,(int)(w*ArenaWorld.clamp(d.trail/d.maxHp,0,1)),h);
            g.setColor(new Color(d.team==0?0x55cce5:0xf16c79));g.fillRect(left,y,(int)(w*ArenaWorld.clamp(d.hp/d.maxHp,0,1)),h);
            if(!soldier){g.setColor(new Color(0x18323a));for(int i=1;i<4;i++)g.fillRect(left+w*i/4,y,1,h);}
            if(d.player)text(g,"你",x-7,y-7,13,0xffe0a0);
            else if(d.selected)text(g,"目标",x-13,y-7,12,0xffb49a);
            else if(d.shield)text(g,"护盾",x-13,y-7,12,0xb5d3e1);
            if(d.threat)text(g,"锁定你",x-21,y-23,13,0xff8a72);
        }
        for(Dot d:f.dots)if(d.threat){
            panel(g,454,146,292,36,0x562c2a);text(g,"防御塔锁定你 · 撤出射程",472,171,18,0xffc4a0);break;
        }
    }
    private void bar(Graphics2D g,int x,int y,int w,int h,double ratio,int color){g.setColor(new Color(0x081b24));g.fillRect(x-1,y-1,w+2,h+2);g.setColor(new Color(color));g.fillRect(x,y,(int)(w*ArenaWorld.clamp(ratio,0,1)),h);}
    private void text(Graphics2D g,String t,int x,int y,int size,int color){g.setColor(new Color(color));FontManager.layout(t,size,Font.PLAIN).draw(g,x,y);}
    private void wrap(Graphics2D g,String t,int x,int y,int length,int size){for(int i=0;i<t.length();i+=length)text(g,t.substring(i,Math.min(t.length(),i+length)),x,y+(i/length)*30,size,0xb7cbd0);}
    private void panel(Graphics2D g,int x,int y,int w,int h,int color){g.setColor(new Color((color>>16)&255,(color>>8)&255,color&255,235));g.fillRoundRect(x,y,w,h,12,12);}
    private void veil(Graphics2D g,float a){g.setColor(new Color(.02f,.06f,.10f,a));g.fillRect(0,0,1200,800);}
    private void button(Graphics2D g,String t,int x,int y,int w,int h,Runnable action,boolean primary){g.setColor(new Color(primary?0xdec58c:0x294651));g.fillRoundRect(x,y,w,h,8,8);text(g,t,x+12,y+h/2+7,w<120?16:22,primary?0x183139:0xccdcda);hits.add(new Hit(x,y,w,h,action));}
    private static String time(double t){int n=(int)t;return String.format(Locale.ROOT,"%02d:%02d",n/60,n%60);}
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        if(e.getType()==KeyEvent.TEXT)return false;int k=e.getValue();if(k>='A'&&k<='Z')k=Character.toLowerCase((char)k);
        if(e.getType()==KeyEvent.UP){keys.remove(k);wakeRenderer();return true;}if(e.getType()!=KeyEvent.DOWN)return false;if(!keys.add(k))return true;
        Frame f=frame;if(f==null||!error.isEmpty())return true;
        if(k=='m'){music.toggleMuted();invalidate();}
        else if(k==KeyEvent.ESCAPE){keys.clear();keys.add(k);commands.add(()->{if(world.screen==ArenaWorld.Screen.PAUSED)world.resume();else world.pause();});}
        else if(k==KeyEvent.ENTER){if(f.screen==ArenaWorld.Screen.TITLE)begin();else commands.add(()->{if(world.screen==ArenaWorld.Screen.PAUSED)world.resume();else if(world.screen==ArenaWorld.Screen.WON||world.screen==ArenaWorld.Screen.LOST)world.reset(world.hero);});}
        else if(f.screen==ArenaWorld.Screen.TITLE){if(k>='1'&&k<='3'){choice=k-'1';invalidate();}}
        else if(k==32)commands.add(world::requestAttack);else if(k=='q')queueCast(0);else if(k=='e')queueCast(1);else if(k=='r')queueCast(2);else if(k=='b')commands.add(world::recall);else if(k=='1')commands.add(()->world.buy(0));else if(k=='2')commands.add(()->world.buy(1));
        wakeRenderer();return true;
    }
    @Override protected boolean onTouchEvent(MotionEvent e){
        if(e.getAction()==MotionEvent.ACTION_CANCEL){keys.clear();wakeRenderer();return true;}
        double scale=Math.max(.001,Math.min(getWidth()/1200.0,getHeight()/800.0));int x=(int)((e.getX()-(getWidth()-1200*scale)/2)/scale),y=(int)((e.getY()-(getHeight()-800*scale)/2)/scale);
        if(x<0||x>=1200||y<0||y>=800)return false;
        Frame f=frame;
        boolean field=y>135&&y<680&&!(x<223&&y>580);
        if(e.getAction()==MotionEvent.ACTION_SCROLL&&f!=null&&f.screen==ArenaWorld.Screen.PLAYING&&field){final int delta=e.getScrollDelta();commands.add(()->world.cameraZoom=ArenaWorld.clamp(world.cameraZoom-delta*.1,.85,1.25));wakeRenderer();return true;}
        if(f!=null&&f.screen==ArenaWorld.Screen.PLAYING&&field){double wx=f.cameraX+(x-600)/f.scale,wz=f.cameraZ+(y-400)/(f.scale*ArenaRenderer.SIN);latestAim.set(new Aim(ArenaWorld.clamp(wx,-43,43),ArenaWorld.clamp(wz,-18,18),f.generation));}
        if(e.getAction()==MotionEvent.ACTION_DOWN){requestFocus();downX=x;downY=y;if(e.getButton()==3&&f!=null&&f.screen==ArenaWorld.Screen.PLAYING&&field){double wx=f.cameraX+(x-600)/f.scale,wz=f.cameraZ+(y-400)/(f.scale*ArenaRenderer.SIN);
                double best=Double.MAX_VALUE;
                for(Dot d:f.dots)if(d.team==1){double px=600+(d.x-f.cameraX)*f.scale,py=400+(d.z-f.cameraZ)*f.scale*ArenaRenderer.SIN;double tall=(d.kind==ArenaWorld.Kind.TOWER||d.kind==ArenaWorld.Kind.CORE?85:55)*f.zoom;
                    double distance=Math.hypot(x-px,y-(py-tall/2));if(Math.abs(x-px)<30*f.zoom&&y>py-tall&&y<py+12&&distance<best){best=distance;wx=d.x;wz=d.z;}}
                final double targetX=wx,targetZ=wz;commands.add(()->world.command(targetX,targetZ));wakeRenderer();}return true;}
        if(e.getAction()==MotionEvent.ACTION_UP&&e.getButton()==1){for(Hit hit:new ArrayList<>(hits))if(hit.box.contains(x,y)&&hit.box.contains(downX,downY)){hit.action.run();wakeRenderer();break;}return true;}
        return e.getAction()==MotionEvent.ACTION_MOVE||e.getAction()==MotionEvent.ACTION_HOVER;
    }
}
