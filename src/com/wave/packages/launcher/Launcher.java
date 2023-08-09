package com.wave.packages.launcher;

import com.wave.Activity;
import com.wave.Log;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class Launcher extends Activity {
    private	String  apps[]={"设置","图库","日历",
            "时钟","文件","通话",
            "短信","相机","视频"
    };

    public  String className[]={"com.packages.Settings","com.packages.Picture","com.wave.Calander",
            "com.wave.Clock","com.wave.Files","com.wave.Telephone",
            "com.wave.Message","com.wave.Camera","com.wave.Video"
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
