package com.jeremykenedy.firetv.ui.nativeapp.updates;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

/** Empty-feed native proof only. Installer and compatible release require explicit fixture validation. */
public final class UpdatesInstrumentation extends Instrumentation {
    private Activity activity;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            activity = startActivitySync(new Intent().setClassName(getTargetContext(), UpdatesActivity.class.getName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            runOnMainSync(new Runnable() {
                @Override public void run() {
                    check(view("check").hasFocus(), "Check must have initial focus");
                    check(view("download").getVisibility() == View.GONE, "No release must mean no download action");
                    check(view("install").getVisibility() == View.GONE, "No release must mean no install action");
                }
            });
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            final boolean[] completed = {false};
            long deadline = SystemClock.uptimeMillis() + 4000;
            while (!completed[0] && SystemClock.uptimeMillis() < deadline) {
                runOnMainSync(new Runnable() {
                    @Override public void run() {
                        completed[0] = ((TextView) view("status")).getText().toString().contains("No compatible source release");
                    }
                });
                SystemClock.sleep(40);
            }
            check(completed[0], "Check must show the truthful unavailable source release state");
            runOnMainSync(new Runnable() {
                @Override public void run() {
                    check(view("check").hasFocus(), "Check completion must restore remote focus");
                    check(view("download").getVisibility() == View.GONE, "Empty feed cannot enable downloading production APKs");
                    check(view("install").getVisibility() == View.GONE, "Empty feed cannot offer installation");
                }
            });
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            result.putString("status", "PASS");
            result.putString("limits", "Direct screen/empty source feed only; no Settings route, download, installer or physical TV claim.");
        } catch (Throwable failed) { result.putString("status", "FAIL"); result.putString("reason", failed.toString()); }
        finish(0, result);
    }
    private View view(String name) {
        View view = activity.getWindow().getDecorView().findViewWithTag("updates:" + name);
        check(view != null, "Missing view " + name); return view;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
