package com.android.app;

import com.android.os.*;
import com.android.view.*;
import java.io.File;
import java.util.concurrent.*;

/** Opt-in real HTTPS -> worker -> UI -> automatic refresh -> detach lifecycle check. */
public final class TonghuashunLiveTest {
    private static View market;
    private static ViewRootImpl surface;
    private static long deadline;
    private static int stage;
    private static String firstStatus;
    public static void main(String[] args) throws Exception {
        Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> { failure.printStackTrace(); System.exit(1); });
        System.setProperty("framework.tonghuashun.online", "true");
        System.setProperty("framework.tonghuashun.data", args[0]);
        Looper.prepareMainLooper(); ActivityThread thread = new ActivityThread();
        thread.bindApplication(new ApplicationStartInfo("com.packages.tonghuashun", "com.packages.tonghuashun.MainActivity", new File("out/system/app/tonghuashun/tonghuashun.apk").getCanonicalPath()));
        Activity a = thread.newActivity("com.packages.tonghuashun.MainActivity");
        a.attach(ContextImpl.createActivityContext(thread, "com.packages.tonghuashun", "com.packages.tonghuashun.MainActivity", null, 0, null), thread, null, null); a.performCreate();
        market = a.findViewById("market"); surface = new ViewRootImpl(a, 0);
        WindowManager.LayoutParams attrs = new WindowManager.LayoutParams(); attrs.width = 550; attrs.height = 800;
        surface.setView(a.getWindow().getDecorView(), attrs, null, 0); deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(35);
        Handler poll = new Handler(message -> { try { check(); } catch (Throwable e) { e.printStackTrace(); finish(1); } return true; });
        Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(() -> poll.sendMessage(poll.obtainMessage(0, null)), 100, 200, TimeUnit.MILLISECONDS);
        Looper.loop();
    }
    private static Object call(String method) throws Exception { return market.getClass().getMethod(method).invoke(market); }
    private static void check() throws Exception {
        String status = (String) call("getQuoteStatus");
        if (System.nanoTime() > deadline) throw new AssertionError("Online market test timed out at stage " + stage + ": " + status);
        if (stage == 0 && status.startsWith("腾讯报价")) {
            Object store = call("getStore"); Object stock = store.getClass().getMethod("find", String.class).invoke(store, "300750");
            if (!(Boolean) store.getClass().getMethod("available", stock.getClass()).invoke(store, stock)) return;
            System.out.println("PASS: actual APK receives HTTPS quotes on its UI thread: " + status); firstStatus = status; stage++;
        } else if (stage == 1 && status.startsWith("腾讯报价") && !status.equals(firstStatus)) {
            System.out.println("PASS: attached application automatically fetches a second quote batch"); surface.close(); stage++;
        } else if (stage == 2 && (Boolean) call("isQuoteFeedClosed")) {
            System.out.println("PASS: detaching the native window stops the quote worker"); finish(0);
        }
    }
    private static void finish(int status) { if (surface != null) surface.close(); try { if (market != null) market.getClass().getMethod("close").invoke(market); } catch (Exception ignored) {} System.exit(status); }
}
