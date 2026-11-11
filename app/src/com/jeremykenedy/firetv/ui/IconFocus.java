package com.jeremykenedy.firetv.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;

import java.util.Map;
import java.util.WeakHashMap;

/** Focus colors sit behind artwork; transparent pack glyphs keep their contrast. */
public final class IconFocus {
    public static final String GROWTH = "icon_focus_growth";
    public static final String SHADOW_SIZE = "icon_focus_shadow_size";
    public static final int DEFAULT_GROWTH = 18;
    public static final int DEFAULT_SHADOW = 18;
    public static final int MAX_GROWTH = 30;
    public static final int MAX_SHADOW = 38;
    public static final String STYLE = "icon_focus_style";
    public static final String BACKGROUND = "icon_focus_background";
    public static final String BORDER = "icon_focus_border";
    public static final String[] STYLES = {"default", "border", "fill"};
    public static final String[] LABELS = {"Default", "Border only", "Color and border"};
    public static final int DEFAULT_BACKGROUND = 0xff263a5c;
    public static final int DEFAULT_BORDER = 0xffffffff;

    private static volatile SharedPreferences preferences;
    private static boolean sizeRefreshPending;
    private static final Map<Bitmap, Glyph> glyphs = new WeakHashMap<>();

    private IconFocus() {}

    public static String style(Context context) {
        return style(DeviceSettings.prefs(context));
    }

    public static void setStyle(Context context, String value) {
        boolean supported = false;
        for (String choice : STYLES) if (choice.equals(value)) supported = true;
        if (!supported) throw new IllegalArgumentException("Choose a supported focus style.");
        if (!DeviceSettings.prefs(context).edit().putString(STYLE, value).commit()) {
            throw new IllegalStateException("The focus style could not be saved.");
        }
    }

    public static int growth(Context context) {
        return number(DeviceSettings.prefs(context), GROWTH, DEFAULT_GROWTH, 0, MAX_GROWTH);
    }

    public static int shadow(Context context) {
        SharedPreferences prefs = DeviceSettings.prefs(context);
        return Boolean.FALSE.equals(prefs.getAll().get("icon_shadow")) ? 0
                : number(prefs, SHADOW_SIZE, DEFAULT_SHADOW, 1, MAX_SHADOW);
    }

    public static void setGrowth(Context context, Integer percent) {
        if (percent != null && (percent < 0 || percent > MAX_GROWTH))
            throw new IllegalArgumentException("Choose focus growth from 0 through 30 percent.");
        saveSize(context, GROWTH, percent, null);
    }

    public static void setShadow(Context context, int size) {
        if (size != 0 && size != DEFAULT_SHADOW && size != 28 && size != MAX_SHADOW)
            throw new IllegalArgumentException("Choose a supported focus shadow size.");
        SharedPreferences prefs = DeviceSettings.prefs(context);
        Integer selected = size == 0 ? optionalNumber(prefs, SHADOW_SIZE)
                : size == DEFAULT_SHADOW ? null : size;
        saveSize(context, SHADOW_SIZE, selected, size != 0);
    }

    private static synchronized void saveSize(Context context, String key, Integer value, Boolean shadow) {
        SharedPreferences prefs = DeviceSettings.prefs(context);
        Integer previous = optionalNumber(prefs, key);
        Object enabled = prefs.getAll().get("icon_shadow");
        if (shadow != null && enabled != null && !(enabled instanceof Boolean))
            throw new IllegalStateException("The saved shadow setting is unavailable.");
        SharedPreferences.Editor edit = prefs.edit();
        putNumber(edit, key, value);
        if (shadow != null) edit.putBoolean("icon_shadow", shadow);
        boolean committed = edit.commit();
        Map<String, ?> actual = prefs.getAll();
        if (committed && java.util.Objects.equals(actual.get(key), value)
                && (shadow == null || shadow.equals(actual.get("icon_shadow")))) {
            sizeRefreshPending = true;
            return;
        }
        SharedPreferences.Editor rollback = prefs.edit();
        putNumber(rollback, key, previous);
        if (shadow != null) {
            if (enabled == null) rollback.remove("icon_shadow");
            else rollback.putBoolean("icon_shadow", (Boolean) enabled);
        }
        rollback.commit();
        throw new IllegalStateException("The focus size could not be saved. Please try again.");
    }

    public static synchronized boolean consumeSizeRefresh() {
        boolean pending = sizeRefreshPending;
        sizeRefreshPending = false;
        return pending;
    }

    private static Integer optionalNumber(SharedPreferences prefs, String key) {
        Object value = prefs.getAll().get(key);
        if (value != null && !(value instanceof Integer))
            throw new IllegalStateException("The saved focus size is unavailable.");
        return (Integer) value;
    }

    private static void putNumber(SharedPreferences.Editor edit, String key, Integer value) {
        if (value == null) edit.remove(key);
        else edit.putInt(key, value);
    }

