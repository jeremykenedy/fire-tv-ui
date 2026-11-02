package com.jeremykenedy.firetv.ui.nativeapp.statusbar;

import android.content.Context;

public final class StatusBarOptionsTest {
    public static void main(String[] args) {
        Context context = new Context();
        StatusBarOptions defaults = StatusBarOptions.load(context);
        check(defaults.configured && !defaults.minimal && defaults.position.equals("Right")
                && defaults.tinted && !defaults.hideUnlessFocused, "Absent keys use visible tinted full pill on right");
        check(context.values.values.isEmpty(), "Reading defaults does not create preferences");
        context.values.values.put("minimal_status_bar", Boolean.FALSE);
        context.values.values.put("status_bar_position", "Left");
        context.values.values.put("tinted_status_bar", false);
        StatusBarOptions full = StatusBarOptions.load(context);
        check(full.configured && !full.minimal && full.position.equals("Left") && !full.tinted, "Explicit full choices retained");
        context.values.values.put("minimal_status_bar", Boolean.TRUE);
        context.values.values.put("hide_status_bar_unless_focused", true);
        check(StatusBarOptions.load(context).minimal && StatusBarOptions.load(context).hideUnlessFocused, "Minimal hidden clock retained");
        context.values.values.put(StatusBarOptions.OFF_KEY, true);
        check(!StatusBarOptions.load(context).configured && Boolean.TRUE.equals(context.values.values.get("minimal_status_bar")), "Explicit off preserves prior mode");
        context.values.values.remove(StatusBarOptions.OFF_KEY);
        check(StatusBarOptions.load(context).minimal, "Removing off marker restores retained mode");
        context.values.values.put("status_bar_position", "invalid");
        check(StatusBarOptions.load(context).position.isEmpty(), "Unknown position not guessed");
        context.values.values.put("minimal_status_bar", "invalid");
        check(!StatusBarOptions.load(context).configured && "invalid".equals(context.values.values.get("minimal_status_bar")), "Malformed preference preserved");
        System.out.println("Status bar defaults, explicit choices, off marker and malformed values passed");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
