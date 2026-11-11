package com.jeremykenedy.firetv.ui.nativeapp.providers;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import com.jeremykenedy.firetv.ui.nativeapp.SettingsActivity;
import com.jeremykenedy.firetv.ui.nativeapp.cards.HomeContentView;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutOptions;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutPreferences;
import com.jeremykenedy.firetv.ui.nativeapp.stateguard.GuardStore;
import com.jeremykenedy.firetv.ui.nativeapp.widgetgroups.WidgetGroupMigration;
import com.jeremykenedy.firetv.ui.nativeapp.widgetgroups.WidgetGroups;
import com.jeremykenedy.firetv.ui.nativeapp.widgetgroups.WidgetGroupsEditor;
import com.jeremykenedy.firetv.ui.nativeapp.widgetgroups.WidgetGroupsView;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/** Synthetic native layout behavior; production upgrade is a separate qualification. */
public final class EnabledWidgetInstrumentation extends Instrumentation {
    private static final String CLOCK = "11111111-1111-4111-8111-111111111111";
    private static final String YEAR = "22222222-2222-4222-8222-222222222222";
    private static final String GROUP = "slot:widget:grouped-widgets";
    private Bundle arguments;
    private Activity activity;
    private HomeContentView home;
    @Override public void onCreate(Bundle args) { super.onCreate(args); arguments = args; start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context context = getTargetContext();
            String caseId = arguments.getString("state_guard_case_id"), hash = arguments.getString("state_guard_manifest_sha256");
            check(caseId != null && hash != null && new GuardStore(context.getDataDir()).verify(caseId,
                    new LinkedHashSet<String>(Arrays.asList("shared_prefs", "files", "no_backup")), hash).matches,
                    "Completed durable StateGuard required before fixture writes.");
            String raw = "[[],[{\"id\":\"" + CLOCK + "\",\"type\":\"DigitalClock\",\"size\":\"Big\",\"data\":{}}],"
                    + "[{\"id\":\"" + YEAR + "\",\"type\":\"YearProgress\",\"size\":\"Small\",\"data\":{}}],[]]";
            Map<String, Object> legacy = new LinkedHashMap<>(); legacy.put("enable_widgets", true); legacy.put("hide_watch_next", false); legacy.put("widgets_config", raw);
            write(context.getSharedPreferences("native_layout", 0), WidgetGroupMigration.supportedPatch(legacy, Collections.emptyMap()));
            final java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(1);
            runOnMainSync(new Runnable() { @Override public void run() {
                com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.start(context, new Runnable() { @Override public void run() { ready.countDown(); } });
            }});
            check(ready.await(10, java.util.concurrent.TimeUnit.SECONDS), "Preview startup did not finish.");
            activity = startActivitySync(new Intent(context, SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            check(!activity.isFinishing(), "Native host must remain visible for interaction checks.");
            runOnMainSync(new Runnable() { @Override public void run() {
                home = new HomeContentView(activity, null, null); activity.setContentView(home); home.onHostResume();
                TvRecommendations.Feed fixture = new TvRecommendations.Feed("enabled-fixture", "Fixture cards", context.getPackageName(),
                        Collections.singletonList(new TvRecommendations.Program("fixture-item", context.getPackageName(), "Fixture title", null, "https://example.invalid/item")));
                check(home.applyResult(home.beginRefresh(), new TvRecommendations.Result(TvRecommendations.Status.READY, Collections.singletonList(fixture))), "Feed applied.");
                check(home.findViewWithTag(CLOCK) != null && home.findViewWithTag(YEAR) != null && home.findViewWithTag(GROUP) != null,
                        "Enabled migrated clock and year records render in Home.");
                check(home.cards().isEmpty(), "Original grouped layout replaces the ordinary card row.");
                check(home.findViewWithTag(CLOCK).requestFocus(), "Retained record receives native focus.");
                home.refreshPreferences();
                check(home.findViewWithTag(CLOCK).isFocused(), "Home refresh preserves retained record focus.");
                LayoutOptions saved = LayoutPreferences.load(context, LayoutOptions.defaults(320, 180));
                LayoutPreferences.save(context, saved.withMode(LayoutOptions.Mode.NONE)); home.refreshPreferences();
                check(home.findViewWithTag(GROUP) == null && home.cards().isEmpty(), "Original hidden row suppresses both widgets and cards.");
                LayoutPreferences.save(context, saved.withMode(LayoutOptions.Mode.CARDS_ONLY)); home.refreshPreferences();
                check(home.findViewWithTag(GROUP) == null && home.cards().size() == 1, "Disabled widgets restore the ordinary card row.");
                LayoutPreferences.save(context, saved.withMode(LayoutOptions.Mode.BOTH)); home.refreshPreferences();
                check(home.findViewWithTag(CLOCK) != null && home.findViewWithTag(YEAR) != null && home.cards().size() == 1,
                        "Explicit native Both mode shows grouped widgets alongside cards.");
                check(raw.equals(context.getSharedPreferences("native_layout", 0).getString("widget_groups", null)), "Visibility changes retain exact grouped JSON.");
                home.onHostPause();
                check(context.getSharedPreferences("native_layout", 0).edit().putString("widget_groups", "[]").commit(), "Save explicit empty groups.");
                WidgetGroupsView padded = new WidgetGroupsView(activity, WidgetGroups.decode("[]"), new WidgetGroupsEditor(activity, new Runnable() {
                    @Override public void run() {}
                }), 212, 270);
                activity.setContentView(padded);
                check(((ViewGroup) padded.getChildAt(0)).getChildCount() == 4 && padded.findViewWithTag("group:empty:3:0") != null,
                        "Explicit empty configuration renders the original four editable empty columns.");
                check(padded.findViewWithTag("group:empty:3:0").performClick(), "Padded column opens its editor.");
            }});
            waitForIdleSync();
            choose("Add clock");
            WidgetGroups edited = WidgetGroups.decode(context.getSharedPreferences("native_layout", 0).getString("widget_groups", ""));
            check(edited.groups.size() == 4 && edited.groups.get(0).isEmpty() && edited.groups.get(2).isEmpty()
                    && edited.groups.get(3).size() == 1 && edited.groups.get(3).get(0).type.equals("DigitalClock"),
                    "Selecting a padded column saves only the requested widget in that position.");
            result.putString("status", "PASS");
            result.putString("cases", "enabled clock/year; native focus; original hidden row; disabled card row; explicit mixed mode; four empty columns; raw retention");
            result.putString("limits", "Synthetic saved settings and card; real production upgrade and physical TV acceptance are separate.");
        } catch (Throwable failed) { result.putString("status", "FAIL"); result.putString("reason", failed.toString()); }
        finally { if (activity != null) runOnMainSync(new Runnable() { @Override public void run() { if (home != null) home.onHostPause(); activity.finish(); }}); }
        finish(0, result);
    }
    private void choose(String label) {
        AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
        check(root != null && !root.findAccessibilityNodeInfosByText(label).isEmpty(), "Native widget menu must expose " + label);
        sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_DOWN);
        sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_UP);
        sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_CENTER);
        waitForIdleSync();
    }
    private static void write(SharedPreferences preferences, Map<String, Object> values) {
        SharedPreferences.Editor editor = preferences.edit();
        for (Map.Entry<String, Object> value : values.entrySet()) editor.putString(value.getKey(), (String) value.getValue());
        check(editor.commit(), "Fixture migration publication failed.");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
