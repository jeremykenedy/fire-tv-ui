package com.jeremykenedy.firetv.ui.nativeapp.setups;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Only launcher presentation choices belong to a saved setup. */
final class LegacySetupSettings {
    private static final Map<String, String> TYPES = types();

    private LegacySetupSettings() {}

    private static Map<String, String> types() {
        Map<String, String> result = new LinkedHashMap<>();
        add(
                result,
                "String",
                "app_order",
                "app_visibility",
                "channel_visibility",
                "custom_gradient",
                "custom_wallpaper_uri",
                "custom_video_uri",
                "display_order",
                "favorites_bg_mode",
                "folders",
                "icon_pack_package",
                "mode",
                "size",
                "status_bar_position",
                "status_bar_weather_loc",
                "status_bar_weather_unit",
                "subreddit_wallpaper_name",
                "widgets_config",
                "menu_animation",
                "menu_transition_style",
                "app_scroll_direction",
                "icon_focus_style",
                "menu_background_palette",
                "menu_overlay",
                "menu_overlay_palette",
                "input_names");
        add(
                result,
                "Boolean",
                "blur_favorites_background",
                "blur_on_scroll",
                "enable_widgets",
                "featured_artwork_fit",
                "hide_favorites_background",
                "hide_status_bar_unless_focused",
                "hide_watch_next",
                "hide_watch_next_titles",
                "icon_shadow",
                "keep_dock_bottom",
                "mini_mode",
                "minimal_status_bar",
                "poster_mode",
                "show_all_apps_button",
                "text_outside_cards",
                "tinted_status_bar",
                "use_24h_clock",
                "white_border",
                "menu_animation_frozen",
                "menu_overlay_frozen");
        add(
                result,
                "Int",
                "apps_per_row",
                "app_max_rows",
                "featured_rotation_seconds",
                "icon_radius",
                "menu_animation_speed",
                "menu_animation_intensity",
                "menu_animation_brightness",
                "icon_focus_background",
                "icon_focus_border",
                "menu_overlay_speed",
                "menu_overlay_intensity",
                "menu_overlay_opacity");
        add(
                result,
                "StringSet",
                "featured_card_order",
                "featured_feed_order",
                "hidden_featured_cards",
                "featured_feed_rotation_seconds",
                "factory_icon_packages");
        return result;
    }

    private static boolean isColor(String key) {
        return key.matches("color_[a-zA-Z0-9_]{1,100}");
    }

    private static void add(Map<String, String> target, String type, String... keys) {
        for (String key : keys) target.put(key, type);
    }

    static Set<String> ownedKeys(Map<String, ?> preferences) {
        Set<String> keys = new LinkedHashSet<>(TYPES.keySet());
        for (String key : preferences.keySet()) if (isColor(key)) keys.add(key);
        return keys;
    }

    static JSONObject capture(Map<String, ?> preferences) throws JSONException {
        JSONObject values = new JSONObject();
        Set<String> keys = ownedKeys(preferences);
        for (String key : keys) {
            if (!preferences.containsKey(key)) continue;
            Object value = preferences.get(key);
            if (value instanceof Set) value = new JSONArray((Set<?>) value);
            values.put(key, value);
        }
        JSONObject snapshot =
                new JSONObject().put("keys", new JSONArray(keys)).put("values", values);
        validate(snapshot);
        return snapshot;
    }

