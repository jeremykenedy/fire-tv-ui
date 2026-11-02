package com.jeremykenedy.firetv.ui.nativeapp.statusbar;

import android.content.Context;
import android.content.SharedPreferences;

/** Read retained choices without writing defaults. */
public final class StatusBarOptions {
    public static final String OFF_KEY = "source_status_bar_off";
    public final boolean configured;
    public final boolean minimal;
    public final String position;
    public final boolean hideUnlessFocused;
    public final boolean tinted;

    private StatusBarOptions(boolean configured, boolean minimal, String position, boolean hideUnlessFocused, boolean tinted) {
        this.configured = configured;
        this.minimal = minimal;
        this.position = position;
        this.hideUnlessFocused = hideUnlessFocused;
        this.tinted = tinted;
    }

    public static StatusBarOptions load(Context context) {
        SharedPreferences preferences = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE);
        try {
            boolean off = preferences.getBoolean(OFF_KEY, false);
            boolean minimal = preferences.getBoolean("minimal_status_bar", false);
            String position = preferences.getString("status_bar_position", "Right");
            if (!"Left".equals(position) && !"Right".equals(position)) position = "";
            boolean hide = preferences.getBoolean("hide_status_bar_unless_focused", false);
            boolean tint = preferences.getBoolean("tinted_status_bar", true);
            return new StatusBarOptions(!off, minimal, position, hide, tint);
        } catch (ClassCastException invalid) {
            return new StatusBarOptions(false, false, "", false, false);
        }
    }
}
