package com.jeremykenedy.firetv.ui.nativeapp.widget.livetv;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Operator must install/publish isolated EPG fixture and preserve ordinary listings permission first. */
public final class LiveTvInstrumentation extends Instrumentation {
    private static final String FIXTURE = "com.jeremykenedy.firetv.ui.livetvfixture";
    private Activity configuration;
    private LiveTvView view;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle(); int code = -1;
        final SharedPreferences preferences = getTargetContext().getSharedPreferences("native_live_tv_favorites", 0);
        String previous = preferences.getString("configuration", null);
        String written = null;
        try {
            LiveTvRepository.Channels channels = LiveTvRepository.channels(getTargetContext(), null);
            check(channels.status == LiveTvRepository.Status.READY || channels.status == LiveTvRepository.Status.PARTIAL,
                    "Ordinary TV listing access must be granted by operator");
            final List<LiveGuide.Channel> owned = new ArrayList<>();
            for (LiveGuide.Channel channel : channels.values) if (FIXTURE.equals(channel.publisher)) owned.add(channel);
            check(owned.size() == 4, "Four actual published fixture channels are visible");
            LiveGuide.Channel current = null;
            for (LiveGuide.Channel channel : owned) {
                LiveGuide.Listing listing = LiveTvRepository.listing(getTargetContext(), channel, System.currentTimeMillis(), null);
                if (channel.name.contains("Current")) {
                    current = channel;
                    check(listing.state == LiveGuide.State.READY && "Fixture now".equals(listing.now.title) && "Fixture next".equals(listing.next.title), "Actual Programs current/next are selected");
                } else if (channel.name.contains("Stale")) check(listing.state == LiveGuide.State.STALE, "Ended guide is stale");
                else if (channel.name.contains("Missing")) check(listing.state == LiveGuide.State.MISSING, "Absent guide is missing");
                else if (channel.name.contains("Gap")) check(listing.state == LiveGuide.State.GAP, "Future-only guide exposes gap");
            }
            check(current != null, "Current fixture channel found");
            final LiveGuide.Channel channel = current;
            Activity settings = startActivitySync(new Intent().setClassName(getTargetContext(),
                    "com.jeremykenedy.firetv.ui.nativeapp.SettingsActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            click(settings, "Widgets");
            ActivityMonitor layoutMonitor = addMonitor("com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutSettingsActivity", null, false);
            click(settings, "Your cards and widgets");
            final Activity layout = waitForMonitorWithTimeout(layoutMonitor, 5000); removeMonitor(layoutMonitor);
            check(layout != null, "Real Widgets route opens layout settings");
            waitTag(layout, "widget:live-tv-favorites:configure");
            ActivityMonitor configMonitor = addMonitor(LiveTvSettingsActivity.class.getName(), null, false);
            onMain(new Runnable() { @Override public void run() { layout.getWindow().getDecorView().findViewWithTag("widget:live-tv-favorites:configure").performClick(); }});
            configuration = waitForMonitorWithTimeout(configMonitor, 5000); removeMonitor(configMonitor);
            check(configuration != null, "Registry Configure route opens actual Live TV settings");
            waitTag(configuration, "livetv:favorite:" + channel.key());
            onMain(new Runnable() { @Override public void run() {
                ViewGroup root = (ViewGroup) configuration.getWindow().getDecorView();
                clearChecks(root);
                ((CheckBox) root.findViewWithTag("livetv:favorite:" + channel.key())).setChecked(true);
                ((EditText) root.findViewWithTag("livetv:zone")).setText("America/Los_Angeles");
            }});
            ActivityMonitor recreatedMonitor = addMonitor(LiveTvSettingsActivity.class.getName(), null, false);
            onMain(new Runnable() { @Override public void run() { configuration.recreate(); }});
            configuration = waitForMonitorWithTimeout(recreatedMonitor, 5000); removeMonitor(recreatedMonitor);
            check(configuration != null, "Live TV configuration recreates");
            waitTag(configuration, "livetv:favorite:" + channel.key());
            onMain(new Runnable() { @Override public void run() {
                View root = configuration.getWindow().getDecorView();
                check(((CheckBox) root.findViewWithTag("livetv:favorite:" + channel.key())).isChecked(), "Unsaved favorite survives activity recreation");
                check("America/Los_Angeles".contentEquals(((EditText) root.findViewWithTag("livetv:zone")).getText()), "Unsaved timezone survives activity recreation");
            }});
            click(configuration, "Save favorites");
            written = preferences.getString("configuration", null);
            check(LiveTvPreferences.read(getTargetContext()).favorites.size() == 1, "Selected favorites persisted independently of source channels");
            onMain(new Runnable() { @Override public void run() {
                view = new LiveTvView(configuration);
                configuration.setContentView(view);
                view.start();
            }});
            waitTag(configuration, "livetv:listing:" + channel.key());
            onMain(new Runnable() { @Override public void run() {
                TextView guide = (TextView) view.findViewWithTag("livetv:listing:" + channel.key());
                check(guide.getText().toString().contains("Fixture now") && guide.getText().toString().contains("Fixture next"), "Native widget renders actual now/next");
                check(((TextView) view.findViewWithTag("livetv:guide-status")).getText().toString().contains("America/Los_Angeles"), "Native widget shows configured timezone");
                check(view.findViewWithTag("livetv:open:" + channel.key()).requestFocus(), "Channel source action accepts remote focus");
            }});
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            check(waitPackage(FIXTURE), "Publisher-owned channel source link visibly opens fixture");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            check(waitPackage(getTargetContext().getPackageName()), "Back returns to launcher-hosted native widget");
            onMain(new Runnable() { @Override public void run() { view.stop(); check(!view.isRefreshing(), "Refresh stops with view lifecycle"); }});
            result.putString("scenarios", "actual Channels/Programs now-next/stale/missing/gap, registry Configure route, explicit favorite/timezone save, native widget render, D-pad source link and Back, lifecycle stop");
            result.putString("limits", "Widget view hosted in real configuration activity for isolated proof; actual Home placement/focus and real-provider tuning require separate integration/hardware evidence. Fixture provides no playback.");
        } catch (Throwable failed) { code = 0; result.putString("failure", failed.toString()); }
        finally {
            if (view != null) onMain(new Runnable() { @Override public void run() { view.stop(); }});
            String current = preferences.getString("configuration", null);
            if (written != null && written.equals(current)) {
                SharedPreferences.Editor editor = preferences.edit();
                if (previous == null) editor.remove("configuration"); else editor.putString("configuration", previous);
                result.putBoolean("preferences_restored", editor.commit());
            } else result.putString("preferences_restore", written == null ? "unchanged" : "intervening change preserved");
        }
        finish(code, result);
    }
    private void clearChecks(View view) {
        if (view instanceof CheckBox) ((CheckBox) view).setChecked(false);
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) clearChecks(((ViewGroup) view).getChildAt(i));
    }
    private void waitTag(final Activity activity, final String tag) {
        long deadline = SystemClock.uptimeMillis() + 7000;
        while (SystemClock.uptimeMillis() < deadline) {
            final boolean[] found = {false};
            onMain(new Runnable() { @Override public void run() { found[0] = activity.getWindow().getDecorView().findViewWithTag(tag) != null; }});
            if (found[0]) return;
            SystemClock.sleep(40);
        }
        throw new AssertionError("Missing native view " + tag);
    }
    private void click(final Activity activity, final String title) {
        waitForIdleSync();
        onMain(new Runnable() { @Override public void run() {
            Button target = button(activity.getWindow().getDecorView(), title); check(target != null, "Missing actual route " + title); target.performClick();
        }}); waitForIdleSync();
    }
    private Button button(View view, String title) {
        if (view instanceof Button && title.contentEquals(((Button) view).getText())) return (Button) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            Button found = button(((ViewGroup) view).getChildAt(i), title); if (found != null) return found;
        }
        return null;
    }
    private boolean waitPackage(String expected) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < deadline) {
            android.view.accessibility.AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (root != null) { boolean matches = expected.contentEquals(root.getPackageName()); root.recycle(); if (matches) return true; }
            SystemClock.sleep(40);
        }
        return false;
    }
    private void onMain(Runnable task) { runOnMainSync(task); }
    private void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
