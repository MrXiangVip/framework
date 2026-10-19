package com.packages.quanhuang;

import com.android.content.Context;
import com.android.graphics.Canvas;
import com.android.os.Handler;
import com.android.util.AttributeSet;
import com.android.view.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

/** Original vector arena and fighters, rendered through the framework's software/X11 surface. */
public class FightView extends View {
    private final FightEngine game = new FightEngine();
    private final FightAudio audio = new FightAudio();
    private BufferedImage frame;
    private Timer timer;
    private long lastTick;
    private double animation;
    private final java.util.Set<Integer> keys = new java.util.HashSet<>();
    private String held = "";
    private final AtomicBoolean pending = new AtomicBoolean();
    private final Handler ui = new Handler(message -> {
        pending.set(false);
        if (!attached()) { stopTimer(); return true; }
        long now = System.nanoTime();
        double dt = Math.min(.1, (now - lastTick) / 1e9); lastTick = now;
        animation += dt;
        syncControls();
        game.update(dt); invalidate();
        return true;
    });

    private static final String[] IDS = {"left", "right", "jump", "guard", "punch", "kick", "special", "pause", "restart", "sound"};
    private static final String[] LABELS = {"<", ">", "JUMP", "GUARD", "PUNCH", "KICK", "BURST", "II", "R", "SFX ON"};
    private static final String[] KEYS = {"A", "D", "W / SPACE", "L", "J", "K", "U  /  40 MP", "P", "R", "M"};
    private static final int[][] BOXES = {{32,718,84,78},{132,718,84,78},{82,620,84,78},{240,718,98,78},
            {402,650,94,80},{510,718,94,78},{592,610,100,86},{558,26,60,44},{632,26,60,44},{450,26,94,44}};

