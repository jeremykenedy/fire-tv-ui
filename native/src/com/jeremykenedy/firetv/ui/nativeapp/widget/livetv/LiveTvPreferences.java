package com.jeremykenedy.firetv.ui.nativeapp.widget.livetv;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public final class LiveTvPreferences {
    private static final String FILE = "native_live_tv_favorites";
    public final List<String> favorites;
    public final String zone;
    LiveTvPreferences(List<String> favorites, String zone) { this.favorites = LiveGuide.favorites(favorites); this.zone = LiveGuide.zone(zone); }
    public static LiveTvPreferences read(Context context) {
        try {
            String stored = context.getSharedPreferences(FILE, 0).getString("configuration", "");
            if (stored.isEmpty()) return new LiveTvPreferences(new ArrayList<String>(), "");
            JSONObject object = new JSONObject(stored);
            if (object.getInt("version") != 1) throw new IllegalArgumentException("Unsupported guide preferences.");
            JSONArray array = object.getJSONArray("favorites");
            if (array.length() > LiveGuide.MAX_FAVORITES) throw new IllegalArgumentException("Too many favorite channels.");
            List<String> keys = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) keys.add(array.getString(i));
            return new LiveTvPreferences(keys, object.getString("zone"));
        } catch (Exception invalid) { throw new IllegalStateException("Saved Live TV favorites could not be read. Saved data was kept.", invalid); }
    }
    public static void save(Context context, List<String> favorites, String zone) {
        LiveTvPreferences value = new LiveTvPreferences(favorites, zone);
        try {
            JSONObject object = new JSONObject().put("version", 1).put("zone", value.zone).put("favorites", new JSONArray(value.favorites));
            if (!context.getSharedPreferences(FILE, 0).edit().putString("configuration", object.toString()).commit())
                throw new IllegalStateException("Favorite channels could not be saved.");
        } catch (org.json.JSONException invalid) { throw new IllegalStateException(invalid); }
    }
}
