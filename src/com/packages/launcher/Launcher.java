package com.packages.launcher;

import com.android.app.Activity;
import com.android.util.Log;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import com.android.app.Activity;
public class Launcher extends Activity {
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
        setContentView( 1 );
        Log.d(TAG,"启动模拟器");
        JFrame frm = new JFrame("桌面");
        frm.setSize( 480, 640);
        // 创建一个网格布局管理器实例grid，表格为3*3
        GridLayout grid = new GridLayout(3, 3);
        // 设置frm的页面布局为grid
        frm.setLayout(grid);
        Log.d(TAG, "设置布局");
        // 定义一个JButton的数组b，数组长度为9
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
            // 将b[i]添加进frm中
            frm.add(btn[i]);
        }
//         默认情况下，该值被设置为 hide_on_close。更改此属性的值将导致激发属性更改事件，其属性名称为 "defaultcloseoperation"。
        frm.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frm.setVisible(true);
        System.out.println("显示模拟器");
    }
}
