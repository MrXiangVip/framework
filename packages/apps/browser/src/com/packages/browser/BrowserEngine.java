package com.packages.browser;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Owns one isolated CEF host. Native code is never loaded into the framework JVM. */
public final class BrowserEngine implements AutoCloseable {
    public static final class Snapshot {
        public final String url, title, error;
        public final boolean loading, back, forward, ready;
        Snapshot(String url, String title, String error, boolean loading, boolean back, boolean forward, boolean ready) {
            this.url = url; this.title = title; this.error = error; this.loading = loading;
            this.back = back; this.forward = forward; this.ready = ready;
        }
    }
    private volatile Snapshot state = new Snapshot("about:blank", "New page", "", false, false, false, false);
    private volatile BufferedImage frame;
    private volatile boolean closed;
    private volatile Runnable listener = () -> {};
    private final BlockingQueue<String> commands = new ArrayBlockingQueue<>(256);
    private final Process process;
    private final Path session;
    private int width = 800, height = 600;
    private Thread commandWriter;

    public BrowserEngine() throws IOException { this(runtime()); }
    BrowserEngine(Path runtime) throws IOException {
        if (!Files.isRegularFile(runtime.resolve("host.jar"))) throw new IOException("Browser engine is not installed. Run the Browser setup script.");
        Path profiles = Paths.get(System.getProperty("framework.browser.profile",
                Paths.get(System.getProperty("user.home"), ".local", "share", "framework-browser").toString()));
        Files.createDirectories(profiles);
        session = Files.createTempDirectory(profiles, "session-");
        // A private profile avoids two independently launched windows corrupting one cache.
        List<String> command = new ArrayList<>();
        String java = System.getProperty("framework.browser.java");
        if (java == null && Files.isRegularFile(runtime.resolve("java.path")))
            java = new String(Files.readAllBytes(runtime.resolve("java.path")), StandardCharsets.UTF_8).trim();
        command.add(java == null ? Paths.get(System.getProperty("java.home"), "bin", "java").toString() : java);
        command.add("-Djava.awt.headless=false"); command.add("-Djava.library.path=" + runtime.resolve("bin/lib/linux64"));
        command.add("-cp"); command.add(runtime.resolve("host.jar") + File.pathSeparator + runtime.resolve("bin/*"));
        command.add("com.packages.browser.host.CefHost"); command.add(runtime.toString()); command.add(session.toString());
        ProcessBuilder builder = new ProcessBuilder(command).directory(runtime.toFile());
        for (String name : Arrays.asList("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS")) builder.environment().remove(name);
        String old = builder.environment().getOrDefault("LD_LIBRARY_PATH", "");
        builder.environment().put("LD_LIBRARY_PATH", runtime.resolve("bin/lib/linux64") + (old.isEmpty() ? "" : ":" + old));
        // Private Fontconfig adds bundled CJK fonts without changing desktop/system settings.
        builder.environment().put("FONTCONFIG_FILE", runtime.resolve("fonts.conf").toAbsolutePath().toString());
        builder.redirectError(session.resolve("host.log").toFile());
        process = builder.start();
        // A stuck native startup reports a recoverable error instead of waiting forever.
        daemon("browser-startup-watchdog", () -> {
            try { if (!process.waitFor(30, TimeUnit.SECONDS) && !closed && !state.ready) {
                fail("Browser engine startup timed out. Open Home to retry."); process.destroy();
            } } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        commandWriter = new Thread(this::writeCommands, "browser-commands"); commandWriter.setDaemon(true); commandWriter.start();
        daemon("browser-events", this::readEvents);
        daemon("browser-exit", () -> {
            try { int code = process.waitFor(); if (!closed) fail("Browser engine stopped (" + code + "). Open Home to retry. Log: " + session.resolve("host.log")); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
    }
    private static void daemon(String name, Runnable task) { Thread t = new Thread(task, name); t.setDaemon(true); t.start(); }
    public void setListener(Runnable listener) { this.listener = Objects.requireNonNull(listener); }
    public Snapshot snapshot() { return state; }
    public BufferedImage frame() { return frame; }
    public boolean isAlive() { return !closed && process.isAlive(); }
    public void navigate(String address) { send("NAV\t" + BrowserProtocol.encode(BrowserController.address(address))); }
    public void back() { send("BACK"); }
    public void forward() { send("FORWARD"); }
    public void reload() { send("RELOAD"); }
    public void stop() { send("STOP"); }
    public void focus(boolean focus) { send("FOCUS\t" + (focus ? 1 : 0)); }
    public synchronized void resize(int w, int h) {
        w = Math.max(1, Math.min(4096, w)); h = Math.max(1, Math.min(2160, h));
        if (w == width && h == height) return;
        width = w; height = h; send("SIZE\t" + w + "\t" + h);
    }
    public void mouse(int action, int x, int y, int button, int modifiers) { send("MOUSE\t" + action + "\t" + x + "\t" + y + "\t" + button + "\t" + modifiers); }
    public void wheel(int x, int y, int delta, int modifiers) { send("WHEEL\t" + x + "\t" + y + "\t" + delta + "\t" + modifiers); }
    public void key(int type, int key, int modifiers) { send("KEY\t" + type + "\t" + key + "\t" + modifiers); }
    private void send(String command) {
        if (closed || !process.isAlive()) return;
        // Bound pending input so an unresponsive renderer cannot grow the UI heap.
        if (!commands.offer(command)) fail("Browser is busy. Please wait, or reopen Home.");
    }
    private void writeCommands() {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(process.getOutputStream()))) {
            while (!closed) BrowserProtocol.write(out, BrowserProtocol.COMMAND, commands.take().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) { if (!closed) fail("Could not communicate with browser engine"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
    private void readEvents() {
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(process.getInputStream()))) {
            while (!closed) {
                BrowserProtocol.Message message = BrowserProtocol.read(input);
                if (message.type == BrowserProtocol.FRAME) acceptFrame(message.bytes);
                else if (message.type == BrowserProtocol.STATE) {
                    String[] values = message.text().split("\t", -1);
                    if (values.length != 7) throw new IOException("Invalid navigation event");
                    state = new Snapshot(BrowserProtocol.decode(values[0]), BrowserProtocol.decode(values[1]), BrowserProtocol.decode(values[2]),
                            values[3].equals("1"), values[4].equals("1"), values[5].equals("1"), values[6].equals("1"));
                } else if (message.type == BrowserProtocol.ERROR) fail(message.text());
                listener.run();
            }
        } catch (IOException | IllegalArgumentException e) { if (!closed) fail("Browser connection closed. Open Home to retry."); }
    }
    private void acceptFrame(byte[] bytes) throws IOException {
        if (bytes.length < 8) throw new IOException("Truncated browser frame");
        ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        int w = data.getInt(), h = data.getInt(); BrowserProtocol.validateSize(w, h);
        if (bytes.length != 8 + w * h * 4) throw new IOException("Invalid browser frame length");
        // Publish complete immutable frames; UI never races a native pixel buffer.
        BufferedImage next = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] pixels = ((DataBufferInt) next.getRaster().getDataBuffer()).getData();
        data.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(pixels);
        frame = next;
    }
    private void fail(String error) {
        Snapshot s = state; state = new Snapshot(s.url, s.title, error, false, s.back, s.forward, s.ready); listener.run();
    }
    public static Path runtime() {
        String configured = System.getProperty("framework.browser.runtime", System.getenv("FRAMEWORK_BROWSER_RUNTIME"));
        if (configured != null && !configured.isEmpty()) return Paths.get(configured).toAbsolutePath().normalize();
        Path cwd = Paths.get("").toAbsolutePath();
        for (Path root = cwd; root != null; root = root.getParent()) {
            Path candidate = root.resolve("out/system/lib/browser");
            if (Files.isRegularFile(candidate.resolve("host.jar"))) return candidate;
        }
        return cwd.resolve("out/system/lib/browser");
    }
    @Override public void close() {
        if (closed) return;
        // Closing the pipe is also the host's parent-death signal.
        closed = true; commands.clear(); commandWriter.interrupt();
        // A blocked pipe must never stall the UI or the independent termination watchdog.
        daemon("browser-pipe-close", () -> { try { process.getOutputStream().close(); } catch (IOException ignored) {} });
        daemon("browser-cleanup", () -> {
            try {
                if (!process.waitFor(3, TimeUnit.SECONDS)) { process.destroy(); if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly(); }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); process.destroyForcibly(); }
        });
    }
}
