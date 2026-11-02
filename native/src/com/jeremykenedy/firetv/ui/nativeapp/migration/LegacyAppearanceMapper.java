package com.jeremykenedy.firetv.ui.nativeapp.migration;

import com.jeremykenedy.firetv.ui.nativeapp.appearance.AppearanceOptions;
import com.jeremykenedy.firetv.ui.nativeapp.appearance.PaletteRecipes;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/** Only reviewed layer and palette choices; inherited gradient modes remain unresolved. */
final class LegacyAppearanceMapper {
    private static final Map<String, String> KEYS;
    static {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("menu_animation", "base"); keys.put("menu_animation_frozen", "base_frozen");
        keys.put("menu_animation_speed", "base_speed"); keys.put("menu_animation_intensity", "base_intensity");
        keys.put("menu_animation_brightness", "base_brightness"); keys.put("menu_overlay", "overlay");
        keys.put("menu_overlay_frozen", "overlay_frozen"); keys.put("menu_overlay_speed", "overlay_speed");
        keys.put("menu_overlay_intensity", "overlay_intensity"); keys.put("menu_overlay_opacity", "overlay_opacity");
        keys.put("menu_background_palette", "base_palette"); keys.put("menu_overlay_palette", "overlay_palette");
        keys.put("custom_wallpaper_uri", "wallpaper_image_uri"); keys.put("custom_video_uri", "wallpaper_video_uri");
        KEYS = Collections.unmodifiableMap(keys);
    }
    private LegacyAppearanceMapper() {}
    static Map<String, String> keys() { return KEYS; }
    static Map<String, Object> map(Map<String, Object> legacy, Set<String> mapped) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : KEYS.entrySet()) {
            if (!legacy.containsKey(entry.getKey())) continue;
            Object value = legacy.get(entry.getKey());
            String target = entry.getValue();
            if (target.equals("base")) choice(value, AppearanceOptions.BASES);
            else if (target.equals("overlay")) choice(value, AppearanceOptions.OVERLAYS);
            else if (target.endsWith("_palette")) palette(value);
            else if (target.endsWith("_frozen")) { if (!(value instanceof Boolean)) throw invalid(); }
            else if (target.endsWith("_uri")) { if (!(value instanceof String)) throw invalid(); }
            else if (!(value instanceof Integer) || (Integer) value < 0 || (Integer) value > 100) throw invalid();
            values.put(target, value); mapped.add(entry.getKey());
        }
        if ("customv".equals(legacy.get("mode"))) {
            Object uri = legacy.get("custom_video_uri");
            if (!(uri instanceof String) || !contentUri((String) uri)) {
                throw new IllegalArgumentException("The active legacy video needs a retained native-supported content URI before mapping.");
            }
            values.put("wallpaper_mode", "video"); mapped.add("mode");
        }
        return values;
    }
    static void validateNative(Map<String, Object> values) {
        for (String key : KEYS.values()) {
            if (!values.containsKey(key)) continue;
            Object value = values.get(key);
            if (key.equals("base")) choice(value, AppearanceOptions.BASES);
            else if (key.equals("overlay")) choice(value, AppearanceOptions.OVERLAYS);
            else if (key.endsWith("_palette")) palette(value);
            else if (key.endsWith("_frozen")) { if (!(value instanceof Boolean)) throw invalid(); }
            else if (key.endsWith("_uri")) { if (!(value instanceof String)) throw invalid(); }
            else if (!(value instanceof Integer) || (Integer) value < 0 || (Integer) value > 100) throw invalid();
        }
        if (values.containsKey("wallpaper_mode")) choice(values.get("wallpaper_mode"), new String[]{"system", "image", "video"});
        if (values.containsKey("scene")) choice(values.get("scene"), new String[]{"", "halloween", "christmas", "winter"});
    }
    private static void palette(Object value) {
        if (!(value instanceof String) || !((String) value).isEmpty() && PaletteRecipes.find((String) value) == null) throw invalid();
    }
    private static void choice(Object value, String[] supported) {
        if (!(value instanceof String) || !Arrays.asList(supported).contains(value)) throw invalid();
    }
    private static boolean contentUri(String value) {
        try {
            URI uri = new URI(value);
            return "content".equals(uri.getScheme()) && uri.getRawAuthority() != null && !uri.getRawAuthority().isEmpty()
                    && uri.getRawUserInfo() == null && uri.getRawFragment() == null;
        } catch (URISyntaxException invalid) { return false; }
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Unsupported legacy background or overlay setting."); }
}
