package com.android.packages.launcher;

import com.android.app.Activity;
import com.android.util.Log;

public class Launcher extends Activity {
    private	String  apps[]={"设置","图库","日历",
            "时钟","文件","通话",
            "短信","相机","视频"
    };

    public  String className[]={"com.packages.Settings","com.packages.Picture","com.android.Calander",
            "com.android.Clock","com.android.Files","com.android.Telephone",
            "com.android.os.Message","com.android.Camera","com.android.Video"
    };
    private String TAG="Launcher.";

    @Override
    protected void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        setContentView( 1 );
        Log.d(TAG,"启动模拟器");
    }
}
