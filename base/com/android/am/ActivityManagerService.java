package com.android.am;

import com.android.util.Log;
import com.android.pm.PackageManagerService;
import com.android.pm.ParsedPackage;
import com.android.os.Process;
import com.android.os.ApplicationStartInfo;
import com.android.os.Process.*;
import com.android.wm.ActivityTaskManagerService;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class ActivityManagerService  implements AutoCloseable{

    private static ActivityManagerService  ams;
    public ActivityTaskManagerService mActivityTaskManager;
    private final ServerSocket server;
    private Thread worker =null;
    public static ActivityManagerService getInstance( ) throws IOException {
        if (ams == null) {
            ams = new ActivityManagerService( );
        }
        return ams;
    }
    private PackageManagerService mPackageManagerService;


    public ActivityManagerService( ) throws IOException {
        int port=Integer.getInteger("framework.ams.port", 10001);
        server = new ServerSocket(port, 50, InetAddress.getLoopbackAddress());

    }
    private void run() {
        while (!server.isClosed()) {
            try (Socket client = server.accept()) {
                client.setSoTimeout(5000);
                DataOutputStream output = new DataOutputStream(client.getOutputStream());
                try {
                    DataInputStream input = new DataInputStream(client.getInputStream());
                    int operation = input.readInt();
                    if (operation == 1) {
                        startActivity(input.readUTF());
                        output.writeInt(0);
                    } else if (operation == 2) {
                        java.util.List<com.android.pm.LauncherActivityInfo> entries = getLauncherActivities();
                        output.writeInt(0);
                        output.writeInt(entries.size());
                        for (com.android.pm.LauncherActivityInfo entry : entries) entry.writeTo(output);
                    } else throw new IOException("Unsupported ActivityManager protocol");
                } catch (IOException | RuntimeException e) {
                    output.writeInt(1);
                    String message = String.valueOf(e.getMessage());
                    output.writeUTF(message.substring(0, Math.min(message.length(), 4000)));
                }
                output.flush();
            } catch (IOException e) {
                if (!server.isClosed()) Log.w("ActivityManager request failed: " + e);
            }
        }
    }
    public void setPackageManagerService(PackageManagerService packageManagerService) {
        mPackageManagerService = java.util.Objects.requireNonNull(packageManagerService);
    }


    public void systemReady() {
        if( worker == null ){
            worker = new Thread(this::run, "activity-manager-requests");
            worker.setDaemon(true);
            worker.start();
        }
        startHomeActivityLocked( "systemReady");

    }

    boolean startHomeActivityLocked( String reason) {
        Log.d("startHomeActivityLocked\n");
        if (mPackageManagerService == null) {
            throw new IllegalStateException("PackageManagerService must be initialized before AMS");
        }
        ParsedPackage.ActivityInfo home = mPackageManagerService.resolveHomeActivity();
        if (home == null) {
            Log.w("No enabled HOME activity found");
            return false;
        }
        startActivity(home.getName());
        return true;
    }
    public final int startActivity( String callingPackageClass ){
        Log.d("ActivityManagerService startActivity "+callingPackageClass);
        startProcessLocked( callingPackageClass );
        return 0;
    }

    private boolean startProcessLocked(String callingPackageClass ){
        Log.d("ActivityManagerService startProcessLocked "+callingPackageClass);
        startProcess( callingPackageClass );
        return true;
    }

    public java.util.List<com.android.pm.LauncherActivityInfo> getLauncherActivities() {
        if (mPackageManagerService == null) throw new IllegalStateException("PackageManagerService is not initialized");
        return mPackageManagerService.getLauncherActivities();
    }

    public ApplicationStartInfo getApplicationStartInfo(String activityName) {
        if (mPackageManagerService == null) {
            throw new IllegalStateException("PackageManagerService must be initialized before AMS");
        }
        ParsedPackage pkg = mPackageManagerService.getPackageForActivity(activityName);
        if (pkg == null) throw new IllegalArgumentException("No installed enabled Activity: " + activityName);
        if (pkg.getBaseApkPath() == null) {
            throw new IllegalStateException("Activity has no packaged APK: " + activityName);
        }
        return new ApplicationStartInfo(pkg.getPackageName(), activityName,
                pkg.getBaseApkPath().getAbsolutePath());
    }

    private ProcessStartResult startProcess(String callingPackageClass ){
        Log.d("ActivityManagerService startProcess "+callingPackageClass);
        return Process.start(getApplicationStartInfo(callingPackageClass));
    }

    @Override
    public void close() throws Exception {
        server.close();
    }
}
