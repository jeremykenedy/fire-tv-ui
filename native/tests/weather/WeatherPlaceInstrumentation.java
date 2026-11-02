package com.jeremykenedy.firetv.ui.nativeapp.widget.weather;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

/** Test-only source injection. No live Census request or preference writes. */
public final class WeatherPlaceInstrumentation extends Instrumentation {
    private static volatile String scenario = "success";
    private static volatile boolean requestStarted, requestCancelled;
    private Activity activity;
    public static final class FixtureSettings extends WeatherSettingsActivity {
        @Override protected List<WeatherPlaces.Place> searchPlaces(String query, WeatherRequest request) throws Exception {
            if ("failure".equals(scenario)) throw new IOException("Fixture failure");
            if ("wait".equals(scenario)) {
                requestStarted = true;
                try {
                    long deadline = SystemClock.uptimeMillis() + 5000;
                    while (SystemClock.uptimeMillis() < deadline) { request.check(); SystemClock.sleep(20); }
                    throw new IOException("Fixture timeout");
                } catch (CancellationException cancelled) { requestCancelled = true; throw cancelled; }
            }
            List<WeatherPlaces.Place> results = new ArrayList<>();
            results.add(new WeatherPlaces.Place("Portland city, OR", "45.5371363", "-122.6500207"));
            results.add(new WeatherPlaces.Place("Portland city, ME", "43.6471006", "-70.1681549"));
            return results;
        }
    }
    @Override public Activity newActivity(ClassLoader loader, String name, Intent intent)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        if (WeatherSettingsActivity.class.getName().equals(name)) return new FixtureSettings();
        return super.newActivity(loader, name, intent);
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        Map<String, ?> before = getTargetContext().getSharedPreferences(WeatherPreferences.FILE, Context.MODE_PRIVATE).getAll();
        try {
            check(!WeatherPreferences.load(getTargetContext()).configured, "Use a disposable unconfigured weather profile.");
            ActivityMonitor monitor = addMonitor(FixtureSettings.class.getName(), null, false);
            getTargetContext().startActivity(new Intent(getTargetContext(), WeatherSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            activity = waitForMonitorWithTimeout(monitor, 4000); removeMonitor(monitor);
            check(activity instanceof FixtureSettings, "Injected settings did not open");
            waitForIdleSync();
            edit("weather:label", "Portland");
            String unit = text("weather:units");
            String oldLatitude = text("weather:latitude"), oldLongitude = text("weather:longitude");
            search("success"); awaitStatus("Choose a place");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); awaitStatus("Search cancelled");
            check(text("weather:label").equals("Portland") && text("weather:latitude").equals(oldLatitude)
                    && text("weather:longitude").equals(oldLongitude), "Cancel must keep entered values");
            search("success"); awaitStatus("Choose a place");
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER); awaitStatus("Review and save");
            check(text("weather:label").equals("Portland city, OR") && text("weather:latitude").equals("45.5371363")
                    && text("weather:longitude").equals("-122.6500207"), "Selection must fill the chosen location");
            check(text("weather:units").equals(unit), "Selection must preserve units");
            edit("weather:label", "Portland");
            search("failure"); awaitStatus("Place search unavailable");
            check(text("weather:latitude").equals("45.5371363"), "Failure must preserve entered coordinates");
            search("wait");
            long deadline = SystemClock.uptimeMillis() + 3000;
            while (!requestStarted && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(20);
            check(requestStarted, "Waiting request must start");
            runOnMainSync(new Runnable() { @Override public void run() { activity.finish(); } });
            deadline = SystemClock.uptimeMillis() + 3000;
            while (!requestCancelled && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(20);
            check(requestCancelled, "Pause must cancel an in-flight search");
            check(before.equals(getTargetContext().getSharedPreferences(WeatherPreferences.FILE, Context.MODE_PRIVATE).getAll()),
                    "Searching and choosing without Save must not write weather preferences");
            result.putString("status", "PASS");
            result.putString("limits", "Test source only; real Census and NWS requests, Settings navigation and physical TV remain unverified.");
        } catch (Throwable failure) { result.putString("status", "FAIL"); result.putString("reason", failure.toString()); }
        finally { if (activity != null && !activity.isFinishing()) runOnMainSync(new Runnable() { @Override public void run() { activity.finish(); } }); }
        finish(0, result);
    }
    private void search(String next) {
        scenario = next;
        runOnMainSync(new Runnable() { @Override public void run() { check(view("weather:find-place").requestFocus(), "Search must accept focus"); } });
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
    }
    private void edit(final String tag, final String value) { runOnMainSync(new Runnable() { @Override public void run() { ((EditText) view(tag)).setText(value); } }); }
    private View view(String tag) { View v = activity.getWindow().getDecorView().findViewWithTag(tag); check(v != null, "Missing " + tag); return v; }
    private String text(final String tag) {
        final String[] value = new String[1]; runOnMainSync(new Runnable() { @Override public void run() { value[0] = ((TextView) view(tag)).getText().toString(); } }); return value[0];
    }
    private void awaitStatus(String expected) {
        long deadline = SystemClock.uptimeMillis() + 4000;
        while (SystemClock.uptimeMillis() < deadline) { if (text("weather:place-status").contains(expected)) return; SystemClock.sleep(25); }
        throw new AssertionError("Missing status: " + expected);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
