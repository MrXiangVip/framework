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

/** Arcade home, stage selection and combat, rendered in a shared logical coordinate space. */
public class KofView extends View {
    private final GameSession session = new GameSession();
    private final FightAudio audio = new FightAudio();
    private final Set<Integer> keys = new HashSet<>();
    private final StageArt stages = new StageArt();
    private final Font uiFont;
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
    private static final int[][] BUTTONS = {{32,625,66,56},{110,625,66,56},{188,625,82,56},{282,625,82,56},
        {748,625,96,56},{850,625,96,56},{1156,625,92,56},{1100,138,148,38},{32,138,150,38},{945,138,145,38},
        {952,625,96,56},{1054,625,96,56}};
    private static final String[] LABELS = {"← A", "D →", "跳跃 W", "下蹲 S", "轻拳 J", "轻脚 K", "必杀 U", "PAUSE P", "MENU ESC", "SOUND M", "重拳 L", "重脚 ;"};

    public KofView(Context context, AttributeSet attrs) {
        super(context, attrs);
        uiFont = loadFont();
        session.getFight().setSoundListener(audio::play);
    }
    private Font loadFont() {
        try (InputStream input=getContext().getClassLoader().getResourceAsStream("res/fonts/NotoSansCJKsc-Regular.otf")) {
            if(input==null) throw new IllegalStateException("Missing KOF Chinese font");
            return Font.createFont(Font.TRUETYPE_FONT,input);
        } catch (java.io.IOException | FontFormatException error) { throw new IllegalStateException("Cannot load KOF font",error); }
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
                for(int i=0;i<3;i++) if(inside(x,y,new int[]{440,215+i*95,400,80})) {
                    session.setMode(GameSession.Mode.values()[i]); openRoster(); break;
                }
                for(int i=0;i<StageArt.NAMES.length;i++) if(inside(x,y,new int[]{48+i*199,584,188,90})) selectStage(i);
            } else if(session.getScreen()==GameSession.Screen.ROSTER) {
                if(y>=535 && y<632 && x>=55 && x<1217) { int i=(int)((x-55)/117); if(x<55+i*117+108) choose(i); }
                else if(inside(x,y,new int[]{490,652,300,48})) startBattle();
                else if(inside(x,y,new int[]{55,652,190,48})) returnToRoster();
                else if(inside(x,y,new int[]{1035,652,190,48})) choose(activeCharacter()+1);
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
    private int activeCharacter() { return session.getSelectingPlayer()==0?session.getSelected():session.getOpponent(); }
    private void openRoster() { clearInput(); session.openRoster(); audio.play(FightEngine.Sound.SELECT); }
    private void selectStage(int id) { session.selectStage(id); audio.play(FightEngine.Sound.SELECT); }
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
            if(k==KeyEvent.ARROW_UP||k=='w') session.setMode(GameSession.Mode.values()[Math.floorMod(session.getMode().ordinal()-1,3)]);
            if(k==KeyEvent.ARROW_DOWN||k=='s') session.setMode(GameSession.Mode.values()[(session.getMode().ordinal()+1)%3]);
            if(k=='a'||k==KeyEvent.LEFT) selectStage(session.getStage()-1);
            if(k=='d'||k==KeyEvent.RIGHT) selectStage(session.getStage()+1);
            if(k==KeyEvent.ENTER||k==' ') openRoster();
        } else if(session.getScreen()==GameSession.Screen.ROSTER) {
            if(k=='a'||k==KeyEvent.LEFT) choose(activeCharacter()-1);
            if(k=='d'||k==KeyEvent.RIGHT) choose(activeCharacter()+1);
            if(k>='0'&&k<='9') choose(k=='0'?9:k-'1');
            if(k==KeyEvent.ENTER||k==' ') startBattle();
            if(k==KeyEvent.ESCAPE) returnToRoster();
        } else if(session.getScreen()==GameSession.Screen.RESULT) {
            if(k==KeyEvent.ENTER||k==KeyEvent.ESCAPE||k==' ') returnToRoster();
        } else {
            if(k==KeyEvent.ESCAPE) returnToRoster();
            else if(k=='p') togglePause();
            else if(k=='w'||k==' '||(k==KeyEvent.ARROW_UP&&session.getMode()!=GameSession.Mode.VERSUS)) action(2);
            else if(k=='j') action(4);
            else if(k=='k') action(5);
            else if(k=='l') action(10);
            else if(k==';') action(11);
            else if(k=='u') action(6);
            else if(session.getMode()==GameSession.Mode.TRAINING&&k=='r') { clearInput(); audio.clear(); audio.setPaused(false); session.getFight().restart(); }
            else if(session.getMode()==GameSession.Mode.VERSUS) {
                if(k==KeyEvent.ARROW_UP) session.getFight().enemyJump();
                if(k=='1'||k==0xffb1) session.getFight().enemyAttack(FightEngine.Move.PUNCH);
                if(k=='2'||k==0xffb2) session.getFight().enemyAttack(FightEngine.Move.KICK);
                if(k=='3'||k==0xffb3) session.getFight().enemyAttack(FightEngine.Move.SPECIAL);
            }
            syncControls();
        }
        invalidate(); return true;
    }
    private void action(int index) {
        FightEngine f=session.getFight();
        switch(index) {
            case 2: f.jump(); break;
            case 10: f.attack(FightEngine.Move.PUNCH); break;
            case 11: f.attack(FightEngine.Move.KICK); break;
            case 4: f.attack(FightEngine.Move.LIGHT_PUNCH); break;
            case 5: f.attack(FightEngine.Move.LIGHT_KICK); break;
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
        boolean dual=session.getMode()==GameSession.Mode.VERSUS;
        boolean left=held==0||keys.contains((int)'a')||(!dual&&keys.contains(KeyEvent.LEFT));
        boolean right=held==1||keys.contains((int)'d')||(!dual&&keys.contains(KeyEvent.RIGHT));
        f.setDirection((right?1:0)-(left?1:0)); f.setCrouching(held==3||keys.contains((int)'s'));
        if(dual) { f.setEnemyCrouching(keys.contains(KeyEvent.ARROW_DOWN)); f.setEnemyDirection((keys.contains(KeyEvent.RIGHT)?1:0)-(keys.contains(KeyEvent.LEFT)?1:0)); }
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
            if(session.getScreen()==GameSession.Screen.SELECT) home(g); else if(session.getScreen()==GameSession.Screen.ROSTER) selection(g); else arena(g);
        } finally {g.dispose();}
        canvas.drawImage(frame,0,0,getWidth(),getHeight()); startTimer();
    }
    private void text(Graphics2D g,String s,int x,int y,int size,int color,boolean center) {
        g.setFont(uiFont.deriveFont(size>=24?Font.BOLD:Font.PLAIN,(float)size));
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
        Graphics2D p=(Graphics2D)g.create();
        p.clipRect(x,y,w,h);
        p.setPaint(new GradientPaint(x,y,new Color(GameSession.COLORS[index]).darker(),x+w,y+h,new Color(0x111e30)));p.fillRect(x,y,w,h);
        p.translate(x+w/2,y+h*2.07);p.scale(w/83.0,h/92.0);drawFighter(p,null,index,1,false);p.dispose();
    }
    private void home(Graphics2D g) {
        stages.draw(g,session.getStage(),0,0,1280,720);
        g.setPaint(new GradientPaint(0,0,new Color(4,10,20,95),0,550,new Color(4,10,20,225)));g.fillRect(0,0,1280,720);
        g.setColor(new Color(255,255,255,14));g.setStroke(new BasicStroke(1));
        for(int x=-500;x<1500;x+=100) g.drawLine(x,0,x+450,550);
        text(g,"KOF  /  THE KING OF FIGHTERS",48,44,16,0xdfe8ef,false);
        text(g,"ARCADE COLLECTION     /     LOCAL PLAY",1090,44,12,0x9cafbd,true);
        g.setColor(new Color(0xf8c573));g.fillRect(609,80,62,3);
        text(g,"拳 皇",640,152,64,0xffe2a4,true);
        text(g,"THE KING OF FIGHTERS",640,184,16,0xa6b9c8,true);
        Graphics2D left=(Graphics2D)g.create();left.translate(232,512);left.scale(1.8,1.8);drawFighter(left,null,0,1,false);left.dispose();
        Graphics2D right=(Graphics2D)g.create();right.translate(1048,512);right.scale(1.8,1.8);drawFighter(right,null,4,-1,false);right.dispose();
        text(g,"KYO KUSANAGI",232,530,13,0x80d9e2,true);
        text(g,"IORI YAGAMI",1048,530,13,0xe48f9f,true);
        for(int i=0;i<3;i++) {
            int x=440,y=215+i*95;boolean active=session.getMode().ordinal()==i;
            g.setPaint(new GradientPaint(x,y,new Color(active?0x4b3d2c:0x132334),x+400,y+80,new Color(0x101a2a)));
            g.fillRoundRect(x,y,400,80,12,12);g.setStroke(new BasicStroke(active?2:1));g.setColor(new Color(active?0xf5c77e:0x3a4a5b));g.drawRoundRect(x,y,400,80,12,12);
            if(active){g.setColor(new Color(0xf5c77e));g.fillRoundRect(x,y+20,4,40,4,4);}
            text(g,"0"+(i+1),x+23,y+31,13,active?0xf5c77e:0x6e859a,false);
            text(g,GameSession.Mode.values()[i].title,x+77,y+34,25,active?0xffe2ac:0xe0e8ef,false);
            text(g,GameSession.Mode.values()[i].description,x+77,y+61,12,0x91a6b9,false);
            text(g,active?"→":"+",x+362,y+48,24,active?0xf5c77e:0x647b90,true);
        }
        text(g,"↑ ↓ 选择模式     ENTER 进入选角     ← → 切换场景",640,525,12,0xa1b4c4,true);
        g.setColor(new Color(6,13,23,235));g.fillRect(0,550,1280,170);
        text(g,"选择对战背景",48,575,16,0xe6d3af,false);
        text(g,String.format(java.util.Locale.ROOT,"%02d / %02d    %s",session.getStage()+1,StageArt.NAMES.length,StageArt.CODES[session.getStage()]),810,575,12,0x8fa8bc,false);
        for(int i=0;i<StageArt.NAMES.length;i++) {
            int x=48+i*199;boolean active=i==session.getStage();
            stages.draw(g,i,x,584,188,90);
            g.setColor(new Color(5,10,20,185));g.fillRect(x,645,188,29);
            text(g,StageArt.NAMES[i],x+12,664,13,active?0xffdf9d:0xb8c9d6,false);
            if(active){g.setColor(new Color(0xf6c578));g.setStroke(new BasicStroke(2));g.drawRect(x-2,582,192,94);text(g,"✓",x+169,664,15,0xffdf9d,true);}
        }
        text(g,"原创绘制场景  /  点击缩略图预览",48,700,11,0x6f879a,false);
        text(g,"M 音效开关  ·  "+(audio.isMuted()?"已静音":"音效开启"),1050,700,11,0x6f879a,false);
    }
    private void selection(Graphics2D g) {
        stages.draw(g,session.getStage(),0,0,1280,720);
        g.setPaint(new GradientPaint(0,105,new Color(4,10,20,215),0,520,new Color(4,10,20,245)));
        g.fillRect(0,108,1280,612);
        text(g,"KOF / CHARACTER SELECT",48,44,16,0xbacbd8,false);
        text(g,"ESC 返回主菜单",1110,44,13,0x9cafbd,true);
        text(g,session.getMode().title+" / "+(session.getSelectingPlayer()==0?"1P 选择角色":"2P 选择角色"),640,145,24,0xffd68b,true);
        
        
        int id=session.getSelected(), cpu=session.getMode()==GameSession.Mode.VERSUS?session.getOpponent():(id+4)%10;
        preview(g,id,true);
        if(session.getMode()==GameSession.Mode.TRAINING) {
            panel(g,650,205,520,270,0x446678);
            text(g,"自由练习",690,256,30,0xffd68b,false);
            text(g,"场上只有你选择的人物，自由熟悉技能。",690,298,18,0xb8cadb,false);
            text(g,"A / D 移动  W 跳跃  S 下蹲",690,340,17,0xb8cadb,false);
            text(g,"J 轻拳  K 轻脚  L 重拳  ; 重脚",690,376,17,0xb8cadb,false);
            text(g,"不计时 · 能量自动补满 · ESC 返回菜单",690,425,16,0x8fcabf,false);
        } else {
            preview(g,cpu,false);
            text(g,"VS",640,320,96,0xf8ba69,true);
        }
        if(session.getMode()!=GameSession.Mode.TRAINING) {
            text(g,StageArt.NAMES[session.getStage()],640,370,16,0xb8cadb,true);
            text(g,"60 SECONDS / ONE BOUT",640,395,12,0x849bb5,true);
        }
        text(g,session.getSelectingPlayer()==0?"1P  ROSTER":"2P  ROSTER",55,517,17,0xffda8e,false);
        text(g,"A / D  OR  ARROWS TO SELECT     1 - 0  QUICK SELECT",775,517,12,0xb3c5d5,false);
        for(int i=0;i<10;i++) {
            int x=55+i*117;
            panel(g,x,535,108,97,i==activeCharacter()?0xffce67:0x34475d);
            portrait(g,i,x+4,539,100,68);
            if(i==activeCharacter()) {
                g.setColor(new Color(255,203,82,55));g.fillRect(x+4,539,100,68);
                g.setStroke(new BasicStroke(3));g.setColor(new Color(0xffd369));g.drawRoundRect(x-2,533,112,101,8,8);
            }
            text(g,GameSession.NAMES[i],x+54,624,11,i==activeCharacter()?0xffdc8e:0xa8bacd,true);
        }
        button(g,55,652,190,48,"返回主菜单 / ESC",0x94c8e2,false);
        button(g,490,652,300,48,session.getMode()==GameSession.Mode.VERSUS&&session.getSelectingPlayer()==0?"确认 1P / 选择 2P":session.getMode()==GameSession.Mode.TRAINING?"开始练习 / ENTER":"开始对战 / ENTER",0xffcf73,true);
        button(g,1035,652,190,48,"NEXT  >",0x94c8e2,false);
    }
    private void preview(Graphics2D g,int id,boolean player) {
        int x=player?95:1035;
        portrait(g,id,x,185,150,143);
        text(g,player?"1P / YOU":session.getMode()==GameSession.Mode.VERSUS?"2P / PLAYER":"2P / CPU",x+75,172,16,player?0x7ae4f4:0xff9283,true);
        text(g,GameSession.NAMES[id],x+75,367,29,0xffe1a6,true);
        text(g,"SPD "+(155+id%4*12)+"  /  ATK +"+(id%3),x+75,392,12,0xb3c5d5,true);
        Graphics2D f=(Graphics2D)g.create();
        f.translate(player?370:910,494);f.scale(1.65,1.65);
        drawFighter(f,null,id,player?1:-1,false);f.dispose();
        text(g,player?"READY TO FIGHT":session.getMode()==GameSession.Mode.VERSUS?"PLAYER TWO":"AUTO SELECT",x+75,435,11,0x819ab4,true);
    }
    private void arena(Graphics2D g) {
        FightEngine f=session.getFight();
        Graphics2D scenery=(Graphics2D)g.create();scenery.clipRect(0,112,1280,498);
        stages.draw(scenery,session.getStage(),0,-112,1280,856);scenery.dispose();
        drawOnStage(g,f.getPlayer(),session.getSelected());
        if(!f.isTraining()) drawOnStage(g,f.getEnemy(),session.getOpponent());
        hud(g);
        g.setColor(new Color(6,12,24,240));g.fillRect(0,608,1280,112);
        for(int i=0;i<BUTTONS.length;i++) {
            int[] b=BUTTONS[i];
            String label=i==7&&f.isPaused()?"RESUME  P":i==9?(audio.isMuted()?"SFX OFF  M":audio.isAvailable()?"SFX ON  M":"NO AUDIO"):LABELS[i];
            button(g,b[0],b[1],b[2],b[3],label,i<4?0x86cddd:i==6?0xffcf73:0xe9a494,held==i);
        }
        text(g,StageArt.NAMES[session.getStage()],550,641,14,0xffd28a,true);
        text(g,session.getMode().title,550,668,13,0x8ca4be,true);
        text(g,session.getMode()==GameSession.Mode.VERSUS?"1P: A D 移动 W 跳 S 蹲 J 轻拳 K 轻脚 L 重拳 ; 重脚  |  2P: ← → 移动 ↑ 跳 ↓ 蹲 1 拳 2 脚 3 必杀 | 按后站防 后+下蹲防":"A 左  D 右  W 跳跃  S 下蹲  J 轻拳  K 轻脚  L 重拳  ; 重脚  U 必杀  后方向站防 后+S蹲防  P 暂停  ESC 菜单",640,705,12,0x819ab4,true);
        if(session.getScreen()==GameSession.Screen.RESULT) {
            shade(g);panel(g,390,238,500,232,0xf3c575);
            text(g,f.getMessage(),640,306,57,0xffd583,true);
            String winner=f.getWinner()==0?"DOUBLE K.O. / EVEN SCORE":GameSession.NAMES[f.getWinner()==1?session.getSelected():session.getOpponent()]+" TAKES THE BOUT";
            text(g,winner,640,343,17,0xf0e4cb,true);
            text(g,"返回主菜单 / "+(int)Math.ceil(session.getResultTime())+"...",640,378,15,0x9bb5ce,true);
            button(g,490,405,300,48,"返回主菜单 / ENTER",0xffd184,false);
        } else if(f.isPaused()) {
            shade(g);panel(g,390,250,500,155,0x86cddd);
            text(g,"PAUSED",640,315,52,0xffd583,true);text(g,"P 继续  /  ESC 返回主菜单",640,367,16,0xc2d7e8,true);
        } else if(!f.getMessage().isEmpty()) {
            text(g,f.getMessage(),640,270,67,0xffd583,true);
        }
    }
    private void shade(Graphics2D g) {g.setColor(new Color(3,8,18,175));g.fillRect(0,112,1280,498);}
    private void hud(Graphics2D g) {
        FightEngine f=session.getFight();
        g.setPaint(new GradientPaint(0,0,new Color(0x071124),0,128,new Color(0x152139)));g.fillRect(0,0,1280,128);
        if(f.isTraining()) {
            portrait(g,session.getSelected(),25,18,83,80);
            text(g,GameSession.NAMES[session.getSelected()]+" / 1P",124,35,23,0xe6f4ff,false);
            bar(g,124,55,300,12,f.getPlayer().getEnergy(),0x55dcc8,false);
            text(g,"POWER "+f.getPlayer().getEnergy()+" / 100",124,96,13,0x8fcabf,false);
            text(g,"单人训练",640,53,30,0xffd68b,true);
            text(g,"自由练习 · 不计时",640,88,16,0x9bb5ce,true);
            String skill=f.getPlayer().getMove()!=null?f.getPlayer().getMove().label():f.getPlayer().isCrouching()?"下蹲":f.getPlayer().getHeight()>0?"跳跃":"待机";
            text(g,"当前动作："+skill,950,57,18,0xe6f4ff,false);
            text(g,"R 重置练习 / ESC 返回菜单",950,90,13,0x9bb5ce,false);
            return;
        }
        portrait(g,session.getSelected(),25,18,83,80);portrait(g,session.getOpponent(),1172,18,83,80);
        text(g,GameSession.NAMES[session.getSelected()]+" / YOU",124,35,23,0xe6f4ff,false);
        text(g,GameSession.NAMES[session.getOpponent()]+(session.getMode()==GameSession.Mode.VERSUS?" / 2P":" / CPU"),900,35,23,0xffded1,false);
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
        if(fighter.isGuarding()) text(g,fighter.isCrouching()?"蹲防":"站防",(int)x,600,14,0x91e8ff,true);
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
            if(f!=null&&f.isCrouching()) g.scale(1,.67);
            FightEngine.Move move=f==null?null:f.getMove();
            double time=f==null?0:f.getAttackTime();
            double reach=move==null?0:Math.sin(Math.min(1,time/.23)*Math.PI/2);
            int extension=move!=null&&move.isLight()?44:78;
            int arm=move!=null&&move.isKick()?0:(int)(reach*(extension-12)),leg=move!=null&&move.isKick()?(int)(reach*extension):0;
            int jacket=f!=null&&f.isHit()?0xfff4d9:GameSession.COLORS[id];
            int trousers=id==1?0xdedfe2:id==4?0x982b40:0x273249;
            double walk=combat&&f!=null&&f.getMove()==null&&!f.isGuarding()&&!f.isCrouching()&&((f==session.getFight().getEnemy())||held==0||held==1||keys.contains((int)'a')||keys.contains((int)'d'))?Math.sin(animation*12)*8:0;
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
