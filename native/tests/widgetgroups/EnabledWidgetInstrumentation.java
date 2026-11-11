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
    private static final String WEATHER = "33333333-3333-4333-8333-333333333333";
    private static final String GROUP = "slot:widget:grouped-widgets";
    private Bundle arguments;
    private Activity activity;
    private HomeContentView home;
    private Activity weatherSettings;
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
            }});
            final String weatherRaw = "[[{\"id\":\"" + WEATHER + "\",\"type\":\"Weather\",\"size\":\"Big\",\"data\":{\"location\":\"\",\"unit\":\"C\"}}],[],[],[]]";
            final Map<String, ?> singleton = context.getSharedPreferences("native_weather", 0).getAll();
            Map<String, Object> weatherLegacy = new LinkedHashMap<>(); weatherLegacy.put("widgets_config", weatherRaw); weatherLegacy.put("enable_widgets", true);
            Map<String, Object> weatherPatch = WidgetGroupMigration.supportedPatch(weatherLegacy, Collections.emptyMap());
            write(context.getSharedPreferences("native_layout", 0), Collections.singletonMap(WidgetGroupMigration.KEY, weatherPatch.get(WidgetGroupMigration.KEY)));
            LayoutPreferences.save(context, LayoutPreferences.load(context, LayoutOptions.defaults(320, 180)).withMode(LayoutOptions.Mode.WIDGETS_ONLY));
            runOnMainSync(new Runnable() { @Override public void run() { home.refreshPreferences(); home.scrollTo(0, 0); }});
            boolean weatherReady = false;
            final String[] weatherState = {"Weather view missing"};
            for (int attempt = 0; attempt < 50 && !weatherReady; attempt++) {
                final boolean[] readyState = {false};
                runOnMainSync(new Runnable() { @Override public void run() {
                    com.jeremykenedy.firetv.ui.nativeapp.widget.weather.WeatherView weather = weather(home.findViewWithTag(WEATHER));
                    if (weather != null) weatherState[0] = weather.accessibilitySummary() + "; shown=" + weather.isShown()
                            + "; visible=" + weather.getGlobalVisibleRect(new android.graphics.Rect())
                            + "; bounds=" + weather.getWidth() + "x" + weather.getHeight() + "; host=" + home.getWidth() + "x" + home.getHeight();
                    readyState[0] = weather != null && weather.accessibilitySummary().toString().contains("Choose exact location");
                }});
                weatherReady = readyState[0];
                if (!weatherReady) android.os.SystemClock.sleep(100);
            }
            check(weatherReady, "Enabled empty Weather must render its actionable setup state in Home: " + weatherState[0]);
            ActivityMonitor monitor = addMonitor("com.jeremykenedy.firetv.ui.nativeapp.widget.weather.WeatherSettingsActivity", null, false);
            runOnMainSync(new Runnable() { @Override public void run() { check(home.findViewWithTag(WEATHER).performClick(), "Open empty Weather record menu."); }});
            waitForIdleSync(); chooseFirst("Set weather location");
            weatherSettings = monitor.waitForActivityWithTimeout(5000); removeMonitor(monitor);
            check(weatherSettings != null && !weatherSettings.isFinishing(), "Empty Weather must open exact-location setup.");
            runOnMainSync(new Runnable() { @Override public void run() {
                View decor = weatherSettings.getWindow().getDecorView();
                check(((android.widget.TextView) decor.findViewWithTag("weather:units")).getText().toString().contains("Celsius"), "Setup retains the original metric choice.");
                check(((android.widget.TextView) decor.findViewWithTag("weather:label")).getText().length() == 0
                        && ((android.widget.TextView) decor.findViewWithTag("weather:latitude")).getText().length() == 0
                        && ((android.widget.TextView) decor.findViewWithTag("weather:longitude")).getText().length() == 0,
                        "Setup keeps the empty name and requires an explicit coordinate selection.");
                weatherSettings.finish(); weatherSettings = null;
            }});
            waitForIdleSync();
            check(weatherRaw.equals(context.getSharedPreferences("native_layout", 0).getString("widget_groups", null))
                    && singleton.equals(context.getSharedPreferences("native_weather", 0).getAll()),
                    "Opening and canceling setup retains exact record bytes and singleton weather choices.");
            runOnMainSync(new Runnable() { @Override public void run() {
                check(context.getSharedPreferences("native_layout", 0).edit().putString("widget_groups", "[]").commit(), "Save explicit empty groups.");
                home.refreshPreferences(); home.scrollTo(0, 0);
            }});
            boolean emptyColumns = false;
            for (int attempt = 0; attempt < 50 && !emptyColumns; attempt++) {
                final boolean[] measured = {false};
                runOnMainSync(new Runnable() { @Override public void run() {
                    View slot = home.findViewWithTag(GROUP), fourth = home.findViewWithTag("group:empty:3:0");
                    float density = activity.getResources().getDisplayMetrics().density;
                    measured[0] = slot != null && slot.getWidth() == Math.round(896 * density)
                            && fourth != null && fourth.getWidth() == Math.round(212 * density)
                            && fourth.getGlobalVisibleRect(new android.graphics.Rect());
                }});
                emptyColumns = measured[0];
                if (!emptyColumns) android.os.SystemClock.sleep(100);
            }
            check(emptyColumns, "Full Home must render explicit empty groups as four full-width visible columns.");
            runOnMainSync(new Runnable() { @Override public void run() {
                check(home.findViewWithTag("group:empty:3:0").performClick(), "Home padded column opens its editor.");
            }});
            waitForIdleSync();
            chooseFirst("Add clock");
            WidgetGroups edited = WidgetGroups.decode(context.getSharedPreferences("native_layout", 0).getString("widget_groups", ""));
            check(edited.groups.size() == 4 && edited.groups.get(0).isEmpty() && edited.groups.get(2).isEmpty()
                    && edited.groups.get(3).size() == 1 && edited.groups.get(3).get(0).type.equals("DigitalClock"),
                    "Selecting a padded column saves only the requested widget in that position.");
            result.putString("status", "PASS");
            result.putString("cases", "enabled clock/year; native focus; original hidden row; disabled card row; explicit mixed mode; four empty columns; raw retention; empty Weather setup and original units");
            result.putString("limits", "Synthetic saved settings and card; real production upgrade and physical TV acceptance are separate.");
        } catch (Throwable failed) { result.putString("status", "FAIL"); result.putString("reason", failed.toString()); }
        finally { if (activity != null) runOnMainSync(new Runnable() { @Override public void run() { if (home != null) home.onHostPause(); if (weatherSettings != null) weatherSettings.finish(); activity.finish(); }}); }
        finish(0, result);
    }
    private static com.jeremykenedy.firetv.ui.nativeapp.widget.weather.WeatherView weather(View view) {
        if (view instanceof com.jeremykenedy.firetv.ui.nativeapp.widget.weather.WeatherView)
            return (com.jeremykenedy.firetv.ui.nativeapp.widget.weather.WeatherView) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            com.jeremykenedy.firetv.ui.nativeapp.widget.weather.WeatherView result = weather(((ViewGroup) view).getChildAt(i));
            if (result != null) return result;
        }
        return null;
    }
    private void chooseFirst(String label) {
        boolean visible = false;
        for (int attempt = 0; attempt < 50 && !visible; attempt++) {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (root != null) for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(label)) {
                if (label.contentEquals(node.getText()) && node.isVisibleToUser()) { visible = true; break; }
            }
            if (!visible) android.os.SystemClock.sleep(100);
        }
        check(visible, "Native widget menu must expose " + label);
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
