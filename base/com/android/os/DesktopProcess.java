package com.android.os;

import com.android.util.Log;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Fresh JVM processes for desktop HotSpot, which cannot resume Java safely after raw fork. */
public final class DesktopProcess implements AutoCloseable {
    private final Set<java.lang.Process> children = ConcurrentHashMap.newKeySet();
    private boolean closed;

    public DesktopProcess() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::close, "desktop-process-cleanup"));
    }

    public synchronized int start(String mainClass, String... args) throws IOException {
        if (closed) throw new IOException("Process launcher is closed");
        List<String> command = new ArrayList<>();
        File bin = new File(System.getProperty("java.home"), "bin");
        command.add(new File(bin, System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").getPath());
        // Framework only. The APK is an ActivityThread argument, never a -cp entry.
        command.add("-Djava.library.path=" + System.getProperty("java.library.path"));
        command.add("-Djava.awt.headless=true");
        for (String name : System.getProperties().stringPropertyNames()) {
            if (name.startsWith("framework.")) command.add("-D" + name + "=" + System.getProperty(name));
        }
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(mainClass);
        Collections.addAll(command, args);
        ProcessBuilder builder = new ProcessBuilder(command).inheritIO();
        // JAVA_TOOL_OPTIONS may specify the parent's classpath and override -cp in a fresh launcher.
        // Required properties are supplied explicitly above, so don't replay injected JVM options.
        builder.environment().remove("JAVA_TOOL_OPTIONS");
        builder.environment().remove("_JAVA_OPTIONS");
        builder.environment().remove("JDK_JAVA_OPTIONS");
        java.lang.Process child = builder.start();
        final int pid;
        try { pid = pidOf(child); }
        catch (ReflectiveOperationException | RuntimeException | IOException e) {
            child.destroyForcibly();
            throw new IOException("Cannot obtain PID of " + mainClass, e);
        }
        children.add(child);
        Thread reaper = new Thread(() -> {
            try {
                int code = child.waitFor();
                Log.d("Desktop process exited pid=" + pid + " main=" + mainClass + " status=" + code);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { children.remove(child); }
        }, "process-reaper-" + pid);
        reaper.setDaemon(true);
        reaper.start();
        Log.d("Desktop process started pid=" + pid + " main=" + mainClass);
        return pid;
    }

    private static int pidOf(java.lang.Process child) throws ReflectiveOperationException, IOException {
        long pid;
        try {
            Method method = java.lang.Process.class.getMethod("pid");
            pid = ((Number) method.invoke(child)).longValue();
        } catch (NoSuchMethodException java8) {
            // OpenJDK 8 UNIXProcess has no public PID API. Linux is the native backend's target.
            Field field = child.getClass().getDeclaredField("pid");
            field.setAccessible(true);
            pid = ((Number) field.get(child)).longValue();
        }
        if (pid <= 0 || pid > Integer.MAX_VALUE) throw new IOException("Invalid child PID: " + pid);
        return (int) pid;
    }

    @Override public synchronized void close() {
        closed = true;
        for (java.lang.Process child : children) child.destroy();
        children.clear();
    }
}
