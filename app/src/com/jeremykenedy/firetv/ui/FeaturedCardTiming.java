package com.jeremykenedy.firetv.ui;

import android.content.SharedPreferences;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class FeaturedCardTiming {
    public static final String OVERRIDES = "featured_feed_rotation_seconds";
    static final int MAX_OVERRIDES = 256;
    static final String SETTING = "featured_rotation_seconds";
    static final int MIN_CUSTOM_SECONDS = 1;
    static final int MAX_CUSTOM_SECONDS = 86400;
    static final int[] OPTIONS = {0, 5, 8, 15, 30};

    private FeaturedCardTiming() {}

    static int seconds(int value) {
        return value >= 0 && value <= MAX_CUSTOM_SECONDS ? value : 8;
    }

    public static long delay(SharedPreferences preferences) {
        return milliseconds(preferences.getInt(SETTING, 8));
    }

    public static Integer setting(SharedPreferences preferences) {
        return Integer.valueOf(seconds(preferences.getInt(SETTING, 8)));
    }

    static Map<String, Integer> overrides(SharedPreferences prefs) {
        Set<String> values = prefs.getStringSet(OVERRIDES, Collections.<String>emptySet());
        Map<String, Integer> result = new HashMap<>();
        if (!validOverrides(values)) return result;
        for (String value : values) result.put(value.substring(0, 64), Integer.parseInt(value.substring(65)));
        return result;
    }

    public static boolean validOverrides(Set<?> values) {
        if (values == null || values.size() > MAX_OVERRIDES) return false;
        Set<String> seen = new HashSet<>();
        for (Object item : values) {
            if (!(item instanceof String)) return false;
            String value = (String) item;
            if (!value.matches("[a-f0-9]{64}:(0|[1-9][0-9]{0,4})")) return false;
            if (Integer.parseInt(value.substring(65)) > MAX_CUSTOM_SECONDS
                    || !seen.add(value.substring(0, 64))) return false;
        }
        return true;
    }

    static int seconds(SharedPreferences prefs, String feed) {
        Integer value = overrides(prefs).get(feed);
        return value == null ? seconds(prefs.getInt(SETTING, 8)) : value;
    }

    static void save(SharedPreferences prefs, String feed, Integer seconds) {
        if (seconds != null && (seconds < 0 || seconds > MAX_CUSTOM_SECONDS))
            throw new IllegalArgumentException("Enter whole seconds from 1 to 86400, or choose Manual only.");
        SharedPreferences.Editor editor = prefs.edit();
        if (feed == null) {
            if (seconds == null) editor.remove(SETTING);
            else editor.putInt(SETTING, seconds);
        } else {
            if (!feed.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("This channel has no stable identity.");
            Map<String, Integer> values = overrides(prefs);
            if (seconds == null) values.remove(feed); else values.put(feed, seconds);
            if (values.size() > MAX_OVERRIDES) throw new IllegalArgumentException("You can customize up to 256 channel timers.");
            Set<String> encoded = new HashSet<>();
            for (Map.Entry<String, Integer> entry : values.entrySet()) encoded.add(entry.getKey() + ":" + entry.getValue());
            if (encoded.isEmpty()) editor.remove(OVERRIDES); else editor.putStringSet(OVERRIDES, encoded);
        }
        if (!editor.commit()) throw new IllegalStateException("The card timer could not be saved. Try again.");
    }

    static long milliseconds(int value) {
        int seconds = seconds(value);
        return seconds == 0 ? Long.MAX_VALUE : seconds * 1000L;
    }

    static String label(int value) {
        int seconds = seconds(value);
        return seconds == 0 ? "Manual only" : "Every " + seconds + " seconds";
    }
}
