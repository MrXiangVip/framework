/*
 *源码路径
 * frameworks/base/core/java/com/android/internal/os/ZygoteInit.java
 */
package com.android;
import com.android.os.RuntimeInit;
import com.android.os.Zygote;
import com.android.os.ZygoteServer;
import com.android.util.Log;

import java.lang.Thread;
//import com.wave.OS;

public class ZygoteInit{

    public native void helloWorld(); // 注意，这个native方法就是调用C语言接口用的

    static void preload( ) {
          preloadClasses();
          preloadResources();
          preloadSharedLibraries();
    }
    private static void preloadClasses() {
            class PreloadClassThread extends Thread {
                        public void run() {
                                    Log.d("线程中预加载类资源...");

                        }
            }
            PreloadClassThread thread = new PreloadClassThread();
            thread.start();
            Log.d("预加载类资源...");

    }
    private static void preloadResources() {
            Log.d("预加载资源...");

    }
    private static void preloadSharedLibraries() {
            Log.d("预加载动态库...");
            System.loadLibrary("android"); //加载 libandroid.so  库
            Log.d("加载 libandroid.so   over...");

//             System.loadLibrary("compiler_rt");
//             System.loadLibrary("jnigraphics");
    }
    private static Runnable handleSystemServerProcess(){
        Log.d("handleSystemServerProcess...");
        return ZygoteInit.zygoteInit( );
    }

    private static Runnable forkSystemServer(String abiList, String socketName,
                ZygoteServer zygoteServer) {
        int pid;
        pid = Zygote.forkSystemServer();
        if( pid ==0 ){
              Log.d("system_server进程 pid " + pid );
              zygoteServer.closeServerSocket();
              Log.d("system_server进程关闭 socket");
              return handleSystemServerProcess();
        }
        Log.d("zygote 进程 pid " + pid+"\n");
        return null;
    }

	public static void main(String[] args){
		Log.d("Hello, ZygoteInit");
        Log.d(System.getProperty("java.library.path"));
		String abiList = null;
        String socketName = "zygote";


        final Runnable caller;
		ZygoteServer zygoteServer = new ZygoteServer();
		zygoteServer.registerServerSocketFromEnv(socketName);
        preload();
// 		进程重命名为 zygote
//        OS.Prctl("zygote");
        boolean startSystemServer = true;
        if (startSystemServer) {
                Runnable r = forkSystemServer(abiList, socketName, zygoteServer);
                // {@code r == null} in the parent (zygote) process, and {@code r != null} in the
                // child (system_server) process.
                if (r != null) {
                    Log.d("准备启动 system_server");
                    r.run();
                    return;
                }
        }

        caller = zygoteServer.runSelectLoop(abiList);
        if( caller != null ){
            caller.run();
        }else{
            Log.d("caller null");
        }
	}

    public static final Runnable zygoteInit( ) {
        Log.d("zygoteInit  这里启动 systemserver  ");
//        return new MethodAndArgsCaller("com.android.server.SystemServer","system_server");
        return RuntimeInit.applicationInit("com.android.server.SystemServer", null, null);
    }

    public static final Runnable childZygoteInit(String[] args) {
        Log.d("childZygoteInit  这里启动 activitythread "+args);
//        return new MethodAndArgsCaller("com.android.app.ActivityThread", className);

        return RuntimeInit.findStaticMain("com.android.app.ActivityThread", args, null);
    }
}