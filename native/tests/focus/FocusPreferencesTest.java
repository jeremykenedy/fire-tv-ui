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
        Context motion = new Context();
        FocusPreferences.saveMotion(motion, "duration_millis", 180);
        check(FocusPreferences.legacyDefaultAppMode(motion) && !FocusPreferences.hasExplicitGrowth(motion),
                "Changing speed keeps the default app appearance and does not invent a growth choice.");
        FocusPreferences.saveMotion(motion, "growth_percent", 0);
        check(FocusPreferences.legacyDefaultAppMode(motion) && FocusPreferences.hasExplicitGrowth(motion),
                "Explicit zero growth stays active with default app style.");
        FocusPreferences.setAppStyle(motion, "color");
        check(!FocusPreferences.legacyDefaultAppMode(motion) && FocusPreferences.load(motion).growthPercent == 0
                && FocusPreferences.load(motion).durationMillis == 180, "Style selection retains independent motion.");
        FocusPreferences.setAppStyle(motion, "default");
        check(FocusPreferences.legacyDefaultAppMode(motion) && FocusPreferences.hasExplicitGrowth(motion),
                "Returning to default retains selected growth.");
        Map<String, Object> beforeStyle = new HashMap<>(motion.prefs.values);
        motion.prefs.fail = true;
        try { FocusPreferences.setAppStyle(motion, "border"); throw new AssertionError("Failed style save accepted."); }
        catch (IllegalStateException expected) { check(beforeStyle.equals(motion.prefs.values), "Failed style save restores all prior values."); }
        motion.prefs.fail = false;
        motion.prefs.fail = true;
        try { FocusPreferences.saveMotion(motion, "growth_percent", 3); throw new AssertionError("Failed motion save accepted."); }
        catch (IllegalStateException expected) { check(beforeStyle.equals(motion.prefs.values), "Failed motion save restores value and style."); }
        motion.prefs.fail = false;
        Context appearance = new Context();
        check(!FocusPreferences.appBorder(appearance) && FocusPreferences.iconShadow(appearance)
                && FocusPreferences.iconRadius(appearance) == 16, "Original app icon defaults are read without writes.");
        check(FocusPreferences.appColor(appearance, true) == 0x263a5c
                && FocusPreferences.appColor(appearance, false) == 0xffffff, "Original app focus color defaults.");
        check(FocusPreferences.iconBackgroundOpacity(appearance) == 0, "Absent app background stays transparent.");
        check(appearance.prefs.values.isEmpty(), "Reading icon defaults does not write preferences.");
        appearance.prefs.values.put("color_back_color_argb", 0xffe53935);
        appearance.prefs.values.put("color_back_color_opacity", 50);
        check(FocusPreferences.iconBackgroundColor(appearance) == 0xffe53935
                && FocusPreferences.iconBackgroundOpacity(appearance) == 50, "Original typed app background color and opacity are read.");
        FocusPreferences.saveAppColorAndOpacity(appearance, true, 0xffe53935, 50);
        check(FocusPreferences.legacyDefaultAppMode(appearance) && FocusPreferences.load(appearance).fillRgb == 0xe53935
                && FocusPreferences.load(appearance).fillOpacity == 50, "Focus color and opacity retain default app style.");
        Map<String, Object> beforeColorFailure = new HashMap<>(appearance.prefs.values);
        appearance.prefs.fail = true;
        try { FocusPreferences.saveAppColorAndOpacity(appearance, true, 0xff112233, 25); throw new AssertionError("Failed color save accepted."); }
        catch (IllegalStateException expected) { check(beforeColorFailure.equals(appearance.prefs.values), "Color failure restores color, opacity and style."); }
        appearance.prefs.fail = false;
        FocusPreferences.setAppBorder(appearance, true);
        check(FocusPreferences.appBorder(appearance) && FocusPreferences.legacyDefaultAppMode(appearance),
                "Border can be enabled without replacing the default app style.");
        FocusPreferences.saveIconOption(appearance, "icon_shadow", false);
        FocusPreferences.saveIconOption(appearance, "icon_radius", 18);
        check(!FocusPreferences.iconShadow(appearance) && FocusPreferences.iconRadius(appearance) == 18,
                "Explicit shadow and radius persist.");
        Map<String, Object> beforeIconFailure = new HashMap<>(appearance.prefs.values);
        appearance.prefs.fail = true;
        try { FocusPreferences.saveIconOption(appearance, "icon_radius", 20); throw new AssertionError("Failed icon save accepted."); }
        catch (IllegalStateException expected) { check(beforeIconFailure.equals(appearance.prefs.values), "Failed icon save restores prior values."); }
        appearance.prefs.fail = false;
        SeparateContext app = new SeparateContext();
        check(FocusPreferences.appGrowth(app) == 18 && FocusPreferences.appShadowSize(app) == 18,
                "Absent app sizes retain original eighteen percent and eighteen dp without writes.");
        app.focus.values.put("app_focus_style","border");
        check(FocusPreferences.appGrowth(app) == 18,"App growth default is independent of color and border style.");
        app.focus.values.put("growth_percent",7);
        check(FocusPreferences.appGrowth(app) == 7,"Existing explicit source growth remains the app compatibility fallback.");
        Map<String,Object> generic = new HashMap<>(app.focus.values);
        FocusPreferences.saveAppSize(app,FocusPreferences.APP_GROWTH,30);
        FocusPreferences.saveAppShadow(app,38);
        check(FocusPreferences.appGrowth(app) == 30 && FocusPreferences.appShadowSize(app) == 38
                && generic.equals(app.focus.values),"Stable app sizing wins without changing generic focus choices.");
        FocusPreferences.saveAppShadow(app,0);
        check(FocusPreferences.appShadowSize(app) == 0 && Integer.valueOf(38).equals(app.legacy.values.get(FocusPreferences.APP_SHADOW_SIZE)),
                "Shadow Off is authoritative and retains the selected shadow size.");
        Map<String,Object> beforeAppFailure = new HashMap<>(app.legacy.values);
        app.legacy.fail=true;
        try { FocusPreferences.saveAppShadow(app,18);throw new AssertionError("Failed shadow save accepted"); }
        catch (IllegalStateException expected) {check(beforeAppFailure.equals(app.legacy.values),"Failed shadow save restores enabled state and size exactly.");}
        app.legacy.fail=false;
        FocusPreferences.saveAppShadow(app,18);
        check(FocusPreferences.appShadowSize(app) == 18 && !app.legacy.values.containsKey(FocusPreferences.APP_SHADOW_SIZE),"Default shadow restores original absent-size semantics.");
        for (String key : new String[]{FocusPreferences.APP_GROWTH,FocusPreferences.APP_SHADOW_SIZE}) {
            for (Object bad : new Object[]{-1,39,18L,"18",true}) {
                app.legacy.values.put(key,bad);
                try {if(key.equals(FocusPreferences.APP_GROWTH))FocusPreferences.appGrowth(app);else FocusPreferences.appShadowSize(app);throw new AssertionError("Malformed app size accepted");}
                catch (IllegalStateException expected) {check(bad.equals(app.legacy.values.get(key)),"Malformed app size is never overwritten.");}
            }
            app.legacy.values.remove(key);
        }
        System.out.println("Focus persistence, failed-save rollback and malformed settings passed.");
    }

    private static final class SeparateContext extends Context {
        final android.content.SharedPreferences focus = new android.content.SharedPreferences();
        final android.content.SharedPreferences legacy = new android.content.SharedPreferences();
        @Override public android.content.SharedPreferences getSharedPreferences(String name,int mode) {return name.equals("native_focus")?focus:legacy;}
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
