package com.jeremykenedy.firetv.ui.nativeapp.widget.weather;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class WeatherInstrumentation extends Instrumentation {
    private Activity activity;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        SharedPreferences preferences = getTargetContext().getSharedPreferences(WeatherPreferences.FILE, Context.MODE_PRIVATE);
        Map<String, ?> before = preferences.getAll();
        File directory = new File(getTargetContext().getCacheDir(), "weather-hourly");
        Map<String, byte[]> cacheBefore = new LinkedHashMap<>();
        Map<String, Long> cacheTimes = new LinkedHashMap<>();
        boolean cacheExisted = directory.isDirectory(); boolean snapshot = false;
        java.util.List<File> temporaryDirectories = new java.util.ArrayList<>();
        try {
            File[] files = directory.listFiles(); long size = 0;
            if (files != null) for (File file : files) {
                check(file.isFile() && file.length() <= WeatherCache.MAX_FILE && cacheBefore.size() < 8, "Refuse an unexpected cache fixture shape.");
                size += file.length(); check(size <= 5 * 1024 * 1024, "Cache fixture snapshot is bounded.");
                cacheBefore.put(file.getName(), Files.readAllBytes(file.toPath())); cacheTimes.put(file.getName(), file.lastModified());
            }
            snapshot = true;
            long now = System.currentTimeMillis();
            WeatherOptions selected = WeatherOptions.location("Weather fixture", "39.7456", "-97.0892", true, true);
            WeatherCache cache = new WeatherCache(directory); Map<String,String> headers = new LinkedHashMap<>();
            headers.put("cache-control", "max-age=3600");
            cache.write("https://api.weather.gov/points/39.7456,-97.0892", new WeatherCache.Entry(WeatherFixture.points(false), headers, now, 86400000));
            cache.write("https://api.weather.gov/gridpoints/TOP/32,81/forecast/hourly?units=si", new WeatherCache.Entry(WeatherFixture.hourly(now, true), headers, now, 3600000));
            WeatherPreferences.save(getTargetContext(), WeatherOptions.defaults());
            check(!new WeatherWidgetProvider().defaultVisible(), "Weather is not enabled on Home implicitly.");
            activity = startActivitySync(new Intent(getTargetContext(), WeatherSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            awaitWindow();
            edit("weather:label", "Weather fixture"); edit("weather:latitude", "91"); edit("weather:longitude", "-97.0892"); click("weather:save");
            check(!WeatherPreferences.load(getTargetContext()).configured, "Invalid native coordinate input must not save or start weather requests.");
            edit("weather:latitude", "39.7456"); click("weather:units"); click("weather:time"); click("weather:save");
            WeatherOptions stored = WeatherPreferences.load(getTargetContext());
            check(stored.configured && stored.metric && stored.deviceTime && stored.latitude().equals("39.7456"), "Native Save persists explicit location, metric units and TV time zone.");
            awaitText("°C"); awaitText("National Weather Service"); awaitText("TV time:");
            final WeatherForecast ready = WeatherForecast.parse(WeatherFixture.points(false), WeatherFixture.hourly(now, true), selected, now, now, now + 3600000, false);
            final WeatherForecast stale = WeatherForecast.parse(WeatherFixture.points(false), WeatherFixture.hourly(now, true), selected, now, now, now + 60000, true);
            final WeatherView[] view = new WeatherView[1]; final String[] rendered = new String[1];
            onMain(new Runnable() { @Override public void run() {
                FrameLayout frame = new FrameLayout(activity); view[0] = new WeatherView(activity);
                frame.addView(view[0], new FrameLayout.LayoutParams(dp(440), dp(300))); activity.setContentView(frame);
                view[0].render(selected, stale); rendered[0] = text(view[0]);
            } });
            check(rendered[0].contains("Stale saved forecast") && rendered[0].contains("Updated") && rendered[0].contains("Checked")
                    && rendered[0].contains("National Weather Service") && rendered[0].contains("°C"), "Native stale rendering preserves source, timestamps and actual units.");
            onMain(new Runnable() { @Override public void run() { view[0].render(selected, ready); view[0].start(); view[0].start(); view[0].stop(); } });
            try (final WeatherFixture fixture = new WeatherFixture()) {
                File temporary = File.createTempFile("weather-proof-http", "", getTargetContext().getCacheDir()); temporary.delete(); temporary.mkdirs(); temporaryDirectories.add(temporary);
                WeatherForecast http = fixture.repository(temporary).read(selected, fixture.origin, new WeatherRequest());
                check(http.state == WeatherForecast.State.READY && fixture.requests.get() == 2, "Native HTTP wrapper discovers points and forecast with identifying headers.");
                fixture.assertHealthy(); clear(temporary); temporary.delete();
            }
            try (final WeatherFixture fixture = new WeatherFixture()) {
                fixture.mode = "stall"; final AtomicInteger callbacks = new AtomicInteger(); final WeatherLoader[] loader = new WeatherLoader[1];
                File temporary = File.createTempFile("weather-proof-cancel", "", getTargetContext().getCacheDir()); temporary.delete(); temporary.mkdirs(); temporaryDirectories.add(temporary);
                final WeatherRepository repository = fixture.repository(temporary);
                onMain(new Runnable() { @Override public void run() {
                    loader[0] = new WeatherLoader(getTargetContext(), new WeatherLoader.Listener() { @Override public void onForecast(WeatherOptions options, WeatherForecast forecast) { callbacks.incrementAndGet(); } }, repository);
                    loader[0].start();
                } });
                check(fixture.stalled.await(3, TimeUnit.SECONDS), "Native stalled forecast request must begin.");
                final long[] elapsed = new long[1];
                onMain(new Runnable() { @Override public void run() { long start = SystemClock.uptimeMillis(); loader[0].close(); elapsed[0] = SystemClock.uptimeMillis() - start; } });
                check(elapsed[0] < 250, "Stopping weather must not block the UI thread on network teardown.");
                check(fixture.disconnected.await(3, TimeUnit.SECONDS), "Cancellation must release the native socket."); waitForIdleSync();
                check(callbacks.get() == 0, "Stopped weather loader must not deliver stale activity callbacks.");
                clear(temporary); temporary.delete();
            }
            result.putString("status", "PASS"); result.putBoolean("native_coordinate_units_zone_save", true);
            result.putBoolean("native_source_timestamps_stale_render", true); result.putBoolean("native_http_and_nonblocking_cancel", true);
            result.putString("limits", "Synthetic cached and localhost weather fixtures only. Direct weather settings activity exercised; shared Widgets route, live NWS availability and physical TV visual acceptance need separate proof.");
        } catch (Throwable failure) { result.putString("status", "FAIL"); result.putString("reason", failure.toString()); }
        finally {
            if (activity != null) try { onMain(new Runnable() { @Override public void run() { activity.finish(); } }); waitForIdleSync(); } catch (Throwable failure) { result.putString("activity_cleanup_error", failure.toString()); }
            for (File temporary : temporaryDirectories) { clear(temporary); temporary.delete(); }
            result.putBoolean("weather_preferences_restored", restore(preferences, before));
            if (snapshot) try {
                clear(directory); if (!cacheBefore.isEmpty()) directory.mkdirs();
                for (Map.Entry<String,byte[]> entry : cacheBefore.entrySet()) {
                    File file = new File(directory, entry.getKey()); Files.write(file.toPath(), entry.getValue()); file.setLastModified(cacheTimes.get(entry.getKey()));
                }
                if (!cacheExisted && cacheBefore.isEmpty()) directory.delete();
                boolean same = true; File[] restored = directory.listFiles(); int count = restored == null ? 0 : restored.length;
                same &= count == cacheBefore.size();
                for (Map.Entry<String,byte[]> entry : cacheBefore.entrySet()) same &= java.util.Arrays.equals(entry.getValue(), Files.readAllBytes(new File(directory, entry.getKey()).toPath()));
                result.putBoolean("weather_cache_restored", same);
            } catch (Throwable failure) { result.putBoolean("weather_cache_restored", false); result.putString("cache_cleanup_error", failure.toString()); }
        }
        finish(0, result);
    }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    private void onMain(final Runnable action) {
        final Throwable[] error = new Throwable[1]; runOnMainSync(new Runnable() { @Override public void run() { try { action.run(); } catch (Throwable failure) { error[0] = failure; } } });
        if (error[0] != null) throw new AssertionError("Native test action failed", error[0]);
    }
    private void awaitWindow() {
        long end = SystemClock.uptimeMillis() + 3000;
        while (SystemClock.uptimeMillis() < end) { final boolean[] focused = new boolean[1]; onMain(new Runnable() { @Override public void run() { focused[0] = activity.hasWindowFocus(); } }); if (focused[0]) return; SystemClock.sleep(30); }
        throw new AssertionError("Weather settings must take window focus.");
    }
    private void edit(final String tag, final String value) { onMain(new Runnable() { @Override public void run() { ((EditText) activity.getWindow().getDecorView().findViewWithTag(tag)).setText(value); } }); }
    private void click(final String tag) {
        awaitWindow(); final boolean[] focused = new boolean[1];
        onMain(new Runnable() { @Override public void run() { View view = activity.getWindow().getDecorView().findViewWithTag(tag); focused[0] = view != null && view.isAttachedToWindow() && view.requestFocus(); } });
        check(focused[0], "Native weather action must receive focus: " + tag); sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER); waitForIdleSync();
    }
    private void awaitText(final String expected) {
        long end = SystemClock.uptimeMillis() + 4000;
        while (SystemClock.uptimeMillis() < end) { final boolean[] found = new boolean[1]; onMain(new Runnable() { @Override public void run() { found[0] = text(activity.getWindow().getDecorView()).contains(expected); } }); if (found[0]) return; SystemClock.sleep(30); }
        throw new AssertionError("Expected native weather content: " + expected);
    }
    private String text(View view) { StringBuilder result = new StringBuilder(); if (view instanceof TextView) result.append(((TextView) view).getText()).append('\n'); if (view instanceof ViewGroup) for (int i=0;i<((ViewGroup) view).getChildCount();i++) result.append(text(((ViewGroup)view).getChildAt(i))); return result.toString(); }
    private void clear(File directory) { File[] files = directory.listFiles(); if (files != null) for (File file : files) file.delete(); }
    @SuppressWarnings("unchecked") private boolean restore(SharedPreferences preferences, Map<String, ?> values) {
        SharedPreferences.Editor edit = preferences.edit().clear();
        for (Map.Entry<String, ?> entry : values.entrySet()) { Object value = entry.getValue(); String key = entry.getKey();
            if (value instanceof String) edit.putString(key,(String)value); else if(value instanceof Integer)edit.putInt(key,(Integer)value);
            else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value); else if(value instanceof Long)edit.putLong(key,(Long)value);
            else if(value instanceof Float)edit.putFloat(key,(Float)value); else if(value instanceof Set)edit.putStringSet(key,(Set<String>)value);
        }
        return edit.commit() && values.equals(preferences.getAll());
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
