package com.packages.browser;

import com.android.content.Context;
import com.android.graphics.Canvas;
import com.android.os.Handler;
import com.android.util.AttributeSet;
import com.android.view.*;
import com.android.widget.EditText;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Browser chrome and input only; Chromium owns all webpage layout and execution. */
public final class BrowserView extends ViewGroup implements AutoCloseable {
    private static final int TOP = 108, FOOT = 26;
    private final BrowserText fonts = new BrowserText();
    private BrowserEngine engine;
    private String notice = "", pageUrl = "about:blank";
    private final EditText addressInput;
    private boolean addressPointer;
    private boolean home = true, closed, starting, pressedPage;
    private int pressedButton = -1;
    private final AtomicBoolean pending = new AtomicBoolean();
    private final Handler ui = new Handler(message -> { pending.set(false); ((Runnable) message.obj).run(); return true; });
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "browser-start"); t.setDaemon(true); return t; });
    private ScheduledExecutorService lifecycle;
    private final Rectangle[] buttons = new Rectangle[5];
    private Rectangle location = new Rectangle();
    public BrowserView(Context context, AttributeSet attrs) {
        super(context, attrs);
        addressInput = new EditText(context) {
            @Override protected int measureText(String value) { return fonts.width(value, mTextSize); }
            @Override protected void drawTextLine(Canvas canvas, String value, int x, int baseline, int color) {
                fonts.draw(canvas, value, x, baseline, mTextSize, color);
            }
            @Override protected void onDraw(Canvas canvas) {
                canvas.fillRect(0, 0, getWidth(), getHeight(), isFocused() ? 0xff344761 : 0xff132035);
                super.onDraw(canvas);
            }
            @Override protected void onFocusChanged(boolean focused) {
                super.onFocusChanged(focused);
                if (engine != null) engine.focus(!focused && !home);
            }
        };
        addressInput.setId("address"); addressInput.setSingleLine(); addressInput.setMaxLength(8192);
        addressInput.setTextSize(13); addressInput.setTextColor(0xffe7eefb);
        addressInput.setHint("Search or enter a website"); addressInput.setHintTextColor(0xff94a8c4);
        addressInput.setSelectAllOnFocus(true);
        addView(addressInput, new LayoutParams(-2, -2)); addressInput.requestFocus();
    }
    private void setAddress(String value) {
        addressInput.setText(value); addressInput.setSelection(addressInput.getText().length());
    }
    public BrowserEngine getEngine() { return engine; }
    public String getAddress() { return addressInput.getText(); }
    public boolean isHome() { return home; }
    public String getNotice() { return notice; }
    private void post(Runnable task) { ui.sendMessage(ui.obtainMessage(0, task)); }
    private void engineChanged() {
        if (closed || !pending.compareAndSet(false, true)) return;
        post(() -> {
            if (closed || engine == null) return;
            BrowserEngine.Snapshot state = engine.snapshot(); pageUrl = state.url;
            if (!addressInput.isFocused()) setAddress(state.url);
            notice = state.error; invalidate();
        });
    }
    public void navigate(String text) {
        final String url;
        try { url = BrowserController.address(text); }
        catch (IllegalArgumentException e) { notice = e.getMessage(); invalidate(); return; }
        if (closed) return;
        setAddress(url); home = false; addressInput.clearFocus(); notice = ""; pageUrl = url;
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
                    if (!home) { next.navigate(pageUrl); next.focus(!addressInput.isFocused()); }
                    notice = "";
                }
                invalidate();
            });
        });
        invalidate();
    }
    private void resizeEngine() { if (engine != null) engine.resize(getWidth(), Math.max(1, getHeight() - contentTop() - FOOT)); }
    private void goHome() {
        home = true; setAddress(""); addressInput.requestFocus(); notice = "";
        if (engine != null) engine.focus(false); invalidate();
    }
    private void editAddress() {
        addressInput.requestFocus(); addressInput.selectAll();
        if (engine != null) engine.focus(false); invalidate();
    }
    private void action(int index) {
        if (index == 3) { goHome(); return; }
        if (index == 4) { navigate(addressInput.getText()); return; }
        if (engine == null || home) return;
        addressInput.clearFocus(); engine.focus(true);
        if (index == 0) engine.back();
        if (index == 1) engine.forward();
        if (index == 2) { if (engine.snapshot().loading) engine.stop(); else engine.reload(); }
    }
    @Override protected void onMeasure(int width, int height) {
        setMeasuredDimension(resolveSize(960, width), resolveSize(720, height));
        int left = getMeasuredWidth() < 420 ? 12 : 166;
        addressInput.measure(MeasureSpec.makeMeasureSpec(Math.max(30, getMeasuredWidth() - left - 64), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(34, MeasureSpec.EXACTLY));
    }
    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < 4; i++) buttons[i] = new Rectangle(12 + i * 36, 53, 32, 34);
        int left = getWidth() < 420 ? 12 : 166;
        int y = getWidth() < 420 ? 92 : 53;
        location = new Rectangle(left, y, Math.max(30, getWidth() - left - 64), 34);
        buttons[4] = new Rectangle(Math.max(left + 34, getWidth() - 48), y, 36, 34);
        addressInput.layout(location.x, location.y, location.x + location.width, location.y + location.height);
        resizeEngine();
    }
    private int contentTop() { return getWidth() < 420 ? 142 : TOP; }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getValue(); boolean ctrl = (event.getModifiers() & KeyEvent.CONTROL) != 0;
        if (event.getType() == KeyEvent.DOWN && ctrl && (key == 'l' || key == 'L')) { editAddress(); return true; }
        if (event.getType() == KeyEvent.DOWN && !addressInput.isFocused()
                && (key == 0xffc2 || ctrl && (key == 'r' || key == 'R'))) {
            if (engine != null) engine.reload(); return true;
        }
        if (addressInput.isFocused()) {
            if (event.getType() == KeyEvent.DOWN && (key == KeyEvent.ENTER || key == 0xff8d)) { navigate(addressInput.getText()); return true; }
            if (event.getType() == KeyEvent.DOWN && key == KeyEvent.ESCAPE) { addressInput.clearFocus(); return true; }
            return super.dispatchKeyEvent(event);
        }
        if (!home) { if (engine != null) engine.key(event.getType(), key, event.getModifiers()); return true; }
        if (event.getType() == KeyEvent.DOWN && (key == KeyEvent.TAB || key == KeyEvent.ISO_LEFT_TAB)) return addressInput.requestFocus();
        return false;
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int x = event.getX(), y = event.getY(), action = event.getAction();
        if (action == MotionEvent.ACTION_CANCEL) { addressPointer = false; super.dispatchTouchEvent(event); pressedButton = -1; pressedPage = false; if (engine != null) { engine.mouse(3, 0, 0, 1, 0); engine.focus(false); } return true; }
        if (addressPointer || action == MotionEvent.ACTION_DOWN && location.contains(x, y)) {
            if (action == MotionEvent.ACTION_DOWN) addressPointer = true;
            boolean handled = super.dispatchTouchEvent(event);
            if (action == MotionEvent.ACTION_UP) addressPointer = false;
            return handled;
        }
        if (action == MotionEvent.ACTION_SCROLL) {
            if (!home && engine != null && y >= contentTop()) engine.wheel(x, y - contentTop(), event.getScrollDelta(), event.getModifiers());
            return true;
        }
        // Secondary clicks belong to the webpage, not the browser toolbar.
        if (event.getButton() != 1 && (home || y < contentTop())) return true;
        if (action == MotionEvent.ACTION_DOWN) {
            for (int i = 0; i < buttons.length; i++) if (buttons[i] != null && buttons[i].contains(x, y)) { pressedButton = i; invalidate(); return true; }
            if (home && y >= contentTop()) { editAddress(); return true; }
            if (y >= contentTop() && y < getHeight() - FOOT && engine != null) {
                addressInput.clearFocus(); pressedPage = true; engine.focus(true);
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
