package com.jeremykenedy.firetv.ui.nativeapp.focus;

import android.content.Context;
import java.util.HashMap;
import java.util.Map;

public final class FocusPreferencesTest {
    public static void main(String[] args) {
        Context context = new Context();
        context.prefs.values.put("unrelated", "retained");
        FocusPreferences.save(context, new FocusOptions(0, 0, false, 0x123456, 27));
        FocusOptions read = FocusPreferences.load(context);
        check(read.durationMillis == 0 && read.growthPercent == 0 && !read.borderEnabled
                && read.borderRgb == 0x123456 && read.borderOpacity == 27,
                "All focus controls persist independently.");
        Map<String, Object> before = new HashMap<>(context.prefs.values);
        context.prefs.fail = true;
        try {
            FocusPreferences.save(context, FocusOptions.defaults());
            throw new AssertionError("Failed disk save was accepted.");
        } catch (IllegalStateException expected) {
            check(before.equals(context.prefs.values), "Failed commit rolls memory back without touching other settings.");
        }
        context.prefs.fail = false;
        context.prefs.values.put("duration_millis", "not an integer");
        context.prefs.values.put("border_opacity", 200);
        read = FocusPreferences.load(context);
        check(read.durationMillis == 100 && read.borderOpacity == 100 && read.borderRgb == 0x123456,
                "Malformed values fall back independently without losing valid color choices.");
        System.out.println("Focus persistence, failed-save rollback and malformed settings passed.");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
