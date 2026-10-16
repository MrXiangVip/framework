package com.android.am;

import com.android.util.Log;
import com.android.pm.PackageManagerService;
import com.android.pm.ParsedPackage;
import com.android.os.Process;
import com.android.os.ApplicationStartInfo;
import com.android.os.Process.*;
import com.android.wm.ActivityTaskManagerService;

public class ActivityManagerService {

    private static ActivityManagerService  ams;
    public ActivityTaskManagerService mActivityTaskManager;

    public static ActivityManagerService getInstance( ){
        if (ams == null) {
            ams = new ActivityManagerService( );
        }
        return ams;
    }
    private PackageManagerService mPackageManagerService;

    public void setPackageManagerService(PackageManagerService packageManagerService) {
        mPackageManagerService = java.util.Objects.requireNonNull(packageManagerService);
    }

    public void systemReady() {

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

}
