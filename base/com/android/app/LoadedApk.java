package com.android.app;

import com.android.os.ApplicationStartInfo;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;

/** Desktop analogue of Android LoadedApk; loads JVM bytecode, not Android DEX. */
public final class LoadedApk implements Closeable {
    private final File apkFile;
    private final URLClassLoader classLoader;

    public LoadedApk(ApplicationStartInfo info) {
        try {
            apkFile = new File(info.apkPath).getCanonicalFile();
            if (!apkFile.isFile()) throw new IOException("APK does not exist: " + apkFile);
            // Framework types must come from the parent to preserve Activity type identity.
            classLoader = new URLClassLoader(new URL[] {apkFile.toURI().toURL()},
                    Activity.class.getClassLoader());
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot load application " + info.packageName
                    + " from " + info.apkPath, e);
        }
    }

    public File getApkFile() { return apkFile; }
    public ClassLoader getClassLoader() { return classLoader; }

    @Override public void close() throws IOException { classLoader.close(); }
}
