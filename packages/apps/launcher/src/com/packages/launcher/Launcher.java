package com.packages.launcher;

import com.android.am.ActivityManagerClient;
import com.android.app.Activity;
import com.android.pm.LauncherActivityInfo;
import com.android.util.Log;
import com.android.view.LayoutInflater;
import com.android.view.ViewGroup;
import com.android.widget.GridLayout;
import com.android.widget.TextView;
import java.util.List;
import java.util.ArrayList;

public class Launcher extends Activity {
    private List<LauncherActivityInfo> apps;
    private DesktopPages desktop;
    private static final int PAGE_SIZE = 9;

    @Override protected void onCreate() {
        super.onCreate();
        getWindow().setLayout(640, 800);
        loadDesktop();
    }

    private void loadDesktop() {
        try {
            apps = ActivityManagerClient.getLauncherActivities();
            List<GridLayout> grids = new ArrayList<>();
            int count = Math.max(1, (apps.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            for (int page = 0; page < count; page++) {
                GridLayout grid = (GridLayout) LayoutInflater.from(this).inflate("res/layout/activity_main.xml", null, false);
                for (int i = page * PAGE_SIZE; i < Math.min(apps.size(), (page + 1) * PAGE_SIZE); i++) {
                    final LauncherActivityInfo info = apps.get(i);
                    AppIconView icon = new AppIconView(this, info);
                    icon.setOnClickListener(view -> {
                        try { startActivity(info.activityName); }
                        catch (RuntimeException e) { Log.w("Cannot start " + info.activityName + ": " + e.getMessage()); }
                    });
                    grid.addView(icon, new ViewGroup.LayoutParams(-2, -2));
                }
                if (apps.isEmpty()) {
                    TextView empty = new TextView(this); empty.setText("No applications");
                    grid.addView(empty, new ViewGroup.LayoutParams(-2, -2));
                }
                grids.add(grid);
            }
            desktop = new DesktopPages(this, grids);
            setContentView(desktop);
            Log.d("Launcher.", "Loaded " + apps.size() + " desktop activities");
        } catch (RuntimeException e) {
            Log.w("Cannot load desktop: " + e.getMessage());
            TextView retry = new TextView(this);
            retry.setText("Cannot load applications. Click to retry.");
            retry.setOnClickListener(view -> loadDesktop());
            setContentView(retry);
        }
    }

}
