package com.android.pm;

import com.android.content.Context;
import com.android.util.Log;
import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AOSP-style boot scanning for this desktop framework.
 * No Binder, signature verification, dex loading, persistent settings or multi-user installation.
 */
public class PackageManagerService implements PackageSender {
    private static final String TAG = "PackageManagerService. ";
    public static final String APK_FILE_EXTENSION = ".apk";
    public static final int SCAN_AS_SYSTEM = 1;
    public static final int SCAN_AS_PRIVILEGED = 1 << 1;
    public static final String ACTION_MAIN = "android.intent.action.MAIN";
    public static final String CATEGORY_HOME = "android.intent.category.HOME";
    public static final String CATEGORY_LAUNCHER = "android.intent.category.LAUNCHER";

    private final Object mLock = new Object();
    private final Object mScanLock = new Object();
    private final Map<String, PackageSetting> mPackages = new TreeMap<>();
    private final Map<File, String> mScanErrors = new LinkedHashMap<>();
    private final List<ScanPartition> mDirsToScanAsSystem = new ArrayList<>();
    private final boolean mOnlyCore;
    private final File mRoot;

    public static PackageManagerService main(Context context, Installer installer,
                                              boolean factoryTest, boolean onlyCore) {
        return new PackageManagerService(onlyCore, factoryTest);
    }

    public PackageManagerService(boolean onlyCore, boolean factoryTest) {
        this(defaultRoot(), onlyCore, factoryTest);
    }

    /** Explicit root makes boot scanning independent of the host's /system and /data. */
    public PackageManagerService(File root, boolean onlyCore, boolean factoryTest) {
        mRoot = Objects.requireNonNull(root, "root").getAbsoluteFile();
        mOnlyCore = onlyCore;
        // factoryTest is retained for source compatibility; factory mode has no model here.
        for (String partition : Arrays.asList("system", "vendor", "odm", "oem", "product", "system_ext")) {
            mDirsToScanAsSystem.add(new ScanPartition(new File(mRoot, partition)));
        }
        scanPackages();
    }

    private static File defaultRoot() {
        String configured = System.getProperty("framework.root");
        if (configured != null && !configured.trim().isEmpty()) return new File(configured);
        File cwd = new File(System.getProperty("user.dir")).getAbsoluteFile();
        // build.sh launches from src; IDEs normally launch from the repository root.
        return new File(cwd, "com/android").isDirectory() ? cwd.getParentFile() : cwd;
    }

