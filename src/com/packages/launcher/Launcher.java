package com.packages.launcher;

import com.android.app.Activity;
import com.android.util.Log;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import com.android.app.Activity;
import com.android.wm.XWindow;

public class Launcher extends Activity {
    String DIR="/home/xshx/IdeaProjects/untitled/res/layout";
    String FileName="activity_main.xml";

    private	String  apps[]={"设置","图库","日历",
            "时钟","文件","通话",
            "短信","相机","视频"
    };

    public  String className[]={"com.packages.Settings","com.packages.Picture","com.packages.Calander",
            "com.packages.Clock","com.packages.Files","com.packages.Telephone",
            "com.packages.os.Message","com.packages.Camera","com.packages.Video"
    };
    private String TAG="Launcher.";

    @Override
    protected void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        setContentView( new File(DIR, FileName) );
        Log.d(TAG,"启动模拟器");
        XWindow.createAWindow();
/*        JFrame frm = new JFrame("桌面");
        frm.setSize( 480, 640);
        GridLayout grid = new GridLayout(3, 3);
        frm.setLayout(grid);
        Log.d(TAG, "设置布局");
        JButton[] btn = new JButton[9];
        for(int i=0; i< btn.length; i++) {
            btn[i] = new JButton( apps[i]);
            final String name= className[i];
            btn[i].addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    System.out.println("按钮被点击了");
                    startActivity( name);
                }
            });
            frm.add(btn[i]);
        }
        frm.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frm.setVisible(true);
        Log.d(TAG,"显示模拟器");*/
    }
}
