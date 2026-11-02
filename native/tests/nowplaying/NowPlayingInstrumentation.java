package com.jeremykenedy.firetv.ui.nativeapp.widget.nowplaying;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import com.jeremykenedy.firetv.ui.nativeapp.layout.HomeSlots;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutOptions;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutPreferences;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider;
import com.jeremykenedy.firetv.ui.nativeapp.providers.NotificationRecommendations;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Synthetic cross-app MediaSession proof; operator grants normal notification access before granted mode. */
public final class NowPlayingInstrumentation extends Instrumentation {
    private static final String FIXTURE = "com.jeremykenedy.firetv.nowplayingfixture";
    private String mode;
    private NowPlayingView view;
    private Activity home;
    private View originalSlot;
    private NowPlayingSessions.Observer observer;

    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); mode = arguments == null ? "granted" : arguments.getString("mode", "granted"); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        final SharedPreferences preferences = getTargetContext().getSharedPreferences("native_layout", Context.MODE_PRIVATE);
        Map<String, ?> before = preferences.getAll();
        try {
            if ("denied".equals(mode)) {
                check(!NotificationRecommendations.accessGranted(getTargetContext()), "Denied mode requires notification access off.");
                check(new NowPlayingWidgetProvider().state(getTargetContext()) == WidgetProvider.State.SETUP_REQUIRED,
                        "Denied access must show setup rather than empty playback.");
                check(NowPlayingSessions.read(getTargetContext()).sessions.isEmpty(), "Denied mode must not expose sessions.");
                result.putBoolean("denied_setup_state", true);
            } else {
                check(NotificationRecommendations.accessGranted(getTargetContext()), "Grant normal notification access before granted mode.");
                releaseFixture();
                awaitNoFixture();
                check(NowPlayingSessions.read(getTargetContext()).state == NowPlayingSessions.State.EMPTY,
                        "Home transition proof requires an isolated emulator with no other active sessions.");
                check(HomeSlots.widget(NowPlayingWidgetProvider.ITEM.key()) instanceof NowPlayingWidgetProvider,
                        "Integrate NowPlayingWidgetProvider in the next-batch registry before native Home proof.");
                LayoutOptions layout = HomeSlots.discover(LayoutPreferences.load(getTargetContext(), HomeSlots.defaults()),
                        Collections.<com.jeremykenedy.firetv.ui.nativeapp.providers.TvRecommendations.Feed>emptyList());
                layout = layout.withMode(LayoutOptions.Mode.WIDGETS_ONLY).setVisible(NowPlayingWidgetProvider.ITEM.key(), true)
                        .moveBefore(NowPlayingWidgetProvider.ITEM.key(), layout.order.get(0));
                LayoutPreferences.save(getTargetContext(), layout);
                home = startActivitySync(new Intent().setClassName(getTargetContext(),
                        "com.jeremykenedy.firetv.ui.nativeapp.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                waitForIdleSync();
                onMain(new Runnable() {
                    @Override public void run() {
                        originalSlot = home.getWindow().getDecorView().findViewWithTag("slot:" + NowPlayingWidgetProvider.ITEM.key());
                        check(originalSlot != null && originalSlot.requestFocus(), "Actual Home must retain a focusable empty Now Playing slot.");
                        check(widget(originalSlot) == null, "Empty Home slot must show its real empty state before publication.");
                    }
                });
                final CountDownLatch changed = new CountDownLatch(1);
                onMain(new Runnable() {
                    @Override public void run() {
                        observer = NowPlayingSessions.observe(getTargetContext(), new Runnable() {
                            @Override public void run() { changed.countDown(); }
                        });
                        check(observer.start(), "Actual authorized session observer must register.");
                    }
                });
                getTargetContext().sendBroadcast(new Intent().setClassName(FIXTURE, FIXTURE + ".FixtureReceiver").putExtra("command", "publish"));
                NowPlayingSessions.Session initial = awaitFixture("Native media fixture", "Playing");
                check(changed.await(5, TimeUnit.SECONDS), "Actual session-list callback must observe fixture publication.");
                check(initial.actions.contains(SessionControls.Action.PAUSE) && !initial.actions.contains(SessionControls.Action.STOP),
                        "Controls must reflect the fixture's actual advertised actions.");
                awaitHomeWidget();
                check(NowPlayingSessions.request(getTargetContext(), initial, SessionControls.Action.PAUSE) == NowPlayingSessions.Outcome.REQUESTED,
                        "Advertised Pause must dispatch to the real session.");
                NowPlayingSessions.Session paused = awaitFixture("Native media fixture", "Paused");
                check(NowPlayingSessions.request(getTargetContext(), initial, SessionControls.Action.PAUSE) == NowPlayingSessions.Outcome.UNAVAILABLE,
                        "Previously advertised Pause must be rechecked after the state changes.");
                check(NowPlayingSessions.request(getTargetContext(), paused, SessionControls.Action.STOP) == NowPlayingSessions.Outcome.UNAVAILABLE,
                        "An unadvertised Stop must not dispatch.");
                check(NowPlayingSessions.request(getTargetContext(), paused, SessionControls.Action.NEXT) == NowPlayingSessions.Outcome.REQUESTED,
                        "Advertised Next must dispatch to the fixture.");
                awaitFixture("Native next fixture", "Paused");
                awaitView("Native next fixture");
                releaseFixture();
                awaitNoFixture();
                awaitHomeEmpty();
                check(NowPlayingSessions.request(getTargetContext(), paused, SessionControls.Action.PLAY) == NowPlayingSessions.Outcome.UNAVAILABLE,
                        "Released session must not accept stale commands.");
                result.putBoolean("real_cross_app_session", true);
                result.putBoolean("home_empty_to_ready_same_focused_slot", true);
                result.putBoolean("advertised_controls_revalidated", true);
                result.putBoolean("live_metadata_and_state", true);
                result.putBoolean("released_session_unavailable", true);
            }
            result.putString("status", "PASS");
            result.putString("limits", "Synthetic MediaSession callbacks, not physical audio playback or streaming-service entitlement. Notification grant remains operator-owned.");
        } catch (Throwable failure) { result.putString("status", "FAIL"); result.putString("reason", failure.toString()); }
        finally {
            releaseFixture();
            onMain(new Runnable() {
                @Override public void run() {
                    if (home != null) home.finish();
                    if (observer != null) observer.stop();
                }
            });
            waitForIdleSync();
            SharedPreferences.Editor restore = preferences.edit().clear();
            for (Map.Entry<String, ?> entry : before.entrySet()) restore(restore, entry.getKey(), entry.getValue());
            result.putBoolean("layout_restored", restore.commit() && preferences.getAll().equals(before));
        }
        finish(0, result);
    }

    private void awaitHomeWidget() {
        long deadline = SystemClock.uptimeMillis() + 5000;
        final boolean[] ready = new boolean[1];
        while (SystemClock.uptimeMillis() < deadline) {
            onMain(new Runnable() {
                @Override public void run() {
                    View slot = home.getWindow().getDecorView().findViewWithTag("slot:" + NowPlayingWidgetProvider.ITEM.key());
                    check(slot == originalSlot && slot.isFocused(), "Source arrival must retain the exact focused Home slot.");
                    view = widget(slot);
                    ready[0] = view != null;
                }
            });
            if (ready[0]) return;
            SystemClock.sleep(30);
        }
        throw new AssertionError("Actual Home did not transition from empty to the live Now Playing view.");
    }
    private void awaitHomeEmpty() {
        long deadline = SystemClock.uptimeMillis() + 5000;
        final boolean[] empty = new boolean[1];
        while (SystemClock.uptimeMillis() < deadline) {
            onMain(new Runnable() {
                @Override public void run() {
                    View slot = home.getWindow().getDecorView().findViewWithTag("slot:" + NowPlayingWidgetProvider.ITEM.key());
                    check(slot == originalSlot && slot.isFocused(), "Session removal must retain the exact focused Home slot.");
                    empty[0] = view != null && view.getVisibility() == View.GONE;
                }
            });
            if (empty[0]) return;
            SystemClock.sleep(30);
        }
        throw new AssertionError("Actual Home did not return to its empty Now Playing state.");
    }
    private NowPlayingView widget(View parent) {
        if (parent instanceof NowPlayingView) return (NowPlayingView) parent;
        if (parent instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) parent;
            for (int index = 0; index < group.getChildCount(); index++) {
                NowPlayingView child = widget(group.getChildAt(index));
                if (child != null) return child;
            }
        }
        return null;
    }
    @SuppressWarnings("unchecked") private static void restore(SharedPreferences.Editor editor, String key, Object value) {
        if (value instanceof String) editor.putString(key, (String) value);
        else if (value instanceof Integer) editor.putInt(key, (Integer) value);
        else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
        else if (value instanceof Long) editor.putLong(key, (Long) value);
        else if (value instanceof Float) editor.putFloat(key, (Float) value);
        else if (value instanceof Set) editor.putStringSet(key, (Set<String>) value);
        else throw new AssertionError("Unexpected layout preference type: " + key);
    }

    private NowPlayingSessions.Session awaitFixture(String title, String playback) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < deadline) {
            for (NowPlayingSessions.Session session : NowPlayingSessions.read(getTargetContext()).sessions)
                if (FIXTURE.equals(session.source) && title.equals(session.title) && playback.equals(session.playback)) return session;
            SystemClock.sleep(30);
        }
        throw new AssertionError("Fixture session did not reach " + title + ": " + playback);
    }
    private void awaitNoFixture() {
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < deadline) {
            boolean present = false;
            for (NowPlayingSessions.Session session : NowPlayingSessions.read(getTargetContext()).sessions) if (FIXTURE.equals(session.source)) present = true;
            if (!present) return;
            SystemClock.sleep(30);
        }
        throw new AssertionError("Released fixture remained published.");
    }
    private void awaitView(final String expected) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        final boolean[] ready = new boolean[1];
        while (SystemClock.uptimeMillis() < deadline) {
            onMain(new Runnable() { @Override public void run() { ready[0] = view.getText().toString().contains(expected); } });
            if (ready[0]) return;
            SystemClock.sleep(30);
        }
        throw new AssertionError("Native widget did not update metadata from controller callback.");
    }
    private void releaseFixture() { getTargetContext().sendBroadcast(new Intent().setClassName(FIXTURE, FIXTURE + ".FixtureReceiver").putExtra("command", "release")); }
    private void onMain(final Runnable action) {
        final Throwable[] error = new Throwable[1];
        runOnMainSync(new Runnable() { @Override public void run() { try { action.run(); } catch (Throwable failure) { error[0] = failure; } } });
        if (error[0] != null) throw new AssertionError(error[0]);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
