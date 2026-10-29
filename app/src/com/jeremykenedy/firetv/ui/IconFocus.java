package com.jeremykenedy.firetv.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;

import java.util.Map;
import java.util.WeakHashMap;

/** Focus colors sit behind artwork; transparent pack glyphs keep their contrast. */
public final class IconFocus {
    public static final String STYLE = "icon_focus_style";
    public static final String BACKGROUND = "icon_focus_background";
    public static final String BORDER = "icon_focus_border";
    public static final String[] STYLES = {"default", "border", "fill"};
    public static final String[] LABELS = {"Default", "Border only", "Color and border"};
    public static final int DEFAULT_BACKGROUND = 0xff263a5c;
    public static final int DEFAULT_BORDER = 0xffffffff;

    private static volatile SharedPreferences preferences;
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
