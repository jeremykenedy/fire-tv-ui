package com.jeremykenedy.firetv.ui;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reports only fields the system actually returned. */
public final class DiagnosticValues {
    private DiagnosticValues() {}

    public static String contentSize(int width, int height) {
        return width > 0 && height > 0
                ? width + "x" + height
                : "Unavailable: the UI has not completed layout.";
    }

    public static String enabled(String value) {
        if (value == null) return "Unavailable: Android did not report an enabled setting.";
        if (value.equals("1")) return "On";
        if (value.equals("0")) return "Off";
        return "Unavailable: Android returned an unsupported enabled setting.";
    }

    public static String currentDream(String dump) {
        if (dump == null || dump.contains("Permission Denial")) {
            return "Unavailable: Android did not allow DreamService inspection.";
        }
        Matcher record = Pattern.compile("(?m)^\\s*mCurrentDream\\s*[:=]\\s*(.*)$").matcher(dump);
        if (!record.find())
            return "Unavailable: this Android version did not report a current Dream record.";
        String value = record.group(1).trim();
        if (value.equals("null") || value.equals("none")) return "None reported";
        Matcher component = Pattern.compile("([A-Za-z0-9_.]+/[A-Za-z0-9_.$]+)").matcher(value);
        return component.find()
                ? component.group(1)
                : "Unavailable: the current Dream record could not be identified.";
    }
}
