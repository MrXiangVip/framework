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

public class Launcher extends Activity {
    private List<LauncherActivityInfo> apps;
    private DesktopPages desktop;
    private int page;
    private static final int PAGE_SIZE = 9;

    @Override protected void onCreate() {
        super.onCreate();
        loadDesktop();
    }

    private void loadDesktop() {
        try {
            apps = ActivityManagerClient.getLauncherActivities();
            GridLayout grid = (GridLayout) LayoutInflater.from(this).inflate("res/layout/activity_main.xml", null, false);
            desktop = new DesktopPages(this, grid, apps.size() > PAGE_SIZE);
            desktop.previous.setOnClickListener(view -> showPage(page - 1));
            desktop.next.setOnClickListener(view -> showPage(page + 1));
            setContentView(desktop);
            showPage(0);
            Log.d("Launcher.", "Loaded " + apps.size() + " desktop activities");
        } catch (RuntimeException e) {
            Log.w("Cannot load desktop: " + e.getMessage());
            TextView retry = new TextView(this);
            retry.setText("Cannot load applications. Click to retry.");
            retry.setOnClickListener(view -> loadDesktop());
            setContentView(retry);
        }
    }

    private void showPage(int requested) {
        int pages = Math.max(1, (apps.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int target = Math.max(0, Math.min(requested, pages - 1));
        if (page == target && desktop.grid.getChildCount() > 0) return;
        page = target;
        desktop.grid.removeAllViews();
        for (int i = page * PAGE_SIZE; i < Math.min(apps.size(), (page + 1) * PAGE_SIZE); i++) {
            final LauncherActivityInfo info = apps.get(i);
            AppIconView icon = new AppIconView(this, info);
            icon.setOnClickListener(view -> {
                try { startActivity(info.activityName); }
                catch (RuntimeException e) { Log.w("Cannot start " + info.activityName + ": " + e.getMessage()); }
            });
            desktop.grid.addView(icon, new ViewGroup.LayoutParams(-2, -2));
        }
        if (apps.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No applications");
            desktop.grid.addView(empty, new ViewGroup.LayoutParams(-2, -2));
        }
        desktop.indicator.setText((page + 1) + " / " + pages);
        desktop.invalidate();
    }
}