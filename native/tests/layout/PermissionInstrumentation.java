package com.jeremykenedy.firetv.ui.nativeapp.layout;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.KeyCharacterMap;
import android.view.InputDevice;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.providers.TvRecommendations;

/** Requires READ_TV_LISTINGS initially revoked and an isolated TV emulator. */
public final class PermissionInstrumentation extends Instrumentation {
    private static final String ROOT = "com.jeremykenedy.firetv.ui.nativeapp.";
    private Activity editor;

    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            check(getTargetContext().checkSelfPermission(TvRecommendations.PERMISSION) != PackageManager.PERMISSION_GRANTED,
                    "Revoke READ_TV_LISTINGS before this test.");
            Activity main = startActivitySync(new Intent().setClassName(getTargetContext(), ROOT + "MainActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            waitForIdleSync();
            Activity settings = route(main, "Settings", ROOT + "SettingsActivity");
            select(settings, "Cards");
            Activity cards = route(settings, "Channel cards and timers", ROOT + "cards.CardsSettingsActivity");
            editor = route(cards, "Channel Cards", ROOT + "layout.LayoutSettingsActivity");
            waitForDiscovery();
            check(status().equals("Allow TV listings access to show cards shared by your apps."),
                    "Revoked access must show the permission-required state.");
            select(editor, "Allow TV listings access");
            waitForPermissionWindow();
            result.putBoolean("native_permission_window", true);
            choosePermission(false);
            waitForDiscovery();
            check(getTargetContext().checkSelfPermission(TvRecommendations.PERMISSION) != PackageManager.PERMISSION_GRANTED,
                    "Don't allow must leave listings access revoked.");
            check(status().contains("access was denied"), "Denial must have an explicit editor state.");
            result.putBoolean("denied_state", true);
            select(editor, "Allow TV listings access");
            waitForPermissionWindow();
            choosePermission(true);
            waitForDiscovery();
            check(getTargetContext().checkSelfPermission(TvRecommendations.PERMISSION) == PackageManager.PERMISSION_GRANTED,
                    "Allow must grant actual listings access.");
            final TvRecommendations.Result recommendations = TvRecommendations.read(getTargetContext());
            check(recommendations.status != TvRecommendations.Status.PERMISSION_REQUIRED,
                    "Granted access must reach the actual provider read.");
            check(status().equals(recommendations.message()), "Editor must show the actual provider read state after granting access.");
            onMain(new Runnable() {
                @Override public void run() {
                    for (TvRecommendations.Feed feed : recommendations.feeds) {
                        check(editor.getWindow().getDecorView().findViewWithTag(feed.item.key() + ":visibility") != null,
                                "Every published feed must have a manageable slot after granting access.");
                    }
                }
            });
            result.putBoolean("actual_settings_route", true);
            result.putBoolean("granted_state", true);
            result.putString("provider_status", recommendations.status.name());
            result.putInt("published_feed_slots", recommendations.feeds.size());
            result.putString("status", "PASS");
            result.putString("restoration", "Listings permission left granted for root fixture checks; root must restore its prior state.");
        } catch (Throwable failure) {
            result.putString("status", "FAIL");
            result.putString("reason", failure.toString());
        }
        finish(0, result);
    }

    private Activity route(Activity activity, String label, String destination) {
        ActivityMonitor monitor = addMonitor(destination, null, false);
        select(activity, label);
        Activity opened = waitForMonitorWithTimeout(monitor, 5000);
        removeMonitor(monitor);
        check(opened != null, "Route did not open: " + label);
        waitForIdleSync();
        return opened;
    }

    private void select(final Activity activity, final String label) {
        onMain(new Runnable() {
            @Override public void run() {
                Button button = button(activity.getWindow().getDecorView(), label);
                check(button != null && button.isEnabled(), "Missing or disabled route: " + label);
                check(button.requestFocus(), "Cannot focus route: " + label);
            }
        });
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
        waitForIdleSync();
    }

