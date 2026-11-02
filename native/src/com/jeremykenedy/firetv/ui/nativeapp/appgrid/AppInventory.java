package com.jeremykenedy.firetv.ui.nativeapp.appgrid;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class AppInventory {
    private AppInventory() {}
    public static final class App {
        public final String label;
        public final Drawable artwork;
        public final ComponentName component;
        public App(String label, Drawable artwork, ComponentName component) {
            this.label = label; this.artwork = artwork; this.component = component;
        }
        public String key() { return component.flattenToString(); }
    }
    /** Call on a worker thread: package metadata and artwork can require disk access. */
    public static List<App> load(Context context) {
        PackageManager packages = context.getPackageManager();
        List<App> apps = new ArrayList<>();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo info : packages.queryIntentActivities(query, 0)) {
            if (Thread.currentThread().isInterrupted()) return apps;
            if (context.getPackageName().equals(info.activityInfo.packageName)) continue;
            try {
                Drawable artwork = info.activityInfo.loadBanner(packages);
                if (artwork == null) artwork = info.loadIcon(packages);
                apps.add(new App(info.loadLabel(packages).toString(), artwork,
                        new ComponentName(info.activityInfo.packageName, info.activityInfo.name)));
            } catch (RuntimeException removed) { /* Package changed during the query. */ }
        }
        Collections.sort(apps, new Comparator<App>() {
            @Override public int compare(App a, App b) {
                int label = a.label.compareToIgnoreCase(b.label);
                return label == 0 ? a.key().compareTo(b.key()) : label;
            }
        });
        return apps;
    }
    public static List<String> keys(List<App> apps) {
        List<String> keys = new ArrayList<>();
        for (App app : apps) keys.add(app.key());
        return keys;
    }
}
