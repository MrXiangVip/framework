package com.packages.camera;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

/** One capture session. Frames are immutable after publication to the UI. */
public final class CameraCapture implements AutoCloseable {
    public static final int WIDTH = 640, HEIGHT = 480;
    private volatile BufferedImage frame;
    private volatile String status = "Opening camera...";
    private volatile boolean closed;
    private Process process;
    private Thread worker;
    private final StringBuilder errors = new StringBuilder();

    public synchronized void start(boolean demo) {
        if (closed || worker != null) throw new IllegalStateException("Capture session already started or closed");
        worker = new Thread(() -> {
            try {
                if (demo) demoLoop(); else cameraLoop();
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            catch (IOException | RuntimeException e) {
                if (!closed) { frame = null; status = "Camera unavailable: " + e.getMessage(); }
            } finally { stopProcess(); }
        }, "camera-capture");
        worker.setDaemon(true); worker.start();
    }
    public BufferedImage getFrame() { return frame; }
    public String getStatus() { return status; }
    public boolean isClosed() { return closed; }

    private void cameraLoop() throws IOException, InterruptedException {
        String device = System.getProperty("framework.camera.device", "/dev/video0");
        String executable = System.getProperty("framework.camera.ffmpeg", "ffmpeg");
        if (!System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("linux"))
            throw new IOException("Live capture requires Linux; try Demo");
        Process child;
        try {
            child = new ProcessBuilder(Arrays.asList(executable, "-hide_banner", "-loglevel", "error", "-nostdin",
                    "-f", "v4l2", "-i", device, "-an", "-vf", "scale=640:480:force_original_aspect_ratio=decrease,pad=640:480:(ow-iw)/2:(oh-ih)/2", "-r", "10",
                    "-pix_fmt", "bgr24", "-f", "rawvideo", "pipe:1")).start();
        } catch (IOException e) { throw new IOException("Cannot start FFmpeg; install it or set framework.camera.ffmpeg", e); }
        synchronized (this) {
            if (closed) { child.destroyForcibly(); return; }
            process = child;
        }
        Thread stderr = new Thread(() -> {
            try (InputStream input = child.getErrorStream()) {
                byte[] bytes = new byte[512]; int count;
                while ((count = input.read(bytes)) != -1) synchronized (errors) {
                    if (errors.length() < 4096) errors.append(new String(bytes, 0, count, StandardCharsets.UTF_8));
                }
            } catch (IOException ignored) { /* Stream closes during stop. */ }
        }, "camera-errors");
        stderr.setDaemon(true); stderr.start();
        // Bound a missing/hung device and stalled preview instead of waiting forever in read().
        java.util.concurrent.atomic.AtomicLong progress = new java.util.concurrent.atomic.AtomicLong(System.nanoTime());
        Thread watchdog = new Thread(() -> {
            try {
                while (!closed && child.isAlive()) {
                    if (System.nanoTime() - progress.get() > TimeUnit.SECONDS.toNanos(12)) {
                        synchronized (errors) { errors.append(" Camera timed out; check device and permissions."); }
                        child.destroyForcibly(); return;
                    }
                    Thread.sleep(250);
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "camera-watchdog");
        watchdog.setDaemon(true); watchdog.start();
        try (InputStream input = child.getInputStream()) {
            while (!closed) {
                BufferedImage next = readFrame(input);
                if (next == null) {
                    stderr.join(300);
                    String detail;
                    synchronized (errors) { detail = errors.toString().trim().replace('\n', ' ').replace('\r', ' '); }
                    throw new IOException(detail.isEmpty() ? "No video frames; check " + device : detail);
                }
                progress.set(System.nanoTime());
                frame = next;
                status = "LIVE  |  " + device + "  |  640 x 480";
            }
        } finally { watchdog.interrupt(); }
    }
    /** FFmpeg emits packed BGR24; reject truncated frames rather than displaying partial data. */
    public static BufferedImage readFrame(InputStream input) throws IOException {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_3BYTE_BGR);
        byte[] bytes = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        int offset = 0;
        while (offset < bytes.length) {
            int count = input.read(bytes, offset, bytes.length - offset);
            if (count < 0) {
                if (offset == 0) return null;
                throw new EOFException("Incomplete video frame");
            }
            if (count == 0) continue;
            offset += count;
        }
        return image;
    }
    private void demoLoop() throws InterruptedException {
        int index = 0;
        while (!closed) {
            BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            try {
                g.setPaint(new GradientPaint(0, 0, new Color(0x294b72), WIDTH, HEIGHT, new Color(0x65c3b5)));
                g.fillRect(0, 0, WIDTH, HEIGHT);
                g.setColor(new Color(0xffdb83)); g.fillOval(60 + index % 420, 100, 80, 80);
                g.setColor(Color.WHITE); g.setFont(com.android.graphics.FontManager.getFont( Font.BOLD, 26));
                g.drawString("DEMO - NOT A CAMERA", 40, 55);
                g.setFont(com.android.graphics.FontManager.getFont( Font.PLAIN, 20));
                g.drawString(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), 40, HEIGHT - 40);
            } finally { g.dispose(); }
            frame = image; status = "DEMO  |  Generated preview, not a camera";
            index += 5; Thread.sleep(100);
        }
    }
    public static File savePhoto(BufferedImage snapshot, File directory) throws IOException {
        if (snapshot == null) throw new IOException("No preview frame available");
        Path folder = directory.toPath(); Files.createDirectories(folder);
        String prefix = "IMG_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")) + "_";
        Path file = Files.createTempFile(folder, prefix, ".jpg");
        boolean complete = false;
        try {
            if (!ImageIO.write(snapshot, "jpeg", file.toFile())) throw new IOException("JPEG writer unavailable");
            complete = true;
            return file.toFile();
        } finally { if (!complete) Files.deleteIfExists(file); }
    }
    private synchronized void stopProcess() {
        if (process != null) {
            process.destroy();
            if (process.isAlive()) process.destroyForcibly();
            process = null;
        }
    }
    @Override public synchronized void close() {
        closed = true;
        stopProcess();
        if (worker != null) worker.interrupt();
    }
}
