package com.packages.clock;

import com.android.content.Context;
import com.android.graphics.Canvas;
import com.android.os.Handler;
import com.android.util.AttributeSet;
import com.android.view.View;
import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

/** Local wall clock; timer only posts invalidation, all drawing stays on the UI thread. */
public class ClockView extends View {
    private Clock clock = Clock.systemDefaultZone();
    private BufferedImage frame;
    private Timer timer;
    private final AtomicBoolean tickPending = new AtomicBoolean();
    private final Handler tickHandler = new Handler(message -> {
        tickPending.set(false);
        if (attached()) invalidate();
        else if (timer != null) { timer.cancel(); timer = null; }
        return true;
    });

    public ClockView(Context context, AttributeSet attrs) { super(context, attrs); }
    public ZonedDateTime getCurrentTime() { return ZonedDateTime.now(clock); }
    public void setClock(Clock value) { clock = Objects.requireNonNull(value); invalidate(); }
    private boolean attached() {
        View root = this;
        while (root.getParent() instanceof View) root = (View) root.getParent();
        return root.getParent() != null;
    }
    private void startTicker() {
        if (timer != null || !attached()) return;
        timer = new Timer("clock-ticker", true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() {
                // At most one queued tick if the UI thread is busy.
                if (tickPending.compareAndSet(false, true)) tickHandler.sendMessage(tickHandler.obtainMessage(0, null));
            }
        }, 1000 - System.currentTimeMillis() % 1000, 1000);
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(resolveSize(600, widthSpec), resolveSize(800, heightSpec));
    }
    @Override protected void onDraw(Canvas canvas) {
        int width = getWidth(), height = getHeight();
        if (width <= 0 || height <= 0) return;
        if (frame == null || frame.getWidth() != width || frame.getHeight() != height)
            frame = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = frame.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(0xf5f7fc)); g.fillRect(0, 0, width, height);
            ZonedDateTime time = getCurrentTime();
            int cx = width / 2, cy = (int) (height * .40);
            int radius = Math.max(1, (int) Math.min(width * .36, height * .24));
            centered(g, "CLOCK", cx, Math.max(24, height / 14), 16, 0x667085);
            g.setColor(new Color(0xdfe6f2)); g.fillOval(cx - radius - 5, cy - radius - 1, radius * 2 + 10, radius * 2 + 10);
            g.setColor(Color.WHITE); g.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
            for (int i = 0; i < 60; i++) {
                double angle = i * Math.PI / 30 - Math.PI / 2;
                double inner = radius * (i % 5 == 0 ? .84 : .91);
                g.setStroke(new BasicStroke(i % 5 == 0 ? 3 : 1));
                g.setColor(new Color(i % 5 == 0 ? 0x344054 : 0xc2cad8));
                g.draw(new Line2D.Double(cx + Math.cos(angle) * inner, cy + Math.sin(angle) * inner,
                        cx + Math.cos(angle) * radius * .96, cy + Math.sin(angle) * radius * .96));
            }
            for (int i = 1; i <= 12; i++) {
                double angle = i * Math.PI / 6 - Math.PI / 2;
                int x = cx + (int) (Math.cos(angle) * radius * .70);
                int y = cy + (int) (Math.sin(angle) * radius * .70);
                int size = Math.max(10, radius / 10);
                centered(g, String.valueOf(i), x, y + size / 3, size, 0x667085);
            }
            double seconds = time.getSecond();
            double minutes = time.getMinute() + seconds / 60;
            double hours = time.getHour() % 12 + minutes / 60;
            hand(g, cx, cy, hours * Math.PI / 6, radius * .48, Math.max(3, radius / 30), 0x263653);
            hand(g, cx, cy, minutes * Math.PI / 30, radius * .68, Math.max(2, radius / 45), 0x5179db);
            hand(g, cx, cy, seconds * Math.PI / 30, radius * .79, 2, 0xe75865);
            g.setColor(new Color(0xe75865)); g.fillOval(cx - 5, cy - 5, 10, 10);
            int digitalSize = Math.max(14, Math.min(52, width / 9));
            int baseline = cy + radius + digitalSize + 18;
            centered(g, time.format(DateTimeFormatter.ofPattern("HH:mm:ss")), cx, baseline, digitalSize, 0x182230);
            int dateSize = Math.max(10, Math.min(18, width / 22));
            centered(g, time.format(DateTimeFormatter.ofPattern("EEEE, yyyy-MM-dd", Locale.ENGLISH)),
                    cx, baseline + dateSize + 18, dateSize, 0x667085);
            centered(g, time.getZone().getId(), cx, baseline + dateSize + 44,
                    Math.max(9, Math.min(14, width / 28)), 0x8491a6);
        } finally { g.dispose(); }
        canvas.drawImage(frame, 0, 0, width, height);
        startTicker();
    }
    private static void hand(Graphics2D g, int cx, int cy, double angle, double length, int stroke, int color) {
        angle -= Math.PI / 2;
        g.setColor(new Color(color));
        g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(cx, cy, cx + Math.cos(angle) * length, cy + Math.sin(angle) * length));
    }
    private static void centered(Graphics2D g, String text, int x, int baseline, int size, int color) {
        g.setFont(com.android.graphics.FontManager.getFont( Font.PLAIN, size));
        g.setColor(new Color(color));
        g.drawString(text, x - g.getFontMetrics().stringWidth(text) / 2, baseline);
    }
}
