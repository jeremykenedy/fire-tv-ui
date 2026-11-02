package com.jeremykenedy.firetv.ui.nativeapp.widget.weather;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.widgetgroups.WidgetGroups;
import com.jeremykenedy.firetv.ui.nativeapp.widgetgroups.WidgetGroupsStore;
import com.jeremykenedy.firetv.ui.nativeapp.widgetgroups.WidgetGroupMigration;
import com.jeremykenedy.firetv.ui.nativeapp.widgetgroups.WeatherGroupOptions;
import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

public final class WeatherGroupInstrumentation extends Instrumentation {
    private static final String A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
    private Activity activity;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        Context context = getTargetContext();
        WidgetGroupsStore store = new WidgetGroupsStore(context);
        SharedPreferences singleton = context.getSharedPreferences(WeatherPreferences.FILE, Context.MODE_PRIVATE);
        Map<String, ?> singletonBefore = singleton.getAll();
        String before = null; boolean groupSnapshot = false;
        File directory = new File(context.getCacheDir(), "weather-hourly");
        Map<String, byte[]> files = new LinkedHashMap<>(); Map<String, Long> times = new LinkedHashMap<>();
        boolean existed = directory.isDirectory(), cacheSnapshot = false;
        try {
            before = store.raw(); groupSnapshot = true;
            File[] initial = directory.listFiles(); long size = 0;
            if (initial != null) for (File file : initial) {
                check(file.isFile() && file.length() <= WeatherCache.MAX_FILE && files.size() < 8, "Unexpected weather cache shape.");
                size += file.length(); check(size <= 5 * 1024 * 1024, "Weather cache exceeds fixture limit.");
                files.put(file.getName(), Files.readAllBytes(file.toPath())); times.put(file.getName(), file.lastModified());
            }
            cacheSnapshot = true;
            long now = System.currentTimeMillis(); Map<String, String> headers = new LinkedHashMap<>(); headers.put("cache-control", "max-age=3600");
            WeatherCache cache = new WeatherCache(directory);
            cache.write("https://api.weather.gov/points/39.7456,-97.0892", new WeatherCache.Entry(WeatherFixture.points(false), headers, now, 86400000));
            for (String units : new String[]{"us", "si"}) cache.write("https://api.weather.gov/gridpoints/TOP/32,81/forecast/hourly?units=" + units, new WeatherCache.Entry(WeatherFixture.hourly(now, true), headers, now, 3600000));
            String initialGroups = "[[{\"id\":\"" + A + "\",\"type\":\"Weather\",\"size\":\"Small\",\"data\":{\"location\":\"Legacy A\",\"unit\":\"F\"}},{\"id\":\"" + B + "\",\"type\":\"Weather\",\"size\":\"Small\",\"data\":{\"location\":\"Legacy B\",\"unit\":\"C\"}}]]";
            check(store.save(before, WidgetGroups.decode(initialGroups)), "Could not install test group.");
            open(A); set("weather:label", "Fixture A"); set("weather:latitude", "39.7456"); set("weather:longitude", "-97.0892"); click("weather:save"); close();
            open(B); set("weather:label", "Fixture B"); set("weather:latitude", "39.7456"); set("weather:longitude", "-97.0892"); click("weather:save"); close();
            WidgetGroups groups = store.load(); WeatherOptions first = WeatherGroupOptions.read(groups.groups.get(0).get(0)); WeatherOptions second = WeatherGroupOptions.read(groups.groups.get(0).get(1));
            check(first.configured && first.label.equals("Fixture A") && !first.metric && second.configured && second.label.equals("Fixture B") && second.metric, "Records must retain distinct choices.");
            check(groups.groups.get(0).get(0).data.get("location").equals("Legacy A") && groups.groups.get(0).get(1).data.get("unit").equals("C"), "Legacy fields must remain intact.");
            check(singletonBefore.equals(singleton.getAll()), "Group editing must not modify singleton weather settings.");
            open(A); check(text("weather:label").equals("Fixture A") && text("weather:units").contains("Fahrenheit"), "First record must reopen with its choices."); close();
            open(B); check(text("weather:label").equals("Fixture B") && text("weather:units").contains("Celsius"), "Second record must reopen with its choices.");
            String previous = store.raw(); check(store.save(previous, store.load().appendEmptyGroup()), "Concurrent change must save.");
            String concurrent = store.raw(); set("weather:label", "Stale edit"); click("weather:save");
            check(concurrent.equals(store.raw()), "A stale editor must not overwrite concurrent group changes."); close();
            open(B); check(text("weather:label").equals("Fixture B"), "Rejected stale edit must not persist."); close();
            check(singletonBefore.equals(singleton.getAll()), "Singleton settings must remain untouched.");
            result.putString("status", "PASS"); result.putString("limits", "Synthetic cache and direct group settings only; Home routing and physical TV are separate proof.");
        } catch (Throwable failure) { result.putString("status", "FAIL"); result.putString("reason", failure.toString()); }
        finally {
            try { close(); } catch (Throwable failure) { result.putString("activity_cleanup_error", failure.toString()); }
            if (groupSnapshot) try {
                SharedPreferences layout = context.getSharedPreferences("native_layout", Context.MODE_PRIVATE);
                SharedPreferences.Editor edit = layout.edit();
                if (before == null) edit.remove(WidgetGroupMigration.KEY); else edit.putString(WidgetGroupMigration.KEY, before);
                boolean restored = edit.commit();
                result.putBoolean("groups_restored", restored && (before == null ? store.raw() == null : before.equals(store.raw())));
            } catch (Throwable failure) { result.putBoolean("groups_restored", false); result.putString("group_cleanup_error", failure.toString()); }
            if (cacheSnapshot) try {
                clear(directory); if (!files.isEmpty()) directory.mkdirs();
                for (Map.Entry<String, byte[]> file : files.entrySet()) { File target = new File(directory, file.getKey()); Files.write(target.toPath(), file.getValue()); target.setLastModified(times.get(file.getKey())); }
                if (!existed && files.isEmpty()) directory.delete();
                File[] after = directory.listFiles(); boolean same = (after == null ? 0 : after.length) == files.size();
                for (Map.Entry<String, byte[]> file : files.entrySet()) same &= java.util.Arrays.equals(file.getValue(), Files.readAllBytes(new File(directory, file.getKey()).toPath()));
                result.putBoolean("weather_cache_restored", same);
            } catch (Throwable failure) { result.putBoolean("weather_cache_restored", false); result.putString("cache_cleanup_error", failure.toString()); }
            result.putBoolean("singleton_unchanged", singletonBefore.equals(singleton.getAll()));
        }
        if (!result.getBoolean("groups_restored", false) || !result.getBoolean("weather_cache_restored", false)
                || !result.getBoolean("singleton_unchanged", false) || result.containsKey("activity_cleanup_error")) {
            result.putString("status", "FAIL");
            if (!result.containsKey("reason")) result.putString("reason", "Fixture cleanup or state preservation failed.");
        }
        finish(0, result);
    }
    private void open(String id) {
        activity = startActivitySync(new Intent(getTargetContext(), WeatherSettingsActivity.class).putExtra(WeatherSettingsActivity.EXTRA_GROUP_WIDGET_ID, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        long end = SystemClock.uptimeMillis() + 4000;
        while (SystemClock.uptimeMillis() < end) { final boolean[] focused = new boolean[1]; runOnMainSync(new Runnable() { @Override public void run() { focused[0] = activity.hasWindowFocus(); } }); if (focused[0]) return; SystemClock.sleep(25); }
        throw new AssertionError("Group weather editor did not receive window focus.");
    }
    private void close() { if (activity != null) { final Activity current = activity; activity = null; runOnMainSync(new Runnable() { @Override public void run() { current.finish(); } }); waitForIdleSync(); } }
    private View view(String tag) { View value = activity.getWindow().getDecorView().findViewWithTag(tag); check(value != null, "Missing " + tag); return value; }
    private void set(final String tag, final String value) { runOnMainSync(new Runnable() { @Override public void run() { ((EditText) view(tag)).setText(value); } }); }
    private String text(final String tag) { final String[] value = new String[1]; runOnMainSync(new Runnable() { @Override public void run() { value[0] = ((TextView) view(tag)).getText().toString(); } }); return value[0]; }
    private void click(final String tag) { runOnMainSync(new Runnable() { @Override public void run() { check(view(tag).performClick(), "Cannot activate " + tag); } }); waitForIdleSync(); }
    private static void clear(File directory) { File[] files = directory.listFiles(); if (files != null) for (File file : files) file.delete(); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
