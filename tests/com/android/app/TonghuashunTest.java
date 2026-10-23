package com.android.app;

import com.android.os.ApplicationStartInfo;
import com.android.os.Looper;
import com.android.view.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URLClassLoader;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Inflate the actual APK, render the reference layout and exercise interactive order flow. */
public final class TonghuashunTest {
    private static int checks;
    private static View root, market;
    private static int width = 550, height = 1148;
    private static String preview;
    public static void main(String[] args) throws Exception {
        System.setProperty("framework.tonghuashun.online", "false");
        Path dir = Paths.get(args[0]); Files.createDirectories(dir);
        preview = dir.toString(); System.setProperty("framework.tonghuashun.data", dir.resolve("ui-account.properties").toString());
        Looper.prepareMainLooper(); ActivityThread thread = new ActivityThread();
        File apk = new File("out/system/app/tonghuashun/tonghuashun.apk").getCanonicalFile();
        thread.bindApplication(new ApplicationStartInfo("com.packages.tonghuashun", "com.packages.tonghuashun.MainActivity", apk.toString()));
        try {
            Activity a = thread.newActivity("com.packages.tonghuashun.MainActivity");
            a.attach(ContextImpl.createActivityContext(thread, "com.packages.tonghuashun", "com.packages.tonghuashun.MainActivity", null, 0, null), thread, null, null); a.performCreate();
            root = a.getWindow().getDecorView(); market = a.findViewById("market"); check(market != null, "actual APK inflates market view");
            BufferedImage image = render("watchlist"); check(image.getRGB(5, 5) == 0xfff3313c, "reference red header rendered");
            check((Integer) call("getPage") == 2, "watchlist is the default tab");
            // Header sorting changes the first symbol from HSI999 to the highest priced fixture.
            click(359, 239); render(null); click(100, 293); render("detail");
            check(!call("getSelectedCode").equals(""), "sorted quote opens security detail");
            click(200, 210); render("candles");
            key(KeyEvent.DOWN, KeyEvent.ESCAPE, 0); render(null);
            click(510, 35); render("search"); for (int code : "300750".codePoints().toArray()) key(KeyEvent.TEXT, code, 0);
            render(null); check(call("getQuery").equals("300750"), "keyboard code search");
            Object watchStore = call("getStore");
            click(510, 154); render(null);
            check(!(Boolean) watchStore.getClass().getMethod("watched", String.class).invoke(watchStore, "300750"), "search removes a watched security");
            click(510, 154); render(null);
            check((Boolean) watchStore.getClass().getMethod("watched", String.class).invoke(watchStore, "300750"), "search adds a security back to watchlist");
            key(KeyEvent.DOWN, KeyEvent.ENTER, 0); render(null);
            check(call("getSelectedCode").equals("300750"), "search opens the matching security");
            click(120, 1034); render("order"); check((Boolean) call("isOrderOpen"), "buy opens modal paper order");
            click(320, 1110); render(null);
            check((Boolean) call("isOrderOpen") && (Integer) call("getPage") == 2, "modal blocks clicks on underlying navigation");
            click(334, 524); for (int code : "101".codePoints().toArray()) key(KeyEvent.TEXT, code, 0); render(null);
            click(270, 728); render(null);
            check((Boolean) call("isOrderOpen") && call("getNotice").toString().contains("100"), "UI rejects an odd-lot order");
            click(334, 524); for (int code : "100".codePoints().toArray()) key(KeyEvent.TEXT, code, 0); render(null);
            click(270, 728); render(null);
            check(!(Boolean) call("isOrderOpen"), "paper buy completes");
            Object store = call("getStore"); long cash = (Long) store.getClass().getMethod("cash").invoke(store);
            check(cash == 100_000_000L - 24680L * 100, "buy is reflected in available cash");
            click(385, 1034); render(null); click(270, 728); render(null);
            check((Long) store.getClass().getMethod("cash").invoke(store) == 100_000_000L, "sell returns cash without drift");
            click(320, 1110); render("account"); check((Integer) call("getPage") == 3, "bottom navigation opens account");
            click(411, 1110); render(null); click(180, 220); render("guide");
            key(KeyEvent.DOWN, KeyEvent.ESCAPE, 0); render(null);
            click(228, 1110); render(null);
            width = 320; height = 650; render("narrow");
            width = 1000; height = 650; render("wide");
            check(new com.android.pm.PackageManagerService(new File("."), false, false).getLauncherActivities().stream().anyMatch(info -> info.activityName.equals("com.packages.tonghuashun.MainActivity")), "launcher discovers installed app");
            System.out.println("PASS: " + checks + " actual market APK/UI checks; previews: " + preview);
        } finally { ((URLClassLoader) thread.getApplicationClassLoader()).close(); }
    }
    private static Object call(String name) throws Exception { return market.getClass().getMethod(name).invoke(market); }
    private static BufferedImage render(String name) throws Exception {
        BufferedImage image = ViewRootImpl.renderToImage(root, width, height);
        if (name != null) ImageIO.write(image, "png", new File(preview, name + ".png")); return image;
    }
    private static void click(int x, int y) { root.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_DOWN, x, y)); root.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_UP, x, y)); }
    private static void key(int type, int value, int modifiers) { root.dispatchKeyEvent(new KeyEvent(type, value, modifiers)); }
    private static void check(boolean ok, String label) { checks++; if (!ok) throw new AssertionError(label); }
}
