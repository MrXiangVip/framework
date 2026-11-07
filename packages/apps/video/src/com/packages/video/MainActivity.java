package com.packages.video;

import com.android.app.Activity;

public class MainActivity extends Activity {
    @Override protected void onCreate() {
        super.onCreate();
        getWindow().setLayout(1080, 720);
        setContentView("res/layout/activity_main.xml");
    }
}
