package com.jeremykenedy.firetv.ui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PlexLibrary {
    private PlexLibrary() {}

    static List<Item> items(JSONObject response) {
        Map<String, Item> result = new LinkedHashMap<>();
        JSONObject container = response.optJSONObject("MediaContainer");
        if (container != null) {
            collect(container.optJSONArray("Metadata"), result);
            JSONArray hubs = container.optJSONArray("Hub");
            if (hubs != null) {
                for (int i = 0; i < hubs.length() && result.size() < 12; i++) {
                    JSONObject hub = hubs.optJSONObject(i);
                    if (hub != null) collect(hub.optJSONArray("Metadata"), result);
                }
            }
        }
        return new ArrayList<>(result.values());
    }

    private static void collect(JSONArray values, Map<String, Item> result) {
        if (values == null) return;
        for (int i = 0; i < values.length() && result.size() < 12; i++) {
            JSONObject value = values.optJSONObject(i);
            if (value == null) continue;
            String id = value.optString("ratingKey", "");
            String type = value.optString("type", "");
            String title = value.optString("title", "").trim();
            if (!id.matches("[0-9]{1,18}")
                    || title.isEmpty()
                    || !("movie".equals(type) || "episode".equals(type) || "show".equals(type)))
                continue;
            if (result.containsKey(id)) continue;
            String series = value.optString("grandparentTitle", "").trim();
            if (!series.isEmpty()) title = series + ": " + title;
            String art = value.optString("art", "");
            boolean landscape = PlexValues.libraryPath(art);
            if (!landscape) art = value.optString("thumb", "");
            if (!PlexValues.libraryPath(art)) art = null;
            result.put(
                    id,
                    new Item(
                            id,
                            title,
                            value.optString("summary", ""),
                            type,
                            art,
                            landscape,
                            Math.max(0, value.optLong("viewOffset", 0)),
                            Math.max(0, value.optLong("duration", 0))));
        }
    }

    static final class Item {
        final String id;
        final String title;
        final String summary;
        final String type;
        final String art;
        final boolean landscape;
        final long position;
        final long duration;

        Item(
                String id,
                String title,
                String summary,
                String type,
                String art,
                boolean landscape,
                long position,
                long duration) {
            this.id = id;
            this.title = title;
            this.summary = summary;
            this.type = type;
            this.art = art;
            this.landscape = landscape;
            this.position = position;
            this.duration = duration;
        }
    }
}
