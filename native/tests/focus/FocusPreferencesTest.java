package com.jeremykenedy.firetv.ui.nativeapp.focus;

import android.content.Context;
import java.util.HashMap;
import java.util.Map;

public final class FocusPreferencesTest {
    public static void main(String[] args) {
        Context context = new Context();
        check(FocusPreferences.legacyDefaultAppMode(context), "Fresh Home uses artwork-only app tiles.");
        check(context.prefs.values.isEmpty(), "Reading Home defaults does not create explicit settings.");
        context.prefs.values.put("unrelated", "retained");
        check(!FocusPreferences.hasExplicitSettings(context), "Unrelated data does not override legacy presentation.");
        context.prefs.values.put("growth_percent", 0);
        check(FocusPreferences.hasExplicitSettings(context), "An explicit zero overrides legacy presentation.");
        context.prefs.values.put("growth_percent", "malformed");
        check(FocusPreferences.hasExplicitSettings(context), "Malformed explicit data still prevents silently replacing a choice.");
        context.prefs.values.remove("growth_percent");
        context.prefs.values.put(FocusOptions.LEGACY_APP_DEFAULT, true);
        check(FocusPreferences.legacyDefaultAppMode(context), "Legacy marker enables app mode when native choices are absent.");
        Map<String, Object> beforeLegacy = new HashMap<>(context.prefs.values);
        context.prefs.fail = true;
        try { FocusPreferences.save(context, FocusOptions.defaults()); throw new AssertionError("Failed save accepted."); }
        catch (IllegalStateException expected) { check(beforeLegacy.equals(context.prefs.values), "Failed save exactly restores the legacy marker and previous settings."); }
        context.prefs.fail = false;
        context.prefs.values.put("duration_millis", 42L);
        context.prefs.values.put("growth_percent", 2.5f);
        context.prefs.values.put("border_enabled", new java.util.LinkedHashSet<>(java.util.Arrays.asList("one", "two")));
        Map<String, Object> malformedBefore = new HashMap<>(context.prefs.values);
        context.prefs.fail = true;
        try { FocusPreferences.save(context, FocusOptions.defaults()); throw new AssertionError("Failed save accepted."); }
        catch (IllegalStateException expected) { check(malformedBefore.equals(context.prefs.values), "Failed save preserves long, float, set and marker values exactly."); }
        context.prefs.fail = false;
        FocusPreferences.save(context, new FocusOptions(0, 0, false, 0x123456, 27));
        check(!context.prefs.values.containsKey(FocusOptions.LEGACY_APP_DEFAULT), "Explicit source focus save exits legacy app mode.");
        FocusOptions read = FocusPreferences.load(context);
        check(read.durationMillis == 0 && read.growthPercent == 0 && !read.borderEnabled
                && read.borderRgb == 0x123456 && read.borderOpacity == 27,
                "All focus controls persist independently.");
        FocusPreferences.save(context, new FocusOptions(180, 7, true, 0x123456, 27, false, 0xabcd12, 43));
        read = FocusPreferences.load(context);
        check(read.growthPercent == 7 && !read.fillEnabled && read.fillRgb == 0xabcd12
                && read.fillOpacity == 43 && read.borderRgb == 0x123456 && read.borderOpacity == 27,
                "Fill, border and custom growth persist independently.");
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
        context.prefs.values.put("fill_rgb", -1);
        context.prefs.values.put("fill_opacity", "invalid");
        read = FocusPreferences.load(context);
        check(!read.fillEnabled && read.fillRgb == 0x3c3251 && read.fillOpacity == 100,
                "Invalid fill fields fall back without enabling a disabled fill.");
        context.prefs.values.remove("fill_enabled");
        context.prefs.values.remove("fill_rgb");
        context.prefs.values.remove("fill_opacity");
        read = FocusPreferences.load(context);
        check(read.fillEnabled && read.fillRgb == 0x3c3251 && read.fillOpacity == 100
                && read.borderRgb == 0x123456,
                "Existing preferences retain their border and historical fill appearance.");
        System.out.println("Focus persistence, failed-save rollback and malformed settings passed.");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
