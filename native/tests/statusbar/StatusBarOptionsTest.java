package com.jeremykenedy.firetv.ui.nativeapp.statusbar;

import android.content.Context;

public final class StatusBarOptionsTest {
    public static void main(String[] args) {
        Context context = new Context();
        check(!StatusBarOptions.load(context).configured, "Absence must preserve existing Home");
        context.values.values.put("minimal_status_bar", Boolean.FALSE);
        check(StatusBarOptions.load(context).configured && !StatusBarOptions.load(context).minimal, "Explicit full row");
        context.values.values.put("status_bar_position", "Left");
        check("Left".equals(StatusBarOptions.load(context).position), "Explicit left position");
        context.values.values.put("status_bar_position", "Right");
        check("Right".equals(StatusBarOptions.load(context).position), "Explicit right position");
        context.values.values.put("status_bar_position", "invalid");
        check(StatusBarOptions.load(context).position.isEmpty(), "Unknown position not guessed");
        context.values.values.put("minimal_status_bar", Boolean.TRUE);
        check(StatusBarOptions.load(context).configured && StatusBarOptions.load(context).minimal, "Explicit clock only");
        context.values.values.put("hide_status_bar_unless_focused", true);
        check(StatusBarOptions.load(context).minimal && StatusBarOptions.load(context).hideUnlessFocused, "Minimal hidden clock remains supported");
        context.values.values.put("hide_status_bar_unless_focused", false);
        context.values.values.put("tinted_status_bar", true);
        check(StatusBarOptions.load(context).tinted, "Explicit tint retained");
        context.values.values.put("minimal_status_bar", "invalid");
        check(!StatusBarOptions.load(context).configured && "invalid".equals(context.values.values.get("minimal_status_bar")), "Malformed preference preserved");
        System.out.println("Status bar absent, full, minimal and malformed choices passed");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
