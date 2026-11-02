package com.jeremykenedy.firetv.ui.nativeapp.statusbar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.widget.Toast;

/** Explicit choices update the retained launcher keys without writing absent defaults. */
public final class StatusBarSettings {
    private StatusBarSettings() {}

    public static void show(final Activity activity) {
        final SharedPreferences prefs = activity.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE);
        final String[] labels = {
                "Status bar: " + mode(prefs),
                "Position: " + text(prefs, "status_bar_position"),
                "Hide unless focused: " + bool(prefs, "hide_status_bar_unless_focused"),
                "Tint: " + bool(prefs, "tinted_status_bar"),
                "Clock format: " + clockFormat(prefs)
        };
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Status bar")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface ignored, int which) {
                        if (which == 0) choose(activity, prefs, "Status bar", "minimal_status_bar",
                                new String[]{"Off", "Full", "Minimal"}, new Object[]{null, false, true});
                        else if (which == 1) choose(activity, prefs, "Position", "status_bar_position",
                                new String[]{"Default", "Left", "Right"}, new Object[]{null, "Left", "Right"});
                        else if (which == 2) choose(activity, prefs, "Hide unless focused", "hide_status_bar_unless_focused",
                                new String[]{"Default", "Off", "On"}, new Object[]{null, false, true});
                        else if (which == 3) choose(activity, prefs, "Tint", "tinted_status_bar",
                                new String[]{"Default", "Off", "On"}, new Object[]{null, false, true});
                        else choose(activity, prefs, "Clock format", "use_24h_clock",
                                new String[]{"Follow TV", "12-hour", "24-hour"}, new Object[]{null, false, true});
                    }
                }).setNegativeButton("Done", null).show();
        dialog.getListView().requestFocus();
    }

    private static void choose(final Activity activity, final SharedPreferences prefs, String title,
            final String key, String[] labels, final Object[] values) {
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(title)
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface ignored, int which) {
                        try { save(prefs, key, values[which]); }
                        catch (RuntimeException failure) {
                            Toast.makeText(activity, "Status bar setting could not be saved. Try again.", Toast.LENGTH_LONG).show();
                        }
                        show(activity);
                    }
                }).setNegativeButton("Back", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface ignored, int which) { show(activity); }
                }).show();
        dialog.getListView().requestFocus();
    }

    private static void save(SharedPreferences prefs, String key, Object value) {
        Object previous = prefs.getAll().get(key);
        boolean existed = prefs.contains(key);
        if (existed && !(previous instanceof Boolean) && !(previous instanceof String))
            throw new IllegalStateException("Stored setting has an unexpected type.");
        SharedPreferences.Editor edit = prefs.edit();
        put(edit, key, value);
        if (edit.commit()) return;
        SharedPreferences.Editor restore = prefs.edit();
        put(restore, key, existed ? previous : null);
        restore.commit();
        throw new IllegalStateException("Setting could not be saved.");
    }

    private static void put(SharedPreferences.Editor edit, String key, Object value) {
        if (value == null) edit.remove(key);
        else if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
        else edit.putString(key, (String) value);
    }
    private static String bool(SharedPreferences prefs, String key) {
        try { return !prefs.contains(key) ? "Default" : prefs.getBoolean(key, false) ? "On" : "Off"; }
        catch (ClassCastException invalid) { return "Unavailable"; }
    }
    private static String text(SharedPreferences prefs, String key) {
        try { return !prefs.contains(key) ? "Default" : prefs.getString(key, "Default"); }
        catch (ClassCastException invalid) { return "Unavailable"; }
    }
    private static String clockFormat(SharedPreferences prefs) {
        try { return !prefs.contains("use_24h_clock") ? "Follow TV"
                : prefs.getBoolean("use_24h_clock", false) ? "24-hour" : "12-hour"; }
        catch (ClassCastException invalid) { return "Unavailable"; }
    }
    private static String mode(SharedPreferences prefs) {
        try { return !prefs.contains("minimal_status_bar") ? "Off"
                : prefs.getBoolean("minimal_status_bar", false) ? "Minimal" : "Full"; }
        catch (ClassCastException invalid) { return "Unavailable"; }
    }
}
