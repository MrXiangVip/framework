package org.cef.browser;

import org.cef.CefBrowserSettings;
import org.cef.CefClient;
import org.cef.callback.CefDragData;
import org.cef.handler.CefRenderHandler;
import org.cef.handler.CefScreenInfo;
import java.awt.*;
import java.awt.event.*;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

/** Raw CEF pixels, without JCEF's Swing/JOGL rendering surface or a second window. */
public final class FrameworkOsrBrowser extends CefBrowser_N implements CefRenderHandler {
    public interface Pixels { void paint(boolean popup, ByteBuffer data, int width, int height); }
    private final Component input = new Canvas();
    private final Pixels pixels;
    private int width = 800, height = 600;
    private boolean dragging;
    public FrameworkOsrBrowser(CefClient client, Pixels pixels) {
        super(client, "about:blank", null, null, null, new CefBrowserSettings());
        this.pixels = pixels;
    }
    @Override public java.util.concurrent.CompletableFuture<java.awt.image.BufferedImage> createScreenshot(boolean nativeResolution) {
        java.util.concurrent.CompletableFuture<java.awt.image.BufferedImage> future = new java.util.concurrent.CompletableFuture<>();
        future.completeExceptionally(new UnsupportedOperationException("Use the BrowserProtocol frame stream")); return future;
    }
    @Override public Component getUIComponent() { return input; }
    @Override public CefRenderHandler getRenderHandler() { return this; }
    @Override public void createImmediately() { createBrowser(getClient(), 0, getUrl(), true, false, null, getRequestContext()); }
    @Override protected CefBrowser_N createDevToolsBrowser(CefClient client, String url, CefRequestContext context, CefBrowser_N parent, Point at) { return null; }
    public void resize(int w, int h) { width = w; height = h; wasResized(w, h); }
    @Override public Rectangle getViewRect(CefBrowser browser) { return new Rectangle(0, 0, width, height); }
    @Override public Point getScreenPoint(CefBrowser browser, Point point) { return point; }
    @Override public boolean getScreenInfo(CefBrowser browser, CefScreenInfo info) {
        info.Set(1, 32, 8, false, getViewRect(browser), getViewRect(browser)); return true;
    }
    @Override public void onPaint(CefBrowser browser, boolean popup, Rectangle[] dirty, ByteBuffer data, int w, int h) { pixels.paint(popup, data, w, h); }
    // HTML select widgets use a separate paint surface; the host composites it over the page.
    private Consumer<Rectangle> popupSize = r -> {};
    private Consumer<Boolean> popupShow = show -> {};
    public void popupCallbacks(Consumer<Rectangle> size, Consumer<Boolean> show) { popupSize = size; popupShow = show; }
    @Override public void onPopupSize(CefBrowser browser, Rectangle size) { popupSize.accept(size); }
    @Override public void onPopupShow(CefBrowser browser, boolean show) { popupShow.accept(show); }
    @Override public boolean onCursorChange(CefBrowser browser, int cursor) { return true; }
    @Override public boolean startDragging(CefBrowser browser, CefDragData data, int mask, int x, int y) { return false; }
    @Override public void updateDragCursor(CefBrowser browser, int operation) {}
    @Override public void addOnPaintListener(Consumer<CefPaintEvent> listener) {}
    @Override public void setOnPaintListener(Consumer<CefPaintEvent> listener) {}
    @Override public void removeOnPaintListener(Consumer<CefPaintEvent> listener) {}
    private static int modifiers(int x11) {
        return ((x11 & 1) != 0 ? InputEvent.SHIFT_DOWN_MASK : 0)
                | ((x11 & 4) != 0 ? InputEvent.CTRL_DOWN_MASK : 0)
                | ((x11 & 8) != 0 ? InputEvent.ALT_DOWN_MASK : 0);
    }
    public void pointer(int action, int x, int y, int button, int mods) {
        int type;
        if (action == 0) { type = MouseEvent.MOUSE_PRESSED; dragging = true; }
        else if (action == 1 || action == 3) { type = MouseEvent.MOUSE_RELEASED; dragging = false; }
        else type = dragging ? MouseEvent.MOUSE_DRAGGED : MouseEvent.MOUSE_MOVED;
        int mask = modifiers(mods) | (dragging ? button == 3 ? InputEvent.BUTTON3_DOWN_MASK : InputEvent.BUTTON1_DOWN_MASK : 0);
        sendMouseEvent(new MouseEvent(input, type, System.currentTimeMillis(), mask, x, y, 1,
                button == 3, type == MouseEvent.MOUSE_MOVED || type == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : button));
    }
    public void wheel(int x, int y, int delta, int mods) {
        sendMouseWheelEvent(new MouseWheelEvent(input, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), modifiers(mods),
                x, y, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 40, delta > 0 ? 1 : -1));
    }
    public void keyboard(int type, int symbol, int mods) {
        if (type == 0) {
            // Commit XIM text through Chromium's input API: JCEF CHAR events corrupt surrogate pairs on Linux.
            if (!Character.isValidCodePoint(symbol) || symbol >= 0xd800 && symbol <= 0xdfff) return;
            StringBuilder json = new StringBuilder("{\"text\":\"");
            for (char ch : Character.toChars(symbol)) json.append(String.format("\\u%04x", (int) ch));
            executeDevToolsMethod("Input.insertText", json.append("\"}").toString());
            return;
        }
        int code = keyCode(symbol);
        if (code == KeyEvent.VK_UNDEFINED) return;
        char ch = symbol >= 32 && symbol < 127 ? (char) symbol : symbol == 0xff0d ? '\n' : symbol == 0xff08 ? '\b' : KeyEvent.CHAR_UNDEFINED;
        sendKeyEvent(new KeyEvent(input, type == 1 ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED,
                System.currentTimeMillis(), modifiers(mods), code, ch));
    }
    private static int keyCode(int symbol) {
        if (symbol >= 'a' && symbol <= 'z') return KeyEvent.VK_A + symbol - 'a';
        if (symbol >= 'A' && symbol <= 'Z') return KeyEvent.VK_A + symbol - 'A';
        if (symbol >= '0' && symbol <= '9') return KeyEvent.VK_0 + symbol - '0';
        if (symbol >= 0xffbe && symbol <= 0xffc9) return KeyEvent.VK_F1 + symbol - 0xffbe;
        switch (symbol) {
            case 0xff08: return KeyEvent.VK_BACK_SPACE; case 0xff09: return KeyEvent.VK_TAB;
            case 0xff0d: return KeyEvent.VK_ENTER; case 0xff1b: return KeyEvent.VK_ESCAPE;
            case 0xff50: return KeyEvent.VK_HOME; case 0xff51: return KeyEvent.VK_LEFT;
            case 0xff52: return KeyEvent.VK_UP; case 0xff53: return KeyEvent.VK_RIGHT;
            case 0xff54: return KeyEvent.VK_DOWN; case 0xff55: return KeyEvent.VK_PAGE_UP;
            case 0xff56: return KeyEvent.VK_PAGE_DOWN; case 0xff57: return KeyEvent.VK_END;
            case 0xffff: return KeyEvent.VK_DELETE; case 0xffe1: case 0xffe2: return KeyEvent.VK_SHIFT;
            case 0xffe3: case 0xffe4: return KeyEvent.VK_CONTROL; case 0xffe9: case 0xffea: return KeyEvent.VK_ALT;
            default: return symbol >= 32 && symbol < 127 ? KeyEvent.getExtendedKeyCodeForChar(symbol) : KeyEvent.VK_UNDEFINED;
        }
    }
}
