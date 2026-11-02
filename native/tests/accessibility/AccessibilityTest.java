package com.jeremykenedy.firetv.ui.nativeapp.accessibility;

import android.content.Context;
import java.util.Collections;

public final class AccessibilityTest {
    public static void main(String[] args) {
        Context context = new Context();
        check(AccessibilityPreferences.textPercent(context) == 100 && !AccessibilityPreferences.highContrast(context), "Existing appearance is the default");
        AccessibilityPreferences.save(context, 120, true);
        check(AccessibilityPreferences.textPercent(context) == 120 && AccessibilityPreferences.highContrast(context), "Read back explicit choices");
        AccessibilityPreferences.save(context, 100, false);
        check(AccessibilityPreferences.textPercent(context) == 100 && !AccessibilityPreferences.highContrast(context), "Restore default appearance");
        context.prefs.values.put("text_percent", "120");
        context.prefs.values.put("high_contrast", "true");
        check(AccessibilityPreferences.textPercent(context) == 100 && !AccessibilityPreferences.highContrast(context), "Malformed storage uses safe defaults");
        check(context.prefs.values.get("text_percent").equals("120"), "Read fallback preserves raw stored value");
        try { AccessibilityPreferences.save(context, 200, true); throw new AssertionError("Invalid size accepted"); }
        catch (IllegalArgumentException expected) {}
        try { AccessibilityPreferences.validateSnapshot(Collections.<String,Object>singletonMap("text_percent", 105)); throw new AssertionError("Invalid setup accepted"); }
        catch (IllegalArgumentException expected) {}
        try { AccessibilityPreferences.validateSnapshot(Collections.<String,Object>singletonMap("high_contrast", "true")); throw new AssertionError("Untyped setup accepted"); }
        catch (IllegalArgumentException expected) {}
        AccessibilityPreferences.validateSnapshot(Collections.<String,Object>singletonMap("text_percent", 110));
        context.prefs.values.put("unrelated", "keep");
        context.prefs.fail = true;
        try { AccessibilityPreferences.save(context, 110, true); throw new AssertionError("Failed save reported success"); }
        catch (IllegalStateException expected) {}
        check(context.prefs.values.get("text_percent").equals("120") && context.prefs.values.get("high_contrast").equals("true"), "Failed save restores changed keys");
        check(context.prefs.values.get("unrelated").equals("keep"), "Save does not clear unrelated preferences");
        System.out.println("Readability preference checks passed");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
