package com.packages.launcher;

import com.android.content.Context;
import com.android.graphics.Canvas;
import com.android.pm.LauncherActivityInfo;
import com.android.util.Log;
import com.android.view.View;
import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/** Icon and label from the target APK; no target application classes are loaded. */
final class AppIconView extends View {
    private final LauncherActivityInfo info;
    private BufferedImage image;
    private static final FontRenderContext FONTS = new FontRenderContext(null, true, true);

    AppIconView(Context context, LauncherActivityInfo info) {
        super(context);
        this.info = info;
        setId(info.activityName);
        String path = info.iconPath;
        if (path.startsWith("@drawable/")) path = "res/drawable/" + path.substring(10) + ".png";
        if (!path.isEmpty()) {
            try (ZipFile apk = new ZipFile(info.apkPath)) {
                ZipEntry entry = apk.getEntry(path);
                if (entry == null) throw new IllegalArgumentException("Missing " + path);
                try (InputStream input = apk.getInputStream(entry)) { image = ImageIO.read(input); }
            } catch (Exception e) { Log.w("Cannot load icon for " + info.packageName + ": " + e.getMessage()); }
        }
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(resolveSize(156, widthSpec), resolveSize(180, heightSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        if (isPressed()) canvas.fillRect(0, 0, getWidth(), getHeight(), 0xffdce8fa);
        int size = Math.max(0, Math.min(112, Math.min(getWidth() - 24, getHeight() - 52)));
        int x = (getWidth() - size) / 2, y = Math.max(8, (getHeight() - size - 30) / 2);
        if (image != null) {
            double scale = Math.min((double) size / image.getWidth(), (double) size / image.getHeight());
            int w = (int) (image.getWidth() * scale), h = (int) (image.getHeight() * scale);
            canvas.drawImage(image, x + (size - w) / 2, y + (size - h) / 2, w, h);
        } else {
            canvas.fillRect(x, y, size, size, 0xff4778bd);
            String initial = info.label.isEmpty() ? "?" : info.label.substring(0, info.label.offsetByCodePoints(0, 1));
            drawCentered(canvas, initial, y + size / 2 + 14, 40);
        }
        String label = info.label;
        int available = Math.max(0, getWidth() - 12);
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 18);
        if (font.getStringBounds(label, FONTS).getWidth() > available) {
            while (!label.isEmpty() && font.getStringBounds(label + "...", FONTS).getWidth() > available)
                label = label.substring(0, label.offsetByCodePoints(label.length(), -1));
            label += "...";
        }
        drawCentered(canvas, label, Math.min(getHeight() - 8, y + size + 26), 18);
    }

    private void drawCentered(Canvas canvas, String text, int baseline, int size) {
        int width = (int) new Font(Font.SANS_SERIF, Font.PLAIN, size).getStringBounds(text, FONTS).getWidth();
        canvas.drawText(text, (getWidth() - width) / 2, baseline, size, 0xff202124);
    }
}
