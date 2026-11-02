package com.jeremykenedy.firetv.ui.nativeapp.updates;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import java.io.IOException;

/** Tests the Settings route with a test-only source and never downloads or installs an APK. */
public final class UpdatesInstrumentation extends Instrumentation {
    private static final String ROOT = "com.jeremykenedy.firetv.ui.nativeapp.";
    private static volatile String scenario = "empty";
    private Activity settings, screen;

    public static final class FixtureUpdatesActivity extends UpdatesActivity {
        @Override protected UpdateCoordinator.Source releaseSource() {
            final String selected = scenario;
            return new UpdateCoordinator.Source() {
                @Override public UpdateCoordinator.Release check(UpdateCoordinator.Cancellation cancellation) throws Exception {
                    if ("error".equals(selected)) throw new IOException("Fixture feed unavailable");
                    if ("cancel".equals(selected)) {
                        long deadline = SystemClock.uptimeMillis() + 3000;
                        while (SystemClock.uptimeMillis() < deadline) {
                            cancellation.check();
                            SystemClock.sleep(20);
                        }
                        throw new IOException("Cancellation was not received");
                    }
                    cancellation.check();
                    if ("empty".equals(selected)) return null;
                    long current = new AndroidPackages(FixtureUpdatesActivity.this).installed(getPackageName()).version;
                    return new UpdateCoordinator.Release("fixture/update-tests", "v2", getPackageName(),
                            "https://github.com/fixture/update-tests/releases/download/v2/fixture.apk",
                            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 100,
                            current + 1, "Fixture 2", "Local fixture release");
                }
            };
        }
    }
    @Override public Activity newActivity(ClassLoader loader, String className, Intent intent)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        if ((ROOT + "updates.UpdatesActivity").equals(className)) return new FixtureUpdatesActivity();
        return super.newActivity(loader, className, intent);
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            settings = startActivitySync(new Intent().setClassName(getTargetContext(), ROOT + "SettingsActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            settle();
            main(new Runnable() { @Override public void run() {
                Button category = button(settings.getWindow().getDecorView(), "Updates and backup");
                check(category != null && category.requestFocus(), "Updates category must accept focus");
            }});
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            settle();
            ActivityMonitor monitor = addMonitor(FixtureUpdatesActivity.class.getName(), null, false);
            main(new Runnable() { @Override public void run() {
                Button route = button(settings.getWindow().getDecorView(), "Updates");
                check(route != null && route.requestFocus(), "Updates route must accept focus");
            }});
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            screen = waitForMonitorWithTimeout(monitor, 3000);
            removeMonitor(monitor);
            check(screen instanceof FixtureUpdatesActivity, "Settings must open injected Updates screen");
            settle();
            main(new Runnable() { @Override public void run() {
                check(view("check").hasFocus(), "Check must have initial focus");
                check(view("download").getVisibility() == View.GONE, "Download must start hidden");
                check(view("install").getVisibility() == View.GONE, "Install must start hidden");
            }});
            runCheck("empty", "No compatible source release");
            main(new Runnable() { @Override public void run() {
                check(view("check").hasFocus(), "Empty result must restore Check focus");
                check(view("download").getVisibility() == View.GONE, "Empty feed must not offer download");
            }});
            runCheck("error", "Verification or download failed");
            runCheck("available", "A reviewed update is available");
            main(new Runnable() { @Override public void run() {
                check(view("download").getVisibility() == View.VISIBLE && view("download").hasFocus(), "Available release must offer download");
                check(view("install").getVisibility() == View.GONE, "Unverified release must not offer installation");
                check(((TextView) view("release")).getText().toString().contains("Local fixture release"), "Release details must be visible");
            }});
            scenario = "cancel";
            focus("check");
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            awaitText("Checking compatible source releases");
            main(new Runnable() { @Override public void run() {
                check(view("cancel").getVisibility() == View.VISIBLE, "Check must offer cancellation");
                check(view("cancel").requestFocus(), "Cancel must accept focus");
            }});
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            awaitText("Cancelled.");
            main(new Runnable() { @Override public void run() {
                check(view("check").hasFocus(), "Cancellation must restore Check focus");
                check(view("download").getVisibility() == View.GONE, "Cancelled check must not offer download");
                check(view("install").getVisibility() == View.GONE, "Cancelled check must not offer install");
            }});
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            settle();
            check(screen.isFinishing(), "Back must finish Updates");
            main(new Runnable() { @Override public void run() {
                check(settings.getCurrentFocus() instanceof Button && "Updates".contentEquals(((Button) settings.getCurrentFocus()).getText()), "Back must restore Settings route focus");
            }});
            result.putString("status", "PASS");
            result.putBoolean("settings_route_and_back", true);
            result.putBoolean("empty_error_available_and_cancel", true);
            result.putString("limits", "Test-only source; no real network, download, installer or physical TV validation.");
        } catch (Throwable failed) {
            result.putString("status", "FAIL"); result.putString("reason", failed.toString());
        } finally {
            if (screen != null && !screen.isFinishing()) main(new Runnable() { @Override public void run() { screen.finish(); }});
            if (settings != null) main(new Runnable() { @Override public void run() { settings.finish(); }});
        }
        finish(0, result);
    }
    private void runCheck(String selected, String expected) {
        scenario = selected; focus("check"); sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER); awaitText(expected);
    }
    private void focus(final String tag) { main(new Runnable() { @Override public void run() { check(view(tag).requestFocus(), tag + " must accept focus"); }}); }
    private void awaitText(final String expected) {
        long deadline = SystemClock.uptimeMillis() + 4000;
        while (SystemClock.uptimeMillis() < deadline) {
            final boolean[] found = {false};
            main(new Runnable() { @Override public void run() { found[0] = ((TextView) view("status")).getText().toString().contains(expected); }});
            if (found[0]) return;
            SystemClock.sleep(25);
        }
        throw new AssertionError("Missing status: " + expected);
    }
    private void settle() { waitForIdleSync(); SystemClock.sleep(100); }
    private void main(Runnable action) { runOnMainSync(action); }
    private View view(String name) {
        View found = screen.getWindow().getDecorView().findViewWithTag("updates:" + name);
        check(found != null, "Missing view " + name); return found;
    }
    private static Button button(View view, String text) {
        if (view instanceof Button && text.contentEquals(((Button) view).getText())) return (Button) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Button found = button(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
