package com.jeremykenedy.firetv.ui.nativeapp.statusbar;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import java.util.UUID;

/** Run only on a disposable preview: starting Home can discover and save layout slots. */
public final class StatusBarInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        final String file = "statusbar-proof-" + UUID.randomUUID();
        final Context target = getTargetContext();
        final SharedPreferences prefs = target.getSharedPreferences(file, Context.MODE_PRIVATE);
        final Context fixture = new ContextWrapper(target) {
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return "launcher_prefs".equals(name) ? prefs : super.getSharedPreferences(name, mode);
            }
        };
        Activity home = null;
        try {
            check(!StatusBarOptions.load(fixture).configured, "Absent setting changes default Home");
            String main = "com.jeremykenedy.firetv.ui.nativeapp.MainActivity";
            ActivityMonitor monitor = addMonitor(main, null, false);
            target.startActivity(new Intent().setClassName(target, main)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            home = waitForMonitorWithTimeout(monitor, 5000);
            removeMonitor(monitor);
            check(home != null, "Could not open disposable preview Home");
            final Activity activity = home;
            check(prefs.edit().putBoolean("minimal_status_bar", false).putString("status_bar_position", "Left").commit(), "Fixture save failed");
            final StatusBarOptions full = StatusBarOptions.load(fixture);
            final int[] clicks = new int[3];
            runOnMainSync(new Runnable() { @Override public void run() {
                StatusBarView row = new StatusBarView(activity, full,
                        new Runnable() { @Override public void run() { clicks[0]++; } },
                        new Runnable() { @Override public void run() { clicks[1]++; } },
                        new Runnable() { @Override public void run() { clicks[2]++; } }, null);
                android.view.ViewGroup content = (android.view.ViewGroup) row.getChildAt(0);
                check("statusbar:clock".equals(content.getChildAt(0).getTag())
                        && "statusbar:network".equals(content.getChildAt(1).getTag()), "Clock must precede network");
                check((row.getGravity() & android.view.Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) == android.view.Gravity.START,
                        "Saved Left position must align to start");
                for (String tag : new String[]{"statusbar:network", "statusbar:device-settings", "statusbar:preferences"}) {
                    View button = row.findViewWithTag(tag);
                    check(button != null && button.performClick(), "Full status action missing: " + tag);
                }
                check(row.findViewWithTag("statusbar:clock") != null, "Clock missing from full row");
            }});
            check(clicks[0] == 1 && clicks[1] == 1 && clicks[2] == 1, "Status action callback failed");
            check(prefs.edit().putBoolean("minimal_status_bar", true).commit(), "Fixture save failed");
            final StatusBarOptions minimal = StatusBarOptions.load(fixture);
            runOnMainSync(new Runnable() { @Override public void run() {
                StatusBarView row = new StatusBarView(activity, minimal, null, null, null, null);
                check(row.findViewWithTag("statusbar:clock") != null && row.findViewWithTag("statusbar:network") == null
                        && row.findViewWithTag("statusbar:device-settings") == null
                        && row.findViewWithTag("statusbar:preferences") == null, "Minimal row must contain only clock");
            }});
            check(prefs.edit().putBoolean("hide_status_bar_unless_focused", true)
                    .putBoolean("minimal_status_bar", false).commit(), "Full hidden fixture save failed");
            verifyHidden(activity, StatusBarOptions.load(fixture), false);
            check(prefs.edit().putBoolean("minimal_status_bar", true).commit(), "Minimal hidden fixture save failed");
            verifyHidden(activity, StatusBarOptions.load(fixture), true);
            result.putBoolean("full_and_minimal_dpad", true);
            result.putBoolean("minimal_clock_opens_quick_panel", true);
            result.putString("status", "PASS");
        } catch (Throwable failure) {
            result.putString("status", "FAIL"); result.putString("reason", failure.toString());
        } finally {
            if (home != null) { final Activity opened = home; runOnMainSync(new Runnable() { @Override public void run() { opened.finish(); } }); }
            result.putBoolean("fixture_removed", target.deleteSharedPreferences(file));
        }
        finish(0, result);
    }
    private void verifyHidden(final Activity activity, final StatusBarOptions options, final boolean minimal) {
        final StatusBarView[] row = new StatusBarView[1];
        final Button[] below = new Button[1];
        final int[] quick = {0};
        final int rowId = 0x7010, belowId = 0x7011;
        runOnMainSync(new Runnable() { @Override public void run() {
            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            row[0] = new StatusBarView(activity, options, null, null, null,
                    new Runnable() { @Override public void run() { quick[0]++; } });
            row[0].setId(rowId);
            below[0] = new Button(activity);
            below[0].setId(belowId); below[0].setText("Below status bar");
            below[0].setNextFocusUpId(rowId);
            View target = row[0].findViewWithTag(minimal ? "statusbar:clock" : "statusbar:network");
            target.setNextFocusDownId(belowId);
            root.addView(row[0], new LinearLayout.LayoutParams(-1, -2));
            root.addView(below[0], new LinearLayout.LayoutParams(-1, -2));
            activity.setContentView(root);
            below[0].requestFocus();
        }});
        waitForIdleSync();
        check(row[0].getAlpha() == 0f, "Bar must hide when focus is below");
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);
        waitForIdleSync();
        final boolean[] visible = {false};
        runOnMainSync(new Runnable() { @Override public void run() {
            View target = row[0].findViewWithTag(minimal ? "statusbar:clock" : "statusbar:network");
            visible[0] = row[0].getAlpha() == 1f && target.isFocused();
        }});
        check(visible[0], "Up must reveal and focus " + (minimal ? "clock" : "network"));
        if (minimal) {
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            waitForIdleSync();
            check(quick[0] == 1, "Minimal clock Select must open quick panel callback");
        }
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
        waitForIdleSync();
        final boolean[] hidden = {false};
        runOnMainSync(new Runnable() { @Override public void run() {
            hidden[0] = below[0].isFocused() && row[0].getAlpha() == 0f;
        }});
        check(hidden[0], "Down must hide the whole bar and focus below");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
