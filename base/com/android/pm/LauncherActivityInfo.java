package com.android.pm;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/** Launchable component metadata sent from SystemServer to the desktop process. */
public final class LauncherActivityInfo {
    public final String packageName, activityName, label, iconPath, apkPath;

    public LauncherActivityInfo(String packageName, String activityName, String label,
                                String iconPath, String apkPath) {
        this.packageName = packageName;
        this.activityName = activityName;
        this.label = label;
        this.iconPath = iconPath;
        this.apkPath = apkPath;
    }

    public void writeTo(DataOutput output) throws IOException {
        output.writeUTF(packageName);
        output.writeUTF(activityName);
        output.writeUTF(label);
        output.writeUTF(iconPath);
        output.writeUTF(apkPath);
    }

    public static LauncherActivityInfo readFrom(DataInput input) throws IOException {
        return new LauncherActivityInfo(input.readUTF(), input.readUTF(), input.readUTF(),
                input.readUTF(), input.readUTF());
    }
}
