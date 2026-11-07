package com.packages.settings;

import com.android.app.Activity;

public class MainActivity extends Activity {
    @Override protected void onCreate() {
        super.onCreate();
        getWindow().setLayout(640, 800);
        setContentView("res/layout/activity_main.xml");
    }
}