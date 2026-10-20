package com.packages.kof;

import com.android.content.Context;
import com.android.graphics.Canvas;
import com.android.os.Handler;
import com.android.util.AttributeSet;
import com.android.view.View;
import com.android.view.MotionEvent;
import com.android.view.KeyEvent;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Landscape arcade interface. Reference illustrations are scenery, never the combat simulation. */
public class KofView extends View {
    private final GameSession session = new GameSession();
    private final FightAudio audio = new FightAudio();
    private final Set<Integer> keys = new HashSet<>();
    private final BufferedImage choice, battle;
    private BufferedImage frame;
    private Timer timer;
    private long lastTick;
    private double animation;
    private int held = -1;
    private final AtomicBoolean pending = new AtomicBoolean();
    private final Handler ui = new Handler(message -> {
        pending.set(false);
        if (!attached()) { stop(); return true; }
        long now = System.nanoTime();
        advance(Math.min(.1, (now - lastTick) / 1e9)); lastTick = now;
        invalidate(); return true;
    });
    private static final int[][] BUTTONS = {{32,625,72,56},{114,625,72,56},{196,625,90,56},{296,625,90,56},
        {830,625,108,56},{948,625,108,56},{1066,625,182,56},{1100,138,148,38},{32,138,150,38},{945,138,145,38}};
    private static final String[] LABELS = {"<  A", "D  >", "JUMP  W", "GUARD  L", "PUNCH  J", "KICK  K", "BURST  U / 40", "PAUSE  P", "ROSTER  ESC", "SOUND  M"};

