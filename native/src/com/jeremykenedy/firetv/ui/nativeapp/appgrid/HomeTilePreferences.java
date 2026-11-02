package com.jeremykenedy.firetv.ui.nativeapp.appgrid;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class HomeTilePreferences {
    public static final String KEY = "home_order";
    private HomeTilePreferences() {}

    public static HomeTileOrder load(Context context) {
        try {
            String saved = values(context).getString(KEY, "");
            return new HomeTileOrder(saved.isEmpty() ? Collections.<String>emptyList()
                    : Arrays.asList(saved.split("\n", -1)));
        } catch (RuntimeException unreadable) {
            throw new IllegalStateException("Saved Home order could not be read. Its stored data has been kept.", unreadable);
        }
    }

    public static void move(Context context, String key, int delta, List<String> visible, Set<String> favorites) {
        HomeTileOrder old = load(context);
        HomeTileOrder changed = old.move(key, delta, visible, favorites);
        if (changed.order.equals(old.order)) return;
        SharedPreferences prefs = values(context);
        String previous = prefs.getString(KEY, null);
        if (prefs.edit().putString(KEY, String.join("\n", changed.order)).commit()) return;
        SharedPreferences.Editor restore = prefs.edit();
        if (previous == null) restore.remove(KEY); else restore.putString(KEY, previous);
        restore.commit();
        throw new IllegalStateException("Home order could not be saved. Please try again.");
    }

    private static SharedPreferences values(Context context) {
        return context.getSharedPreferences("native_app_grid", Context.MODE_PRIVATE);
    }
}