    static Map<String, Object> validate(JSONObject snapshot) throws JSONException {
        if (snapshot == null || snapshot.length() != 2) throw invalid();
        JSONArray keys = snapshot.getJSONArray("keys");
        JSONObject values = snapshot.getJSONObject("values");
        if (keys.length() > TYPES.size() + 256) throw invalid();
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < keys.length(); i++) {
            Object item = keys.get(i);
            if (!(item instanceof String)) throw invalid();
            String key = (String) item;
            if ((!TYPES.containsKey(key) && !isColor(key)) || result.containsKey(key))
                throw invalid();
            Object value = values.has(key) ? values.get(key) : null;
            if (value != null) value = validateValue(key, value);
            result.put(key, value);
        }
        for (java.util.Iterator<String> i = values.keys(); i.hasNext(); ) {
            if (!result.containsKey(i.next())) throw invalid();
        }
        return result;
    }

    private static Object validateValue(String key, Object value) throws JSONException {
        String type = isColor(key) ? "Int" : TYPES.get(key);
        boolean valid =
                "String".equals(type) && value instanceof String
                        || "Boolean".equals(type) && value instanceof Boolean
                        || "Int".equals(type) && value instanceof Integer
                        || "StringSet".equals(type) && value instanceof JSONArray;
        if (!valid) throw invalid();
        if (value instanceof String && ((String) value).length() > 1024 * 1024) throw invalid();
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            if (array.length() > 2048) throw invalid();
            Set<String> strings = new LinkedHashSet<>();
            for (int i = 0; i < array.length(); i++) {
                Object item = array.get(i);
                if (!(item instanceof String)
                        || ((String) item).length() > 4096
                        || !strings.add((String) item)) throw invalid();
            }
            value = strings;
        }
        if ("featured_rotation_seconds".equals(key)
                && ((Integer) value < 0 || (Integer) value > 86400)) throw invalid();
        if ("app_max_rows".equals(key) && ((Integer) value < 0 || (Integer) value > 6))
            throw invalid();
        if ("app_scroll_direction".equals(key)
                && !Arrays.asList("vertical", "horizontal").contains(value)) throw invalid();
        if ("menu_transition_style".equals(key)
                && !Arrays.asList("default", "none", "fade", "slide").contains(value))
            throw invalid();
        if ("featured_feed_rotation_seconds".equals(key)) {
            Set<String> feeds = new LinkedHashSet<>();
            if (((Set<?>) value).size() > 256) throw invalid();
            for (Object item : (Set<?>) value)
                if (!((String) item).matches("[a-f0-9]{64}:(0|[1-9][0-9]{0,4})")
                        || Integer.parseInt(((String) item).substring(65)) > 86400
                        || !feeds.add(((String) item).substring(0, 64))) throw invalid();
        }
        if ("app_visibility".equals(key)) new JSONObject((String) value);
        if ("input_names".equals(key)) {
            JSONObject names = new JSONObject((String) value);
            if (names.length() > 64) throw invalid();
            for (java.util.Iterator<String> keys = names.keys(); keys.hasNext(); ) {
                String input = keys.next();
                Object name = names.get(input);
                if (!input.startsWith("tvinput_name:")
                        || input.length() <= 13
                        || input.length() > 512
                        || !(name instanceof String)
                        || ((String) name).trim().isEmpty()
                        || ((String) name).length() > 60
                        || !name.equals(((String) name).trim())
                        || ((String) name).matches("(?s).*\\p{Cntrl}.*")) throw invalid();
            }
        }
        return value;
    }

    static Map<String, Object> toApply(JSONObject snapshot, Map<String, ?> current, boolean icons)
            throws JSONException {
        Map<String, Object> result = validate(snapshot);
        for (String key : current.keySet())
            if (isColor(key) && !result.containsKey(key)) result.put(key, null);
        if (!icons) {
            result.remove("icon_pack_package");
            result.remove("factory_icon_packages");
            if (result.containsKey("app_visibility")) {
                Object saved = result.get("app_visibility");
                Object present = current.get("app_visibility");
                JSONObject target = new JSONObject(saved == null ? "{}" : (String) saved);
                JSONObject source = new JSONObject(present == null ? "{}" : (String) present);
                Set<String> ids = new LinkedHashSet<>();
                for (java.util.Iterator<String> i = target.keys(); i.hasNext(); ) ids.add(i.next());
                for (java.util.Iterator<String> i = source.keys(); i.hasNext(); ) ids.add(i.next());
                for (String id : ids) {
                    JSONObject old = source.optJSONObject(id), next = target.optJSONObject(id);
                    if (next == null) next = new JSONObject();
                    if (old != null && old.has("customIconUri"))
                        next.put("customIconUri", old.get("customIconUri"));
                    else next.remove("customIconUri");
                    if (next.length() > 0) target.put(id, next);
                }
                result.put(
                        "app_visibility",
                        saved == null && target.length() == 0 ? null : target.toString());
            }
        }
        return result;
    }

    private static JSONException invalid() {
        return new JSONException(
                "This saved setup contains invalid settings. No settings were applied.");
    }
}
