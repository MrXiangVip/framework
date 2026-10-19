package com.android.am;

import com.android.util.Log;
import java.io.*;
import java.net.*;

/** Small desktop substitute for the ActivityManager Binder entry point. */
public final class ActivityManagerSocketServer implements AutoCloseable {
    private final ServerSocket server;
    private final ActivityManagerService service;
    public ActivityManagerSocketServer(ActivityManagerService service) throws IOException {
        this(service, Integer.getInteger("framework.ams.port", 10001));
    }
    public ActivityManagerSocketServer(ActivityManagerService service, int port) throws IOException {
        this.service = java.util.Objects.requireNonNull(service);
        server = new ServerSocket(port, 50, InetAddress.getLoopbackAddress());
        Thread worker = new Thread(this::run, "activity-manager-requests");
        worker.setDaemon(true);
        worker.start();
        Log.d("ActivityManager listening port=" + server.getLocalPort());
    }
    public int getPort() { return server.getLocalPort(); }
    private void run() {
        while (!server.isClosed()) {
            try (Socket client = server.accept()) {
                client.setSoTimeout(5000);
                DataOutputStream output = new DataOutputStream(client.getOutputStream());
                try {
                    DataInputStream input = new DataInputStream(client.getInputStream());
                    int operation = input.readInt();
                    if (operation == 1) {
                        service.startActivity(input.readUTF());
                        output.writeInt(0);
                    } else if (operation == 2) {
                        java.util.List<com.android.pm.LauncherActivityInfo> entries = service.getLauncherActivities();
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
    @Override public void close() throws IOException { server.close(); }
}