    private void waitForDiscovery() {
        long deadline = SystemClock.uptimeMillis() + 8000;
        while (SystemClock.uptimeMillis() < deadline) {
            final boolean[] ready = new boolean[1];
            onMain(new Runnable() {
                @Override public void run() {
                    View action = editor.getWindow().getDecorView().findViewWithTag("layout:provider-action");
                    ready[0] = action != null && action.isEnabled();
                }
            });
            if (ready[0]) return;
            SystemClock.sleep(25);
        }
        throw new AssertionError("Editor provider discovery did not finish.");
    }

    private String status() {
        final String[] value = new String[1];
        onMain(new Runnable() {
            @Override public void run() {
                TextView status = (TextView) editor.getWindow().getDecorView().findViewWithTag("layout:provider-status");
                check(status != null, "Missing editor provider state.");
                value[0] = status.getText().toString();
            }
        });
        return value[0];
    }

    private void waitForPermissionWindow() {
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (permissionWindow(root) && containsChoice(root, true) && containsChoice(root, false)) return;
            SystemClock.sleep(50);
        }
        throw new AssertionError("Actual OS permission window with Allow and Don't allow was not shown.");
    }

    private void choosePermission(boolean allow) {
        int[] directions = {KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_LEFT};
        for (int direction : directions) {
            for (int attempt = 0; attempt < 8; attempt++) {
                AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
                check(permissionWindow(root), "OS permission window disappeared before selection.");
                AccessibilityNodeInfo focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                if (focus != null && containsChoice(focus, allow) && !containsChoice(focus, !allow)) {
                    inject(KeyEvent.KEYCODE_DPAD_CENTER);
                    long deadline = SystemClock.uptimeMillis() + 5000;
                    while (SystemClock.uptimeMillis() < deadline) {
                        if (!permissionWindow(getUiAutomation().getRootInActiveWindow())) {
                            waitForIdleSync();
                            return;
                        }
                        SystemClock.sleep(25);
                    }
                    throw new AssertionError("Permission window did not close after Select.");
                }
                inject(direction);
                SystemClock.sleep(50);
            }
        }
        throw new AssertionError("Could not reach OS permission choice using the D-pad.");
    }

    private void inject(int key) {
        long downTime = SystemClock.uptimeMillis();
        KeyEvent down = new KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, key, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_DPAD);
        KeyEvent up = new KeyEvent(downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, key, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_DPAD);
        check(getUiAutomation().injectInputEvent(down, true), "D-pad down injection failed.");
        check(getUiAutomation().injectInputEvent(up, true), "D-pad up injection failed.");
    }

    private boolean permissionWindow(AccessibilityNodeInfo root) {
        if (root == null || root.getPackageName() == null) return false;
        String name = root.getPackageName().toString();
        return name.contains("permissioncontroller") || name.contains("packageinstaller");
    }

    private boolean containsChoice(AccessibilityNodeInfo node, boolean allow) {
        if (node == null) return false;
        String text = node.getText() == null ? "" : node.getText().toString().replace('\u2019', '\'').trim();
        if (allow ? text.equalsIgnoreCase("Allow") : text.equalsIgnoreCase("Don't allow") || text.equalsIgnoreCase("Deny")) return true;
        for (int i = 0; i < node.getChildCount(); i++) if (containsChoice(node.getChild(i), allow)) return true;
        return false;
    }

    private Button button(View view, String label) {
        if (view instanceof Button && label.contentEquals(((Button) view).getText())) return (Button) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Button found = button(group.getChildAt(i), label);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void onMain(final Runnable action) {
        final Throwable[] error = new Throwable[1];
        runOnMainSync(new Runnable() {
            @Override public void run() { try { action.run(); } catch (Throwable failure) { error[0] = failure; } }
        });
        if (error[0] != null) throw new AssertionError(error[0]);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
