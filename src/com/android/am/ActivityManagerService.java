package com.android.am;

import com.android.util.Log;
import com.android.os.Process;
import com.android.os.Process.*;
public class ActivityManagerService {

    private static ActivityManagerService  ams;

    public static ActivityManagerService getInstance( ){
        if (ams == null) {
            ams = new ActivityManagerService( );
        }
        return ams;
    }
    public void systemReady() {

        startHomeActivityLocked( "systemReady");

    }

    boolean startHomeActivityLocked( String reason) {
        Log.d("startHomeActivityLocked\n");
        if( true ){
            startActivity("com.android.packages.launcher.Launcher");
        }
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

    private ProcessStartResult startProcess(String callingPackageClass ){
        Log.d("ActivityManagerService startProcess "+callingPackageClass);
        return Process.start(callingPackageClass);
    }

}
