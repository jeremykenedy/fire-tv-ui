package com.jeremykenedy.firetv.ui.nativeapp.providers;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.cards.ChannelCardView;
import com.jeremykenedy.firetv.ui.nativeapp.cards.HomeContentView;
import com.jeremykenedy.firetv.ui.nativeapp.layout.HomeSlots;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutPreferences;
import com.jeremykenedy.firetv.ui.nativeapp.providers.plex.RoutePlexFixture;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/** Disposable production-package fixture. Injects a retained Program into the real Home host. */
public final class PlexHomeRouteInstrumentation extends Instrumentation {
    private static final String PACKAGE = "com.jeremykenedy.firetv.ui";
    private static final String MAIN = "com.jeremykenedy.firetv.ui.nativeapp.MainActivity";
    private static final String COMPAT = "com.jeremykenedy.firetv.ui.nativeapp.providers.plex.LegacyPlexCardActivity";
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle(); Activity main = null, detail = null; RoutePlexFixture fixture = null;
        Context context = getTargetContext();
        SharedPreferences layout = context.getSharedPreferences("native_layout", Context.MODE_PRIVATE);
        Map<String, ?> before = layout.getAll();
        String step = "target and isolated connection";
        try {
            check(PACKAGE.equals(context.getPackageName()), "Expected disposable production package.");
            fixture = RoutePlexFixture.start(context);
            String[] parts = fixture.firstKey().substring("plex:".length()).split(":");
            Intent retained = new Intent(Intent.ACTION_VIEW, Uri.parse("firetvui://plex/item/" + parts[0] + "/" + parts[1]))
                    .setClassName(PACKAGE, PACKAGE + ".PlexItemActivity");
            final TvRecommendations.Program program = new TvRecommendations.Program(fixture.firstKey(), PACKAGE,
                    "Synthetic retained card", null, retained.toUri(Intent.URI_INTENT_SCHEME));
            final TvRecommendations.Feed feed = new TvRecommendations.Feed("synthetic-retained-plex", "Synthetic retained Plex", PACKAGE,
                    Collections.singletonList(program));
            LayoutPreferences.save(context, LayoutPreferences.load(context, HomeSlots.defaults())
                    .discover(Collections.singletonList(feed.item)).setVisible(feed.item.key(), true));
            step = "open candidate Home";
            main = startActivitySync(new Intent().setClassName(context, MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            waitForIdleSync();
            long windowDeadline = SystemClock.uptimeMillis() + 5000;
            boolean windowReady = false;
            while (SystemClock.uptimeMillis() < windowDeadline) {
                final Activity window = main; final boolean[] ready = {false};
                onMain(new Runnable() { @Override public void run() {
                    ready[0] = window.getWindow().getDecorView().isAttachedToWindow()
                            && window.getWindow().getDecorView().hasWindowFocus();
                } });
                if (ready[0]) { windowReady = true; break; }
                SystemClock.sleep(50);
            }
            check(windowReady, "Candidate Home window did not obtain focus.");
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN); waitForIdleSync();
            final HomeContentView home = findHome(main.getWindow().getDecorView());
            check(home != null, "Candidate Main does not contain HomeContentView.");
            final ChannelCardView[] card = new ChannelCardView[1];
            step = "inject retained program into real Home";
            onMain(new Runnable() { @Override public void run() {
                home.applyResult(home.beginRefresh(), new TvRecommendations.Result(TvRecommendations.Status.READY, Collections.singletonList(feed)));
                card[0] = home.findCard(feed.item.key());
                check(card[0] != null && card[0].currentProgram() == program, "Home card did not display injected program.");
                View focus = card[0];
                while (focus != null && !focus.isFocusable())
                    focus = focus.getParent() instanceof View ? (View) focus.getParent() : null;
                check(focus != null && focus.isAttachedToWindow() && focus.hasWindowFocus() && focus.requestFocus(),
                        "Home card slot could not take focus.");
            } });
            step = "select actual Home card and action";
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER); waitForIdleSync();
            long deadline = SystemClock.uptimeMillis() + 5000;
            boolean action = false;
            while (SystemClock.uptimeMillis() < deadline) {
                AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
                if (root != null && !root.findAccessibilityNodeInfosByText("Open title in").isEmpty()) { action = true; root.recycle(); break; }
                if (root != null) root.recycle();
                SystemClock.sleep(50);
            }
            check(action, "Actual Home action dialog did not expose Open title.");
            ActivityMonitor monitor = addMonitor(COMPAT, null, false);
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            detail = waitForMonitorWithTimeout(monitor, 5000); removeMonitor(monitor);
            check(detail != null, "Home action did not open the private compatibility activity.");
            step = "verified synthetic item";
            deadline = SystemClock.uptimeMillis() + 5000; boolean found = false;
            while (SystemClock.uptimeMillis() < deadline) {
                final Activity observed = detail; final boolean[] visible = {false};
                onMain(new Runnable() { @Override public void run() { visible[0] = contains(observed.getWindow().getDecorView(), "Fixture Show: Pilot"); } });
                if (visible[0]) { found = true; break; } SystemClock.sleep(50);
            }
            check(found, "Compatibility activity did not revalidate and show the synthetic item.");
            fixture.assertHealthy();
            result.putString("status", "PASS");
            result.putBoolean("actual_home_card_action_opens_private_alias", true);
            result.putBoolean("synthetic_server_item_revalidated", true);
            result.putString("limits", "Synthetic injected Program; TV provider persisted row, real Plex and physical TV are not covered.");
        } catch (Throwable failure) { result.putString("status", "FAIL"); result.putString("step", step); result.putString("reason", failure.toString()); if (failure.getCause() != null) result.putString("cause", failure.getCause().toString()); }
        finally {
            try {
                if (detail != null) { final Activity value = detail; onMain(new Runnable() { @Override public void run() { value.finish(); } }); }
                if (main != null) { final Activity value = main; onMain(new Runnable() { @Override public void run() { value.finish(); } }); }
                waitForIdleSync();
            } catch (Throwable failure) { result.putString("activity_cleanup_error", failure.toString()); }
            if (fixture != null) try { fixture.close(); result.putBoolean("fixture_connection_removed", true); }
                catch (Exception failure) { result.putBoolean("fixture_connection_removed", false); result.putString("fixture_cleanup_error", failure.toString()); }
            try { result.putBoolean("native_layout_restored", restore(layout, before)); }
            catch (Throwable failure) { result.putBoolean("native_layout_restored", false); result.putString("layout_cleanup_error", failure.toString()); }
            if (!result.getBoolean("fixture_connection_removed") || !result.getBoolean("native_layout_restored")
                    || result.containsKey("activity_cleanup_error")) result.putString("status", "FAIL");
        }
        finish(0, result);
    }
    private void onMain(final Runnable action) {
        final Throwable[] failure = {null};
        runOnMainSync(new Runnable() { @Override public void run() {
            try { action.run(); } catch (Throwable error) { failure[0] = error; }
        } });
        if (failure[0] != null) throw new AssertionError("Main-thread fixture action failed", failure[0]);
    }
    private static HomeContentView findHome(View view) {
        if (view instanceof HomeContentView) return (HomeContentView) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            HomeContentView found = findHome(((ViewGroup) view).getChildAt(i)); if (found != null) return found;
        }
        return null;
    }
    private static boolean contains(View view, String text) {
        if (view instanceof TextView && ((TextView) view).getText().toString().contains(text)) return true;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            if (contains(((ViewGroup) view).getChildAt(i), text)) return true;
        return false;
    }
    @SuppressWarnings("unchecked") private static boolean restore(SharedPreferences prefs, Map<String, ?> before) {
        SharedPreferences.Editor edit = prefs.edit().clear();
        for (Map.Entry<String, ?> entry : before.entrySet()) {
            Object value = entry.getValue(); String key = entry.getKey();
            if (value instanceof String) edit.putString(key, (String) value);
            else if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
            else if (value instanceof Integer) edit.putInt(key, (Integer) value);
            else if (value instanceof Long) edit.putLong(key, (Long) value);
            else if (value instanceof Float) edit.putFloat(key, (Float) value);
            else if (value instanceof Set) edit.putStringSet(key, (Set<String>) value);
            else return false;
        }
        return edit.commit() && prefs.getAll().equals(before);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
