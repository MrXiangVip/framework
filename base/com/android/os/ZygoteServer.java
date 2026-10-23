/*
 *源码路径
 *frameworks/base/core/java/com/android/internal/os/ZygoteServer.java
 */
package com.android.os;

import com.android.ZygoteInit;
import com.android.util.Log;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ZygoteServer {
    private ServerSocket mServerSocket;

    public void registerServerSocketFromEnv(String socketName) {
        Log.d("registerServerSocketFromEnv " + socketName);
        if (mServerSocket == null) {
            int port = Integer.getInteger("framework.zygote.port", 10000);
            try {
                mServerSocket = new ServerSocket(port);
                // Confirm binding completed; the preceding log only marks the start of registration.
                Log.d("Zygote socket listening on port " + mServerSocket.getLocalPort());
            } catch (IOException e) {
                throw new IllegalStateException("Cannot open Zygote socket on port " + port
                        + "; check whether another framework instance is already running", e);
            }
        }
    }

    public Runnable runSelectLoop(String abiList) {
        if (!"fork".equals(System.getProperty("framework.process.mode", "spawn"))) {
            return runSpawnLoop();
        }
        return runForkLoop();
    }

    private Runnable runSpawnLoop() {
        try (DesktopProcess processes = new DesktopProcess()) {
            while (true) {
                try (Socket client = mServerSocket.accept()) {
                    DataOutputStream output = new DataOutputStream(client.getOutputStream());
                    try {
                        ApplicationStartInfo info = ApplicationStartInfo.readFrom(
                                new DataInputStream(client.getInputStream()));
                        Log.d("Zygote spawn " + info.activityName + " apk=" + info.apkPath);
                        int pid = processes.start("com.android.app.ActivityThread", info.toArgs());
                        output.writeInt(pid);
                    } catch (IOException | IllegalArgumentException e) {
                        Log.w("Zygote spawn failed: " + e);
                        output.writeInt(-1);
                    }
                    output.flush();
                } catch (IOException e) {
                    if (mServerSocket == null || mServerSocket.isClosed()) return null;
                    Log.w("Zygote request failed: " + e);
                }
            }
        }
    }

    private Runnable runForkLoop() {
        while (true) {
            try (Socket client = mServerSocket.accept()) {
                ApplicationStartInfo info = ApplicationStartInfo.readFrom(
                        new DataInputStream(client.getInputStream()));
                Log.d("Zygote launch " + info.activityName + " apk=" + info.apkPath);
                int pid = Zygote.forkAndSpecialize();
                if (pid == 0) {
                    closeServerSocket();
                    return handleChildProc(info);
                }
                // Only the parent writes the reply. Both processes close their client socket.
                DataOutputStream output = new DataOutputStream(client.getOutputStream());
                output.writeInt(pid);
                output.flush();
            } catch (IOException | IllegalArgumentException e) {
                // A malformed or disconnected client must not terminate the Zygote server.
                Log.w("Zygote request failed: " + e);
            }
        }
    }

    public void closeServerSocket() {
        if (mServerSocket != null) {
            try {
                mServerSocket.close();
            } catch (IOException e) {
                throw new IllegalStateException("Cannot close Zygote socket", e);
            } finally {
                mServerSocket = null;
            }
        }
    }

    private Runnable handleChildProc(ApplicationStartInfo info) {
        return ZygoteInit.childZygoteInit(info.toArgs());
    }
}