    public KofView(Context context, AttributeSet attrs) {
        super(context, attrs);
        choice = load("res/drawable/choice.jpeg"); battle = load("res/drawable/battle.jpeg");
        session.getFight().setSoundListener(audio::play);
    }
    private BufferedImage load(String path) {
        try (InputStream input = getContext().getClassLoader().getResourceAsStream(path)) {
            if (input == null) throw new IllegalStateException("Missing KOF resource: " + path);
            BufferedImage image = ImageIO.read(input);
            if (image == null) throw new IllegalStateException("Invalid KOF image: " + path);
            BufferedImage normalized = new BufferedImage(2048, 1144, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = normalized.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(image, 0, 0, 2048, 1144, null); graphics.dispose();
            return normalized;
        } catch (java.io.IOException error) { throw new IllegalStateException(path, error); }
    }
    public GameSession getSession() { return session; }
    public void advance(double dt) {
        animation += Math.max(0, Math.min(.1, dt));
        syncControls();
        GameSession.Screen before = session.getScreen(); session.update(dt);
        if (before != session.getScreen()) clearInput();
    }
    private boolean attached() {
        View root = this;
        while (root.getParent() instanceof View) root = (View) root.getParent();
        return root.getParent() != null;
    }
    private void startTimer() {
        if (timer != null || !attached()) return;
        lastTick = System.nanoTime(); timer = new Timer("kof-frames", true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() {
                if (pending.compareAndSet(false, true)) ui.sendMessage(ui.obtainMessage(0, null));
            }
        }, 33, 33);
    }
    private void stop() { if (timer != null) { timer.cancel(); timer = null; } clearInput(); audio.close(); }
    private void clearInput() { held = -1; keys.clear(); session.getFight().releaseControls(); }
    @Override protected void onMeasure(int w, int h) { setMeasuredDimension(resolveSize(1280,w), resolveSize(720,h)); }
    private double scale() { return Math.max(.001, Math.min(getWidth()/1280.0, getHeight()/720.0)); }
    private double ox() { return (getWidth()-1280*scale())/2; }
    private double oy() { return (getHeight()-720*scale())/2; }
    private boolean inside(double x, double y, int[] b) { return x>=b[0] && y>=b[1] && x<b[0]+b[2] && y<b[1]+b[3]; }
    private int buttonAt(double x, double y) {
        for (int i=0;i<BUTTONS.length;i++) if(inside(x,y,BUTTONS[i])) return i;
        return -1;
    }
    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        double x=(e.getX()-ox())/scale(), y=(e.getY()-oy())/scale();
        if(e.getAction()==MotionEvent.ACTION_CANCEL) {
            clearInput();
            if(session.getScreen()==GameSession.Screen.BATTLE && !session.getFight().isPaused()) togglePause();
        } else if(e.getAction()==MotionEvent.ACTION_DOWN) {
            if(session.getScreen()==GameSession.Screen.SELECT) {
                if(y>=535 && y<632 && x>=55 && x<1225) choose((int)((x-55)/117));
                else if(inside(x,y,new int[]{490,652,300,48})) startBattle();
                else if(inside(x,y,new int[]{55,652,190,48})) choose(session.getSelected()-1);
                else if(inside(x,y,new int[]{1035,652,190,48})) choose(session.getSelected()+1);
            } else if(session.getScreen()==GameSession.Screen.RESULT) {
                if(inside(x,y,new int[]{490,405,300,48})) returnToRoster();
            } else {
                held=buttonAt(x,y); action(held); syncControls();
            }
        } else if(e.getAction()==MotionEvent.ACTION_UP || e.getAction()==MotionEvent.ACTION_MOVE && buttonAt(x,y)!=held) {
            held=-1; syncControls();
        }
        invalidate(); return true;
    }
    private void choose(int index) { session.select(index); audio.play(FightEngine.Sound.SELECT); }
    private void startBattle() { clearInput(); audio.clear(); audio.setPaused(false); session.start(); }
    private void returnToRoster() { clearInput(); session.back(); audio.clear(); audio.setPaused(false); }
    private void togglePause() { session.getFight().togglePause(); held = -1; session.getFight().releaseControls(); audio.setPaused(session.getFight().isPaused()); }
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int k=Character.toLowerCase(e.getValue());
        if(e.getType()==KeyEvent.TEXT) return true;
        if(e.getType()==KeyEvent.UP) { keys.remove(k); syncControls(); return true; }
        if(e.getType()!=KeyEvent.DOWN || !keys.add(k)) return true;
        if(k=='m') { audio.setMuted(!audio.isMuted()); invalidate(); return true; }
        if(session.getScreen()==GameSession.Screen.SELECT) {
            if(k=='a'||k==KeyEvent.LEFT) choose(session.getSelected()-1);
            if(k=='d'||k==KeyEvent.RIGHT) choose(session.getSelected()+1);
            if(k>= '0' && k<= '9') choose(k=='0'?9:k-'1');
            if(k==KeyEvent.ENTER||k==' ') startBattle();
        } else if(session.getScreen()==GameSession.Screen.RESULT) {
            if(k==KeyEvent.ENTER||k==KeyEvent.ESCAPE||k==' ') returnToRoster();
        } else {
            if(k==KeyEvent.ESCAPE) returnToRoster();
            else if(k=='p') togglePause();
            else if(k=='w'||k==' '||k==0xff52) action(2);
            else if(k=='j') action(4);
            else if(k=='k') action(5);
            else if(k=='u') action(6);
            syncControls();
        }
        invalidate(); return true;
    }
    private void action(int index) {
        FightEngine f=session.getFight();
        switch(index) {
            case 2: f.jump(); break;
            case 4: f.attack(FightEngine.Move.PUNCH); break;
            case 5: f.attack(FightEngine.Move.KICK); break;
            case 6: f.attack(FightEngine.Move.SPECIAL); break;
            case 7: togglePause(); break;
            case 8: returnToRoster(); break;
            case 9: audio.setMuted(!audio.isMuted()); break;
            default: break;
        }
    }
    private void syncControls() {
        FightEngine f=session.getFight();
        if(session.getScreen()!=GameSession.Screen.BATTLE||f.isPaused()) { f.releaseControls(); return; }
        boolean left=held==0||keys.contains((int)'a')||keys.contains(KeyEvent.LEFT);
        boolean right=held==1||keys.contains((int)'d')||keys.contains(KeyEvent.RIGHT);
        f.setDirection((right?1:0)-(left?1:0)); f.setBlocking(held==3||keys.contains((int)'l'));
    }
    @Override protected void onDraw(Canvas canvas) {
        if(getWidth()<=0||getHeight()<=0) return;
        if(frame==null||frame.getWidth()!=getWidth()||frame.getHeight()!=getHeight()) frame=new BufferedImage(getWidth(),getHeight(),BufferedImage.TYPE_INT_RGB);
        Graphics2D g=frame.createGraphics();
        try {
            g.setColor(new Color(0x050a13)); g.fillRect(0,0,getWidth(),getHeight());
            g.translate(ox(),oy()); g.scale(scale(),scale());
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            if(session.getScreen()==GameSession.Screen.SELECT) selection(g); else arena(g);
        } finally {g.dispose();}
        canvas.drawImage(frame,0,0,getWidth(),getHeight()); startTimer();
    }
    private void text(Graphics2D g,String s,int x,int y,int size,int color,boolean center) {
        g.setFont(new Font(Font.SANS_SERIF,size>=24?Font.BOLD:Font.PLAIN,size));
        g.setColor(new Color(0,0,0,180)); int px=center?x-g.getFontMetrics().stringWidth(s)/2:x;
        g.drawString(s,px+1,y+2); g.setColor(new Color(color)); g.drawString(s,px,y);
    }
    private void panel(Graphics2D g,int x,int y,int w,int h,int accent) {
        g.setColor(new Color(8,15,28,225)); g.fillRoundRect(x,y,w,h,10,10);
        g.setStroke(new BasicStroke(1));g.setColor(new Color(accent));g.drawRoundRect(x,y,w,h,10,10);
    }
    private void button(Graphics2D g,int x,int y,int w,int h,String title,int accent,boolean pressed) {
        g.setPaint(new GradientPaint(x,y,new Color(pressed?accent:0x283b50),x,y+h,new Color(0x0c1527)));
        g.fillRoundRect(x,y,w,h,8,8);g.setColor(new Color(accent));g.setStroke(new BasicStroke(pressed?3:1));g.drawRoundRect(x,y,w,h,8,8);
        text(g,title,x+w/2,y+h/2+6,16,accent,true);
    }
    private void portrait(Graphics2D g,int index,int x,int y,int w,int h) {
        int sx=184+index*171;
        g.drawImage(choice,x,y,x+w,y+h,sx,879,sx+150,1022,null);
    }
    private void selection(Graphics2D g) {
        g.drawImage(choice,0,0,1280,720,null);
        g.setPaint(new GradientPaint(0,105,new Color(4,10,20,215),0,520,new Color(4,10,20,245)));
        g.fillRect(0,108,1280,612);
        text(g,"CHARACTER SELECT",640,145,22,0xffd68b,true);
        
        
        int id=session.getSelected(), cpu=(id+3)%10;
        preview(g,id,true); preview(g,cpu,false);
        text(g,"VS",640,320,96,0xf8ba69,true);
        text(g,"AIRPORT AT MIDNIGHT",640,370,13,0xb8cadb,true);
        text(g,"60 SECONDS / ONE BOUT",640,395,12,0x849bb5,true);
        text(g,"1P  ROSTER",55,517,17,0xffda8e,false);
        text(g,"A / D  OR  ARROWS TO SELECT     1 - 0  QUICK SELECT",775,517,12,0xb3c5d5,false);
        for(int i=0;i<10;i++) {
            int x=55+i*117;
            panel(g,x,535,108,97,i==id?0xffce67:0x34475d);
            portrait(g,i,x+4,539,100,68);
            if(i==id) {
                g.setColor(new Color(255,203,82,55));g.fillRect(x+4,539,100,68);
                g.setStroke(new BasicStroke(3));g.setColor(new Color(0xffd369));g.drawRoundRect(x-2,533,112,101,8,8);
            }
            text(g,GameSession.NAMES[i],x+54,624,11,i==id?0xffdc8e:0xa8bacd,true);
        }
        button(g,55,652,190,48,"<  PREVIOUS",0x94c8e2,false);
        button(g,490,652,300,48,"CONFIRM  /  ENTER",0xffcf73,true);
        button(g,1035,652,190,48,"NEXT  >",0x94c8e2,false);
    }
    private void preview(Graphics2D g,int id,boolean player) {
        int x=player?95:1035;
        portrait(g,id,x,185,150,143);
        text(g,player?"1P / YOU":"2P / CPU",x+75,172,16,player?0x7ae4f4:0xff9283,true);
        text(g,GameSession.NAMES[id],x+75,367,29,0xffe1a6,true);
        text(g,"SPD "+(155+id%4*12)+"  /  ATK +"+(id%3),x+75,392,12,0xb3c5d5,true);
        Graphics2D f=(Graphics2D)g.create();
        f.translate(player?370:910,494);f.scale(1.65,1.65);
        drawFighter(f,null,id,player?1:-1,false);f.dispose();
        text(g,player?"READY TO FIGHT":"CPU AUTO SELECT",x+75,435,11,0x819ab4,true);
    }
    private void arena(Graphics2D g) {
        FightEngine f=session.getFight();
        // Sample only unobstructed scenery; the reference's baked fighters/HUD are never drawn here.
        g.drawImage(battle,0,112,1280,380,0,160,600,365,null);
        for(int i=0;i<4;i++) g.drawImage(battle,i*320,370,i*320+320,610,i%2==0?0:300,500,i%2==0?300:0,1000,null);
        g.setPaint(new GradientPaint(0,150,new Color(4,9,24,35),0,605,new Color(4,9,24,110)));
        g.fillRect(0,112,1280,498);
        g.setColor(new Color(0x32acc5));g.fillRect(0,410,1280,2);
        for(int i=0;i<19;i++){g.setColor(new Color(i%2==0?0x5ad8fa:0xff655a));g.fillOval(i*73,426,5,3);}
        for(int i=0;i<9;i++){g.setColor(new Color(255,255,240,55));g.fillPolygon(new int[]{i*180-80,i*180-10,i*180+50,i*180-20},new int[]{551,551,564,564},4);}
        drawOnStage(g,f.getPlayer(),session.getSelected()); drawOnStage(g,f.getEnemy(),session.getOpponent());
        hud(g);
        g.setColor(new Color(6,12,24,240));g.fillRect(0,608,1280,112);
        for(int i=0;i<BUTTONS.length;i++) {
            int[] b=BUTTONS[i];
            String label=i==7&&f.isPaused()?"RESUME  P":i==9?(audio.isMuted()?"SFX OFF  M":audio.isAvailable()?"SFX ON  M":"NO AUDIO"):LABELS[i];
            button(g,b[0],b[1],b[2],b[3],label,i<4?0x86cddd:i==6?0xffcf73:0xe9a494,held==i);
        }
        text(g,"AIRPORT / MIDNIGHT",640,641,13,0xffd28a,true);
        text(g,"MOVE  +  STRIKE  +  GUARD",640,668,11,0x8ca4be,true);
        text(g,"W / SPACE  JUMP     J  PUNCH     K  KICK     L  GUARD     U  BURST     P  PAUSE     ESC  ROSTER",640,705,12,0x819ab4,true);
        if(session.getScreen()==GameSession.Screen.RESULT) {
            shade(g);panel(g,390,238,500,232,0xf3c575);
            text(g,f.getMessage(),640,306,57,0xffd583,true);
            String winner=f.getWinner()==0?"DOUBLE K.O. / EVEN SCORE":GameSession.NAMES[f.getWinner()==1?session.getSelected():session.getOpponent()]+" TAKES THE BOUT";
            text(g,winner,640,343,17,0xf0e4cb,true);
            text(g,"Returning to roster in "+(int)Math.ceil(session.getResultTime())+"...",640,378,15,0x9bb5ce,true);
            button(g,490,405,300,48,"CHARACTER SELECT  /  ENTER",0xffd184,false);
        } else if(f.isPaused()) {
            shade(g);panel(g,390,250,500,155,0x86cddd);
            text(g,"PAUSED",640,315,52,0xffd583,true);text(g,"P TO RESUME  /  ESC TO SELECT",640,367,16,0xc2d7e8,true);
        } else if(!f.getMessage().isEmpty()) {
            text(g,f.getMessage(),640,270,67,0xffd583,true);
        }
    }
    private void shade(Graphics2D g) {g.setColor(new Color(3,8,18,175));g.fillRect(0,112,1280,498);}
    private void hud(Graphics2D g) {
        FightEngine f=session.getFight();
        g.setPaint(new GradientPaint(0,0,new Color(0x071124),0,128,new Color(0x152139)));g.fillRect(0,0,1280,128);
        portrait(g,session.getSelected(),25,18,83,80);portrait(g,session.getOpponent(),1172,18,83,80);
        text(g,GameSession.NAMES[session.getSelected()]+" / YOU",124,35,23,0xe6f4ff,false);
        text(g,GameSession.NAMES[session.getOpponent()]+" / CPU",900,35,23,0xffded1,false);
        bar(g,124,49,425,25,f.getPlayer().getHealth(),0xe8564d,false);
        bar(g,731,49,425,25,f.getEnemy().getHealth(),0xe8564d,true);
        bar(g,124,85,300,8,f.getPlayer().getEnergy(),0x55dcc8,false);
        bar(g,856,85,300,8,f.getEnemy().getEnergy(),0x55dcc8,true);
        text(g,"HP "+f.getPlayer().getHealth(),460,98,12,0xd5e4f4,false);
        text(g,"HP "+f.getEnemy().getHealth(),741,98,12,0xd5e4f4,false);
        text(g,"ROUND 01",640,30,18,0xf1dfc2,true);
        text(g,String.format(java.util.Locale.ROOT,"%02d",f.getSeconds()),640,89,62,0xffcf73,true);
        text(g,"POWER "+f.getPlayer().getEnergy()+" / 100",124,114,11,0x8fcabf,false);
        text(g,"POWER "+f.getEnemy().getEnergy()+" / 100",1025,114,11,0x8fcabf,false);
    }
    private void bar(Graphics2D g,int x,int y,int w,int h,int value,int color,boolean reverse) {
        g.setColor(new Color(0x303544));g.fillRect(x-3,y-3,w+6,h+6);
        g.setColor(new Color(0x170e19));g.fillRect(x,y,w,h);
        int fill=w*value/100;
        g.setPaint(new GradientPaint(x,y,new Color(0xffc15a),x,y+h,new Color(color)));
        g.fillRect(reverse?x+w-fill:x,y,fill,h);
        g.setColor(new Color(0xbbd1dc));g.drawRect(x-3,y-3,w+6,h+6);
    }
    private void drawOnStage(Graphics2D g,FightEngine.Fighter fighter,int id) {
        double x=100+(fighter.getX()-65)*1080/590;
        g.setColor(new Color(0,0,0,110));g.fillOval((int)x-70,576,140,20);
        Graphics2D f=(Graphics2D)g.create();
        f.translate(x,584-fighter.getHeight()*1.25);f.scale(1.65,1.65);
        if(fighter.getHealth()==0){f.rotate(-fighter.getFacing()*Math.PI/2);f.translate(-8,0);}
        drawFighter(f,fighter,id,fighter.getFacing(),true); f.dispose();
    }
    private void limb(Graphics2D g,int x,int y,int jx,int jy,int ex,int ey,int width,int color) {
        g.setStroke(new BasicStroke(width+3,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.setColor(new Color(0x111728));
        g.drawLine(x,y,jx,jy);g.drawLine(jx,jy,ex,ey);
        g.setStroke(new BasicStroke(width,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.setColor(new Color(color));
        g.drawLine(x,y,jx,jy);g.drawLine(jx,jy,ex,ey);
    }
    private void drawFighter(Graphics2D source,FightEngine.Fighter f,int id,int facing,boolean combat) {
        Graphics2D g=(Graphics2D)source.create();
        try {
            g.scale(facing,1);
            g.translate(0,Math.sin(animation*6+id)*2);
            FightEngine.Move move=f==null?null:f.getMove();
            double time=f==null?0:f.getAttackTime();
            double reach=move==null?0:Math.sin(Math.min(1,time/.23)*Math.PI/2);
            int arm=move==FightEngine.Move.KICK?0:(int)(reach*66),leg=move==FightEngine.Move.KICK?(int)(reach*78):0;
            int jacket=f!=null&&f.isHit()?0xfff4d9:GameSession.COLORS[id];
            int trousers=id==1?0xdedfe2:id==4?0x982b40:0x273249;
            double walk=combat&&f!=null&&f.getMove()==null&&!f.isGuarding()&&((f==session.getFight().getEnemy())||held==0||held==1||keys.contains((int)'a')||keys.contains((int)'d'))?Math.sin(animation*12)*8:0;
            limb(g,-12,-55,-25+(int)walk,-30,-39+(int)walk,-5,20,trousers);
            limb(g,12,-55,27+leg/2,-31-leg/2,36+leg-(int)walk,-7-leg,21,trousers);
            g.setColor(new Color(0x101723));g.fillRoundRect(-54+(int)walk,-12,35,14,5,5);g.fillRoundRect(25+leg-(int)walk,-13-leg,37,15,5,5);
            g.setColor(new Color(0x101723));g.fillPolygon(new int[]{-26,14,29,17,-24,-33},new int[]{-130,-131,-72,-52,-57,-104},6);
            g.setColor(new Color(jacket));g.fillPolygon(new int[]{-23,12,25,14,-21,-29},new int[]{-127,-128,-73,-56,-60,-103},6);
            g.setColor(new Color(0xf2e7d7));g.fillPolygon(new int[]{-7,5,15,6,-8},new int[]{-127,-128,-67,-59,-61},5);
            g.setColor(new Color(0x192433));g.fillRect(-24,-67,45,8);g.setStroke(new BasicStroke(1));g.setColor(new Color(0xc0b99f));g.drawRect(0,-67,10,8);
            limb(g,-23,-117,-41,-90,-12,-98,15,jacket);
            boolean guard=f!=null&&f.isGuarding();int hx=guard?24:38+arm,hy=guard?-145:-112;
            limb(g,14,-121,32+arm/2,-96-arm/5,hx,hy,16,jacket);
            g.setColor(new Color(0xecc19d));g.fillOval(hx-10,hy-9,21,18);g.fillOval(-23,-107,19,16);
            g.setColor(new Color(0x212638));g.fillRoundRect(hx-15,hy-9,8,19,3,3);
            g.setColor(new Color(0xc49578));g.fillRect(-7,-137,19,18);g.setColor(new Color(0xefc8aa));g.fillOval(-19,-174,39,43);
            g.setColor(new Color(GameSession.HAIR[id]));g.fillPolygon(new int[]{-23,-20,-10,-3,5,14,24,21,12,3,-9,-21},new int[]{-150,-176,-186,-179,-187,-181,-161,-147,-162,-156,-166,-147},12);
            g.setColor(new Color(255,255,255,50));g.drawLine(-15,-174,3,-180);g.drawLine(-11,-172,9,-179);
            g.setStroke(new BasicStroke(1));g.setColor(new Color(0x262039));g.fillRect(9,-151,7,3);g.drawLine(8,-138,15,-139);
            if(id==0){g.setColor(new Color(0xe8d9c3));g.fillRect(-19,-157,37,4);}
            if(id==7){g.setColor(new Color(GameSession.HAIR[id]));g.fillRoundRect(-27,-173,13,83,8,8);}
            if(guard){g.setStroke(new BasicStroke(3));g.setColor(new Color(0x91e8ff));g.drawArc(-2,-179,70,130,-75,150);}
            if(move==FightEngine.Move.SPECIAL){
                g.setColor(new Color(255,176,62,85));g.fillOval(25,-171,155,125);
                g.setColor(new Color(0xffc35e));g.fillOval(60,-142,80,63);g.setColor(new Color(0xfff4c5));g.fillOval(78,-128,44,35);
                for(int i=0;i<6;i++)g.drawLine(-30-i*10,-155+i*16,70,-111);
            }
            if(f!=null&&f.isHit()&&f.getHealth()>0){
                g.setStroke(new BasicStroke(3));g.setColor(new Color(0xffe3a0));
                for(int i=0;i<12;i++){double a=i*Math.PI/6+animation;g.drawLine((int)(Math.cos(a)*14),-100+(int)(Math.sin(a)*14),(int)(Math.cos(a)*49),-100+(int)(Math.sin(a)*49));}
            }
        } finally {g.dispose();}
    }
}
