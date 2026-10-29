package com.jeremykenedy.firetv.ui;

/** Display names only; the base launcher keeps its route tokens and focus keys. */
public final class UiMenuLabels {
    private UiMenuLabels() { }

    public static String display(String route) {
        if ("Appearance".equals(route)) return "UI settings";
        if ("Theme Mode".equals(route)) return "Theme and wallpaper";
        if ("Controls".equals(route)) return "Remote controls";
        return route;
    }
}
