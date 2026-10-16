package com.android.os;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;

/** Application launch metadata transported to the child process, not a runtime classpath. */
public final class ApplicationStartInfo {
    private static final int PROTOCOL_VERSION = 1;
    public final String packageName;
    public final String activityName;
    public final String apkPath;

    public ApplicationStartInfo(String packageName, String activityName, String apkPath) {
        this.packageName = requireValue(packageName, "packageName");
        this.activityName = requireValue(activityName, "activityName");
        this.apkPath = requireValue(apkPath, "apkPath");
    }

    private static String requireValue(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.trim().isEmpty()) throw new IllegalArgumentException("Missing " + name);
        return value;
    }

    public String[] toArgs() {
        return new String[] {packageName, activityName, apkPath};
    }

    public static ApplicationStartInfo fromArgs(String[] args) {
        if (args == null || args.length != 3) {
            throw new IllegalArgumentException("Expected packageName, activityName and apkPath");
        }
        return new ApplicationStartInfo(args[0], args[1], args[2]);
    }

    public void writeTo(DataOutput output) throws IOException {
        output.writeInt(PROTOCOL_VERSION);
        output.writeUTF(packageName);
        output.writeUTF(activityName);
        output.writeUTF(apkPath);
    }

    public static ApplicationStartInfo readFrom(DataInput input) throws IOException {
        if (input.readInt() != PROTOCOL_VERSION) throw new IOException("Unsupported launch protocol");
        try {
            return new ApplicationStartInfo(input.readUTF(), input.readUTF(), input.readUTF());
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid application launch metadata", e);
        }
    }
}