    private void scanPackages() {
        ExecutorService executor = Executors.newFixedThreadPool(
                Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors())));
        PackageParser2 parser = new PackageParser2();
        try {
            long now = System.currentTimeMillis();
            for (ScanPartition partition : mDirsToScanAsSystem) {
                scanDirTracedLI(partition.getPrivAppFolder(), 0,
                        SCAN_AS_SYSTEM | SCAN_AS_PRIVILEGED, now, parser, executor);
                scanDirTracedLI(partition.getAppFolder(), 0, SCAN_AS_SYSTEM, now, parser, executor);
            }
            // Standalone builds install into out/system/{priv-app,app}.
            ScanPartition output = new ScanPartition(new File(mRoot, "out/system"));
            scanDirTracedLI(output.getPrivAppFolder(), 0,
                    SCAN_AS_SYSTEM | SCAN_AS_PRIVILEGED, now, parser, executor);
            scanDirTracedLI(output.getAppFolder(), 0, SCAN_AS_SYSTEM, now, parser, executor);
            // Retain legacy source packages after installed APKs.
            scanDirTracedLI(new File(mRoot, "base/com/packages"), 0, SCAN_AS_SYSTEM,
                    now, parser, executor);
            if (!mOnlyCore) {
                scanDirTracedLI(new File(mRoot, "data/app"), 0, 0, now, parser, executor);
            }
        } finally {
            executor.shutdownNow();
        }
        Log.i(TAG, "Boot scan complete: " + getInstalledPackages().size() + " packages");
    }

    public void scanDirTracedLI(File scanDir, int parseFlags, int scanFlags,
                               long currentTime, PackageParser2 packageParser,
                               ExecutorService executorService) {
        synchronized (mScanLock) {
            scanDirLI(scanDir, parseFlags, scanFlags, packageParser, executorService);
        }
    }

    private void scanDirLI(File scanDir, int parseFlags, int scanFlags,
                           PackageParser2 packageParser, ExecutorService executorService) {
        Objects.requireNonNull(scanDir, "scanDir");
        if (!scanDir.exists()) return;
        File[] files = scanDir.listFiles();
        if (files == null) {
            recordError(scanDir, "Cannot list package directory");
            return;
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        ParallelPackageParser parallel = new ParallelPackageParser(packageParser, executorService);
        int count = 0;
        for (File file : files) {
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("Package scan interrupted");
            }
            String name = file.getName();
            // Skip staging/incomplete directories and unrelated source directories.
            if (name.startsWith(".") || name.startsWith("vmdl") || name.startsWith("smdl")) continue;
            boolean directoryPackage = file.isDirectory()
                    && (new File(file, "AndroidManifest.xml").isFile()
                        || containsApk(file));
            if (!(file.isFile() && isApkFile(file)) && !directoryPackage) continue;
            parallel.submit(file, parseFlags);
            count++;
        }
        List<ParallelPackageParser.ParseResult> results = new ArrayList<>();
        for (int i = 0; i < count; i++) results.add(parallel.take());
        // Completion order must not decide which duplicate package wins.
        results.sort(Comparator.comparing(result -> result.scanFile.getName()));
        for (ParallelPackageParser.ParseResult result : results) {
            if (result.throwable != null) {
                recordError(result.scanFile, result.throwable.getMessage());
            } else if (!mOnlyCore || result.parsedPackage.isCoreApp()) {
                registerPackage(result.parsedPackage, scanFlags);
            }
        }
    }

    private void registerPackage(ParsedPackage pkg, int scanFlags) {
        synchronized (mLock) {
            for (PackageSetting setting : mPackages.values()) {
                if (setting.pkg.getCodePath().equals(pkg.getCodePath())
                        && !setting.pkg.getPackageName().equals(pkg.getPackageName())) {
                    recordError(pkg.getCodePath(), "Package name changed at an already registered path");
                    return;
                }
            }
            PackageSetting previous = mPackages.get(pkg.getPackageName());
            if (previous != null && !previous.pkg.getCodePath().equals(pkg.getCodePath())) {
                recordError(pkg.getCodePath(), "Duplicate package " + pkg.getPackageName()
                        + "; keeping " + previous.pkg.getCodePath());
                return;
            }
            // A rescan of the same location replaces all metadata atomically.
            mPackages.put(pkg.getPackageName(), new PackageSetting(pkg, scanFlags));
            mScanErrors.remove(pkg.getCodePath());
        }
        Log.i(TAG, "Registered " + pkg.getPackageName() + " from " + pkg.getCodePath());
    }

    private void recordError(File file, String message) {
        File key = file.getAbsoluteFile();
        try { key = file.getCanonicalFile(); } catch (java.io.IOException ignored) { }
        synchronized (mLock) { mScanErrors.put(key, message); }
        Log.w(TAG, "Skipping " + file + ": " + message);
    }

    /** Find the installed package declaring an enabled Activity, without guessing its package name. */
    public ParsedPackage getPackageForActivity(String activityName) {
        synchronized (mLock) {
            for (PackageSetting setting : mPackages.values()) {
                for (ParsedPackage.ActivityInfo activity : setting.pkg.getActivities()) {
                    if (activity.isEnabled() && activity.getName().equals(activityName)) {
                        return setting.pkg;
                    }
                }
            }
            return null;
        }
    }

    public ParsedPackage getPackageInfo(String packageName) {
        synchronized (mLock) {
            PackageSetting setting = mPackages.get(packageName);
            return setting == null ? null : setting.pkg;
        }
    }

    public List<ParsedPackage> getInstalledPackages() {
        synchronized (mLock) {
            List<ParsedPackage> result = new ArrayList<>();
            for (PackageSetting setting : mPackages.values()) result.add(setting.pkg);
            return Collections.unmodifiableList(result);
        }
    }

    public boolean isSystemPackage(String packageName) {
        synchronized (mLock) {
            PackageSetting setting = mPackages.get(packageName);
            return setting != null && (setting.scanFlags & SCAN_AS_SYSTEM) != 0;
        }
    }

    public boolean isPrivilegedPackage(String packageName) {
        synchronized (mLock) {
            PackageSetting setting = mPackages.get(packageName);
            return setting != null && (setting.scanFlags & SCAN_AS_PRIVILEGED) != 0;
        }
    }

    /** Data-free action/category lookup; enabled and exported activities only. */
    public List<ParsedPackage.ActivityInfo> queryIntentActivities(String action, String category) {
        Objects.requireNonNull(action, "action");
        synchronized (mLock) {
            List<ParsedPackage.ActivityInfo> result = new ArrayList<>();
            for (PackageSetting setting : mPackages.values()) {
                for (ParsedPackage.ActivityInfo activity : setting.pkg.getActivities()) {
                    if (activity.matches(action, category)) result.add(activity);
                }
            }
            result.sort(Comparator.comparing(ParsedPackage.ActivityInfo::getName));
            return Collections.unmodifiableList(result);
        }
    }

    /** Desktop entries are declared components, never inferred from directory or class names. */
    public List<LauncherActivityInfo> getLauncherActivities() {
        synchronized (mLock) {
            List<LauncherActivityInfo> result = new ArrayList<>();
            for (PackageSetting setting : mPackages.values()) {
                ParsedPackage pkg = setting.pkg;
                if (pkg.getBaseApkPath() == null) continue;
                for (ParsedPackage.ActivityInfo activity : pkg.getActivities()) {
                    if (activity.matches(ACTION_MAIN, CATEGORY_LAUNCHER)
                            && !activity.matches(ACTION_MAIN, CATEGORY_HOME)) {
                        result.add(new LauncherActivityInfo(pkg.getPackageName(), activity.getName(),
                                activity.getLabel(), activity.getIconPath(), pkg.getBaseApkPath().getAbsolutePath()));
                    }
                }
            }
            result.sort(Comparator.comparing((LauncherActivityInfo entry) -> entry.packageName)
                    .thenComparing(entry -> entry.activityName));
            return Collections.unmodifiableList(result);
        }
    }

    public ParsedPackage.ActivityInfo resolveHomeActivity() {
        List<ParsedPackage.ActivityInfo> homes = queryIntentActivities(ACTION_MAIN, CATEGORY_HOME);
        return homes.isEmpty() ? null : homes.get(0);
    }

    public Map<File, String> getScanErrors() {
        synchronized (mLock) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(mScanErrors));
        }
    }

    private static boolean containsApk(File directory) {
        File[] apks = directory.listFiles(file -> file.isFile() && isApkFile(file));
        return apks != null && apks.length > 0;
    }

    public static boolean isApkFile(File file) {
        return file != null && isApkPath(file.getName());
    }
    public static boolean isApkPath(String path) {
        return path != null && path.endsWith(APK_FILE_EXTENSION);
    }
    public static class ScanPartition extends SystemPartition {
        public ScanPartition(File folder) { super(folder); }
    }
    private static final class PackageSetting {
        final ParsedPackage pkg;
        final int scanFlags;
        PackageSetting(ParsedPackage pkg, int scanFlags) {
            this.pkg = pkg;
            this.scanFlags = scanFlags;
        }
    }

    // Broadcast delivery depends on a future AMS receiver registry.
    @Override
    public void sendPackageBroadcast(String action, String pkg, String extras, int flags,
            String targetPkg, String finishedReceiver, int[] userIds, int[] instantUserIds,
            ArrayList<int[]> broadcastWhitelist) {
        throw new UnsupportedOperationException("Package broadcasts are not implemented");
    }
    @Override
    public void sendPackageAddedForNewUsers(String packageName, boolean sendBootCompleted,
            boolean includeStopped, int appId, int[] userIds, int[] instantUserIds, int dataLoaderType) {
        throw new UnsupportedOperationException("Multi-user package broadcasts are not implemented");
    }
    @Override public void notifyPackageAdded(String packageName, int uid) {
        throw new UnsupportedOperationException("Package observers are not implemented");
    }
    @Override public void notifyPackageChanged(String packageName, int uid) {
        throw new UnsupportedOperationException("Package observers are not implemented");
    }
    @Override public void notifyPackageRemoved(String packageName, int uid) {
        throw new UnsupportedOperationException("Package observers are not implemented");
    }
}
interface PackageSender {
    /**
     * @param userIds User IDs where the action occurred on a full application
     * @param instantUserIds User IDs where the action occurred on an instant application
     */
    void sendPackageBroadcast(final String action, final String pkg,
                              final String extras, final int flags, final String targetPkg,
                              final String finishedReceiver, final int[] userIds, int[] instantUserIds,
                              ArrayList<int[]> broadcastWhitelist);
    void sendPackageAddedForNewUsers(String packageName, boolean sendBootCompleted,
                                     boolean includeStopped, int appId, int[] userIds, int[] instantUserIds,
                                     int dataLoaderType);
    void notifyPackageAdded(String packageName, int uid);
    void notifyPackageChanged(String packageName, int uid);
    void notifyPackageRemoved(String packageName, int uid);
}

