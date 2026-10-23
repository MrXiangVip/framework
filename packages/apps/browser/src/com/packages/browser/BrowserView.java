package com.packages.browser;

import com.android.content.Context;
import com.android.graphics.Canvas;
import com.android.os.Handler;
import com.android.util.AttributeSet;
import com.android.view.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Browser chrome and input only; Chromium owns all webpage layout and execution. */
public final class BrowserView extends View implements AutoCloseable {
    private static final int TOP = 108, FOOT = 26;
    private final BrowserText fonts = new BrowserText();
    private BrowserEngine engine;
    private String address = "", notice = "", pageUrl = "about:blank";
    private boolean editing = true, all, home = true, closed, starting, pressedPage;
    private int caret, pressedButton = -1;
    private final AtomicBoolean pending = new AtomicBoolean();
    private final Handler ui = new Handler(message -> { pending.set(false); ((Runnable) message.obj).run(); return true; });
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "browser-start"); t.setDaemon(true); return t; });
    private ScheduledExecutorService lifecycle;
    private final Rectangle[] buttons = new Rectangle[5];
    private Rectangle location = new Rectangle();
    public BrowserView(Context context, AttributeSet attrs) { super(context, attrs); }
    public BrowserEngine getEngine() { return engine; }
    public String getAddress() { return address; }
    public boolean isHome() { return home; }
    public String getNotice() { return notice; }
    private void post(Runnable task) { ui.sendMessage(ui.obtainMessage(0, task)); }
    private void engineChanged() {
        if (closed || !pending.compareAndSet(false, true)) return;
        post(() -> {
            if (closed || engine == null) return;
            BrowserEngine.Snapshot state = engine.snapshot(); pageUrl = state.url;
            if (!editing) { address = state.url; caret = address.length(); }
            notice = state.error; invalidate();
        });
    }
    public void navigate(String text) {
        final String url;
        try { url = BrowserController.address(text); }
        catch (IllegalArgumentException e) { notice = e.getMessage(); invalidate(); return; }
        if (closed) return;
        address = url; caret = url.length(); editing = all = home = false; notice = ""; pageUrl = url;
        if (engine != null && engine.isAlive()) { engine.navigate(url); engine.focus(true); invalidate(); return; }
        if (starting) { notice = "Starting browser..."; invalidate(); return; }
        starting = true; notice = "Starting browser...";
        worker.execute(() -> {
            BrowserEngine created = null; String failure = "";
            try { created = new BrowserEngine(); }
            catch (Exception e) { failure = e.getMessage() == null ? "Browser could not start" : e.getMessage(); }
            final BrowserEngine next = created; final String error = failure;
            post(() -> {
                starting = false;
                if (closed) { if (next != null) next.close(); return; }
                if (engine != null) engine.close(); engine = next;
                if (next == null) notice = error;
                else {
                    next.setListener(this::engineChanged); resizeEngine();
                    if (!home) { next.navigate(pageUrl); next.focus(!editing); }
                    notice = "";
                }
                invalidate();
            });
        });
        invalidate();
    }
    private void resizeEngine() { if (engine != null) engine.resize(getWidth(), Math.max(1, getHeight() - contentTop() - FOOT)); }
    private void goHome() { home = true; editing = true; address = ""; caret = 0; all = false; notice = ""; if (engine != null) engine.focus(false); invalidate(); }
    private void editAddress() { editing = true; all = true; caret = address.length(); if (engine != null) engine.focus(false); invalidate(); }
    private void action(int index) {
        if (index == 3) { goHome(); return; }
        if (index == 4) { navigate(address); return; }
        if (engine == null || home) return;
        editing = false; engine.focus(true);
        if (index == 0) engine.back();
        if (index == 1) engine.forward();
        if (index == 2) { if (engine.snapshot().loading) engine.stop(); else engine.reload(); }
    }
    @Override protected void onMeasure(int width, int height) { setMeasuredDimension(resolveSize(960, width), resolveSize(720, height)); }
    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < 4; i++) buttons[i] = new Rectangle(12 + i * 36, 53, 32, 34);
        int left = getWidth() < 420 ? 12 : 166;
        int y = getWidth() < 420 ? 92 : 53;
        location = new Rectangle(left, y, Math.max(30, getWidth() - left - 64), 34);
        buttons[4] = new Rectangle(Math.max(left + 34, getWidth() - 48), y, 36, 34);
        resizeEngine();
    }
    private int contentTop() { return getWidth() < 420 ? 142 : TOP; }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getValue(); boolean ctrl = (event.getModifiers() & KeyEvent.CONTROL) != 0;
        if (event.getType() == KeyEvent.DOWN && ctrl && (key == 'l' || key == 'L')) { editAddress(); return true; }
        if (event.getType() == KeyEvent.DOWN && !editing && (key == 0xffc2 || ctrl && (key == 'r' || key == 'R'))) {
            if (engine != null) engine.reload(); return true;
        }
        if (!editing && !home) { if (engine != null) engine.key(event.getType(), key, event.getModifiers()); return true; }
        if (event.getType() == KeyEvent.TEXT) {
            if (Character.isValidCodePoint(key) && !Character.isISOControl(key) && !ctrl) replaceSelection(new String(Character.toChars(key)));
        } else if (event.getType() == KeyEvent.DOWN) {
            if (ctrl && (key == 'a' || key == 'A')) all = true;
            else if (key == KeyEvent.ENTER) navigate(address);
            else if (key == KeyEvent.ESCAPE) { editing = false; all = false; if (engine != null) engine.focus(true); }
            else if (key == KeyEvent.BACKSPACE) {
                if (all) replaceSelection(""); else if (caret > 0) { int start = address.offsetByCodePoints(caret, -1); address = address.substring(0, start) + address.substring(caret); caret = start; }
            } else if (key == KeyEvent.DELETE) {
                if (all) replaceSelection(""); else if (caret < address.length()) address = address.substring(0, caret) + address.substring(address.offsetByCodePoints(caret, 1));
            } else if (key == KeyEvent.LEFT) { caret = all ? 0 : caret > 0 ? address.offsetByCodePoints(caret, -1) : 0; all = false; }
            else if (key == KeyEvent.RIGHT) { caret = all ? address.length() : caret < address.length() ? address.offsetByCodePoints(caret, 1) : caret; all = false; }
            else if (key == KeyEvent.HOME) { caret = 0; all = false; }
            else if (key == KeyEvent.END) { caret = address.length(); all = false; }
        }
        invalidate(); return true;
    }
    private void replaceSelection(String text) {
        if ((all ? 0 : address.length()) + text.length() > 8192) return;
        if (all) { address = ""; caret = 0; all = false; }
        address = address.substring(0, caret) + text + address.substring(caret); caret += text.length();
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int x = event.getX(), y = event.getY(), action = event.getAction();
        if (action == MotionEvent.ACTION_CANCEL) { pressedButton = -1; pressedPage = false; if (engine != null) { engine.mouse(3, 0, 0, 1, 0); engine.focus(false); } return true; }
        if (action == MotionEvent.ACTION_SCROLL) {
            if (!home && engine != null && y >= contentTop()) engine.wheel(x, y - contentTop(), event.getScrollDelta(), event.getModifiers());
            return true;
        }
        // Secondary clicks belong to the webpage, not the browser toolbar.
        if (event.getButton() != 1 && (home || y < contentTop())) return true;
        if (action == MotionEvent.ACTION_DOWN) {
            for (int i = 0; i < buttons.length; i++) if (buttons[i] != null && buttons[i].contains(x, y)) { pressedButton = i; invalidate(); return true; }
            if (location.contains(x, y) || home && y >= contentTop()) { editAddress(); return true; }
            if (y >= contentTop() && y < getHeight() - FOOT && engine != null) {
                editing = false; pressedPage = true; engine.focus(true);
                engine.mouse(action, x, y - contentTop(), event.getButton(), event.getModifiers()); invalidate(); return true;
            }
        }
        if (action == MotionEvent.ACTION_UP && pressedButton >= 0) {
            int index = pressedButton; pressedButton = -1;
            if (buttons[index].contains(x, y)) action(index); invalidate(); return true;
        }
        if (!home && engine != null && (pressedPage || y >= contentTop()))
            engine.mouse(action, x, y - contentTop(), event.getButton(), event.getModifiers());
        if (action == MotionEvent.ACTION_UP) pressedPage = false;
        return true;
    }
    @Override protected void onDraw(Canvas canvas) {
        Graphics2D g = canvas.getImage().createGraphics();
        try {

            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight(), top = contentTop();
            g.setColor(new Color(0x101827)); g.fillRect(0, 0, w, h);
            g.setColor(new Color(0x6ce0c1)); g.fillOval(17, 16, 12, 12);
            text(g, "ATLAS", 38, 28, 14, 0xe8eef9, true);
            BrowserEngine.Snapshot state = engine == null ? null : engine.snapshot();
            text(g, home ? "A place to explore" : state == null ? "New page" : state.title, 122, 28, 12, 0xa4b3ca, false);
            g.setColor(new Color(0x1c283c)); g.fillRect(0, 41, w, top - 41);
            String[] labels = {"‹", "›", state != null && state.loading ? "×" : "↻", "⌂", "→"};
            for (int i = 0; i < buttons.length; i++) {
                Rectangle box = buttons[i]; if (box == null) continue;
                g.setColor(new Color(i == 4 ? 0x69dfbd : pressedButton == i ? 0x3c4d68 : 0x26354c));
                g.fillRoundRect(box.x, box.y, box.width, box.height, 10, 10);
                text(g, labels[i], box.x + 10, box.y + 24, 23, i == 4 ? 0x10251f : 0xe3edf9, false);
            }
            g.setColor(new Color(editing ? 0x344761 : 0x132035)); g.fillRoundRect(location.x, location.y, location.width, location.height, 12, 12);
            Graphics2D entry = (Graphics2D) g.create();
            try {
                entry.clipRect(location.x + 10, location.y, Math.max(1, location.width - 20), location.height);
                int shift = Math.max(0, fonts.width(address.substring(0, caret), 13) - location.width + 28);
                if (editing && all) { entry.setColor(new Color(0x426e9a)); entry.fillRect(location.x + 10, location.y + 7, fonts.width(address, 13), 21); }
                text(entry, address.isEmpty() ? "Search or enter a website" : address, location.x + 11 - shift, location.y + 22, 13, address.isEmpty() ? 0x94a8c4 : 0xe7eefb, false);
                if (editing && !all) { int cx = location.x + 11 + fonts.width(address.substring(0, caret), 13) - shift; entry.setColor(new Color(0x6ce0c1)); entry.drawLine(cx, location.y + 9, cx, location.y + 25); }
            } finally { entry.dispose(); }
            g.setClip(0, top, w, Math.max(0, h - top - FOOT));
            BufferedImage image = engine == null ? null : engine.frame();
            if (!home && image != null) g.drawImage(image, 0, top, w, Math.max(1, h - top - FOOT), null);
            else {
                g.setPaint(new GradientPaint(0, top, new Color(0x132238), w, h, new Color(0x173d45))); g.fillRect(0, top, w, h - top);
                int left = Math.max(24, (w - 460) / 2), y = top + Math.max(60, (h - top - 210) / 3);
                text(g, home ? "Find your next idea." : "Opening your page...", left, y, w < 500 ? 26 : 36, 0xf1f7fc, true);
                text(g, "The web, with room to explore.", left, y + 38, 16, 0xa8c7d1, false);
                text(g, "Enter a website above, or type a search.", left, y + 91, 13, 0xc0d4df, false);
                text(g, "Ctrl+L  Address      Ctrl+R  Refresh      F5  Reload", left, y + 124, 12, 0x86abae, false);
            }
            if (!notice.isEmpty()) {
                g.setColor(new Color(0x202e43)); g.fillRoundRect(16, top + 14, Math.max(1, w - 32), 76, 12, 12);
                text(g, starting ? "Starting browser" : "Unable to open this page", 30, top + 40, 16, 0xffd4a3, true);
                text(g, notice, 30, top + 66, 12, 0xd4dfef, false);
            }
            g.setClip(null); g.setColor(new Color(0x101827)); g.fillRect(0, h - FOOT, w, FOOT);
            text(g, state != null && state.loading ? "Loading..." : home ? "Ready to explore" : "Single tab", 14, h - 9, 11, 0x91a7c3, false);
            text(g, "ATLAS / BROWSER", Math.max(150, w - 126), h - 9, 10, 0x5f8495, false);
        } finally { g.dispose(); }
        watchLifecycle();
    }
    private void text(Graphics2D g, String text, int x, int y, int size, int color, boolean bold) {
        fonts.draw(g, text, x, y, size, color, bold);
    }
    private boolean attached() { View root = this; while (root.getParent() instanceof View) root = (View) root.getParent(); return root.getParent() != null; }
    private void watchLifecycle() {
        if (closed || lifecycle != null || !attached()) return;
        lifecycle = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "browser-lifecycle"); t.setDaemon(true); return t; });
        // Lifecycle checking does not repaint idle webpages or consume CPU at 60 Hz.
        lifecycle.scheduleWithFixedDelay(() -> post(() -> { if (!attached()) close(); }), 1, 1, TimeUnit.SECONDS);
    }
    @Override public void close() { if (closed) return; closed = true; worker.shutdownNow(); if (engine != null) engine.close(); if (lifecycle != null) lifecycle.shutdownNow(); }
}
