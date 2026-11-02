package com.jeremykenedy.firetv.ui.nativeapp.widget.watchlist;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.UiAutomation;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import com.jeremykenedy.firetv.ui.nativeapp.stateguard.GuardStore;
import java.util.Map;
import java.util.Set;

public final class WatchlistInstrumentation extends Instrumentation {
    private Activity activity;
    private Bundle arguments;
    private String step = "preflight";
    @Override public void onCreate(Bundle args) { super.onCreate(args); arguments = args == null ? new Bundle() : args; start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            check("com.jeremykenedy.firetv.ui.preview".equals(getTargetContext().getPackageName()), "Preview package required");
            String id = arguments.getString("state_guard_case_id");
            String hash = arguments.getString("state_guard_manifest_sha256");
            String receipt = arguments.getString("preupgrade_receipt_sha256");
            check(id != null && hash != null && hash.matches("[a-f0-9]{64}")
                    && receipt != null && receipt.matches("[a-f0-9]{64}")
                    && "true".equals(arguments.getString("preupgrade_receipt_verified")), "Verified pre-upgrade receipt and guard identity required");
            java.security.KeyStore keys = java.security.KeyStore.getInstance("AndroidKeyStore"); keys.load(null);
            check(!keys.aliases().hasMoreElements(), "An empty target Keystore is required");
            check(new GuardStore(getTargetContext().getDataDir()).verify(id,
                    new java.util.LinkedHashSet<String>(java.util.Arrays.asList("shared_prefs", "files", "no_backup")), hash).matches,
                    "All three durable guard roots must match before writes");
            result.putBoolean("state_guard_verified_before_write", true);
            result.putString("preupgrade_receipt_sha256", receipt);
        } catch (Throwable failure) {
            result.putString("error", "Fixture refused before writes: " + failure.toString());
            finish(Activity.RESULT_CANCELED, result); return;
        }
        com.jeremykenedy.firetv.ui.nativeapp.providers.plex.PlexFixture plex = null;
        SharedPreferences prefs = getTargetContext().getSharedPreferences(WatchlistStore.FILE, Context.MODE_PRIVATE);
        Map<String, ?> before = prefs.getAll();
        SharedPreferences layout = getTargetContext().getSharedPreferences("native_layout", Context.MODE_PRIVATE);
        Map<String, ?> layoutBefore = layout.getAll();
        try {
            mark("prepare fixture");
            AccessibilityServiceInfo info = automation().getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            automation().setServiceInfo(info);
            check(prefs.edit().clear().commit(), "Clear fixture");
            WatchlistWidgetProvider provider = new WatchlistWidgetProvider();
            activity = startActivitySync(new Intent(getTargetContext(), WatchlistActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync(); sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN); waitForIdleSync();
            mark("add title"); clickTag("watchlist:add"); enter("Fixture movie"); enterSecond("https://example.com/fixture"); clickText("Save");
            Watchlist list = new WatchlistStore(getTargetContext()).load();
            check(list.items.size() == 1 && list.items.get(0).title.equals("Fixture movie"), "Title added");
            String id = list.items.get(0).id;
            check(com.jeremykenedy.firetv.ui.nativeapp.layout.HomeSlots.widget("widget:tonight-watchlist") != null, "Watchlist is registered for Home");
            onMain(new Runnable() { @Override public void run() {
                View widget = provider.createView(activity);
                provider.onStart(widget);
                check(findText(widget, "Fixture movie") && findText(widget, "Availability unknown"), "Widget shows the saved title without an availability claim");
                provider.onStop(widget);
            }});
            WatchlistStore staging = new WatchlistStore(getTargetContext()); String beforeLong = staging.raw();
            Watchlist withLong = staging.load().add(new String(new char[512]).replace('\0', 'L'), "");
            String longId = withLong.items.get(1).id; staging.save(beforeLong, withLong);
            mark("actual Home"); verifyHome(result);
            mark("remove long title");
            staging = new WatchlistStore(getTargetContext()); String withLongRaw = staging.raw();
            staging.save(withLongRaw, staging.load().remove(longId));
            mark("link confirmation"); clickTag("watchlist:" + id); clickText("Open saved link");
            clickText("Cancel");
            mark("mark watched"); clickTag("watchlist:" + id); clickText("Mark watched");
            check(new WatchlistStore(getTargetContext()).load().items.get(0).watched, "Watched state saved");
            mark("edit title"); clickTag("watchlist:" + id); clickText("Edit title or link"); enter("Edited movie"); clickText("Save");
            check(new WatchlistStore(getTargetContext()).load().items.get(0).title.equals("Edited movie"), "Title edited");
            check(provider.state(getTargetContext()) == com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider.State.READY, "Widget contains saved title");
            mark("remove title"); clickTag("watchlist:" + id); clickText("Remove"); clickText("Remove");
            check(new WatchlistStore(getTargetContext()).load().items.isEmpty(), "Title removed");
            check(provider.state(getTargetContext()) == com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider.State.EMPTY, "Empty state");
            mark("synthetic Plex lookup"); plex = com.jeremykenedy.firetv.ui.nativeapp.providers.plex.PlexFixture.start(getTargetContext());
            WatchlistStore store = new WatchlistStore(getTargetContext()); String previous = store.raw();
            store.save(previous, store.load().addPlex("Fixture Discovery Movie", plex.discoveryKey()));
            com.jeremykenedy.firetv.ui.nativeapp.providers.plex.PlexWatchlistLookup.Result found =
                    com.jeremykenedy.firetv.ui.nativeapp.providers.plex.PlexWatchlistLookup.read(getTargetContext(), plex.discoveryKey(), new android.os.CancellationSignal());
            check(found.found && found.detailsUri != null && found.checkedAtMillis > 0, "Exact Plex item verified on synthetic server");
            String wrong = "plex:" + new String(new char[64]).replace('\0', 'f') + ":21";
            if (wrong.equals(plex.discoveryKey())) wrong = "plex:" + new String(new char[64]).replace('\0', 'e') + ":21";
            check(!com.jeremykenedy.firetv.ui.nativeapp.providers.plex.PlexWatchlistLookup.read(getTargetContext(), wrong, new android.os.CancellationSignal()).found,
                    "Unverifiable Plex item remains unknown");
            check(!store.raw().contains("fixture-server-token"), "Watchlist stores no Plex token");
            plex.assertHealthy();
            result.putString("result", "PASS: native title add, edit, watched, remove and widget state");
        } catch (Throwable error) { result.putString("step", step); result.putString("error", android.util.Log.getStackTraceString(error)); }
        finally {
            if (plex != null) try { plex.close(); } catch (Throwable failure) { result.putString("error", "Plex fixture cleanup failed"); }
            if (activity != null) try { onMain(new Runnable() { @Override public void run() { activity.finish(); } }); }
            catch (Throwable failure) { result.putString("error", "Watchlist fixture activity cleanup failed"); }
            SharedPreferences.Editor restore = prefs.edit().clear();
            for (Map.Entry<String, ?> entry : before.entrySet()) {
                Object value = entry.getValue(); String key = entry.getKey();
                if (value instanceof String) restore.putString(key, (String) value);
                else if (value instanceof Boolean) restore.putBoolean(key, (Boolean) value);
                else if (value instanceof Integer) restore.putInt(key, (Integer) value);
                else if (value instanceof Long) restore.putLong(key, (Long) value);
                else if (value instanceof Float) restore.putFloat(key, (Float) value);
                else if (value instanceof Set) { @SuppressWarnings("unchecked") Set<String> set = (Set<String>) value; restore.putStringSet(key, set); }
            }
            boolean restored = restore.commit() && before.equals(prefs.getAll());
            SharedPreferences.Editor restoreLayout = layout.edit().clear();
            for (Map.Entry<String, ?> entry : layoutBefore.entrySet()) {
                Object value = entry.getValue(); String key = entry.getKey();
                if (value instanceof String) restoreLayout.putString(key, (String) value);
                else if (value instanceof Boolean) restoreLayout.putBoolean(key, (Boolean) value);
                else if (value instanceof Integer) restoreLayout.putInt(key, (Integer) value);
                else if (value instanceof Long) restoreLayout.putLong(key, (Long) value);
                else if (value instanceof Float) restoreLayout.putFloat(key, (Float) value);
                else if (value instanceof Set) { @SuppressWarnings("unchecked") Set<String> set = (Set<String>) value; restoreLayout.putStringSet(key, set); }
            }
            restored &= restoreLayout.commit() && layoutBefore.equals(layout.getAll());
            result.putBoolean("restored", restored);
            if (!restored) result.putString("error", "Watchlist fixture restoration failed");
        }
        finish(result.containsKey("error") ? Activity.RESULT_CANCELED : Activity.RESULT_OK, result);
    }
    private void verifyHome(Bundle result) {
        mark("configure Home layout");
        Context context = getTargetContext();
        com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutOptions current =
                com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutPreferences.load(context,
                        com.jeremykenedy.firetv.ui.nativeapp.layout.HomeSlots.defaults());
        current = com.jeremykenedy.firetv.ui.nativeapp.layout.HomeSlots.discover(current, java.util.Collections.emptyList())
                .setVisible("widget:tonight-watchlist", true)
                .withMode(com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutOptions.Mode.BOTH);
        com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutPreferences.save(context, current);
        final Activity home = startActivitySync(new Intent().setClassName(context, "com.jeremykenedy.firetv.ui.nativeapp.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        try {
            mark("Home focus and visible bounds");
            waitForIdleSync(); sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN); waitForIdleSync();
            final boolean[] visible = new boolean[1];
            onMain(new Runnable() { @Override public void run() {
                View slot = home.getWindow().getDecorView().findViewWithTag("slot:widget:tonight-watchlist");
                check(slot != null && slot.requestFocus(), "Actual Home watchlist slot takes focus");
            }});
            long settle = SystemClock.uptimeMillis() + 3000;
            while (!visible[0] && SystemClock.uptimeMillis() < settle) {
                waitForIdleSync();
                onMain(new Runnable() { @Override public void run() {
                    View slot = home.getWindow().getDecorView().findViewWithTag("slot:widget:tonight-watchlist");
                    if (slot == null) return;
                    android.widget.TextView shortRow = slot.findViewWithTag("watchlist:title:0");
                    android.widget.TextView longRow = slot.findViewWithTag("watchlist:title:1");
                    android.widget.TextView footer = slot.findViewWithTag("watchlist:availability");
                    android.graphics.Rect bounds = new android.graphics.Rect();
                    visible[0] = home.hasWindowFocus() && slot.isFocused() && shortRow != null && longRow != null && footer != null
                            && shortRow.getText().toString().equals("Fixture movie")
                            && longRow.getText().length() == 512 && longRow.getMaxLines() == 1
                            && longRow.getLayout() != null && longRow.getLayout().getEllipsisCount(0) > 0
                            && footer.getText().toString().equals("Availability unknown")
                            && footer.getGlobalVisibleRect(bounds) && bounds.width() == footer.getWidth()
                            && bounds.height() == footer.getHeight();
                }});
                if (!visible[0]) SystemClock.sleep(60);
            }
            check(visible[0], "Actual Home shows short and bounded long titles with the entire availability footer visible");
            mark("Home capture hold");
            SystemClock.sleep(1500);
            onMain(new Runnable() { @Override public void run() {
                View slot = home.getWindow().getDecorView().findViewWithTag("slot:widget:tonight-watchlist");
                View footer = slot == null ? null : slot.findViewWithTag("watchlist:availability");
                android.graphics.Rect bounds = new android.graphics.Rect();
                check(home.hasWindowFocus() && slot != null && slot.isFocused() && footer != null
                        && footer.getGlobalVisibleRect(bounds) && bounds.width() == footer.getWidth() && bounds.height() == footer.getHeight(),
                        "Home focus and availability footer remain visible after the capture hold");
            }});
            ActivityMonitor monitor = addMonitor(WatchlistActivity.class.getName(), null, false);
            mark("D-pad open editor");
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            Activity opened = waitForMonitorWithTimeout(monitor, 5000); removeMonitor(monitor);
            check(opened != null, "D-pad Select opens watchlist from Home");
            activity = opened;
            result.putBoolean("actual_home_visible_and_dpad", true);
        } finally { mark("finish Home"); onMain(new Runnable() { @Override public void run() { home.finish(); } }); waitForIdleSync(); }
        mark("editor active after Home");
        final boolean[] ready = {false}; long deadline = SystemClock.uptimeMillis() + 5000;
        while (!ready[0] && SystemClock.uptimeMillis() < deadline) {
            onMain(new Runnable() { @Override public void run() {
                ready[0] = activity != null && !activity.isFinishing() && !activity.isDestroyed() && activity.hasWindowFocus();
            }});
            if (!ready[0]) SystemClock.sleep(60);
        }
        check(ready[0], "D-pad opened editor must remain active after Home finishes");
    }
    private static boolean findText(View view, String text) {
        if (view instanceof android.widget.TextView && ((android.widget.TextView) view).getText().toString().contains(text)) return true;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (findText(group.getChildAt(i), text)) return true;
        }
        return false;
    }
    private void clickTag(final String tag) {
        onMain(new Runnable() { @Override public void run() {
            View view = activity.getWindow().getDecorView().findViewWithTag(tag);
            check(view != null, "Missing native control " + tag); view.requestFocus(); view.performClick();
        } }); waitForIdleSync();
    }
    private void holdTag(final String tag) {
        onMain(new Runnable() { @Override public void run() {
            View view = activity.getWindow().getDecorView().findViewWithTag(tag);
            check(view != null, "Missing item " + tag); view.requestFocus(); view.performLongClick();
        } }); waitForIdleSync();
    }
    private void enter(String value) {
        AccessibilityNodeInfo node = null;
        for (int attempt = 0; attempt < 40 && node == null; attempt++) {
            for (AccessibilityNodeInfo root : appWindows()) {
                node = findEdit(root); if (node != null) break;
            }
            if (node == null) SystemClock.sleep(50);
        }
        check(node != null, "Native text entry visible in the target app"); Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
        check(node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args), "Enter native text"); waitForIdleSync();
        check(node.refresh() && value.contentEquals(node.getText()), "Native editor contains the entered text");
    }
    private AccessibilityNodeInfo findEdit(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if ("android.widget.EditText".contentEquals(node.getClassName())) return node;
        for (int i = 0; i < node.getChildCount(); i++) { AccessibilityNodeInfo found = findEdit(node.getChild(i)); if (found != null) return found; }
        return null;
    }
    private void enterSecond(String value) {
        java.util.List<AccessibilityNodeInfo> fields = new java.util.ArrayList<>();
        for (AccessibilityNodeInfo root : appWindows()) collectEdits(root, fields);
        check(fields.size() >= 2, "Native HTTPS link field visible");
        Bundle args = new Bundle(); args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
        check(fields.get(1).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args), "Enter HTTPS link"); waitForIdleSync();
        check(fields.get(1).refresh() && value.contentEquals(fields.get(1).getText()), "HTTPS link retained in editor");
    }
    private void collectEdits(AccessibilityNodeInfo node, java.util.List<AccessibilityNodeInfo> result) {
        if (node == null) return;
        if ("android.widget.EditText".contentEquals(node.getClassName())) result.add(node);
        for (int i = 0; i < node.getChildCount(); i++) collectEdits(node.getChild(i), result);
    }
    private void clickText(String text) {
        AccessibilityNodeInfo found = null;
        for (int attempt = 0; attempt < 40 && found == null; attempt++) {
            for (AccessibilityNodeInfo root : appWindows()) {
                found = matching(root, text); if (found != null) break;
            }
            if (found == null) SystemClock.sleep(50);
        }
        check(found != null, "Missing dialog action " + text);
        while (!found.isClickable() && found.getParent() != null) found = found.getParent();
        check(found.performAction(AccessibilityNodeInfo.ACTION_CLICK), "Activate dialog action " + text);
        waitForIdleSync();
    }
    private AccessibilityNodeInfo matching(AccessibilityNodeInfo node, String text) {
        if (node == null) return null;
        if (node.isVisibleToUser() && text.equalsIgnoreCase(String.valueOf(node.getText()))) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = matching(node.getChild(i), text); if (found != null) return found;
        }
        return null;
    }
    private UiAutomation automation() { return getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES); }
    private java.util.List<AccessibilityNodeInfo> appWindows() {
        java.util.List<AccessibilityNodeInfo> roots = new java.util.ArrayList<>();
        for (AccessibilityWindowInfo window : automation().getWindows()) {
            AccessibilityNodeInfo root = window.getRoot();
            if (root != null && getTargetContext().getPackageName().equals(String.valueOf(root.getPackageName()))) roots.add(root);
        }
        if (roots.isEmpty()) {
            AccessibilityNodeInfo root = automation().getRootInActiveWindow();
            if (root != null && getTargetContext().getPackageName().equals(String.valueOf(root.getPackageName()))) roots.add(root);
        }
        return roots;
    }
    private void onMain(final Runnable action) {
        final Throwable[] failure = new Throwable[1];
        super.runOnMainSync(new Runnable() { @Override public void run() { try { action.run(); } catch (Throwable error) { failure[0] = error; } } });
        if (failure[0] != null) throw new AssertionError("Main-thread fixture action failed", failure[0]);
    }
    private void mark(String value) {
        step = value; Bundle status = new Bundle(); status.putString("step", value); sendStatus(0, status);
        android.util.Log.i("WatchlistProof", value);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
