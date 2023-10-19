package com.android.pm;

import com.android.content.Context;
import com.android.util.Log;

import java.io.File;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class PackageManagerService implements PackageSender {
    private static final String TAG = "PackageManagerService. ";
    private final List<ScanPartition> mDirsToScanAsSystem;
    public static final String APK_FILE_EXTENSION = ".apk";

    @Override
    public void sendPackageBroadcast(String action, String pkg, String extras, int flags, String targetPkg, String finishedReceiver, int[] userIds, int[] instantUserIds, ArrayList<int[]> broadcastWhitelist) {

    }

    @Override
    public void sendPackageAddedForNewUsers(String packageName, boolean sendBootCompleted, boolean includeStopped, int appId, int[] userIds, int[] instantUserIds, int dataLoaderType) {

    }

    @Override
    public void notifyPackageAdded(String packageName, int uid) {

    }

    @Override
    public void notifyPackageChanged(String packageName, int uid) {

    }

    @Override
    public void notifyPackageRemoved(String packageName, int uid) {

    }

    public static PackageManagerService main(Context context, Installer installer,
                                             boolean factoryTest, boolean onlyCore) {
        Log.d(TAG, "main");
        PackageManagerService m = new PackageManagerService(onlyCore, factoryTest);

        return m;
    }

    public PackageManagerService( boolean onlyCore, boolean factoryTest) {

        mDirsToScanAsSystem = new ArrayList<>();
        for (int i = mDirsToScanAsSystem.size() - 1; i >= 0; i--) {

        }
    }

    public void scanDirTracedLI(File scanDir, final int parseFlags, int scanFlags,
                                long currentTime, PackageParser2 packageParser, ExecutorService executorService) {
        try {
            scanDirLI(scanDir, parseFlags, scanFlags, currentTime, packageParser, executorService);
        } finally {

        }
    }

    private void scanDirLI(File scanDir, int parseFlags, int scanFlags, long currentTime,
                           PackageParser2 packageParser, ExecutorService executorService) {
        final File[] files = scanDir.listFiles();
        ParallelPackageParser parallelPackageParser =
                new ParallelPackageParser(packageParser, executorService);
        int fileCount = 0;
        for (File file : files) {
            final boolean isPackage = (isApkFile(file) || file.isDirectory());
            if (!isPackage) {
                // Ignore entries which are not packages
                continue;
            }
            parallelPackageParser.submit(file, parseFlags);
            fileCount++;
        }
    }
    public static final boolean isApkFile(File file) {
        return isApkPath(file.getName());
    }
    public static boolean isApkPath(String path) {
        return path.endsWith(APK_FILE_EXTENSION);
    }
    public static class ScanPartition extends SystemPartition {

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