    public FightView(Context context, AttributeSet attrs) {
        super(context, attrs);
        game.setSoundListener(sound -> { if (attached()) audio.play(sound); });
    }
    public boolean isSoundMuted() { return audio.isMuted(); }
    public void setSoundMuted(boolean muted) {
        audio.setMuted(muted);
        if (!muted && attached()) audio.play(FightEngine.Sound.SELECT);
        invalidate();
    }
    public FightEngine getGame() { return game; }
    private boolean attached() {
        View root = this;
        while (root.getParent() instanceof View) root = (View) root.getParent();
        return root.getParent() != null;
    }
    private void startTimer() {
        if (timer != null || !attached()) return;
        if (game.getState() == FightEngine.State.READY) audio.play(FightEngine.Sound.ROUND);
        lastTick = System.nanoTime();
        timer = new Timer("quanhuang-frames", true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() {
                if (pending.compareAndSet(false, true)) ui.sendMessage(ui.obtainMessage(0, null));
            }
        }, 33, 33);
    }
    private void stopTimer() { if (timer != null) { timer.cancel(); timer = null; } game.releaseControls(); audio.close(); }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(resolveSize(600, widthSpec), resolveSize(800, heightSpec));
    }
    private double scale() { return Math.max(.001, Math.min(getWidth() / 720.0, getHeight() / 900.0)); }
    private double offsetX() { return (getWidth() - 720 * scale()) / 2; }
    private double offsetY() { return (getHeight() - 900 * scale()) / 2; }
    private int buttonAt(int x, int y) {
        double vx = (x - offsetX()) / scale(), vy = (y - offsetY()) / scale();
        for (int i = 0; i < BOXES.length; i++) {
            int[] box = BOXES[i];
            if (vx >= box[0] && vx < box[0] + box[2] && vy >= box[1] && vy < box[1] + box[3]) return i;
        }
        return -1;
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            held = ""; keys.clear(); game.releaseControls();
            if (!game.isPaused()) game.togglePause();
            audio.setPaused(true);
            invalidate(); return true;
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            int index = buttonAt(event.getX(), event.getY());
            held = index < 0 ? "" : IDS[index];
            action(held); syncControls(); invalidate(); return true;
        }
        if (event.getAction() == MotionEvent.ACTION_UP || (event.getAction() == MotionEvent.ACTION_MOVE
                && (buttonAt(event.getX(), event.getY()) < 0 || !IDS[buttonAt(event.getX(), event.getY())].equals(held)))) {
            held = ""; syncControls(); invalidate(); return true;
        }
        return true;
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        // X11 sends both keysyms and committed text. Consume text without executing the action twice.
        int key = Character.toLowerCase(event.getValue());
        if (event.getType() == KeyEvent.TEXT) return true;
        if (event.getType() == KeyEvent.UP) { keys.remove(key); syncControls(); invalidate(); return true; }
        if (event.getType() != KeyEvent.DOWN || !keys.add(key)) return true;
        String command = key == 'a' || key == KeyEvent.LEFT ? "left" : key == 'd' || key == KeyEvent.RIGHT ? "right"
                : key == 'w' || key == ' ' || key == 0xff52 ? "jump" : key == 'j' ? "punch" : key == 'k' ? "kick"
                : key == 'u' ? "special" : key == 'l' ? "guard" : key == 'p' || key == KeyEvent.ESCAPE ? "pause"
                : key == 'm' ? "sound" : key == 'r' || key == KeyEvent.ENTER && game.getState() == FightEngine.State.MATCH_OVER ? "restart" : "";
        action(command); syncControls(); invalidate(); return !command.isEmpty();
    }
    private void syncControls() {
        if (game.isPaused()) { game.releaseControls(); return; }
        boolean left = held.equals("left") || keys.contains((int) 'a') || keys.contains(KeyEvent.LEFT);
        boolean right = held.equals("right") || keys.contains((int) 'd') || keys.contains(KeyEvent.RIGHT);
        game.setDirection((right ? 1 : 0) - (left ? 1 : 0));
        game.setBlocking(held.equals("guard") || keys.contains((int) 'l'));
    }
    private void action(String command) {
        switch (command) {
            case "left": break;
            case "right": break;
            case "jump": game.jump(); break;
            case "guard": break;
            case "punch": game.attack(FightEngine.Move.PUNCH); break;
            case "kick": game.attack(FightEngine.Move.KICK); break;
            case "special": game.attack(FightEngine.Move.SPECIAL); break;
            case "pause": game.togglePause(); audio.setPaused(game.isPaused()); held = ""; break;
            case "restart": audio.clear(); audio.setPaused(false); game.restart(); held = ""; break;
            case "sound": setSoundMuted(!audio.isMuted()); held = ""; break;
            default: break;
        }
    }
    @Override protected void onDraw(Canvas canvas) {
        int width = getWidth(), height = getHeight();
        if (width <= 0 || height <= 0) return;
        if (frame == null || frame.getWidth() != width || frame.getHeight() != height)
            frame = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = frame.createGraphics();
        try {
            g.setColor(new Color(0x080f20)); g.fillRect(0, 0, width, height);
            g.translate(offsetX(), offsetY()); g.scale(scale(), scale());
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            stage(g);
            fighter(g, game.getPlayer(), true); fighter(g, game.getEnemy(), false);
            hud(g);
            controls(g);
            if (game.isPaused()) overlay(g, "PAUSED", "P or II to resume");
            else if (!game.getMessage().isEmpty()) overlay(g, game.getMessage(), game.getState() == FightEngine.State.MATCH_OVER
                    ? "Press R for a rematch" : "SPARK  /  IRON");
        } finally { g.dispose(); }
        canvas.drawImage(frame, 0, 0, width, height);
        startTimer();
    }
    private void stage(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 130, new Color(0x152344), 0, 540, new Color(0x75445e)));
        g.fillRect(0, 0, 720, 620);
        g.setColor(new Color(0xffd6b2)); g.fillOval(516, 180, 84, 84);
        g.setColor(new Color(0xf6a989));
        for (int i = 0; i < 4; i++) g.fillRect(510, 205 + i * 15, 100, 5);
        for (int i = 0; i < 18; i++) {
            int x = i * 45 - 12, h = 50 + (i * 47 % 137);
            g.setColor(new Color(i % 2 == 0 ? 0x17263d : 0x1b2e47)); g.fillRect(x, 427 - h, 37, h + 50);
            g.setColor(new Color(0xc4977a));
            for (int row = 0; row < h / 20; row++) for (int col = 0; col < 3; col++)
                if ((row + col + i) % 3 != 0) g.fillRect(x + 5 + col * 10, 440 - h + row * 20, 3, 6);
        }
        g.setColor(new Color(0x283b4f)); g.fillRect(0, 448, 720, 13);
        for (int i = 0; i < 10; i++) g.fillRect(i * 85, 450, 8, 95);
        g.setColor(new Color(0x72d9ec)); g.fillRect(0, 448, 720, 3);
        g.setPaint(new GradientPaint(0, 540, new Color(0x40546a), 0, 620, new Color(0x182a41)));
        g.fillRect(0, 540, 720, 80);
        g.setStroke(new BasicStroke(1)); g.setColor(new Color(0x657b91));
        for (int x = -720; x < 1440; x += 100) g.drawLine(360 + (x - 360) / 3, 540, x, 620);
        for (int y = 548; y < 620; y += (y - 530) / 2) g.drawLine(0, y, 720, y);
        g.setColor(new Color(0x101b2d)); g.fillRect(0, 585, 720, 35);
        text(g, "NIGHTFALL ROOFTOP  //  FREE PLAY", 360, 608, 12, 0x8ba9c6, true);
    }
    private void hud(Graphics2D g) {
        g.setColor(new Color(0x0b1425)); g.fillRect(0, 0, 720, 166);
        text(g, "KING OF FIGHTERS", 24, 39, 26, 0xffc766, false);
        text(g, "ARCADE DUEL  /  ORIGINAL FIGHTERS", 25, 60, 11, 0x8ba9c6, false);
        text(g, "SPARK  /  YOU", 27, 93, 15, 0x72e5ef, false);
        text(g, "IRON  /  CPU", 560, 93, 15, 0xffa391, false);
        bar(g, 26, 104, 272, 22, game.getPlayer().getHealth(), 0x52d8c9, false);
        bar(g, 422, 104, 272, 22, game.getEnemy().getHealth(), 0xf38578, true);
        bar(g, 26, 134, 190, 7, game.getPlayer().getEnergy(), 0x69a9ff, false);
        bar(g, 504, 134, 190, 7, game.getEnemy().getEnergy(), 0xc291ff, true);
        text(g, "MP " + game.getPlayer().getEnergy(), 226, 143, 11, 0x94bbef, false);
        g.setColor(new Color(0x24344c)); g.fillRoundRect(316, 82, 88, 65, 16, 16);
        text(g, String.format(java.util.Locale.ROOT, "%02d", game.getSeconds()), 360, 123, 34, 0xffdf9c, true);
        for (int i = 0; i < 2; i++) {
            g.setColor(new Color(game.getPlayer().getWins() > i ? 0xffc766 : 0x344459)); g.fillOval(278 - i * 18, 81, 10, 10);
            g.setColor(new Color(game.getEnemy().getWins() > i ? 0xffc766 : 0x344459)); g.fillOval(430 + i * 18, 81, 10, 10);
        }
        text(g, "ROUND " + game.getRound() + "   /   FIRST TO 2", 360, 162, 10, 0x9cacc4, true);
    }
    private void bar(Graphics2D g, int x, int y, int width, int height, int value, int color, boolean reverse) {
        g.setColor(new Color(0x26374b)); g.fillRoundRect(x, y, width, height, 5, 5);
        int fill = width * value / 100;
        g.setColor(new Color(color)); g.fillRoundRect(reverse ? x + width - fill : x, y, fill, height, 5, 5);
    }
    private void fighter(Graphics2D source, FightEngine.Fighter f, boolean player) {
        int x = (int) f.getX(), floor = 540, y = floor - (int) f.getHeight();
        source.setColor(new Color(0, 0, 0, 85)); source.fillOval(x - 45, floor - 10, 90, 17);
        Graphics2D g = (Graphics2D) source.create();
        try {
            g.translate(x, y); g.scale(f.getFacing(), 1);
            double bounce = f.getHeight() > 0 ? -5 : Math.sin(animation * 6 + (player ? 0 : 1)) * 2;
            g.translate(0, bounce);
            boolean active = f.getMove() != null;
            double reach = active ? Math.sin(Math.min(1, f.getAttackTime() / .23) * Math.PI / 2) : 0;
            int arm = f.getMove() == FightEngine.Move.KICK ? 0 : (int) (reach * 62);
            int leg = f.getMove() == FightEngine.Move.KICK ? (int) (reach * 80) : 0;
            int jacket = f.isHit() ? 0xffffff : player ? 0x2ed9d0 : 0xe96a63;
            // Boots, bent legs and fitted jacket form a readable fighting silhouette.
            limb(g, -13, -54, -23, -24, -29, -5, 18, 0x182032);
            limb(g, 10, -55, 25 + leg / 2, -29 - leg / 2, 32 + leg, -6 - leg, 19, 0x243249);
            g.setColor(new Color(0x0a101c)); g.fillRoundRect(-41, -12, 32, 14, 5, 5);
            g.fillRoundRect(23 + leg, -12 - leg, 33, 14, 5, 5);
            g.setColor(new Color(jacket)); g.fillPolygon(new int[]{-24,13,27,14,-22,-30}, new int[]{-126,-127,-71,-53,-57,-102}, 6);
            g.setColor(new Color(0xf1e3cf)); g.fillPolygon(new int[]{-8,5,15,5,-9}, new int[]{-124,-126,-65,-57,-59}, 5);
            g.setColor(new Color(0x223047)); g.fillRect(-24, -65, 43, 8);
            limb(g, -24, -117, -39, -92, -9, -97, 14, jacket);
            int handX = f.isGuarding() ? 27 : 34 + arm, handY = f.isGuarding() ? -140 : -111;
            limb(g, 13, -120, 29 + arm / 2, -94 - arm / 5, handX, handY, 15, jacket);
            g.setColor(new Color(0xf3c9a3)); g.fillOval(handX - 9, handY - 8, 19, 17);
            g.fillOval(-17, -164, 38, 42);
            g.setColor(new Color(player ? 0x21283d : 0xd4dbe3));
            g.fillPolygon(new int[]{-21,-19,-9,-3,4,14,26,20,7,-6}, new int[]{-145,-168,-180,-170,-181,-171,-155,-148,-162,-149}, 10);
            g.setColor(new Color(0x101925)); g.fillRect(13, -145, 6, 3);
            g.setColor(new Color(player ? 0xf3b948 : 0x87deef)); g.fillRect(-17, -151, 36, 6);
            g.fillPolygon(new int[]{-17,-52,-28}, new int[]{-148,-133,-145}, 3);
            if (f.isGuarding()) {
                g.setColor(new Color(0x6bdcff)); g.setStroke(new BasicStroke(4));
                g.drawArc(-5, -165, 65, 115, -75, 150);
            }
            if (f.getMove() == FightEngine.Move.SPECIAL) {
                g.setColor(new Color(95, 214, 255, 120)); g.fillOval(25, -149, 110, 90);
                g.setColor(new Color(0xc1f8ff)); g.fillOval(52, -131, 54, 54);
                for (int i = 0; i < 5; i++) { g.setColor(new Color(0x73dfff)); g.drawLine(-5 - i * 8, -139 + i * 13, 53, -105); }
            }
            if (f.isHit()) {
                g.setColor(new Color(0xffe7a3)); g.setStroke(new BasicStroke(3));
                for (int i = 0; i < 9; i++) {
                    double a = i * Math.PI * 2 / 9 + animation * 2;
                    g.drawLine((int) (Math.cos(a) * 12), -102 + (int) (Math.sin(a) * 12),
                            (int) (Math.cos(a) * 39), -102 + (int) (Math.sin(a) * 39));
                }
            }
        } finally { g.dispose(); }
    }
    private void limb(Graphics2D g, int x, int y, int jointX, int jointY, int endX, int endY, int width, int color) {
        g.setColor(new Color(color)); g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(x, y, jointX, jointY); g.drawLine(jointX, jointY, endX, endY);
    }
    private void controls(Graphics2D g) {
        g.setColor(new Color(0x0b1425)); g.fillRect(0, 620, 720, 280);
        g.setColor(new Color(0x263e59)); g.fillRect(0, 620, 720, 2);
        text(g, "MOVE + JUMP", 170, 856, 11, 0x8ea6c2, true);
        text(g, "STRIKE + GUARD", 536, 856, 11, 0x8ea6c2, true);
        text(g, "A D / ARROWS  MOVE     W  JUMP     J K  ATTACK     L  GUARD     U  BURST", 360, 880, 10, 0x7790af, true);
        for (int i = 0; i < BOXES.length; i++) {
            int[] b = BOXES[i];
            boolean pressed = IDS[i].equals(held);
            int accent = i < 4 ? 0x65d8d4 : i == 6 ? 0xffcb79 : 0xf19b8b;
            g.setColor(new Color(pressed ? 0x3a5370 : 0x182941));
            g.fillRoundRect(b[0], b[1], b[2], b[3], 18, 18);
            g.setColor(new Color(accent)); g.setStroke(new BasicStroke(pressed ? 3 : 1));
            g.drawRoundRect(b[0], b[1], b[2], b[3], 18, 18);
            text(g, i == 9 ? (audio.isMuted() ? "SFX OFF" : audio.isAvailable() ? "SFX ON" : "NO AUDIO") : i == 7 && game.isPaused() ? ">" : LABELS[i], b[0] + b[2] / 2, b[1] + b[3] / 2 + 1, i < 2 ? 28 : 15, accent, true);
            if (i < 7) text(g, KEYS[i], b[0] + b[2] / 2, b[1] + b[3] - 12, 10, 0x9db2cc, true);
        }
    }
    private void overlay(Graphics2D g, String title, String subtitle) {
        g.setColor(new Color(7, 14, 29, 190)); g.fillRoundRect(130, 238, 460, 126, 18, 18);
        text(g, title, 360, 298, 44, 0xffdc98, true);
        text(g, subtitle, 360, 335, 15, 0xd2e2f6, true);
    }
    private void text(Graphics2D g, String text, int x, int y, int size, int color, boolean centered) {
        g.setFont(new Font(Font.SANS_SERIF, size > 20 ? Font.BOLD : Font.PLAIN, size));
        g.setColor(new Color(color));
        g.drawString(text, centered ? x - g.getFontMetrics().stringWidth(text) / 2 : x, y);
    }
}
