package com.jeremykenedy.firetv.ui.nativeapp.widget.reminders;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;

public final class RemindersInstrumentation extends Instrumentation {
    private Activity activity;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        SharedPreferences prefs = getTargetContext().getSharedPreferences(RemindersPreferences.STORE, Context.MODE_PRIVATE);
        Map<String, ?> before = prefs.getAll();
        try {
            check(prefs.edit().clear().commit(), "Clear fixture");
            RemindersWidgetProvider provider = new RemindersWidgetProvider();
            check(!provider.defaultVisible(), "Reminder widget starts hidden");
            check(provider.state(getTargetContext()) == WidgetProvider.State.SETUP_REQUIRED, "Disabled widget needs setup");
            open();
            clickTag("reminders:add");
            enter(0, "Private recurring fixture");
            enter(1, "America/Los_Angeles");
            clickTextPrefix("Date: "); check(hasClass("android.widget.DatePicker"), "Actual native date picker opens"); clickText("Cancel");
            clickTextPrefix("Time: "); check(hasClass("android.widget.TimePicker"), "Actual native time picker opens"); clickText("Cancel");
            clickText("Once"); clickText("Daily"); clickText("Save");
            RemindersOptions saved = RemindersPreferences.load(getTargetContext());
            check(saved.items.size() == 1 && saved.items.get(0).repeat == RemindersOptions.Repeat.DAILY, "Native form saves recurring reminder");
            final String id = saved.items.get(0).id;
            check(saved.items.get(0).zone.getId().equals("America/Los_Angeles"), "Native explicit zone saved");
            check(!saved.enabled && saved.summaryOnly && !saved.allowExport, "Entry does not enable reminders or private exports");
            clickTag("reminders:enabled"); check(RemindersPreferences.load(getTargetContext()).enabled, "Explicit enable");
            check(!RemindersPreferences.load(getTargetContext()).homeText(System.currentTimeMillis()).contains("Private"), "Default Home hides reminder labels");
            runOnMainSync(new Runnable() { @Override public void run() { activity.finish(); } }); waitForIdleSync();
            RemindersOptions old = RemindersPreferences.load(getTargetContext());
            RemindersPreferences.save(getTargetContext(), old.edit(id, "Private recurring fixture", LocalDate.now(ZoneId.of("America/Los_Angeles")).minusDays(1), LocalTime.of(9, 0), "America/Los_Angeles", RemindersOptions.Repeat.DAILY));
            open();
            check(RemindersPreferences.load(getTargetContext()).dueCount(System.currentTimeMillis()) == 1, "Actual past date is due");
            clickTag("reminders:" + id); clickText("Snooze 1 hour");
            long until = RemindersPreferences.load(getTargetContext()).find(id).snoozeUntil;
            check(until > System.currentTimeMillis() && until <= System.currentTimeMillis() + 3600000L, "Native snooze persists future instant");
            clickTag("reminders:" + id); clickText("Complete occurrence");
            RemindersOptions.Item next = RemindersPreferences.load(getTargetContext()).find(id);
            check(next.scheduledAt() > System.currentTimeMillis() && next.id.equals(id) && next.snoozeUntil == 0, "Native completion advances first future repeat with same identity");
            clickTag("reminders:privacy"); clickText("Show details");
            check(!RemindersPreferences.load(getTargetContext()).summaryOnly, "Explicit Home preview opt-in");
            clickTag("reminders:privacy"); check(RemindersPreferences.load(getTargetContext()).summaryOnly, "Restore Home summaries");
            clickTag("reminders:export"); clickText("Allow in backups"); check(RemindersPreferences.exportAllowed(getTargetContext()), "Explicit backup opt-in");
            clickTag("reminders:export"); check(!RemindersPreferences.exportAllowed(getTargetContext()), "Backup opt-out");
            clickTag("reminders:" + id); clickText("Edit schedule"); enter(0, "Edited recurring fixture"); clickText("Save");
            check(RemindersPreferences.load(getTargetContext()).find(id).label.equals("Edited recurring fixture"), "Native editing retains identity");
            final TextView[] home = new TextView[1];
            runOnMainSync(new Runnable() { @Override public void run() {
                RemindersWidgetProvider provider = new RemindersWidgetProvider();
                home[0] = (TextView) provider.createView(activity); provider.onStart(home[0]); provider.onStart(home[0]);
                check(!home[0].getText().toString().contains("Edited recurring"), "Native Home view redacts private label");
                provider.onStop(home[0]);
            } });
            clickTag("reminders:enabled"); check(provider.state(getTargetContext()) == WidgetProvider.State.SETUP_REQUIRED, "Explicit disable preserves schedule");
            clickTag("reminders:enabled");
            clickTag("reminders:" + id); clickText("Delete"); clickText("Delete");
            check(RemindersPreferences.load(getTargetContext()).items.isEmpty() && provider.state(getTargetContext()) == WidgetProvider.State.EMPTY, "Native delete yields empty enabled widget");
            result.putString("result", "PASS: native form/pickers, zone, recurrence, enable, snooze, completion, edit, privacy and delete");
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
            if (!restored) result.putString("error", "Reminders fixture restoration failed");
        }
        finish(result.containsKey("error") ? Activity.RESULT_CANCELED : Activity.RESULT_OK, result);
    }
    private void open() {
        activity = startActivitySync(new Intent(getTargetContext(), RemindersActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); waitForIdleSync();
    }
    private void clickTag(final String tag) {
        runOnMainSync(new Runnable() { @Override public void run() {
            View view = activity.getWindow().getDecorView().findViewWithTag(tag); check(view != null, "Missing native control " + tag);
            view.requestFocus(); view.performClick();
        } }); waitForIdleSync();
    }
    private void enter(int index, String value) {
        AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
        java.util.List<AccessibilityNodeInfo> inputs = new java.util.ArrayList<>(); collect(root, "android.widget.EditText", inputs);
        check(inputs.size() > index, "Native text input exists"); Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
        check(inputs.get(index).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args), "Enter native text"); waitForIdleSync();
    }
    private boolean hasClass(String name) {
        java.util.List<AccessibilityNodeInfo> found = new java.util.ArrayList<>(); collect(getUiAutomation().getRootInActiveWindow(), name, found); return !found.isEmpty();
    }
    private void collect(AccessibilityNodeInfo node, String name, java.util.List<AccessibilityNodeInfo> found) {
        if (node == null) return; if (name.contentEquals(node.getClassName())) found.add(node);
        for (int i = 0; i < node.getChildCount(); i++) collect(node.getChild(i), name, found);
    }
    private void clickText(String text) { clickText(text, false); }
    private void clickTextPrefix(String text) { clickText(text, true); }
    private void clickText(String text, boolean prefix) {
        AccessibilityNodeInfo found = null;
        for (int attempt = 0; attempt < 20 && found == null; attempt++) {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (root != null) for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text)) {
                CharSequence label = node.getText();
                if (label != null && (prefix ? label.toString().startsWith(text) : text.contentEquals(label))) { found = node; break; }
            }
            if (found == null) SystemClock.sleep(50);
        }
        check(found != null, "Missing native dialog action " + text);
        while (!found.isClickable() && found.getParent() != null) found = found.getParent();
        check(found.performAction(AccessibilityNodeInfo.ACTION_CLICK), "Activate native dialog action " + text); waitForIdleSync();
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
