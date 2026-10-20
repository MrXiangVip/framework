package com.packages.quanhuang;

import com.android.content.Context;
import com.android.graphics.Canvas;
import com.android.os.Handler;
import com.android.util.AttributeSet;
import com.android.view.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

/** Sprite fighters and arena, rendered through the framework's software/X11 surface. */
public class FightView extends View {
    private final FightEngine game = new FightEngine();
    private final FightAudio audio = new FightAudio();
    private static final int FRAME_WIDTH = 128, FRAME_HEIGHT = 192;
    private static final int SHEET_COLUMNS = 6, SHEET_ROWS = 5;
    private final BufferedImage sparkSheet, ironSheet;
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
        if (!game.isPaused()) animation += dt;
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
        sparkSheet = loadSheet(context, "spark_sheet");
        ironSheet = loadSheet(context, "iron_sheet");
        game.setSoundListener(sound -> { if (attached()) audio.play(sound); });
    }
    private static BufferedImage loadSheet(Context context, String name) {
        // Prefer transparent PNGs; the supplied assets currently use JPEG.
        for (String extension : new String[]{".png", ".jpeg"}) {
            String path = "res/drawable/" + name + extension;
            try (InputStream stream = context.getClassLoader().getResourceAsStream(path)) {
                if (stream == null) continue;
                BufferedImage sheet = ImageIO.read(stream);
                if (sheet == null || sheet.getWidth() < SHEET_COLUMNS || sheet.getHeight() < SHEET_ROWS)
                    throw new IOException("Invalid sprite sheet");
                return extension.equals(".jpeg") ? removeSheetBackground(sheet) : sheet;
            } catch (IOException e) {
                throw new IllegalArgumentException("Cannot load sprite sheet: " + path, e);
            }
        }
        throw new IllegalArgumentException("Missing sprite sheet: " + name);
    }
    /**
     * JPEG exports contain a baked checkerboard. Remove only neutral light pixels
     * reachable from each cell's boundary, leaving enclosed hair/shoe whites intact.
     * Run once at load time, never in the render loop. Transparent PNGs bypass this.
     */
    private static BufferedImage removeSheetBackground(BufferedImage source) {
        int width = source.getWidth(), height = source.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = source.getRGB(0, 0, width, height, null, 0, width);
        // Close tiny JPEG gaps in the ink outlines before flooding. Without this,
        // background can leak into white hair and white shoes.
        boolean[] outline = new boolean[pixels.length];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            if (isBackground(pixels[y * width + x])) continue;
            for (int dy = -3; dy <= 3; dy++) for (int dx = -3; dx <= 3; dx++) {
                int nx = x + dx, ny = y + dy;
                if (nx >= 0 && nx < width && ny >= 0 && ny < height)
                    outline[ny * width + nx] = true;
            }
        }
        for (int row = 0; row < SHEET_ROWS; row++) {
            for (int column = 0; column < SHEET_COLUMNS; column++) {
                int left = column * width / SHEET_COLUMNS, right = (column + 1) * width / SHEET_COLUMNS;
                int top = row * height / SHEET_ROWS, bottom = (row + 1) * height / SHEET_ROWS;
                int[] queue = new int[(right - left) * (bottom - top)];
                int tail = 0;
                for (int x = left; x < right; x++) {
                    tail = enqueueBackground(pixels, outline, queue, tail, top * width + x);
                    tail = enqueueBackground(pixels, outline, queue, tail, (bottom - 1) * width + x);
                }
                for (int y = top; y < bottom; y++) {
                    tail = enqueueBackground(pixels, outline, queue, tail, y * width + left);
                    tail = enqueueBackground(pixels, outline, queue, tail, y * width + right - 1);
                }
                for (int head = 0; head < tail; head++) {
                    int index = queue[head], x = index % width, y = index / width;
                    if (x > left) tail = enqueueBackground(pixels, outline, queue, tail, index - 1);
                    if (x + 1 < right) tail = enqueueBackground(pixels, outline, queue, tail, index + 1);
                    if (y > top) tail = enqueueBackground(pixels, outline, queue, tail, index - width);
                    if (y + 1 < bottom) tail = enqueueBackground(pixels, outline, queue, tail, index + width);
                }
            }
        }
        // Remove the temporary outer outline padding without flooding through it.
        int[] flooded = pixels.clone();
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int index = y * width + x;
            if (!outline[index] || !isBackground(flooded[index])) continue;
            boolean exterior = false;
            for (int dy = -3; dy <= 3 && !exterior; dy++) for (int dx = -3; dx <= 3; dx++) {
                int nx = x + dx, ny = y + dy;
                if (nx >= 0 && nx < width && ny >= 0 && ny < height && flooded[ny * width + nx] == 0) {
                    exterior = true; break;
                }
            }
            if (exterior) pixels[index] = 0;
        }
        result.setRGB(0, 0, width, height, pixels, 0, width);
        return result;
    }
    private static boolean isBackground(int color) {
        if ((color >>> 24) == 0) return false;
        int red = (color >>> 16) & 255, green = (color >>> 8) & 255, blue = color & 255;
        int min = Math.min(red, Math.min(green, blue)), max = Math.max(red, Math.max(green, blue));
        return min >= 165 && max - min <= 30;
    }
    private static int enqueueBackground(int[] pixels, boolean[] outline, int[] queue, int tail, int index) {
        if (!outline[index] && isBackground(pixels[index])) {
            pixels[index] = 0;
            queue[tail++] = index;
        }
        return tail;
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
            Graphics2D arena = (Graphics2D) g.create();
            try {
                arena.clipRect(0, 166, 720, 454);
                double remaining = Math.max(game.getPlayer().impactTime, game.getEnemy().impactTime);
                if (remaining > 0) {
                    double elapsed = .24 - remaining;
                    double strength = Math.max(shakeStrength(game.getPlayer()), shakeStrength(game.getEnemy()));
                    arena.translate(Math.sin(elapsed * 145) * strength, Math.sin(elapsed * 193) * strength * .55);
                }
                stage(arena);
                fighter(arena, game.getPlayer(), true); fighter(arena, game.getEnemy(), false);
                impact(arena, game.getPlayer()); impact(arena, game.getEnemy());
            } finally { arena.dispose(); }

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
        BufferedImage sheet = player ? sparkSheet : ironSheet;
        int row = 0, column;
        FightEngine.Move move = f.getMove();
        if (f.isHit() && !f.isGuarding()) {
            row = 4; column = 0;
        } else if (move != null) {
            row = move == FightEngine.Move.PUNCH ? 1 : move == FightEngine.Move.KICK ? 2 : 3;
            // Play each attack once over its windup, active and recovery phases.
            double duration = move.windup + move.active + move.recovery;
            column = Math.min(5, (int) (f.getAttackTime() / duration * SHEET_COLUMNS));
        } else if (f.isGuarding()) {
            row = 4; column = 1 + (int) (animation * 8 % 3);
        } else if (f.getHeight() > 0 || f.velocity > 0) {
            row = 4; column = f.velocity > 0 ? 4 : 5;
        } else {
            column = (int) (animation * 8 % SHEET_COLUMNS);
        }

        // Proportional source bounds also support the supplied larger JPEG sheets.
        int sx1 = column * sheet.getWidth() / SHEET_COLUMNS;
        int sy1 = row * sheet.getHeight() / SHEET_ROWS;
        int sx2 = (column + 1) * sheet.getWidth() / SHEET_COLUMNS;
        int sy2 = (row + 1) * sheet.getHeight() / SHEET_ROWS;
        Graphics2D g = (Graphics2D) source.create();
        try {
            g.translate(f.getX(), 540 - f.getHeight());
            // Kick frames and the fourth punch frame face left in the source.
            // Normalize their native direction before applying combat facing.
            int nativeFacing = row == 2 || (row == 1 && column == 3) ? -1 : 1;
            FightEngine.Fighter opponent = player ? game.getEnemy() : game.getPlayer();
            int facing = move != null ? f.getFacing() : opponent.getX() >= f.getX() ? 1 : -1;
            g.scale(facing * nativeFacing, 1);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(sheet, -FRAME_WIDTH / 2, -FRAME_HEIGHT, FRAME_WIDTH / 2, 0,
                    sx1, sy1, sx2, sy2, null);
        } finally { g.dispose(); }
    }
    private static double shakeStrength(FightEngine.Fighter fighter) {
        if (fighter.impactTime <= 0) return 0;
        double peak = fighter.impactGuarded ? 2 : fighter.impactPower >= 20 ? 9 : 5;
        return peak * fighter.impactTime / .24;
    }
    /** Impact feedback is separate from the character sprites and appears only on contact. */
    private void impact(Graphics2D source, FightEngine.Fighter fighter) {
        if (fighter.impactTime <= 0) return;
        double age = .24 - fighter.impactTime, fade = fighter.impactTime / .24;
        Graphics2D g = (Graphics2D) source.create();
        try {
            g.translate(fighter.impactX, fighter.impactY);
            int red = fighter.impactGuarded ? 100 : 255;
            int green = fighter.impactGuarded ? 220 : 190;
            int blue = fighter.impactGuarded ? 255 : 65;
            // A short white core and colored expanding halo.
            if (age < .09) {
                int radius = (int) (10 + age * 150);
                g.setColor(new Color(red, green, blue, (int) (125 * fade)));
                g.fillOval(-radius * 2, -radius * 2, radius * 4, radius * 4);
                g.setColor(new Color(255, 255, 240, (int) (255 * (1 - age / .09))));
                g.fillOval(-radius, -radius, radius * 2, radius * 2);
            }
            // Deterministic sparks spread away from the contact point and fade.
            g.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int count = fighter.impactGuarded ? 7 : 12;
            for (int i = 0; i < count; i++) {
                double angle = i * Math.PI * 2 / count + .17;
                double distance = 7 + age * (140 + i % 3 * 65);
                double length = 4 + fade * 12;
                double vx = Math.cos(angle), vy = Math.sin(angle);
                double drift = fighter.impactFacing * age * 25;
                g.setColor(new Color(red, green, blue, (int) (255 * fade)));
                g.drawLine((int) (vx * distance + drift), (int) (vy * distance + age * age * 100),
                        (int) (vx * (distance + length) + drift), (int) (vy * (distance + length) + age * age * 100));
            }
        } finally { g.dispose(); }
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
