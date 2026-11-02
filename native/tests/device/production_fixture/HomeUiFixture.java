package com.jeremykenedy.firetv.ui.homepermissionfixture;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

/** External UI driver keeps the real candidate services bound throughout menu navigation. */
public final class HomeUiFixture extends Instrumentation {
    private String destination;
    @Override public void onCreate(Bundle args) { super.onCreate(args); destination = args.getString("destination", "settings"); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            if (!"factory".equals(destination) && !"settings".equals(destination) && !"live".equals(destination) && !"assert-system-focus".equals(destination) && !"trace".equals(destination)) {
                throw new IllegalArgumentException("Unsupported native destination");
            }
            UiAutomation automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            if ("trace".equals(destination)) {
                final java.util.List<String> events = java.util.Collections.synchronizedList(new java.util.ArrayList<String>());
                automation.setOnAccessibilityEventListener(new UiAutomation.OnAccessibilityEventListener() {
                    @Override public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {
                        if (event.getEventType() == android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                                && events.size() < 32) events.add(event.getPackageName() + "/" + event.getClassName() + " changes=" + event.getContentChangeTypes() + "@" + event.getEventTime() + " received=" + SystemClock.uptimeMillis());
                    }
                });
                SystemClock.sleep(2500);
                result.putString("window_events", events.toString());
                result.putString("status", "PASS"); finish(0, result); return;
            }
            if ("assert-system-focus".equals(destination)) {
                AccessibilityNodeInfo root = automation.getRootInActiveWindow();
                boolean focused = false;
                if (root != null) {
                    for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText("System settings")) {
                        if ("System settings".contentEquals(node.getText() == null ? "" : node.getText()) && node.isFocused()) focused = true;
                        node.recycle();
                    }
                    root.recycle();
                }
                if (!focused) throw new AssertionError("System settings action did not retain remote focus");
                result.putBoolean("system_settings_focus_retained", true);
                result.putString("status", "PASS");
                finish(0, result);
                return;
            }
            click(automation, "Settings");
            click(automation, "Device");
            click(automation, "Device screens");
            click(automation, "factory".equals(destination) ? "Factory UI" : "live".equals(destination) ? "Live TV" : "System settings");
            result.putString("status", "PASS");
            result.putString("destination", destination);
            result.putString("route", "Source Settings -> Device -> Device screens -> selected native action");
            result.putString("limits", "External UI driver only; host must verify actual destination window and retained candidate services.");
        } catch (Throwable failed) {
            result.putString("status", "FAIL"); result.putString("reason", failed.toString());
        }
        finish(0, result);
    }
    private void click(UiAutomation automation, String text) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = automation.getRootInActiveWindow();
            if (root != null) {
                List<AccessibilityNodeInfo> candidates = root.findAccessibilityNodeInfosByText(text);
                boolean focused = false;
                for (AccessibilityNodeInfo node : candidates) {
                    if (!focused && text.contentEquals(node.getText() == null ? "" : node.getText())
                            && node.isEnabled() && node.isFocusable() && node.isClickable()) {
                        focused = node.isFocused() || node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                    }
                    node.recycle();
                }
                root.recycle();
                if (focused) {
                    long now = SystemClock.uptimeMillis();
                    automation.injectInputEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN,
                            KeyEvent.KEYCODE_DPAD_CENTER, 0), true);
                    automation.injectInputEvent(new KeyEvent(now, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP,
                            KeyEvent.KEYCODE_DPAD_CENTER, 0), true);
                    SystemClock.sleep(150);
                    return;
                }
            }
            SystemClock.sleep(40);
        }
        throw new AssertionError("Source action was not focusable: " + text);
    }
}
