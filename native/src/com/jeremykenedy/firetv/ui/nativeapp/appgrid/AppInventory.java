package com.jeremykenedy.firetv.ui.nativeapp.appgrid;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import com.jeremykenedy.firetv.ui.preview.R;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AppInventory {
    private AppInventory() {}
    public static final class App {
        public final String label;
        public final Drawable artwork;
        public final ComponentName component;
        public final String launchCategory;
        public App(String label, Drawable artwork, ComponentName component) {
            this(label, artwork, component, Intent.CATEGORY_LEANBACK_LAUNCHER);
        }
        private App(String label, Drawable artwork, ComponentName component, String launchCategory) {
            this.label = label; this.artwork = artwork; this.component = component; this.launchCategory = launchCategory;
        }
        public Intent launchIntent() { return new Intent(Intent.ACTION_MAIN).addCategory(launchCategory).setComponent(component); }
        public String key() { return component.flattenToString(); }
    }
    /** Call on a worker thread: package metadata and artwork can require disk access. */
    public static List<App> load(Context context) {
        return load(context, false);
    }
    /** Explicit All apps discovery; ordinary Android launchers may require touch input. */
    public static List<App> loadAllLaunchable(Context context) { return load(context, true); }

    private static List<App> load(Context context, boolean includeOrdinary) {
        Map<String, App> found = new LinkedHashMap<>();
        discover(context, Intent.CATEGORY_LEANBACK_LAUNCHER, found);
        if (includeOrdinary && !Thread.currentThread().isInterrupted()) discover(context, Intent.CATEGORY_LAUNCHER, found);
        if (!Thread.currentThread().isInterrupted() && RetainedHomePreferences.hasApps(context)) {
            Map<String, App> launchable = new LinkedHashMap<>(found);
            if (!includeOrdinary) discover(context, Intent.CATEGORY_LAUNCHER, launchable);
            RetainedHomePreferences.apps(context, launchable.keySet());
        }
        List<App> apps = new ArrayList<>(found.values());
        if (includeOrdinary) Collections.sort(apps, new Comparator<App>() {
            @Override public int compare(App a, App b) {
                int label = a.label.compareToIgnoreCase(b.label);
                return label == 0 ? a.key().compareTo(b.key()) : label;
            }
        });
        return apps;
    }
    private static void discover(Context context, String category, Map<String, App> found) {
        PackageManager packages = context.getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo info : packages.queryIntentActivities(query, 0)) {
            if (Thread.currentThread().isInterrupted()) return;
            ActivityInfo candidate = info.activityInfo;
            if (candidate == null || context.getPackageName().equals(candidate.packageName)) continue;
            try {
                ComponentName component = new ComponentName(candidate.packageName, candidate.name);
                if (found.containsKey(component.flattenToString())) continue;
                ActivityInfo current = packages.getActivityInfo(component, 0);
                int enabled = packages.getComponentEnabledSetting(component);
                if (!current.exported || !current.enabled || current.applicationInfo == null
                        || !current.applicationInfo.enabled
                        || enabled != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && enabled != PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        || current.permission != null && !current.permission.isEmpty()
                                && context.checkSelfPermission(current.permission) != PackageManager.PERMISSION_GRANTED) continue;
                Drawable artwork = current.loadBanner(packages);
                if (artwork == null) artwork = info.loadIcon(packages);
                artwork = factoryArtwork(context, current.packageName, artwork);
                CharSequence label = info.loadLabel(packages);
                found.put(component.flattenToString(), new App(label == null || label.length() == 0
                        ? current.packageName : label.toString(), artwork, component, category));
            } catch (PackageManager.NameNotFoundException | RuntimeException removed) { /* Package changed during discovery. */ }
        }
    }
    private static Drawable factoryArtwork(Context context, String packageName, Drawable fallback) {
        int resource;
        if ("com.amazon.tv.livetv".equals(packageName)) resource = R.drawable.app_live_tv;
        else if ("com.amazon.tv.launcher".equals(packageName)) resource = R.drawable.app_factory_ui;
        else return fallback;
        try {
            Drawable artwork = context.getDrawable(resource);
            return artwork == null ? fallback : artwork;
        } catch (RuntimeException unavailable) {
            return fallback;
        }
    }

    public static List<String> keys(List<App> apps) {
        List<String> keys = new ArrayList<>();
        for (App app : apps) keys.add(app.key());
        return keys;
    }
}
