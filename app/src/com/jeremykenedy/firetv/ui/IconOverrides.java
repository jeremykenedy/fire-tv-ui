package com.jeremykenedy.firetv.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Individual tiles can retain factory artwork while a theme styles other apps. */
public final class IconOverrides {
    public static final String FACTORY = "factory_icon_packages";

    private static boolean refreshPending;

    private IconOverrides() {}

    public static String pack(Context context, String key) {
        SharedPreferences prefs = DeviceSettings.prefs(context);
        if (prefs.getStringSet(FACTORY, Collections.<String>emptySet()).contains(key)) return null;
        String pack = prefs.getString("icon_pack_package", null);
        return pack == null || pack.trim().isEmpty() ? null : pack;
    }

    public static void markFactory(SharedPreferences prefs, String key) {
        Set<String> selected = new HashSet<>(prefs.getStringSet(FACTORY, Collections.<String>emptySet()));
        selected.add(key);
        prefs.edit().putStringSet(FACTORY, selected).apply();
    }

    static void save(SharedPreferences prefs, String key, boolean factory) throws Exception {
        if (key == null || key.isEmpty()) throw new IllegalArgumentException("Choose a tile first.");
        String previousApps = prefs.getString("app_visibility", null);
        Set<String> previousFactory = prefs.getStringSet(FACTORY, null);
        JSONObject apps = new JSONObject(previousApps == null ? "{}" : previousApps);
        if (apps.has(key)) {
            JSONObject app = apps.getJSONObject(key);
            app.remove("customIconUri");
        }
        Set<String> selected = new HashSet<>(prefs.getStringSet(FACTORY, Collections.<String>emptySet()));
        if (factory) selected.add(key); else selected.remove(key);
        SharedPreferences.Editor editor = prefs.edit().putString("app_visibility", apps.toString());
        if (selected.isEmpty()) editor.remove(FACTORY); else editor.putStringSet(FACTORY, selected);
        if (!editor.commit()) {
            SharedPreferences.Editor restore = prefs.edit();
            if (previousApps == null) restore.remove("app_visibility");
            else restore.putString("app_visibility", previousApps);
            if (previousFactory == null) restore.remove(FACTORY);
            else restore.putStringSet(FACTORY, previousFactory);
            restore.commit();
            throw new IllegalStateException("The icon choice could not be saved. Try again.");
        }
    }

    public static void restore(Context context, String key, boolean factory) {
        try {
            save(DeviceSettings.prefs(context), key, factory);
            refresh(context);
        } catch (Exception error) {
            new AlertDialog.Builder(context).setTitle("Icon could not be changed")
                    .setMessage(error.getMessage()).setPositiveButton("OK", null).show();
        }
    }

    public static boolean consumeRefresh() {
        boolean pending = refreshPending;
        refreshPending = false;
        return pending;
    }

    public static void refresh(Context context) {
        refreshPending = true;
        while (context instanceof ContextWrapper && !(context instanceof Activity)) {
            Context base = ((ContextWrapper) context).getBaseContext();
            if (base == context) return;
            context = base;
        }
        if (context instanceof Activity) ((Activity) context).recreate();
    }
}
