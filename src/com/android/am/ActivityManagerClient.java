package com.android.am;

import java.io.*;
import java.net.*;

/** Desktop IPC proxy: application processes ask SystemServer to resolve and start an Activity. */
public final class ActivityManagerClient {
    private ActivityManagerClient() {}
    public static void startActivity(String activityName) {
        if (activityName == null || activityName.trim().isEmpty()) throw new IllegalArgumentException("Missing Activity name");
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(),
                    Integer.getInteger("framework.ams.port", 10001)), 3000);
            socket.setSoTimeout(10000);
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            output.writeInt(1);
            output.writeUTF(activityName);
            output.flush();
            DataInputStream input = new DataInputStream(socket.getInputStream());
            if (input.readInt() != 0) throw new IllegalStateException(input.readUTF());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot request SystemServer to start " + activityName, e);
        }
    }
}
