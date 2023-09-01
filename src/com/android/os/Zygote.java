/*
 *源码路径
 *frameworks/base/core/java/com/android/internal/os/Zygote.java
 */
package com.android.os;

import com.android.util.Log;

public final class Zygote{

    native private static int nativeForkSystemServer();
    native private static int nativeForkAndSpecialize();
// 创建系统进程
    public static int forkSystemServer(){
                Log.d("forkSystemServer \n");
                int pid = nativeForkSystemServer();
                return pid;
    }

// 创建一个进程
    public static int forkAndSpecialize() {
        // Resets nice priority for zygote process.
        int pid = nativeForkAndSpecialize();
        // Enable tracing as soon as possible for the child process.
        if (pid == 0) {

        }
        return pid;
    }
}