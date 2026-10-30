package com.jeremykenedy.firetv.ui;

/** Portable menu-only animation choices; the existing wallpaper is never replaced in storage. */
public final class MenuBackgroundOptions {
    static final String STYLE = "menu_animation";
    static final String FROZEN = "menu_animation_frozen";
    static final String PALETTE = "menu_background_palette";
    static final String OVERLAY = "menu_overlay";
    static final String OVERLAY_FROZEN = "menu_overlay_frozen";
    static final String OVERLAY_SPEED = "menu_overlay_speed";
    static final String OVERLAY_INTENSITY = "menu_overlay_intensity";
    static final String OVERLAY_OPACITY = "menu_overlay_opacity";
    static final String OVERLAY_PALETTE = "menu_overlay_palette";
    static final String SPEED = "menu_animation_speed";
    static final String INTENSITY = "menu_animation_intensity";
    static final String BRIGHTNESS = "menu_animation_brightness";
    static final String[] STYLES = {"off", "particles", "aurora", "constellation", "prism",
            "silk", "bokeh", "stars", "embers", "waves", "rain", "orbits", "rays",
            "still_midnight", "still_graphite", "still_forest", "still_plum",
            "snow", "snowflakes", "leaves", "hearts_falling", "hearts_floating", "petals", "lanterns"};
    static final String[] LABELS = {"Off: use my wallpaper", "Quiet particles", "Aurora ribbons",
            "Connected lights", "Prism light", "Flowing silk", "Soft bokeh", "Drifting stars",
            "Warm embers", "Ocean ribbons", "Gentle rain", "Orbital arcs", "Light rays",
            "Still: Midnight blue", "Still: Warm graphite", "Still: Forest dusk", "Still: Plum velvet",
            "Falling snow", "Drifting snowflakes", "Falling leaves", "Falling hearts", "Floating hearts",
            "Drifting petals", "Floating lights"};
    static final int[] SPEEDS = {25, 50, 100};
    static final int[] LEVELS = {25, 50, 75, 100};

    private MenuBackgroundOptions() {}

    static int style(String value) {
        for (int i = 0; i < STYLES.length; i++) if (STYLES[i].equals(value)) return i;
        return 0;
    }

    static boolean animated(int style) {
        return style > 0 && style < STYLES.length && (style < 13 || style >= 17);
    }

    static int level(int value) {
        for (int level : LEVELS) if (level == value) return value;
        return 50;
    }

    static int speed(int value) {
        for (int speed : SPEEDS) if (speed == value) return value;
        return 50;
    }
}