    private static int number(SharedPreferences prefs, String key, int fallback, int min, int max) {
        if (prefs == null) return fallback;
        try {
            int value = prefs.getInt(key, fallback);
            return value >= min && value <= max ? value : fallback;
        } catch (ClassCastException invalid) { return fallback; }
    }

    /** Keep the existing focus animation, replacing only its growth amount. */
    public static float scale(float progress) {
        return 1f + progress * (number(preferences, GROWTH, DEFAULT_GROWTH, 0, MAX_GROWTH) / 100f);
    }

    public static float shadowSize(float original) {
        SharedPreferences prefs = preferences;
        if (prefs != null && Boolean.FALSE.equals(prefs.getAll().get("icon_shadow"))) return 0f;
        return number(prefs, SHADOW_SIZE, Math.round(original), 1, MAX_SHADOW);
    }

    public static int color(Context context, String key, int fallback) {
        checkColorKey(key);
        return DeviceSettings.prefs(context).getInt(key, fallback) | 0xff000000;
    }

    public static void setColor(Context context, String key, int value) {
        checkColorKey(key);
        if (!DeviceSettings.prefs(context).edit().putInt(key, value | 0xff000000).commit()) {
            throw new IllegalStateException("The focus color could not be saved.");
        }
    }

    private static void checkColorKey(String key) {
        if (!BACKGROUND.equals(key) && !BORDER.equals(key)) {
            throw new IllegalArgumentException("Choose a supported focus color.");
        }
    }

    public static void configure(Context context) {
        preferences = DeviceSettings.prefs(context.getApplicationContext());
    }

    private static String style(SharedPreferences prefs) {
        String selected = prefs == null ? "default" : prefs.getString(STYLE, "default");
        return "border".equals(selected) || "fill".equals(selected) ? selected : "default";
    }

    /** The caller retains the launcher's existing interpolation and resting color. */
    public static long background(long resting, long focused, Bitmap artwork) {
        SharedPreferences prefs = preferences;
        String selected = style(prefs);
        if ("border".equals(selected)) return resting;
        long target = "fill".equals(selected)
                ? packed(prefs.getInt(BACKGROUND, DEFAULT_BACKGROUND) | 0xff000000) : focused;
        String pack = prefs == null ? null : prefs.getString("icon_pack_package", null);
        if (pack == null || pack.isEmpty() || artwork == null) return target;
        // Packed sRGB has an empty low word. Other color spaces keep their native value.
        if ((target & 0xffffffffL) != 0) return target;
        Glyph glyph = glyph(artwork);
        if (glyph == null || !glyph.transparent) return target;
        int fill = (int) (target >>> 32);
        if ((fill >>> 24) < 224 || contrast(glyph.luminance, luminance(fill)) >= 3) return target;
        return packed(glyph.luminance > 0.45 ? 0xff17202f : 0xfff4f4f4);
    }

    public static boolean borderEnabled(boolean original) {
        return !"default".equals(style(preferences)) || original;
    }

    public static long border(long original) {
        SharedPreferences prefs = preferences;
        return "default".equals(style(prefs)) ? original
                : packed(prefs.getInt(BORDER, DEFAULT_BORDER) | 0xff000000);
    }

    private static long packed(int argb) {
        return ((long) argb) << 32;
    }

    private static synchronized Glyph glyph(Bitmap bitmap) {
        if (bitmap.isRecycled()) return null;
        Glyph cached = glyphs.get(bitmap);
        int generation = bitmap.getGenerationId();
        if (cached != null && cached.generation == generation) return cached;
        try {
            int width = bitmap.getWidth(), height = bitmap.getHeight();
            int clear = 0, opaque = 0;
            double light = 0;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int pixel = bitmap.getPixel(Math.min(width - 1, (2 * x + 1) * width / 32),
                            Math.min(height - 1, (2 * y + 1) * height / 32));
                    int alpha = pixel >>> 24;
                    if (alpha < 32) clear++;
                    if (alpha >= 192) { opaque++; light += luminance(pixel); }
                }
            }
            Glyph result = new Glyph(generation, clear >= 32 && opaque >= 4,
                    opaque == 0 ? 0 : light / opaque);
            glyphs.put(bitmap, result);
            return result;
        } catch (IllegalStateException unreadable) {
            return null;
        }
    }

    private static double luminance(int color) {
        return 0.2126 * linear((color >> 16) & 255)
                + 0.7152 * linear((color >> 8) & 255) + 0.0722 * linear(color & 255);
    }

    private static double linear(int value) {
        double channel = value / 255.0;
        return channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    private static double contrast(double first, double second) {
        return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
    }

    private static final class Glyph {
        final int generation;
        final boolean transparent;
        final double luminance;

        Glyph(int generation, boolean transparent, double luminance) {
            this.generation = generation;
            this.transparent = transparent;
            this.luminance = luminance;
        }
    }
}
