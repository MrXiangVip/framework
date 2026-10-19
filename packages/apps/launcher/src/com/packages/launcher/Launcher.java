package com.packages.launcher;

import com.android.app.Activity;
import com.android.util.Log;
import com.android.view.View;

public class Launcher extends Activity {
    private static final String[] APPS = {
        "calendar", "camera", "clock", 
		"contact", "filebrowser",    "gallery", 
		"messager", "settings", "video"
    };
    @Override protected void onCreate() {
        super.onCreate();
        Log.d("Launcher.", "onCreate");
        setContentView("res/layout/activity_main.xml");
        for (String app : APPS) {
            View button = findViewById(app);
            if (button == null) throw new IllegalStateException("Missing Launcher button: " + app);
            final String activity = "com.packages." + app + ".MainActivity";
            button.setOnClickListener(view -> {
                try {
                    Log.d("Launcher.", "start " + activity);
                    startActivity(activity);
                } catch (RuntimeException e) {
                    Log.w("Cannot start " + activity + ": " + e.getMessage());
                }
            });
        }
    }
}
