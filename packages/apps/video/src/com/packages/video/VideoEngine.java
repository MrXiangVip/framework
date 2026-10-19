package com.packages.video;

import java.awt.image.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import javax.sound.sampled.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

/** Bounded FFmpeg video/PCM streaming. Every seek replaces and cancels its decoding session. */
public final class VideoEngine implements AutoCloseable {
    public static final int WIDTH = 640, HEIGHT = 360, FPS = 24;
    public static final class Media {
        public final Path file;
        public final int width, height, videoStream;
        public final boolean audio;
        public final double duration;
        Media(Path file, int width, int height, int stream, double duration, boolean audio) {
            this.file = file; this.width = width; this.height = height; videoStream = stream; this.duration = duration; this.audio = audio;
        }
    }
    private volatile Media media;
    private volatile BufferedImage frame;
    private volatile String state = "Idle", error = "", audioStatus = "";
    private volatile double position;
    private volatile int volume = 80;
    private volatile boolean muted;
    private volatile Session session;

    public Media getMedia() { return media; }
    public BufferedImage getFrame() { return frame; }
    public String getState() { return state; }
    public String getError() { return error; }
    public String getAudioStatus() { return audioStatus; }
    public double getPosition() { return position; }
    public int getVolume() { return volume; }
    public boolean isMuted() { return muted; }
    public void setVolume(int volume) { this.volume = Math.max(0, Math.min(100, volume)); }
    public void setMuted(boolean muted) { this.muted = muted; }
    public synchronized void open(Media next) { stopSession(); media = next; frame = null; position = 0; start(0, false); }
    public synchronized void togglePause() {
        if (media == null) return;
        if (session == null) { start(state.equals("Ended") ? 0 : position, false); return; }
        session.setPaused(!session.paused);
        state = session.paused ? "Paused" : "Playing";
    }
    public synchronized void seek(double seconds) {
        if (media == null || media.duration <= 0 || !Double.isFinite(seconds)) return;
        boolean paused = !state.equals("Playing") && !state.equals("Opening");
        double target = Math.max(0, Math.min(Math.max(0, media.duration - 1.0 / FPS), seconds));
        stopSession(); start(target, paused);
    }
    public synchronized void stop() { stopSession(); position = 0; frame = null; error = ""; state = media == null ? "Idle" : "Stopped"; }
    private void start(double seconds, boolean paused) {
        error = ""; audioStatus = media.audio ? "Opening audio..." : "No audio track"; position = seconds;
        Session next = new Session(media, seconds, paused); session = next; state = paused ? "Paused" : "Opening";
        Thread worker = new Thread(next::run, "video-decoder"); worker.setDaemon(true); next.worker = worker; worker.start();
    }
    private void stopSession() { Session previous = session; session = null; if (previous != null) previous.close(); }
    @Override public synchronized void close() { stop(); }
    private static String executable(String name) { return System.getProperty("framework.video." + name, name); }
    public static Media probe(Path path) throws Exception {
        Path file = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(file) || !Files.isReadable(file)) throw new IOException("Choose a readable local video file");
        String output = capture(Arrays.asList(executable("ffprobe"), "-v", "error", "-show_entries",
                "stream=index,codec_type,width,height:stream_disposition=attached_pic:format=duration", "-of", "xml", file.toString()));
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)));
        int width = 0, height = 0, stream = -1; boolean audio = false;
        NodeList streams = document.getElementsByTagName("stream");
        for (int i = 0; i < streams.getLength(); i++) {
            Element item = (Element) streams.item(i); String type = item.getAttribute("codec_type");
            if (type.equals("audio")) audio = true;
            NodeList dispositions = item.getElementsByTagName("disposition");
            boolean cover = dispositions.getLength() > 0 && ((Element) dispositions.item(0)).getAttribute("attached_pic").equals("1");
            if (type.equals("video") && stream < 0 && !cover) {
                width = Integer.parseInt(item.getAttribute("width")); height = Integer.parseInt(item.getAttribute("height")); stream = Integer.parseInt(item.getAttribute("index"));
            }
        }
        if (stream < 0 || width < 1 || height < 1 || (long) width * height > 100000000) throw new IOException("No supported video track (maximum 100 megapixels)");
        double duration = 0; NodeList formats = document.getElementsByTagName("format");
        if (formats.getLength() > 0) try { duration = Double.parseDouble(((Element) formats.item(0)).getAttribute("duration")); } catch (NumberFormatException ignored) { }
        if (!Double.isFinite(duration) || duration < 0) duration = 0;
        return new Media(file, width, height, stream, duration, audio);
    }
    private static String capture(List<String> command) throws Exception {
        Process process;
        try { process = new ProcessBuilder(command).redirectErrorStream(true).start(); }
        catch (IOException e) { throw new IOException("FFprobe is unavailable; install FFmpeg or configure framework.video.ffprobe", e); }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Thread reader = new Thread(() -> {
            try (InputStream input = process.getInputStream()) {
                byte[] buffer = new byte[4096]; int count;
                while ((count = input.read(buffer)) >= 0) synchronized (bytes) { if (bytes.size() + count <= 65536) bytes.write(buffer, 0, count); }
            } catch (IOException ignored) { }
        }, "video-probe-output"); reader.setDaemon(true); reader.start();
        try {
            if (!process.waitFor(10, TimeUnit.SECONDS)) throw new IOException("Video inspection timed out");
            reader.join(500); String text;
            synchronized (bytes) { text = new String(bytes.toByteArray(), StandardCharsets.UTF_8); }
            if (process.exitValue() != 0) throw new IOException("Cannot inspect video: " + brief(text));
            return text;
        } finally { process.destroyForcibly(); }
    }
    private static String brief(String text) { String clean = text.trim().replace('\n', ' ').replace('\r', ' '); return clean.substring(0, Math.min(240, clean.length())); }
    public static BufferedImage readFrame(InputStream input) throws IOException {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_3BYTE_BGR);
        byte[] bytes = ((DataBufferByte) image.getRaster().getDataBuffer()).getData(); int offset = 0;
        while (offset < bytes.length) {
            int count = input.read(bytes, offset, bytes.length - offset);
            if (count < 0) { if (offset == 0) return null; throw new EOFException("Incomplete video frame"); }
            offset += count;
        }
        return image;
    }
    /** Scale signed little-endian PCM in place; volume/mute do not restart or desynchronize playback. */
    public static void scalePcm(byte[] bytes, int count, int volume) {
        double scale = Math.max(0, Math.min(100, volume)) / 100.0;
        for (int i = 0; i + 1 < count; i += 2) {
            int sample = (short) ((bytes[i] & 255) | bytes[i + 1] << 8), value = (int) (sample * scale);
            bytes[i] = (byte) value; bytes[i + 1] = (byte) (value >> 8);
        }
    }
    private final class Session implements AutoCloseable {
        final Media source; final double startPosition;
        final Object clock = new Object();
        final CountDownLatch audioReady = new CountDownLatch(1), started = new CountDownLatch(1), audioDone = new CountDownLatch(1);
        final AtomicLong progress = new AtomicLong(System.nanoTime());
        final StringBuilder errors = new StringBuilder();
        volatile boolean closed, paused;
        volatile Process video, audio;
        volatile SourceDataLine line;
        volatile Thread worker;
        long origin, pausedAt, pausedNanos;
        Session(Media source, double seconds, boolean paused) { this.source = source; startPosition = seconds; this.paused = paused; }
        boolean active() { return !closed && session == this; }
        List<String> inputCommand() {
            return new ArrayList<>(Arrays.asList(executable("ffmpeg"), "-hide_banner", "-loglevel", "error", "-nostdin", "-threads", "2", "-ss", String.format(Locale.ROOT, "%.6f", startPosition), "-i", source.file.toString()));
        }
        Process spawn(List<String> command, boolean sound) throws IOException {
            Process child;
            try { child = new ProcessBuilder(command).start(); }
            catch (IOException e) { throw new IOException("FFmpeg is unavailable; install it or configure framework.video.ffmpeg", e); }
            synchronized (clock) { if (!active()) { child.destroyForcibly(); throw new IOException("Playback cancelled"); } if (sound) audio = child; else video = child; }
            Thread reader = new Thread(() -> {
                try (InputStream input = child.getErrorStream()) {
                    byte[] bytes = new byte[512]; int count;
                    while ((count = input.read(bytes)) >= 0) synchronized (errors) { if (errors.length() < 4096) errors.append(new String(bytes, 0, count, StandardCharsets.UTF_8)); }
                } catch (IOException ignored) { }
            }, "video-decoder-errors"); reader.setDaemon(true); reader.start(); return child;
        }
        void setPaused(boolean value) {
            synchronized (clock) {
                if (paused == value) return;
                paused = value;
                if (origin != 0) { if (paused) pausedAt = System.nanoTime(); else pausedNanos += System.nanoTime() - pausedAt; }

                clock.notifyAll();
            }
        }
        boolean await(double elapsed) throws InterruptedException {
            synchronized (clock) {
                while (active()) {
                    if (paused) { clock.wait(100); continue; }
                    long remaining = origin + pausedNanos + (long) (elapsed * 1000000000L) - System.nanoTime();
                    if (remaining <= 0) return true;
                    clock.wait(Math.max(1, Math.min(40, remaining / 1000000)));
                }
                return false;
            }
        }
        void run() {
            Thread watchdog = new Thread(() -> {
                try {
                    while (active()) {
                        if (paused) progress.set(System.nanoTime());
                        if (System.nanoTime() - progress.get() > TimeUnit.SECONDS.toNanos(15)) {
                            synchronized (errors) { errors.append(" Decoder timed out."); }
                            Process child = video; if (child != null) child.destroyForcibly(); return;
                        }
                        Thread.sleep(200);
                    }
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }, "video-watchdog"); watchdog.setDaemon(true); watchdog.start();
            boolean finished = false;
            try {
                List<String> command = inputCommand();
                command.addAll(Arrays.asList("-map", "0:" + source.videoStream, "-an", "-sn", "-dn", "-vf",
                        "scale=640:360:force_original_aspect_ratio=decrease,pad=640:360:(ow-iw)/2:(oh-ih)/2,setsar=1,fps=24",
                        "-threads", "2", "-pix_fmt", "bgr24", "-f", "rawvideo", "pipe:1"));
                Process child = spawn(command, false);
                if (source.audio && !Boolean.getBoolean("framework.video.disableAudio")) {
                    Thread sound = new Thread(this::audioLoop, "video-audio"); sound.setDaemon(true); sound.start();
                } else { if (source.audio) audioStatus = "Audio disabled"; audioReady.countDown(); audioDone.countDown(); }
                try (InputStream input = child.getInputStream()) {
                    BufferedImage next = readFrame(input);
                    if (next == null) throw new IOException("No video frames decoded");
                    progress.set(System.nanoTime()); audioReady.await(2, TimeUnit.SECONDS);
                    synchronized (clock) { origin = System.nanoTime(); if (paused) pausedAt = origin; }
                    started.countDown();
                    if (active()) { frame = next; position = startPosition; state = paused ? "Paused" : "Playing"; }
                    long index = 1;
                    while (active() && (next = readFrame(input)) != null) {
                        progress.set(System.nanoTime());
                        if (!await((double) index / FPS)) return;
                        if (active()) { frame = next; position = startPosition + (double) index / FPS; } index++;
                    }
                    if (!active() || !await((double) index / FPS)) return;
                    if (!child.waitFor(2, TimeUnit.SECONDS) || child.exitValue() != 0) throw new IOException("Video decoder failed");
                    audioDone.await(2, TimeUnit.SECONDS); finished = true;
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            catch (Exception e) {
                synchronized (VideoEngine.this) {
                    if (active()) { String detail; synchronized (errors) { detail = brief(errors.toString()); } error = e.getMessage() + (detail.isEmpty() ? "" : ": " + detail); state = "Error"; }
                }
            } finally {
                synchronized (VideoEngine.this) {
                    if (active()) { if (finished) { state = "Ended"; if (source.duration > 0) position = source.duration; } session = null; }
                }
                close(); watchdog.interrupt();
            }
        }
        void audioLoop() {
            SourceDataLine output = null;
            try {
                AudioFormat format = new AudioFormat(48000, 16, 2, true, false);
                output = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, format)); output.open(format, 16384);
                synchronized (clock) { if (!active()) { output.close(); return; } line = output; }
                List<String> command = inputCommand(); command.addAll(Arrays.asList("-map", "0:a:0", "-vn", "-sn", "-dn", "-ac", "2", "-ar", "48000", "-f", "s16le", "pipe:1"));
                Process child = spawn(command, true);
                try (InputStream input = child.getInputStream()) {
                    byte[] bytes = new byte[3840]; int count = readPcm(input, bytes);
                    if (count < 0) throw new IOException("No audio samples decoded");
                    audioReady.countDown(); started.await();
                    while (active() && count > 0) {
                        // Only this thread may touch the audio device. PulseAudio implementations
                        // can deadlock when close/stop races a write on another thread.
                        while (active() && paused) {
                            output.stop();
                            synchronized (clock) { if (active() && paused) clock.wait(100); }
                        }
                        if (!active()) return;
                        if (!output.isRunning()) output.start();
                        scalePcm(bytes, count, muted ? 0 : volume);
                        int offset = 0;
                        while (active() && offset < count) { int written = output.write(bytes, offset, count - offset); if (written == 0) Thread.sleep(5); offset += written; }
                        if (active()) audioStatus = "Audio / " + (muted ? "muted" : volume + "%");
                        count = readPcm(input, bytes);
                    }
                    if (active()) output.drain();
                    if (active() && child.waitFor(1, TimeUnit.SECONDS) && child.exitValue() != 0) throw new IOException("Audio decoder failed");
                }
            } catch (Exception e) { if (active()) audioStatus = "Audio unavailable (video continues): " + brief(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()); }
            finally {
                audioReady.countDown(); audioDone.countDown();
                if (output != null) { output.stop(); output.close(); }
                Process child = audio; if (child != null) child.destroyForcibly();
            }
        }
        @Override public void close() {
            closed = true; started.countDown();
            synchronized (clock) { clock.notifyAll(); }
            Process v = video, a = audio; if (v != null) v.destroyForcibly(); if (a != null) a.destroyForcibly();
            // The audio owner releases its device in finally; never block the UI on a driver call.
            Thread current = worker; if (current != null && current != Thread.currentThread()) current.interrupt();
        }
    }
    private static int readPcm(InputStream input, byte[] bytes) throws IOException {
        int count = 0;
        while (count < bytes.length) { int read = input.read(bytes, count, bytes.length - count); if (read < 0) break; count += read; }
        if (count % 4 != 0) throw new EOFException("Incomplete stereo audio sample");
        return count == 0 ? -1 : count;
    }
}
