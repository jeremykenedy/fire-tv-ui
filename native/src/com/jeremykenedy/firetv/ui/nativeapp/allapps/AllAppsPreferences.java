package com.jeremykenedy.firetv.ui.nativeapp.allapps;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.util.Set;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;

public final class AllAppsPreferences {
    public static final String KEY = "show_all_apps_button";
    private AllAppsPreferences() {}
    public static boolean enabled(Context context) {
        try { return values(context).getBoolean(KEY, false); }
        catch (ClassCastException malformed) { return false; }
    }
    @SuppressWarnings("unchecked")
    public static void setEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = values(context);
        Object previous = prefs.getAll().get(KEY);
        if (prefs.edit().putBoolean(KEY, enabled).commit()) return;
        SharedPreferences.Editor restore = prefs.edit();
        if (previous instanceof Boolean) restore.putBoolean(KEY, (Boolean) previous);
        else if (previous instanceof String) restore.putString(KEY, (String) previous);
        else if (previous instanceof Integer) restore.putInt(KEY, (Integer) previous);
        else if (previous instanceof Long) restore.putLong(KEY, (Long) previous);
        else if (previous instanceof Float) restore.putFloat(KEY, (Float) previous);
        else if (previous instanceof Set) restore.putStringSet(KEY, (Set<String>) previous);
        else restore.remove(KEY);
        restore.commit();
        throw new IllegalStateException("The All apps button choice could not be saved.");
    }
    public static Button addControl(final Activity activity, LinearLayout parent) {
        final Button button = Pages.button(activity, label(activity), null);
        button.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                try { setEnabled(activity, !enabled(activity)); button.setText(label(activity)); }
                catch (IllegalStateException failed) { Toast.makeText(activity, failed.getMessage(), Toast.LENGTH_LONG).show(); }
            }
        });
        parent.addView(button); return button;
    }
    private static String label(Context context) { return "All apps button: " + (enabled(context) ? "Shown" : "Hidden (default)"); }
    private static SharedPreferences values(Context context) { return context.getSharedPreferences("native_app_grid", Context.MODE_PRIVATE); }
}
