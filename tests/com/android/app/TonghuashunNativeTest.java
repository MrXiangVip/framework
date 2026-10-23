package com.android.app;

import com.android.os.*;
import com.android.view.*;
import java.io.File;
import java.util.concurrent.*;

/** Verify real X11 events reach the installed quote UI and its modal order sheet. */
public final class TonghuashunNativeTest {
    private static View market;
    private static ViewRootImpl surface;
    private static int stage;
    private static long deadline;
    private static String driver;
    public static void main(String[] args) throws Exception {
        System.setProperty("framework.tonghuashun.online", "false");
        driver = args[0]; System.setProperty("framework.tonghuashun.data", args[1]);
        Looper.prepareMainLooper(); ActivityThread thread = new ActivityThread();
        thread.bindApplication(new ApplicationStartInfo("com.packages.tonghuashun", "com.packages.tonghuashun.MainActivity", new File("out/system/app/tonghuashun/tonghuashun.apk").getCanonicalPath()));
        Activity a = thread.newActivity("com.packages.tonghuashun.MainActivity");
        a.attach(ContextImpl.createActivityContext(thread, "com.packages.tonghuashun", "com.packages.tonghuashun.MainActivity", null, 0, null), thread, null, null); a.performCreate();
        market = a.findViewById("market"); surface = new ViewRootImpl(a, 0);
        WindowManager.LayoutParams attrs = new WindowManager.LayoutParams(); attrs.width = 550; attrs.height = 800;
        surface.setView(a.getWindow().getDecorView(), attrs, null, 0); deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        Handler poll = new Handler(message -> { try { check(); } catch (Throwable e) { e.printStackTrace(); finish(1); } return true; });
        Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(() -> poll.sendMessage(poll.obtainMessage(0, null)), 100, 100, TimeUnit.MILLISECONDS);
        Looper.loop();
    }
    private static Object call(String method) throws Exception { return market.getClass().getMethod(method).invoke(market); }
    private static void check() throws Exception {
        if (System.nanoTime() > deadline) throw new AssertionError("X11 stock UI timeout at stage " + stage);
        if (stage == 0) { click(100, 293); stage++; }
        else if (stage == 1 && !call("getSelectedCode").equals("")) {
            System.out.println("PASS: native quote click opens security detail"); click(320, 765); stage++;
        } else if (stage == 2 && (Integer) call("getPage") == 3) {
            System.out.println("PASS: native bottom navigation opens account"); click(120, 287); stage++;
        } else if (stage == 3 && (Boolean) call("isOrderOpen")) {
            System.out.println("PASS: native buy click opens paper order sheet"); click(495, 227); stage++;
        } else if (stage == 4 && !(Boolean) call("isOrderOpen")) {
            System.out.println("PASS: native close click dismisses modal"); finish(0);
        }
    }
    private static void click(int x, int y) throws Exception {
        String pid = java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        int status = new ProcessBuilder(driver, pid, "1", "" + x, "" + y, "0").inheritIO().start().waitFor();
        if (status != 0) throw new AssertionError("X11 driver status " + status);
    }
    private static void finish(int code) { if (surface != null) surface.close(); System.exit(code); }
}
