package com.jeremykenedy.firetv.ui.nativeapp.widget.weather;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.graphics.Color;
import android.os.SystemClock;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.SettingsActivity;
import com.jeremykenedy.firetv.ui.nativeapp.widgetgroups.WidgetGroups;
import com.jeremykenedy.firetv.ui.nativeapp.widgetgroups.WidgetGroupsView;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

public final class WeatherGroupRenderInstrumentation extends Instrumentation {
    private Activity activity;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle(); File directory = null;
        try {
            activity = startActivitySync(new Intent(getTargetContext(), SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            directory = File.createTempFile("weather-render", "", getTargetContext().getCacheDir());
            check(directory.delete() && directory.mkdir(), "Could not create isolated fixture cache.");
            long now = System.currentTimeMillis(); Map<String, String> headers = new LinkedHashMap<>(); headers.put("cache-control", "max-age=3600");
            WeatherCache cache = new WeatherCache(directory);
            cache.write("points", new WeatherCache.Entry(WeatherFixture.points(false), headers, now, 86400000));
            cache.write("hourly", new WeatherCache.Entry(WeatherFixture.hourly(now, false), headers, now, 3600000));
            WeatherOptions options = WeatherOptions.location("Portland, OR", "39.7456", "-97.0892", false, false);
            WeatherForecast fresh = WeatherForecast.parse(cache.read("points", 86400000).body, cache.read("hourly", 3600000).body, options, now, now, now + 3600000, false);
            WeatherForecast stale = WeatherForecast.parse(cache.read("points", 86400000).body, cache.read("hourly", 3600000).body, options, now, now, now + 3600000, true);
            final Throwable[] failure = new Throwable[1];
            runOnMainSync(new Runnable() { @Override public void run() { try {
                checkSize("Small", options, fresh, stale); checkSize("Big", options, fresh, stale);
                checkClockYear();
            } catch (Throwable problem) { failure[0] = problem; } } });
            if (failure[0] != null) throw new AssertionError("Grouped rendering failed", failure[0]);
            final WeatherForecast[] visual = {fresh, stale,
                    WeatherForecast.state(WeatherForecast.State.SETUP_REQUIRED, "setup", now),
                    WeatherForecast.state(WeatherForecast.State.UNAVAILABLE, "error", now)};
            String[] names = {"fresh", "stale", "setup", "error"};
            for (int i = 0; i < names.length; i++) {
                final int index = i; final String name = names[i];
                runOnMainSync(new Runnable() { @Override public void run() { showVisual(name, options, visual[index]); } });
                waitForIdleSync();
                Bundle marker = new Bundle(); marker.putString("visual_state", name); sendStatus(1, marker);
                SystemClock.sleep(8000);
            }
            result.putString("status", "PASS"); result.putString("limits", "Synthetic cached forecast, native measured group view, default device font scale. Home placement, live network and physical TV separate.");
        } catch (Throwable failure) { result.putString("status", "FAIL"); result.putString("reason", failure.toString() + (failure.getCause() == null ? "" : ": " + failure.getCause())); }
        finally {
            if (activity != null) { final Activity current = activity; runOnMainSync(new Runnable() { @Override public void run() { current.finish(); } }); waitForIdleSync(); }
            if (directory != null) { File[] files = directory.listFiles(); if (files != null) for (File file : files) file.delete(); result.putBoolean("fixture_cache_removed", directory.delete()); }
        }
        if (!result.getBoolean("fixture_cache_removed", false)) result.putString("status", "FAIL");
        finish(0, result);
    }
    private void checkSize(String size, WeatherOptions options, WeatherForecast fresh, WeatherForecast stale) {
        WidgetGroupsView group = group(size);
        int width = dp(320), height = dp(180);
        group.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)); group.layout(0, 0, width, height);
        WeatherView weather = findWeather(group); check(weather != null, size + " weather view missing.");
        weather.render(options, fresh); layout(group, width, height);
        checkBounds(weather, size); String content = text(weather);
        check(content.contains("Portland, OR") && content.contains("54 °F") && content.contains("NWS") && content.contains("Updated"), size + " missing forecast, unit or attribution: " + content);
        String accessible = node(group, "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        check(accessible.contains("Portland, OR") && accessible.contains("54 °F") && accessible.contains("NWS"), size + " fresh weather inaccessible: " + accessible);
        LinearLayout periods = (LinearLayout) weather.getChildAt(2);
        check(periods.getChildCount() == (size.equals("Small") ? 1 : 2), size + " forecast period count.");
        weather.render(options, stale); layout(group, width, height); checkBounds(weather, size);
        check(text(weather).contains("Stale") && text(weather).contains("Updated"), size + " stale source must remain visible.");
        check(node(group, "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa").contains("Stale"), size + " stale status inaccessible.");
        weather.render(options, WeatherForecast.state(WeatherForecast.State.SETUP_REQUIRED, "setup", System.currentTimeMillis())); layout(group, width, height); checkBounds(weather, size);
        check(text(weather).contains("Choose a weather location") && text(weather).contains("National Weather Service"), size + " setup state missing.");
        weather.render(options, WeatherForecast.state(WeatherForecast.State.UNAVAILABLE, "error", System.currentTimeMillis())); layout(group, width, height); checkBounds(weather, size);
        check(text(weather).contains("Forecast unavailable") && text(weather).contains("National Weather Service"), size + " error state missing.");
        accessible = node(group, "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        check(accessible.contains("Portland, OR") && accessible.contains("Forecast unavailable"), size + " error inaccessible: " + accessible);
    }
    private String node(View root, String id) {
        View frame = root.findViewWithTag(id);
        check(frame != null && frame.isFocusable(), "Record must remain a focus target: " + id);
        android.view.accessibility.AccessibilityNodeInfo info = frame.createAccessibilityNodeInfo();
        CharSequence description = info.getContentDescription();
        return description == null ? "" : description.toString();
    }
    private void checkClockYear() {
        final String clockId = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
        final String yearId = "cccccccc-cccc-cccc-cccc-cccccccccccc";
        String raw = "[[{\"id\":\"" + clockId + "\",\"type\":\"DigitalClock\",\"size\":\"Big\",\"data\":{}}],"
                + "[{\"id\":\"" + yearId + "\",\"type\":\"YearProgress\",\"size\":\"Big\",\"data\":{}}]]";
        final boolean[] clicked = {false};
        WidgetGroupsView groups = new WidgetGroupsView(activity, WidgetGroups.decode(raw), new WidgetGroupsView.Editor() {
            @Override public void open(int groupIndex, int entryIndex, WidgetGroups.Entry entry) { clicked[0] = true; }
        }, 320, 180);
        View clockFrame = groups.findViewWithTag(clockId);
        com.jeremykenedy.firetv.ui.nativeapp.widget.ClockWidgetView clock = findClock(clockFrame);
        check(clock != null, "Clock content missing.");
        ((TextView) clock.getChildAt(0)).setText("12:34");
        ((TextView) clock.getChildAt(1)).setText("Monday, November 2");
        String description = node(groups, clockId);
        check(description.contains("12:34") && description.contains("Monday, November 2"), "Clock inaccessible: " + description);
        ((TextView) clock.getChildAt(0)).setText("12:35");
        check(node(groups, clockId).contains("12:35"), "Clock node must expose the latest visible value.");
        description = node(groups, yearId);
        check(description.contains("Year") && description.contains("%"), "Year progress inaccessible: " + description);
        check(clockFrame.performClick() && clicked[0], "Record click must still open its editor.");
        check(clock.getImportantForAccessibility() == View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
                "Record contents must not create duplicate accessibility targets.");
    }
    private com.jeremykenedy.firetv.ui.nativeapp.widget.ClockWidgetView findClock(View view) {
        if (view instanceof com.jeremykenedy.firetv.ui.nativeapp.widget.ClockWidgetView)
            return (com.jeremykenedy.firetv.ui.nativeapp.widget.ClockWidgetView) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            com.jeremykenedy.firetv.ui.nativeapp.widget.ClockWidgetView result = findClock(((ViewGroup) view).getChildAt(i));
            if (result != null) return result;
        }
        return null;
    }
    private WidgetGroupsView group(String size) {
        String raw = "[[{\"id\":\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\",\"type\":\"Weather\",\"size\":\"" + size + "\",\"data\":{\"location\":\"Portland, OR\",\"unit\":\"F\"}}]]";
        return new WidgetGroupsView(activity, WidgetGroups.decode(raw), new WidgetGroupsView.Editor() {
            @Override public void open(int groupIndex, int entryIndex, WidgetGroups.Entry entry) { }
        }, 320, 180);
    }
    private void showVisual(String state, WeatherOptions options, WeatherForecast forecast) {
        LinearLayout page = new LinearLayout(activity); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(24), dp(24), dp(24), dp(24)); page.setBackgroundColor(Color.rgb(20, 25, 35));
        TextView heading = new TextView(activity); heading.setTextColor(Color.WHITE); heading.setTextSize(20); heading.setText("Weather: " + state + "   Small / Big"); page.addView(heading);
        LinearLayout row = new LinearLayout(activity); row.setOrientation(LinearLayout.HORIZONTAL); page.addView(row);
        for (String size : new String[]{"Small", "Big"}) {
            WidgetGroupsView group = group(size); WeatherView weather = findWeather(group); weather.render(options, forecast);
            LinearLayout.LayoutParams bounds = new LinearLayout.LayoutParams(dp(320), dp(180)); bounds.rightMargin = dp(16); row.addView(group, bounds);
        }
        activity.setContentView(page);
    }
    private void layout(View group, int width, int height) { group.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)); group.layout(0, 0, width, height); }
    private void checkBounds(WeatherView weather, String size) {
        check(weather.getPaddingLeft() >= dp(12) && weather.getPaddingRight() >= dp(12)
                && weather.getPaddingTop() >= dp(6) && weather.getPaddingBottom() >= dp(6), size + " missing grouped tile inset.");
        for (int i = 0; i < weather.getChildCount(); i++) {
            View child = weather.getChildAt(i); if (child.getVisibility() == View.GONE) continue;
            check(child.getBottom() <= weather.getHeight() - weather.getPaddingBottom(), size + " content exceeds padded tile height.");
            if (child instanceof TextView) {
                TextView text = (TextView) child;
                check(text.getLayout() != null && text.getLayout().getEllipsisCount(0) == 0, size + " state or attribution truncated: " + text.getText());
            }
        }
        checkTextHeights(weather, size);
    }
    private void checkTextHeights(View view, String size) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            check(text.getLayout() != null && text.getLayout().getLineBottom(text.getLayout().getLineCount() - 1)
                    <= text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom(),
                    size + " text line clipped vertically: " + text.getText());
        }
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) checkTextHeights(((ViewGroup) view).getChildAt(i), size);
    }
    private WeatherView findWeather(View view) { if (view instanceof WeatherView) return (WeatherView) view; if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) { WeatherView found = findWeather(((ViewGroup) view).getChildAt(i)); if (found != null) return found; } return null; }
    private String text(View view) { StringBuilder out = new StringBuilder(); if (view instanceof TextView && view.getVisibility() == View.VISIBLE) out.append(((TextView) view).getText()).append('\n'); if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) out.append(text(((ViewGroup) view).getChildAt(i))); return out.toString(); }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
