package com.jeremykenedy.firetv.ui.nativeapp.widget.feeds;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/** Native fixture only: parser, focus, stale state, cancel, no automatic link opening and lifecycle. */
public final class FeedInstrumentation extends Instrumentation {
    private FeedFixtureActivity activity;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        boolean owned = false;
        try {
            check("com.jeremykenedy.firetv.ui.feedfixture".equals(getTargetContext().getPackageName()), "Refuse launcher packages");
            owned = true;
            new FeedStore(getTargetContext()).clear();
            check(new FeedWidgetProvider().state(getTargetContext()) == com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider.State.SETUP_REQUIRED,
                    "No selected source must show setup required");
            String malicious = "<!DOCTYPE rss [<!ENTITY bad SYSTEM 'file:///private-fixture'>]><rss version='2.0'><channel>&bad;</channel></rss>";
            boolean rejected = false;
            try { FeedParser.read(FeedSource.Format.RSS_2, new ByteArrayInputStream(malicious.getBytes(StandardCharsets.UTF_8)), new FeedParser.Cancellation()); }
            catch (Exception expected) { rejected = true; }
            check(rejected, "Actual Android parser must reject entity declarations");
            FeedFixtureActivity.fail = FeedFixtureActivity.block = FeedFixtureActivity.empty = false;
            FeedFixtureActivity.requests = 0;
            launch(); waitStatus("Headlines from the selected source", 4000);
            final int initial = FeedFixtureActivity.requests;
            onMain(new Runnable() {
                @Override public void run() {
                    activity.widget.start();
                    check(FeedFixtureActivity.requests == initial, "Repeated onStart must not duplicate the request");
                    check(view("item:0").getContentDescription().toString().contains("Native fixture"), "Headline must be source labelled");
                    check(!((TextView) view("status")).getText().toString().contains("private="), "Private query must not appear in status");
                    view("item:0").requestFocus();
                }
            });
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            check(waitText("Open headline link?", 2000), "Select must request explicit link confirmation");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            long returnDeadline = SystemClock.uptimeMillis() + 5000;
            while (!activity.hasWindowFocus() && SystemClock.uptimeMillis() < returnDeadline) SystemClock.sleep(40);
            check(activity.hasWindowFocus(), "Feed panel regains window focus after cancel");
            click("item:0");
            check(waitText("Hide headline", 2000), "Headline confirmation offers Hide");
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            check(root != null, "Headline confirmation window exists");
            java.util.List<AccessibilityNodeInfo> hideActions = root.findAccessibilityNodeInfosByText("Hide headline");
            check(!hideActions.isEmpty() && hideActions.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK), "Hide action executes");
            for (AccessibilityNodeInfo action : hideActions) action.recycle();
            root.recycle(); waitForIdleSync();
            waitStatus("All saved headlines are hidden", 2000);
            onMain(new Runnable() { @Override public void run() {
                try {
                    FeedStore store = new FeedStore(activity);
                    FeedSource selected = store.read();
                    check(new FeedStore(activity).hidden(selected).contains(FeedSnapshot.hiddenKey("native-one")), "Hidden item survives rereading encrypted settings");
                    store.save(selected);
                    check(store.hidden(selected).size() == 1, "Saving the same source preserves hidden items");
                    activity.widget.stop(); activity.widget.start();
                    check(view("item:0").getVisibility() == View.GONE, "Hidden headline disappears after restart");
                    check(view("restore").getVisibility() == View.VISIBLE, "Restore action remains reachable when all headlines are hidden");
                } catch (java.io.IOException failed) { throw new AssertionError(failed); }
            } });
            click("restore");
            onMain(new Runnable() { @Override public void run() {
                check(view("item:0").getVisibility() == View.VISIBLE, "Restore returns hidden headline");
                try {
                    FeedStore store = new FeedStore(activity); FeedSource original = store.read();
                    store.hide(original, "native-one");
                    FeedSource other = new FeedSource("Other", "https://other.invalid/feed", FeedSource.Format.RSS_2);
                    store.save(other); check(store.hidden(other).isEmpty(), "Changed source does not inherit hidden IDs");
                    store.save(original); activity.widget.stop(); activity.widget.start();
                } catch (java.io.IOException failed) { throw new AssertionError(failed); }
            } });
            waitStatus("Headlines from the selected source", 4000);
            FeedFixtureActivity.fail = true; click("refresh"); waitStatus("Stale headlines", 4000);
            onMain(new Runnable() { @Override public void run() { check(view("item:0").getVisibility() == View.VISIBLE, "Failed refresh must retain explicitly stale items"); } });
            FeedFixtureActivity.fail = false; FeedFixtureActivity.block = true; click("refresh");
            waitBusy(true); click("cancel"); waitStatus("Refresh cancelled", 4000); waitBusy(false);
            FeedFixtureActivity.empty = true; FeedFixtureActivity.block = false; click("refresh"); waitStatus("no supported headline", 4000);
            FeedFixtureActivity.empty = false; FeedFixtureActivity.block = true; click("refresh"); waitBusy(true);
            final String[] stoppedText = new String[1];
            onMain(new Runnable() { @Override public void run() {
                activity.widget.stop(); stoppedText[0] = ((TextView) view("status")).getText().toString();
            } });
            FeedFixtureActivity.block = false; SystemClock.sleep(120);
            onMain(new Runnable() { @Override public void run() {
                check(!activity.widget.isStarted() && !activity.widget.isRefreshing(), "Stop must release refresh lifecycle");
                check(stoppedText[0].equals(((TextView) view("status")).getText().toString()), "Obsolete callback must not change a stopped view");
                activity.finish();
            } });
            launch(); waitStatus("Headlines from the selected source", 4000);
            result.putString("status", "PASS");
            result.putString("checks", "Actual native XML rejection, explicit source, labelled headline, D-pad link confirmation/cancel, hide/persistence/restore/source isolation, stale retention, request cancel, empty feed, stop/restart");
            result.putString("limits", "Standalone fixture; no live HTTPS service, external browser handoff, Home slot integration, hardware or owner visual acceptance claim");
        } catch (Throwable failed) { result.putString("status", "FAIL"); result.putString("reason", failed.toString()); }
        finally {
            if (activity != null) onMain(new Runnable() { @Override public void run() { activity.widget.stop(); activity.finish(); } });
            if (owned) new FeedStore(getTargetContext()).clear();
        }
        finish(0, result);
    }
    private void launch() {
        activity = (FeedFixtureActivity) startActivitySync(new Intent().setClassName(getTargetContext(), FeedFixtureActivity.class.getName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitForIdleSync();
    }
    private View view(String tag) { return activity.getWindow().getDecorView().findViewWithTag("feed:" + tag); }
    private void click(final String tag) {
        onMain(new Runnable() { @Override public void run() { check(view(tag).requestFocus(), "Remote focus missing: " + tag); } });
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER); waitForIdleSync();
    }
    private void waitBusy(final boolean expected) {
        long deadline = SystemClock.uptimeMillis() + 3000; final boolean[] reached = {false};
        while (!reached[0] && SystemClock.uptimeMillis() < deadline) {
            onMain(new Runnable() { @Override public void run() { reached[0] = activity.widget.isRefreshing() == expected; } }); SystemClock.sleep(20);
        }
        check(reached[0], "Refresh lifecycle did not reach expected state");
    }
    private void waitStatus(final String text, long timeout) {
        long deadline = SystemClock.uptimeMillis() + timeout; final boolean[] reached = {false};
        while (!reached[0] && SystemClock.uptimeMillis() < deadline) {
            onMain(new Runnable() { @Override public void run() { reached[0] = ((TextView) view("status")).getText().toString().contains(text); } }); SystemClock.sleep(20);
        }
        check(reached[0], "Missing native feed state: " + text);
    }
    private boolean waitText(String text, long timeout) {
        long deadline = SystemClock.uptimeMillis() + timeout;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (root != null) { boolean found = !root.findAccessibilityNodeInfosByText(text).isEmpty(); root.recycle(); if (found) return true; }
            SystemClock.sleep(30);
        }
        return false;
    }
    private void onMain(final Runnable action) {
        final Throwable[] failed = {null};
        runOnMainSync(new Runnable() { @Override public void run() {
            try { action.run(); } catch (Throwable error) { failed[0] = error; }
        } });
        if (failed[0] != null) throw new AssertionError(failed[0]);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
