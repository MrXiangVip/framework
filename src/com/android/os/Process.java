/* xshx add
*  path: frameworks/base/core/java/android/os/Process.java
*/
package com.android.os;

import com.android.util.Log;

public class Process {
    private static final String TAG = "Process";
    public static final String ZYGOTE_SOCKET = "zygote";
    public static final String SECONDARY_ZYGOTE_SOCKET = "zygote_secondary";

    public static final class ProcessStartResult {
        /**
         * The PID of the newly started process.
         * Always >= 0.  (If the start failed, an exception will have been thrown instead.)
         */
        public int pid;

        /**
         * True if the process was started with a wrapper attached.
         */
        public boolean usingWrapper;
    }

    public static final ZygoteProcess zygoteProcess =
            new ZygoteProcess(ZYGOTE_SOCKET, SECONDARY_ZYGOTE_SOCKET);

    public static final ProcessStartResult start(final String processClass) {
        Log.d(TAG,"ProcessStartResult start "+processClass);

        return zygoteProcess.start( processClass );
    }
}