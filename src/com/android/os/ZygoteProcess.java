/*
 源码路径
 frameworks/base/core/java/android/os/ZygoteProcess.java
 */
package com.android.os;
import java.net.Socket;
import java.io.*;

import com.android.util.Log;
import com.android.net.LocalSocketAddress;
import com.android.os.Process.ProcessStartResult;
public class ZygoteProcess {
    private static final String LOG_TAG = "ZygoteProcess";

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

    public final Process.ProcessStartResult start(final String processClass ) {
        Log.d("start "+processClass);
        try {
            return startViaZygote(processClass);
        } catch (RuntimeException ex) {
            Log.d(
                    "Starting VM process through Zygote failed");
            throw new RuntimeException(
                    "Starting VM process through Zygote failed", ex);
        }
    }


    private  ProcessStartResult startViaZygote(final String processClass ){
         Log.d("startViaZygote "+processClass);

//         ArrayList<String> argsForZygote = new ArrayList<String>();

        // --runtime-args, --setuid=, --setgid=,
        // and --setgroups= must go first

//         if (extraArgs != null) {
//             for (String arg : extraArgs) {
//                 argsForZygote.add(arg);
//             }
//         }

        synchronized(mLock) {
            return zygoteSendArgsAndGetResult( processClass);
        }
    }

    private static ProcessStartResult zygoteSendArgsAndGetResult(String processClass){

            Log.d("zygoteSendArgsAndGetResult "+processClass);
            try {
                Log.d("尝试连接");
//                Socket s = new Socket("172.16.57.224", 502);
                Socket socket = new Socket("localhost", 10000);
                Log.d("连接成功！");
                //构建IO
                OutputStream outs = socket.getOutputStream();

                PrintWriter writer = new PrintWriter( outs );
                //向服务器端发送一条消息
                String input = processClass;

                writer.write( input );
                writer.flush();
                Log.d("ZygoteProcess 发送:"+input);
                socket.shutdownOutput();
                //读取服务器返回的消息


                InputStream ins = socket.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(ins));
                String msg = "";
                while ( (msg = reader.readLine()) != null){
                    Log.d("服务器端发来：" + msg);
                }

                reader.close();
                ins.close();
                writer.close();
                outs.close();

//                socket.close();

            } catch (Exception e) {
//                 socket.close();
                e.printStackTrace();
            }
            ProcessStartResult result = new ProcessStartResult();
            return result;
    }
}