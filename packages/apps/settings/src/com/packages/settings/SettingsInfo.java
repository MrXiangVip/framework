package com.packages.settings;

import com.android.pm.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.lang.management.ManagementFactory;
import java.util.*;

/** Read-only inspection of the actual desktop runtime and packaged APK directory. */
public final class SettingsInfo {
    public static final class App {
        public final String name, packageName, version, activity;
        public final Path apk;
        public final long bytes;
        App(String name, ParsedPackage parsed, Path apk) throws IOException {
            this.name = name; this.apk = apk; bytes = Files.size(apk);
            packageName = parsed.getPackageName(); version = parsed.getVersionName();
            String launch = "";
            for (ParsedPackage.ActivityInfo info : parsed.getActivities()) {
                if (info.matches("android.intent.action.MAIN", "android.intent.category.LAUNCHER")) { launch = info.getName(); break; }
                if (info.matches("android.intent.action.MAIN", "android.intent.category.HOME")) launch = info.getName();
            }
            activity = launch;
        }
    }
    public static Path appDirectory() {
        String configured = System.getProperty("framework.settings.apps");
        if (configured != null) return Paths.get(configured).toAbsolutePath().normalize();
        String root = System.getProperty("framework.root");
        if (root != null) return Paths.get(root, "out", "system", "app").toAbsolutePath().normalize();
        // Locate this APK rather than depending on the process working directory.
        try {
            Path apk = Paths.get(SettingsInfo.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(apk)) return apk.getParent().getParent();
        } catch (Exception ignored) { }
        return Paths.get("out", "system", "app").toAbsolutePath().normalize();
    }
    public static List<App> apps(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) throw new IOException("Application directory is missing");
        List<App> apps = new ArrayList<>();
        PackageParser2 parser = new PackageParser2();
        try (DirectoryStream<Path> children = Files.newDirectoryStream(directory)) {
            for (Path child : children) {
                Path apk = Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS) ? child.resolve(child.getFileName() + ".apk") : child;
                if (!apk.getFileName().toString().endsWith(".apk") || !Files.isRegularFile(apk, LinkOption.NOFOLLOW_LINKS)) continue;
                try {
                    ParsedPackage parsed = parser.parsePackage(apk.toFile(), 0, false);
                    if (parsed != null) apps.add(new App(child.getFileName().toString().replaceFirst("\\.apk$", ""), parsed, apk));
                } catch (RuntimeException ignored) { /* One invalid APK must not hide the other applications. */ }
            }
        }
        apps.sort(Comparator.comparing(app -> app.name));
        return apps;
    }
    public static List<String[]> device(Path storage) throws IOException {
        List<String[]> result = new ArrayList<>();
        result.add(new String[]{"Operating system", System.getProperty("os.name") + " " + System.getProperty("os.version"), System.getProperty("os.arch")});
        result.add(new String[]{"Java runtime", System.getProperty("java.version"), System.getProperty("java.vm.name")});
        Runtime runtime = Runtime.getRuntime();
        result.add(new String[]{"Java memory", format(runtime.totalMemory() - runtime.freeMemory()) + " used / " + format(runtime.maxMemory()) + " max", runtime.availableProcessors() + " available processors"});
        Path existing = storage.toAbsolutePath();
        while (existing != null && !Files.exists(existing)) existing = existing.getParent();
        if (existing != null) {
            try {
                FileStore disk = Files.getFileStore(existing);
                result.add(new String[]{"Storage", format(disk.getUsableSpace()) + " available / " + format(disk.getTotalSpace()), "Settings data filesystem"});
            } catch (IOException e) {
                result.add(new String[]{"Storage", "Unavailable", "Host filesystem does not expose capacity"});
            }
        }
        result.add(new String[]{"System time zone", TimeZone.getDefault().getID(), "Uses the host clock and time zone"});
        result.add(new String[]{"Application uptime", ManagementFactory.getRuntimeMXBean().getUptime() / 1000 + " seconds", "Time since this Java process started"});
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface nic = interfaces.nextElement();
                if (!nic.isUp() || nic.isLoopback()) continue;
                Enumeration<InetAddress> addresses = nic.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!address.isLoopbackAddress()) result.add(new String[]{"Network / " + nic.getName(), address.getHostAddress(), "Host interface address"});
                }
            }
        } catch (SocketException e) { result.add(new String[]{"Network", "Unavailable", "Could not read host interfaces"}); }
        return result;
    }
    public static String format(long bytes) {
        if (bytes >= 1073741824L) return String.format(Locale.ROOT, "%.1f GiB", bytes / 1073741824.0);
        if (bytes >= 1048576L) return String.format(Locale.ROOT, "%.1f MiB", bytes / 1048576.0);
        return String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0);
    }
}
