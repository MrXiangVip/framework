package com.android.app;

import com.android.am.*;
import com.android.os.ZygoteServer;
import com.android.pm.PackageManagerService;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Real AMS -> Zygote -> child JVM -> APK -> multiple X11 windows. */
public final class MultiActivityIntegrationTest {
    private static Path journal;

    private static List<String[]> events() throws IOException {
        List<String[]> result = new ArrayList<>();
        if (Files.exists(journal)) {
            for (String line : Files.readAllLines(journal)) {
                String[] fields = line.split(" ");
                if (fields.length == 5) result.add(fields);
            }
        }
        return result;
    }

    private static long count(String event) throws IOException {
        return events().stream().filter(fields -> fields[3].equals(event)).count();
    }

    private interface Check { boolean ready() throws Exception; }
    private static void await(Check check) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!check.ready()) {
            if (System.nanoTime() > deadline) throw new AssertionError("Timed out: " + Files.readAllLines(journal));
            Thread.sleep(25);
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    public static void main(String[] args) throws Exception {
        journal = Paths.get(args[1]);
        Files.write(journal, new byte[0]);
        System.setProperty("framework.multi.journal", journal.toString());
        int amsPort = freePort(), zygotePort;
        do { zygotePort = freePort(); } while (zygotePort == amsPort);
        System.setProperty("framework.ams.port", "" + amsPort);
        System.setProperty("framework.zygote.port", "" + zygotePort);
        ZygoteServer zygote = new ZygoteServer();
        zygote.registerServerSocketFromEnv("test");
        Thread zygoteThread = new Thread(() -> zygote.runSelectLoop(""), "test-zygote");
        zygoteThread.setDaemon(true);
        zygoteThread.start();
        try (ActivityManagerService ams = new ActivityManagerService()) {
            ams.setPackageManagerService(new PackageManagerService(new File(args[0]), false, false));
            ams.systemReady();

            // Burst requests overlap initial registration; none may create a second package process.
            ExecutorService callers = Executors.newFixedThreadPool(6);
            try {
                List<Future<?>> requests = new ArrayList<>();
                for (int i = 0; i < 6; i++) requests.add(callers.submit(
                        () -> ActivityManagerClient.startActivity("fixture.multi.First")));
                for (Future<?> request : requests) request.get(10, TimeUnit.SECONDS);
            } finally { callers.shutdownNow(); }
            await(() -> count("resume") == 8); // Home + six Firsts + Second from onCreate.
            String pid = events().get(0)[0];
            Set<String> instances = new HashSet<>();
            for (String[] event : events()) {
                if (!event[0].equals(pid) || !event[4].equals("main"))
                    throw new AssertionError("Activity ran outside shared process/main thread");
                if (event[3].equals("create")) instances.add(event[1]);
            }
            if (instances.size() != 8) throw new AssertionError("Instances or shared ClassLoader not distinct");
            System.out.println("PASS: concurrent starts and onCreate startActivity share one PID, ClassLoader and main thread");

            ActivityManagerClient.startActivity("fixture.multi.Broken");
            ActivityManagerClient.startActivity("fixture.multi.Second");
            await(() -> count("resume") == 9 && count("destroy") == 1);
            if (!events().get(events().size() - 1)[0].equals(pid)) throw new AssertionError("Failure killed process");
            System.out.println("PASS: failed Activity is cleaned up and sibling Activities keep running");

            int result = new ProcessBuilder(args[2], pid, "0", "0", "0", "0").inheritIO().start().waitFor();
            if (result != 0) throw new AssertionError("Native X11 click failed: " + result);
            await(() -> count("destroy") == 2);
            ActivityManagerClient.startActivity("fixture.multi.Second");
            await(() -> count("resume") == 10);
            if (!events().get(events().size() - 1)[0].equals(pid)) throw new AssertionError("Closing one window killed siblings");
            System.out.println("PASS: closing one native window destroys only that instance; process remains reusable");

            // Close every remaining native window, then reuse the idle application process.
            for (int i = 0; i < 9; i++) {
                if (new ProcessBuilder(args[2], pid, "0", "0", "0", "0").start().waitFor() != 0)
                    throw new AssertionError("Cannot close remaining test window");
                final int expected = i + 3;
                await(() -> count("destroy") == expected);
            }
            ActivityManagerClient.startActivity("fixture.multi.Second");
            await(() -> count("resume") == 11);
            if (!events().get(events().size() - 1)[0].equals(pid)) throw new AssertionError("Idle process not reused");
            System.out.println("PASS: all windows can close without unloading the APK; a later launch reuses the idle JVM");

            // Terminate only the child created by this isolated test.
            if (new ProcessBuilder("kill", "-KILL", pid).start().waitFor() != 0)
                throw new AssertionError("Cannot terminate test application");
            // Wait for the manager to observe the death before issuing the next request.
            await(() -> !Files.exists(Paths.get("/proc", pid)));
            ActivityManagerClient.startActivity("fixture.multi.Second");
            await(() -> count("resume") == 13); // New process id=1 also starts its Second.
            String restartedPid = events().get(events().size() - 1)[0];
            if (restartedPid.equals(pid)) throw new AssertionError("Dead process reused");
            System.out.println("PASS: process death removes the registration and subsequent launch creates a new JVM");
        } finally {
            zygote.closeServerSocket();
            zygoteThread.join(5000);
        }
        System.out.println("MultiActivityIntegrationTest passed");
    }
}
