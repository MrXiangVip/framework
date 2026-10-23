package com.android.app;

import com.android.os.ApplicationStartInfo;
import com.android.os.Looper;
import com.android.pm.PackageManagerService;
import com.android.view.*;
import java.io.File;
import java.net.URLClassLoader;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Real APK inflation, launcher registration, Unicode address editing and resized chrome. */
public final class BrowserAppTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        ActivityThread thread = new ActivityThread();
        File apk = new File("out/system/app/browser/browser.apk").getCanonicalFile();
        thread.bindApplication(new ApplicationStartInfo("com.packages.browser", "com.packages.browser.MainActivity", apk.getPath()));
        Object browser = null;
        try {
            Activity activity = thread.newActivity("com.packages.browser.MainActivity");
            activity.attach(ContextImpl.createActivityContext(thread, "com.packages.browser", "com.packages.browser.MainActivity", null, 0, null), thread, null, null);
            activity.performCreate();
            final int[] legacyClicks = {0};
            View legacy = new View(activity); legacy.layout(0, 0, 100, 100); legacy.setOnClickListener(v -> legacyClicks[0]++);
            legacy.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_DOWN, 10, 10, 3, 0, 0));
            legacy.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_UP, 10, 10, 3, 0, 0));
            check(legacyClicks[0] == 0, "right button does not activate legacy click listeners");
            View view = activity.findViewById("browser"), root = activity.getWindow().getDecorView(); browser = view;
            check(view != null, "browser custom view inflated from actual APK");
            BufferedImage image = ViewRootImpl.renderToImage(root, 960, 720);
            ImageIO.write(image, "png", new File("out/browser-preview.png"));
            check(image.getRGB(2, 2) == 0xff101827, "browser chrome rendered");
            check(image.getRGB(3, 400) != image.getRGB(3, 3), "homepage surface painted");
            check((Boolean) view.getClass().getMethod("isHome").invoke(view), "browser starts on local home");
            for (int cp : "中文😀".codePoints().toArray()) root.dispatchKeyEvent(new KeyEvent(KeyEvent.TEXT, cp, 0));
            check(address(view).equals("中文😀"), "address accepts committed Chinese and emoji");
            // Test actual bundled font coverage and pixels, rather than only the stored input string.
            Class<?> fontClass = thread.getApplicationClassLoader().loadClass("com.packages.browser.BrowserText");
            Object fonts = fontClass.newInstance();
            for (int code : "中文国😀".codePoints().toArray())
                check((Boolean) fontClass.getMethod("hasBundledGlyph", int.class).invoke(fonts, code), "bundled glyph exists: " + code);
            java.util.Set<Integer> glyphHashes = new java.util.HashSet<>();
            for (String glyph : new String[]{"中", "文", "国"}) {
                BufferedImage tile = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D graphics = tile.createGraphics();
                try { fontClass.getMethod("draw", java.awt.Graphics2D.class, String.class, int.class, int.class, int.class, int.class, boolean.class)
                        .invoke(fonts, graphics, glyph, 4, 48, 40, 0xffffff, false); }
                finally { graphics.dispose(); }
                int[] pixels = tile.getRGB(0, 0, 64, 64, null, 0, 64);
                check(java.util.Arrays.stream(pixels).anyMatch(pixel -> pixel != 0), "Chinese glyph paints visible pixels");
                glyphHashes.add(java.util.Arrays.hashCode(pixels));
            }
            check(glyphHashes.size() == 3, "Chinese chrome glyphs are distinct instead of missing-glyph boxes");
            key(root, KeyEvent.BACKSPACE, 0);
            check(address(view).equals("中文"), "backspace removes one Unicode codepoint");
            key(root, 'a', KeyEvent.CONTROL); root.dispatchKeyEvent(new KeyEvent(KeyEvent.TEXT, 'x', 0));
            check(address(view).equals("x"), "Ctrl+A replaces selected address");
            key(root, KeyEvent.HOME, 0); root.dispatchKeyEvent(new KeyEvent(KeyEvent.TEXT, 'a', 0));
            check(address(view).equals("ax"), "Home moves the address caret");
            key(root, KeyEvent.DELETE, 0); check(address(view).equals("a"), "Delete edits at caret");
            ViewRootImpl.renderToImage(root, 320, 520);
            root.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_DOWN, 20, 105));
            root.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_UP, 20, 105));
            root.dispatchKeyEvent(new KeyEvent(KeyEvent.TEXT, 'n', 0));
            check(address(view).equals("n"), "narrow address hit testing matches the second toolbar row");
            check(new PackageManagerService(new File("."), false, false).getLauncherActivities().stream()
                    .anyMatch(info -> info.activityName.equals("com.packages.browser.MainActivity")), "Launcher discovers browser APK");
            System.out.println("PASS: " + checks + " browser APK/UI checks; out/browser-preview.png");
        } finally {
            if (browser != null) browser.getClass().getMethod("close").invoke(browser);
            ((URLClassLoader) thread.getApplicationClassLoader()).close();
        }
    }
    private static String address(View view) throws Exception { return (String) view.getClass().getMethod("getAddress").invoke(view); }
    private static void key(View view, int key, int modifiers) { view.dispatchKeyEvent(new KeyEvent(KeyEvent.DOWN, key, modifiers)); }
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
}
