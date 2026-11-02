package com.jeremykenedy.firetv.ui.nativeapp.widget.calendar;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public final class CalendarInstrumentation extends Instrumentation {
    private Activity activity;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        SharedPreferences prefs = getTargetContext().getSharedPreferences(CalendarPreferences.STORE, Context.MODE_PRIVATE);
        Map<String, ?> before = prefs.getAll();
        try {
            AccessibilityServiceInfo info = automation().getServiceInfo(); info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS; automation().setServiceInfo(info);
            long now = System.currentTimeMillis();
            CalendarOptions options = new CalendarOptions(Arrays.asList(7L), "UTC", true);
            CalendarFixture fixture = new CalendarFixture(now);
            CalendarData.Result redacted = CalendarSource.read(fixture, true, options, now, new CancellationSignal());
            check(redacted.state == CalendarData.State.READY && redacted.events.size() == 2, "Read-only fixture includes selected timed and all-day instances only");
            check(!fixture.titleRequested && fixture.allClosed(), "Counts mode omits titles and closes cursors");
            check(fixture.lastSelection.equals("calendar_id IN (?)") && Arrays.equals(fixture.lastArgs, new String[] {"7"}), "Selected calendar IDs are parameterized");
            check(fixture.lastUri.getPathSegments().size() == instancePathLength(), "Instance query uses explicit time-range path");
            CalendarFixture detailsFixture = new CalendarFixture(now);
            final CalendarData.Result details = CalendarSource.read(detailsFixture, true, options.privacy(false), now, new CancellationSignal());
            check(detailsFixture.titleRequested && details.events.size() == 2 && detailsFixture.allClosed(), "Details requested only after explicit option");
            CalendarFixture denied = new CalendarFixture(now);
            check(CalendarSource.read(denied, false, options, now, new CancellationSignal()).state == CalendarData.State.PERMISSION_REQUIRED && denied.queries == 0, "No query without permission");
            denied.denied = true;
            check(CalendarSource.read(denied, true, options, now, new CancellationSignal()).state == CalendarData.State.PERMISSION_REQUIRED, "Revoked permission becomes permission-required");
            CalendarFixture missing = new CalendarFixture(now); missing.missing = true;
            check(CalendarSource.read(missing, true, options, now, new CancellationSignal()).state == CalendarData.State.UNAVAILABLE, "Missing provider cursor does not become empty success");
            CalendarFixture setup = new CalendarFixture(now);
            check(CalendarSource.read(setup, true, CalendarOptions.defaults(), now, new CancellationSignal()).state == CalendarData.State.SETUP_REQUIRED && setup.queries == 1 && setup.allClosed(), "No instances queried before explicit selection");
            CalendarFixture unavailableSelection = new CalendarFixture(now);
            check(CalendarSource.read(unavailableSelection, true, new CalendarOptions(Arrays.asList(99L), "UTC", true), now, new CancellationSignal()).state == CalendarData.State.PARTIAL, "Unavailable selected IDs preserved as partial");
            CalendarFixture many = new CalendarFixture(now); many.many = true;
            CalendarData.Result bounded = CalendarSource.read(many, true, options, now, new CancellationSignal());
            check(bounded.state == CalendarData.State.PARTIAL && bounded.events.size() == CalendarData.MAX_EVENTS && many.allClosed(), "Bounded rows/events report partial and close cursors");
            CalendarFixture malformed = new CalendarFixture(now); malformed.malformed = true;
            check(CalendarSource.read(malformed, true, options, now, new CancellationSignal()).state == CalendarData.State.PARTIAL && malformed.allClosed(), "Invalid occurrence is excluded with partial status");
            CancellationSignal canceled = new CancellationSignal(); canceled.cancel();
            try { CalendarSource.read(new CalendarFixture(now), true, options, now, canceled); throw new AssertionError("Cancellation must propagate"); }
            catch (OperationCanceledException expected) {}
            check(prefs.edit().clear().commit(), "Clear local calendar choices fixture");
            activity = startActivitySync(new Intent(getTargetContext(), CalendarActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); waitForIdleSync();
            check(CalendarPreferences.load(getTargetContext()).selected.isEmpty(), "Native route never chooses a calendar implicitly");
            clickTag("calendar:privacy"); clickText("Show details"); check(!CalendarPreferences.load(getTargetContext()).summaryOnly, "Native deliberate preview opt-in");
            clickTag("calendar:privacy"); check(CalendarPreferences.load(getTargetContext()).summaryOnly, "Native Home counts restored");
            clickTag("calendar:zone"); enterZone("Asia/Tokyo"); clickText("Save");
            check(CalendarPreferences.load(getTargetContext()).zone.equals("Asia/Tokyo"), "Native display zone persisted");
            final CalendarOptions privateOptions = options.privacy(false);
            final CalendarData.Result redactedResult = redacted;
            runOnMainSync(new Runnable() { @Override public void run() {
                CalendarView view = new CalendarView(activity); view.render(privateOptions, details);
                check(view.getText().toString().contains("Private all-day fixture"), "Actual native renderer shows opted-in fixture details");
                view.render(privateOptions.privacy(true), redactedResult);
                check(!view.getText().toString().contains("Private"), "Actual native renderer redacts fixture titles");
                view.start(); view.start(); view.stop();
                check(!view.getText().toString().contains("Private"), "Stopped renderer clears private content");
            } });
            CalendarWidgetProvider provider = new CalendarWidgetProvider();
            check(!provider.defaultVisible(), "Calendar widget starts hidden");
            if (!CalendarSource.available(getTargetContext())) check(provider.state(getTargetContext()) == WidgetProvider.State.UNAVAILABLE, "Actual absent provider unavailable");
            else if (!CalendarSource.permitted(getTargetContext())) check(provider.state(getTargetContext()) == WidgetProvider.State.SETUP_REQUIRED, "Actual permission is required");
            check(!CalendarPreferences.encode(CalendarPreferences.load(getTargetContext())).contains("Private"), "No fixture event data persisted");
            result.putString("result", "PASS: read-only CalendarContract cursor fixture, bounds/cancellation/privacy and native config/renderer. Actual calendar provider contents not seeded or modified.");
        } catch (Throwable error) { result.putString("error", android.util.Log.getStackTraceString(error)); }
        finally {
            if (activity != null) runOnMainSync(new Runnable() { @Override public void run() { activity.finish(); } });
            SharedPreferences.Editor restore = prefs.edit().clear();
            for (Map.Entry<String, ?> entry : before.entrySet()) {
                String key = entry.getKey(); Object value = entry.getValue();
                if (value instanceof String) restore.putString(key, (String) value);
                else if (value instanceof Boolean) restore.putBoolean(key, (Boolean) value);
                else if (value instanceof Integer) restore.putInt(key, (Integer) value);
                else if (value instanceof Long) restore.putLong(key, (Long) value);
                else if (value instanceof Float) restore.putFloat(key, (Float) value);
                else if (value instanceof Set) { @SuppressWarnings("unchecked") Set<String> set = (Set<String>) value; restore.putStringSet(key, set); }
            }
            boolean restored = restore.commit() && before.equals(prefs.getAll()); result.putBoolean("restored", restored);
            if (!restored) result.putString("error", "Calendar fixture restoration failed");
        }
        finish(result.containsKey("error") ? Activity.RESULT_CANCELED : Activity.RESULT_OK, result);
    }
    private int instancePathLength() { return android.provider.CalendarContract.Instances.CONTENT_URI.getPathSegments().size() + 2; }
    private void clickTag(final String tag) {
        runOnMainSync(new Runnable() { @Override public void run() { View view = activity.getWindow().getDecorView().findViewWithTag(tag); check(view != null, "Missing native calendar control " + tag); view.requestFocus(); view.performClick(); } }); waitForIdleSync();
    }
    private void enterZone(String text) {
        AccessibilityNodeInfo input = awaitNode(null, true); Bundle args = new Bundle(); args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        check(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args), "Native zone text input"); waitForIdleSync();
        check(input.refresh() && text.contentEquals(input.getText()), "Native zone editor contains exact entered text");
    }
    private void clickText(String text) {
        if ("Save".equalsIgnoreCase(text) && keyboardVisible()) {
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            for (int i = 0; i < 40 && keyboardVisible(); i++) SystemClock.sleep(50);
            check(!keyboardVisible(), "Dismiss only visible keyboard before Save");
            check("Asia/Tokyo".contentEquals(awaitNode(null, true).getText()), "Zone editor remains open after keyboard dismissal");
        }
        AccessibilityNodeInfo node = awaitNode(text, false);
        while (!node.isClickable() && node.getParent() != null) node = node.getParent();
        check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK), "Activate native calendar dialog control"); waitForIdleSync();
    }
    private AccessibilityNodeInfo awaitNode(String text, boolean input) {
        for (int attempt = 0; attempt < 40; attempt++) {
            for (AccessibilityWindowInfo window : automation().getWindows()) {
                AccessibilityNodeInfo root = window.getRoot();
                if (root != null && getTargetContext().getPackageName().equals(String.valueOf(root.getPackageName()))) {
                    AccessibilityNodeInfo found = match(root, text, input); if (found != null) return found;
                }
            } SystemClock.sleep(50);
        } throw new AssertionError(input ? "Native calendar editor unavailable" : "Native calendar dialog action unavailable");
    }
    private AccessibilityNodeInfo match(AccessibilityNodeInfo node, String text, boolean input) {
        if (node == null) return null;
        if (input ? "android.widget.EditText".contentEquals(node.getClassName()) : node.isVisibleToUser() && text.equalsIgnoreCase(String.valueOf(node.getText()))) return node;
        for (int i = 0; i < node.getChildCount(); i++) { AccessibilityNodeInfo found = match(node.getChild(i), text, input); if (found != null) return found; }
        return null;
    }
    private boolean keyboardVisible() {
        for (AccessibilityWindowInfo window : automation().getWindows()) { AccessibilityNodeInfo root = window.getRoot(); if (window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD && root != null && root.isVisibleToUser()) return true; } return false;
    }
    private UiAutomation automation() { return getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
