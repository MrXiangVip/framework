/*
 源码路径
 frameworks/base/core/java/android/os/ZygoteProcess.java
 */
package com.android.os;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import com.android.net.LocalSocketAddress;
import com.android.os.Process.ProcessStartResult;

/** Sends structured application metadata to Zygote. No application classes load here. */
public class ZygoteProcess {
    private final Object mLock = new Object();
    /**
     * The name of the socket used to communicate with the primary zygote.
     */
    private final LocalSocketAddress mSocket;

    /**
     * The name of the secondary (alternate ABI) zygote socket.
     */
    private final LocalSocketAddress mSecondarySocket;
    public ZygoteProcess(String primarySocket, String secondarySocket) {
        this(new LocalSocketAddress(primarySocket, LocalSocketAddress.Namespace.RESERVED),
                new LocalSocketAddress(secondarySocket, LocalSocketAddress.Namespace.RESERVED));
    }

    public ZygoteProcess(LocalSocketAddress primarySocket, LocalSocketAddress secondarySocket) {
        mSocket = primarySocket;
        mSecondarySocket = secondarySocket;
    }

    public final ProcessStartResult start(ApplicationStartInfo info) {
        synchronized (mLock) {
            try (Socket socket = new Socket("localhost", 10000)) {
                DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                info.writeTo(output);
                output.flush();
                DataInputStream input = new DataInputStream(socket.getInputStream());
                int pid = input.readInt();
                if (pid <= 0) throw new IOException("Zygote fork failed: " + pid);
                ProcessStartResult result = new ProcessStartResult();
                result.pid = pid;
                return result;
            } catch (IOException e) {
                throw new IllegalStateException("Starting " + info.activityName
                        + " from " + info.apkPath + " through Zygote failed", e);
            }
        }
    }
}