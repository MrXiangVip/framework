package com.packages.camera;

import com.android.content.Context;
import com.android.graphics.Canvas;
import com.android.os.Handler;
import com.android.util.AttributeSet;
import com.android.view.View;
import com.android.view.ViewGroup;
import com.android.widget.Button;
import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

/** Preview and controls share the existing X11/ViewRootImpl rendering and input path. */
public class CameraView extends ViewGroup {
    private final Button shutter, retry, demo;
    private CameraCapture capture;
    private Timer timer;
    private boolean demoMode;
    private int previewTop, previewBottom, inset;
    private final AtomicBoolean refreshPending = new AtomicBoolean();
    private final AtomicBoolean saving = new AtomicBoolean();
    private volatile String notification = "";
    private volatile File lastPhoto;
    private final Handler ui = new Handler(message -> {
        refreshPending.set(false);
        if (!attached()) stopCamera(); else invalidate();
        return true;
    });

    public CameraView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mBackground = 0xff101824;
        shutter = control("shutter", "Capture", this::takePhoto);
        retry = control("retry", "Retry", () -> startPreview(false));
        demo = control("demo", "Demo", () -> startPreview(true));
    }
    private Button control(String id, String text, Runnable action) {
        Button button = new Button(getContext()); button.setId(id); button.setText(text);
        button.setOnClickListener(view -> action.run());
        addView(button, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        return button;
    }
    public String getStatus() { return capture == null ? "Opening camera..." : capture.getStatus(); }
    public String getNotification() { return notification; }
    public BufferedImage getPreviewFrame() { return capture == null ? null : capture.getFrame(); }
    public File getLastPhoto() { return lastPhoto; }
    public boolean isRunning() { return timer != null; }
    public void startPreview(boolean useDemo) {
        if (capture != null) capture.close();
        demoMode = useDemo; notification = "";
        capture = new CameraCapture(); capture.start(useDemo);
        startRefresh(); invalidate();
    }
    public void stopCamera() {
        if (capture != null) capture.close();
        if (timer != null) { timer.cancel(); timer = null; }
    }
    private boolean attached() {
        View root = this;
        while (root.getParent() instanceof View) root = (View) root.getParent();
        return root.getParent() != null;
    }
    private void startRefresh() {
        if (timer != null) return;
        timer = new Timer("camera-refresh", true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() {
                if (refreshPending.compareAndSet(false, true)) ui.sendMessage(ui.obtainMessage(0, null));
            }
        }, 100, 100);
    }
    private void takePhoto() {
        BufferedImage snapshot = getPreviewFrame();
        if (snapshot == null) { notification = "No preview yet. Connect a camera or choose Demo."; invalidate(); return; }
        if (!saving.compareAndSet(false, true)) return;
        final boolean generated = demoMode;
        notification = "Saving photo..."; invalidate();
        File directory = new File(System.getProperty("framework.camera.output",
                new File(System.getProperty("user.home"), "Pictures/FrameworkCamera").getPath()));
        Thread writer = new Thread(() -> {
            try {
                File photo = CameraCapture.savePhoto(snapshot, directory);
                lastPhoto = photo;
                notification = (generated ? "Demo saved: " : "Saved: ") + photo.getAbsolutePath();
                System.out.println("Camera. " + notification);
            } catch (IOException | RuntimeException e) { notification = "Save failed: " + e.getMessage(); }
            finally { saving.set(false); }
        }, "camera-photo-writer");
        writer.setDaemon(true); writer.start();
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(resolveSize(600, widthSpec), resolveSize(800, heightSpec));
        inset = Math.min(24, getMeasuredWidth() / 20);
        previewTop = 76; previewBottom = Math.max(previewTop, getMeasuredHeight() - 180);
        int width = Math.max(0, (getMeasuredWidth() - 2 * inset - 16) / 3);
        for (Button button : new Button[]{shutter, retry, demo})
            button.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(48, MeasureSpec.EXACTLY));
    }
    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        Button[] buttons = {retry, shutter, demo};
        for (int i = 0; i < buttons.length; i++) {
            int x = inset + i * (buttons[i].getMeasuredWidth() + 8), y = getHeight() - 68;
            buttons[i].layout(x, y, x + buttons[i].getMeasuredWidth(), y + 48);
        }
    }
    @Override protected void onDraw(Canvas canvas) {
        if (attached() && timer == null) startPreview(demoMode);
        canvas.drawText("CAMERA", inset, 36, 22, 0xffedf2fb);
        canvas.drawText(demoMode ? "DEMO MODE" : "LIVE CAMERA", inset, 59, 12, 0xff95a8c4);
        int width = Math.max(0, getWidth() - 2 * inset), height = previewBottom - previewTop;
        canvas.fillRect(inset, previewTop, width, height, 0xff060b12);
        BufferedImage frame = getPreviewFrame();
        if (frame != null) {
            double scale = Math.min((double) width / frame.getWidth(), (double) height / frame.getHeight());
            int w = (int) (frame.getWidth() * scale), h = (int) (frame.getHeight() * scale);
            canvas.drawImage(frame, inset + (width - w) / 2, previewTop + (height - h) / 2, w, h);
        } else {
            canvas.drawText("No preview", inset + 16, previewTop + height / 2, 20, 0xff95a8c4);
            canvas.drawText("Connect camera, then Retry; or choose Demo", inset + 16,
                    previewTop + height / 2 + 28, Math.max(9, Math.min(14, width / 29)), 0xff95a8c4);
        }
        drawWrapped(canvas, getStatus(), previewBottom + 23, 2, 13, 0xffb6c7e0);
        drawWrapped(canvas, notification, previewBottom + 65, 2, 12, 0xff85d6b1);
    }
    private void drawWrapped(Canvas canvas, String text, int baseline, int lines, int size, int color) {
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, size);
        FontRenderContext metrics = new FontRenderContext(null, true, true);
        int start = 0, available = Math.max(1, getWidth() - 2 * inset);
        for (int line = 0; line < lines && start < text.length(); line++) {
            int end = start;
            while (end < text.length() && font.getStringBounds(text.substring(start, end + 1), metrics).getWidth() <= available) end++;
            if (end == start) end++;
            String part = text.substring(start, end);
            if (line == lines - 1 && end < text.length()) part = part.substring(0, Math.max(0, part.length() - 3)) + "...";
            canvas.drawText(part, inset, baseline + line * (size + 5), size, color);
            start = end;
        }
    }
}
