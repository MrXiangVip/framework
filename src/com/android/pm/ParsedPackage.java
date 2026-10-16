package com.android.pm;

import java.io.File;
import java.util.*;

/** Immutable metadata. APK dex loading is outside this JVM demo. */
public final class ParsedPackage implements ParsingPackage {
    private final String packageName;
    private final long versionCode;
    private final String versionName;
    private final File codePath;
    private final File baseApkPath;
    private final boolean coreApp;
    private final List<ActivityInfo> activities;

    ParsedPackage(String packageName, long versionCode, String versionName, File codePath,
                  File baseApkPath, boolean coreApp, List<ActivityInfo> activities) {
        this.packageName = packageName;
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.codePath = codePath;
        this.baseApkPath = baseApkPath;
        this.coreApp = coreApp;
        this.activities = Collections.unmodifiableList(new ArrayList<>(activities));
    }
    public String getPackageName() { return packageName; }
    public long getVersionCode() { return versionCode; }
    public String getVersionName() { return versionName; }
    public File getCodePath() { return codePath; }
    /** Actual APK file, or null for a legacy source-only manifest. */
    public File getBaseApkPath() { return baseApkPath; }
    public boolean isCoreApp() { return coreApp; }
    public List<ActivityInfo> getActivities() { return activities; }

    public static final class ActivityInfo {
        private final String name;
        private final boolean enabled;
        private final boolean exported;
        private final List<IntentInfo> filters;
        ActivityInfo(String name, boolean enabled, boolean exported, List<IntentInfo> filters) {
            this.name = name;
            this.enabled = enabled;
            this.exported = exported;
            this.filters = Collections.unmodifiableList(new ArrayList<>(filters));
        }
        public String getName() { return name; }
        public boolean isEnabled() { return enabled; }
        public boolean isExported() { return exported; }
        public List<IntentInfo> getIntentFilters() { return filters; }
        public boolean matches(String action, String category) {
            if (!enabled || !exported) return false;
            for (IntentInfo filter : filters) {
                if (filter.actions.contains(action)
                        && (category == null || filter.categories.contains(category))) return true;
            }
            return false;
        }
    }
    public static final class IntentInfo {
        private final Set<String> actions;
        private final Set<String> categories;
        IntentInfo(Set<String> actions, Set<String> categories) {
            this.actions = Collections.unmodifiableSet(new LinkedHashSet<>(actions));
            this.categories = Collections.unmodifiableSet(new LinkedHashSet<>(categories));
        }
        public Set<String> getActions() { return actions; }
        public Set<String> getCategories() { return categories; }
    }
}