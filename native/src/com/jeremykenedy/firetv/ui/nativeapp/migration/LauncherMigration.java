package com.jeremykenedy.firetv.ui.nativeapp.migration;

import java.util.*;

/** Additive in-place contract only. Native renderer preference mapping is a later integration. */
public final class LauncherMigration {
    public static final String PREFERENCES = "launcher_prefs";
    public static final String VERSION_KEY = "source_ui_migration_version";
    public static final int VERSION = 1;
    public static final String SAVED_SETUPS = "saved-setups/setups.json";
    public static final String PRIVATE_CONNECTION = "plex-connection.enc";
    private static final Set<String> PORTABLE_BOOLEANS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            "blur_favorites_background", "blur_on_scroll", "enable_widgets", "featured_artwork_fit",
            "hide_favorites_background", "hide_watch_next", "hide_watch_next_titles", "icon_shadow",
            "keep_dock_bottom", "mini_mode", "minimal_status_bar", "poster_mode", "show_all_apps_button",
            "text_outside_cards", "tinted_status_bar", "use_24h_clock", "white_border")));
    private static final Set<String> PORTABLE_INTS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            "apps_per_row", "app_max_rows", "featured_rotation_seconds", "icon_focus_background", "icon_focus_border")));
    private static final Set<String> PORTABLE_ENUMS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            "app_scroll_direction", "menu_transition_style", "icon_focus_style")));

    private LauncherMigration() {}

    public static PreferenceSnapshot prepare(PreferenceSnapshot current) {
        validate(current.values());
        Object version = current.values().get(VERSION_KEY);
        if (version != null && (!(version instanceof Integer) || (Integer) version < 0 || (Integer) version > VERSION)) {
            throw new IllegalArgumentException("Unsupported migration version. Settings were not changed.");
        }
        Map<String, Object> next = new LinkedHashMap<>(current.values());
        next.put(VERSION_KEY, VERSION);
        return new PreferenceSnapshot(next);
    }

    /** Deliberately excludes identities, media URIs, nested JSON, credentials and device controls. */
    public static PreferenceSnapshot portableValues(PreferenceSnapshot current) {
        validate(current.values());
        Map<String, Object> portable = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : current.values().entrySet()) {
            String key = entry.getKey();
            if (PORTABLE_BOOLEANS.contains(key) || PORTABLE_INTS.contains(key) || PORTABLE_ENUMS.contains(key)) {
                portable.put(key, entry.getValue());
            }
        }
        return new PreferenceSnapshot(portable);
    }

    public static Map<String, String> portableTypes() {
        Map<String, String> types = new LinkedHashMap<>();
        for (String key : PORTABLE_BOOLEANS) types.put(key, "Boolean");
        for (String key : PORTABLE_INTS) types.put(key, "Int");
        for (String key : PORTABLE_ENUMS) types.put(key, "String");
        return Collections.unmodifiableMap(types);
    }

    private static void validate(Map<String, Object> values) {
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            boolean valid = true;
            if (PORTABLE_BOOLEANS.contains(key)) valid = value instanceof Boolean;
            if (PORTABLE_INTS.contains(key)) valid = value instanceof Integer;
            if ("apps_per_row".equals(key)) valid = integer(value, 1, 20);
            if ("app_max_rows".equals(key)) valid = integer(value, 0, 6);
            if ("featured_rotation_seconds".equals(key)) valid = integer(value, 0, 86400);
            if ("app_scroll_direction".equals(key)) valid = Arrays.asList("vertical", "horizontal").contains(value);
            if ("menu_transition_style".equals(key)) valid = Arrays.asList("default", "none", "fade", "slide").contains(value);
            if ("icon_focus_style".equals(key)) valid = Arrays.asList("default", "border", "fill").contains(value);
            // These maintained saved-setup keys are absent from preference-types.json.
            if ("custom_video_uri".equals(key)) valid = value instanceof String;
            if ("factory_icon_packages".equals(key)) valid = value instanceof Set;
            if (!valid) throw new IllegalArgumentException("Invalid maintained preference. Settings were not changed.");
        }
    }

    private static boolean integer(Object value, int min, int max) {
        return value instanceof Integer && (Integer) value >= min && (Integer) value <= max;
    }
}
