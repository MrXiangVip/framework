package com.android.pm;
import java.io.File;
import java.util.Objects;
public class SystemPartition {
    private final File folder;
    public SystemPartition(File folder) {
        this.folder = Objects.requireNonNull(folder, "folder");
    }
    public File getFolder() { return folder; }
    public File getAppFolder() { return new File(folder, "app"); }
    public File getPrivAppFolder() { return new File(folder, "priv-app"); }
}