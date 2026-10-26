package com.jeremykenedy.firetv.ui;

/** Portable menu-only animation choices; the existing wallpaper is never replaced in storage. */
public final class MenuBackgroundOptions {
    static final String STYLE = "menu_animation";
    static final String SPEED = "menu_animation_speed";
    static final String INTENSITY = "menu_animation_intensity";
    static final String BRIGHTNESS = "menu_animation_brightness";
    static final String[] STYLES = {"off", "particles", "aurora", "constellation", "prism"};
    static final String[] LABELS = {"Off: use my wallpaper", "Quiet particles", "Aurora ribbons", "Connected lights", "Prism light"};
    static final int[] SPEEDS = {25, 50, 100};
    static final int[] LEVELS = {25, 50, 75, 100};

    private MenuBackgroundOptions() {}

    static int style(String value) {
        for (int i = 0; i < STYLES.length; i++) if (STYLES[i].equals(value)) return i;
        return 0;
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
