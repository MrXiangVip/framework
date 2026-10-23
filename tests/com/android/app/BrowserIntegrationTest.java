package com.android.app;

import com.android.os.*;
import com.android.view.*;
import java.io.File;
import java.lang.reflect.Method;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Actual APK -> Java 8 bridge -> Java 17 CEF -> View tree -> X11 integration. */
public final class BrowserIntegrationTest {
    private static View view, root;
    private static ViewRootImpl surface;
    private static Method engineMethod;
    private static long deadline;
    private static int stage;
    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        ActivityThread thread = new ActivityThread();
        String apk = new File("out/system/app/browser/browser.apk").getCanonicalPath();
        thread.bindApplication(new ApplicationStartInfo("com.packages.browser", "com.packages.browser.MainActivity", apk));
        Activity activity = thread.newActivity("com.packages.browser.MainActivity");
        activity.attach(ContextImpl.createActivityContext(thread, "com.packages.browser", "com.packages.browser.MainActivity", null, 0, null), thread, null, null);
        activity.performCreate();
        view = activity.findViewById("browser"); root = activity.getWindow().getDecorView();
        surface = new ViewRootImpl(activity, 0);
        WindowManager.LayoutParams attrs = new WindowManager.LayoutParams(); attrs.width = 960; attrs.height = 720;
        surface.setView(root, attrs, null, 0);
        engineMethod = view.getClass().getMethod("getEngine");
        view.getClass().getMethod("navigate", String.class).invoke(view, args[0]);
        deadline = System.nanoTime() + 40_000_000_000L;
        Handler poll = new Handler(message -> {
            try { check(); }
            catch (Throwable failure) { failure.printStackTrace(); finish(1); }
            return true;
        });
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(
                () -> poll.sendMessage(poll.obtainMessage(0, null)), 0, 100, java.util.concurrent.TimeUnit.MILLISECONDS);
        Looper.loop();
    }
    private static void check() throws Exception {
        Object engine = engineMethod.invoke(view);
        if (System.nanoTime() > deadline) throw new AssertionError("Timed out at browser integration stage " + stage + ": " + view.getClass().getMethod("getNotice").invoke(view));
        if (engine == null) return;
        Object state = engine.getClass().getMethod("snapshot").invoke(engine);
        String title = (String) state.getClass().getField("title").get(state);
        if (stage == 0 && title.equals("JS READY")) {
            BufferedImage image = ViewRootImpl.renderToImage(root, 960, 720);
            if ((image.getRGB(700, 408) & 0xffffff) != 0x0f64b4) return;
            ImageIO.write(image, "png", new File("out/browser-webpage-preview.png"));
            System.out.println("PASS: actual browser APK paints Chromium pixels into its framework window");
            nativeClick(1, 80, 153, 0); stage = 1;
        } else if (stage == 1 && title.equals("CLICKED")) {
            System.out.println("PASS: native X11 click reaches the actual webpage DOM");
            nativeClick(1, 80, 153, 4); stage = 2;
        } else if (stage == 2 && title.equals("CTRL CLICKED")) {
            System.out.println("PASS: Ctrl modifier survives native X11, View routing and CEF input");
            nativeClick(3, 700, 408, 0); stage = 3;
        } else if (stage == 3 && title.equals("RIGHT CLICKED")) {
            System.out.println("PASS: native right button reaches webpage contextmenu handler");
            nativeClick(5, 700, 408, 0); stage = 4;
        } else if (stage == 4 && title.startsWith("SCROLL:")) {
            System.out.println("PASS: native wheel scrolls the actual webpage"); surface.close(); stage = 5;
        } else if (stage == 5 && !(Boolean) engine.getClass().getMethod("isAlive").invoke(engine)) {
            System.out.println("PASS: detaching the framework window closes its Chromium host"); stage = 6; finish(0);
        }
    }
    private static void nativeClick(int button, int x, int y, int modifiers) throws Exception {
        String pid = java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        int code = new ProcessBuilder("out/browser-integration-native/driver", pid, "" + button, "" + x, "" + y, "" + modifiers).inheritIO().start().waitFor();
        if (code != 0) throw new AssertionError("Native browser input failed: " + code);
    }
    private static void finish(int status) {
        try { if (surface != null) surface.close(); if (view != null) view.getClass().getMethod("close").invoke(view); }
        catch (Exception ignored) {}
        // Allow the asynchronous close watchdog to reap native children before test JVM exits.
        new Thread(() -> { try { Thread.sleep(1500); } catch (InterruptedException ignored) {} System.exit(status); }).start();
    }
}
