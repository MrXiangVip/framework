package com.packages.browser.host;

import com.packages.browser.BrowserProtocol;
import org.cef.*;
import org.cef.browser.*;
import org.cef.handler.*;
import org.cef.network.CefRequest;
import java.awt.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Dedicated process: native CEF lifetime, navigation and offscreen frames. */
public final class CefHost {
    private final DataOutputStream output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(FileDescriptor.out)));
    private final AtomicReference<byte[]> latestFrame = new AtomicReference<>();
    private final AtomicReference<String> latestState = new AtomicReference<>();
    private final Semaphore changed = new Semaphore(0);
    private volatile boolean running = true, ready, loading, back, forward;
    private String url = "about:blank", title = "New page", error = "";
    private FrameworkOsrBrowser browser;
    private CefApp app;
    private byte[] page, popup;
    private int pageWidth, pageHeight, popupWidth, popupHeight;
    private Rectangle popupRect = new Rectangle();
    private boolean popupVisible;
    public static void main(String[] args) {
        // JNI or libraries may print to System.out; keep those bytes off the IPC stream.
        CefHost host = new CefHost(); System.setOut(new PrintStream(System.err, true));
        Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> { System.err.println(thread.getName() + ": " + failure); failure.printStackTrace(System.err); });
        try { host.run(Paths.get(args[0]), Paths.get(args[1])); }
        catch (Throwable e) { e.printStackTrace(System.err); System.exit(1); }
    }
    private void run(Path runtime, Path session) throws Exception {
        String[] flags = {"--no-first-run", "--disable-gpu", "--disable-gpu-compositing", "--disable-dev-shm-usage", "--disable-background-networking"};
        if (!CefApp.startup(flags)) throw new IOException("CEF startup failed");
        CefSettings settings = new CefSettings(); settings.windowless_rendering_enabled = true;
        settings.browser_subprocess_path = runtime.resolve("bin/lib/linux64/jcef_helper").toString();
        settings.cache_path = session.resolve("cache").toString(); settings.log_file = session.resolve("chromium.log").toString();
        settings.log_severity = CefSettings.LogSeverity.LOGSEVERITY_WARNING;
        app = CefApp.getInstance(flags, settings);
        CefClient client = app.createClient();
        client.addLifeSpanHandler(new CefLifeSpanHandlerAdapter() {
            @Override public void onAfterCreated(CefBrowser b) { ready = true; state(); }
            @Override public void onBeforeClose(CefBrowser b) { running = false; changed.release(); }
            // A first-version single-tab browser opens target=_blank in the current tab.
            @Override public boolean onBeforePopup(CefBrowser b, CefFrame frame, String target, String targetFrame) {
                if (target.startsWith("https://") || target.startsWith("http://")) EventQueue.invokeLater(() -> b.loadURL(target)); return true;
            }
        });
        client.addDisplayHandler(new CefDisplayHandlerAdapter() {
            @Override public void onAddressChange(CefBrowser b, CefFrame frame, String address) { if (frame.isMain()) { url = address; state(); } }
            @Override public void onTitleChange(CefBrowser b, String text) { title = text; state(); }
        });
        client.addLoadHandler(new CefLoadHandlerAdapter() {
            @Override public void onLoadingStateChange(CefBrowser b, boolean busy, boolean canBack, boolean canForward) {
                loading = busy; back = canBack; forward = canForward; if (busy) error = ""; state();
            }
            @Override public void onLoadEnd(CefBrowser b, CefFrame frame, int status) {
                // Upstream JCEF cancels OSR popups before its Java callback: route blank-target links in the document.
                frame.executeJavaScript("if(!window.__atlasLinks){window.__atlasLinks=true;document.addEventListener('click',function(e){var a=e.target.closest&&e.target.closest('a');if(a&&a.target==='_blank')a.target='_self';},true);document.addEventListener('submit',function(e){if(e.target.target==='_blank')e.target.target='_self';},true);}", frame.getURL(), 0);
            }
            @Override public void onLoadError(CefBrowser b, CefFrame frame, ErrorCode code, String text, String failed) {
                if (frame.isMain() && code != ErrorCode.ERR_ABORTED) { error = "Could not load this website: " + text; loading = false; state(); }
            }
        });
        client.addRequestHandler(new CefRequestHandlerAdapter() {
            @Override public boolean onOpenURLFromTab(CefBrowser b, CefFrame frame, String target, boolean userGesture) {
                if (target.startsWith("https://") || target.startsWith("http://")) EventQueue.invokeLater(() -> b.loadURL(target));
                return true;
            }
            @Override public void onRenderProcessTerminated(CefBrowser b, TerminationStatus status, int code, String message) {
                error = "The webpage stopped responding. Reload to try again."; loading = false; state();
            }
            @Override public boolean onBeforeBrowse(CefBrowser b, CefFrame frame, CefRequest request, boolean userGesture, boolean redirect) {
                String target = request.getURL();
                boolean allowed = target.startsWith("https://") || target.startsWith("http://") || target.equals("about:blank");
                // Embedded documents may use local data/blob URLs without opening an external application.
                if (!frame.isMain()) allowed |= target.startsWith("data:") || target.startsWith("blob:") || target.equals("about:srcdoc");
                if (!allowed && frame.isMain()) { error = "This address type is not supported"; state(); }
                return !allowed;
            }
        });
        browser = new FrameworkOsrBrowser(client, this::paint);
        browser.popupCallbacks(rect -> { popupRect = rect; publishFrame(); }, show -> { popupVisible = show; if (!show) popup = null; publishFrame(); });
        Thread writer = new Thread(this::writeEvents, "cef-pixels"); writer.setDaemon(true); writer.start();
        EventQueue.invokeAndWait(browser::createImmediately);
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(System.in))) {
            while (running) {
                BrowserProtocol.Message message = BrowserProtocol.read(input);
                if (message.type != BrowserProtocol.COMMAND || message.bytes.length > 65536) throw new IOException("Invalid browser command");
                String command = message.text();
                EventQueue.invokeLater(() -> { try { command(command); } catch (Exception e) { error = "Browser command failed"; state(); e.printStackTrace(System.err); } });
            }
        } catch (EOFException parentClosed) { /* Closing the parent pipe closes all CEF children. */ }
        finally {
            EventQueue.invokeAndWait(() -> { browser.setCloseAllowed(); browser.close(true); });
            app.dispose(); running = false; changed.release();
        }
    }
    private void command(String line) throws Exception {
        String[] v = line.split("\t", -1);
        switch (v[0]) {
            case "NAV": error = ""; browser.loadURL(BrowserProtocol.decode(v[1])); break;
            case "BACK": browser.goBack(); break;
            case "FORWARD": browser.goForward(); break;
            case "RELOAD": browser.reload(); break;
            case "STOP": browser.stopLoad(); break;
            case "FOCUS": browser.setFocus(v[1].equals("1")); break;
            case "SIZE": int w = Integer.parseInt(v[1]), h = Integer.parseInt(v[2]); BrowserProtocol.validateSize(w, h); browser.resize(w, h); break;
            case "KEY": browser.keyboard(Integer.parseInt(v[1]), Integer.parseInt(v[2]), Integer.parseInt(v[3])); break;
            case "MOUSE": browser.pointer(Integer.parseInt(v[1]), Integer.parseInt(v[2]), Integer.parseInt(v[3]), Integer.parseInt(v[4]), Integer.parseInt(v[5])); break;
            case "WHEEL": browser.wheel(Integer.parseInt(v[1]), Integer.parseInt(v[2]), Integer.parseInt(v[3]), Integer.parseInt(v[4])); break;
            default: throw new IOException("Unknown command");
        }
    }
    private synchronized void state() {
        latestState.set(BrowserProtocol.encode(url) + "\t" + BrowserProtocol.encode(title) + "\t" + BrowserProtocol.encode(error)
                + "\t" + (loading ? 1 : 0) + "\t" + (back ? 1 : 0) + "\t" + (forward ? 1 : 0) + "\t" + (ready ? 1 : 0));
        signal();
    }
    private synchronized void paint(boolean isPopup, ByteBuffer data, int w, int h) {
        try { BrowserProtocol.validateSize(w, h); } catch (IOException ignored) { return; }
        byte[] copy = new byte[w * h * 4]; data.get(copy);
        if (isPopup) { popup = copy; popupWidth = w; popupHeight = h; }
        else { page = copy; pageWidth = w; pageHeight = h; }
        publishFrame();
    }
    private synchronized void publishFrame() {
        if (page == null) return;
        byte[] bytes = new byte[page.length + 8];
        ByteBuffer.wrap(bytes).putInt(pageWidth).putInt(pageHeight); System.arraycopy(page, 0, bytes, 8, page.length);
        if (popupVisible && popup != null) {
            for (int y = 0; y < popupHeight; y++) {
                int py = popupRect.y + y; if (py < 0 || py >= pageHeight) continue;
                int sx = Math.max(0, -popupRect.x), dx = Math.max(0, popupRect.x);
                int count = Math.min(popupWidth - sx, pageWidth - dx);
                if (count > 0) System.arraycopy(popup, (y * popupWidth + sx) * 4, bytes, 8 + (py * pageWidth + dx) * 4, count * 4);
            }
        }
        latestFrame.set(bytes); signal();
    }
    private void signal() { if (changed.availablePermits() == 0) changed.release(); }
    private void writeEvents() {
        try {
            while (running) {
                changed.acquire(); String status = latestState.getAndSet(null);
                if (status != null) BrowserProtocol.write(output, BrowserProtocol.STATE, status.getBytes(StandardCharsets.UTF_8));
                byte[] pixels = latestFrame.getAndSet(null);
                if (pixels != null) BrowserProtocol.write(output, BrowserProtocol.FRAME, pixels);
            }
        } catch (IOException | InterruptedException closed) { running = false; }
    }
}
