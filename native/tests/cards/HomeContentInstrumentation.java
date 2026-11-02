package com.jeremykenedy.firetv.ui.nativeapp.providers;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import com.jeremykenedy.firetv.ui.nativeapp.cards.CardOptions;
import com.jeremykenedy.firetv.ui.nativeapp.cards.CardPreferences;
import com.jeremykenedy.firetv.ui.nativeapp.cards.ChannelCardView;
import com.jeremykenedy.firetv.ui.nativeapp.cards.HomeContentView;
import com.jeremykenedy.firetv.ui.nativeapp.layout.HomeSlots;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutOptions;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutPreferences;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Tests the integrated native Home host, including actual Activity recreation. */
public final class HomeContentInstrumentation extends Instrumentation {
    private static final String MAIN = "com.jeremykenedy.firetv.ui.nativeapp.MainActivity";
    private Activity activity;
    private HomeContentView home;
    private String step = "setup";

    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        SharedPreferences layout = getTargetContext().getSharedPreferences("native_layout", Context.MODE_PRIVATE);
        SharedPreferences cards = getTargetContext().getSharedPreferences("native_cards", Context.MODE_PRIVATE);
        Map<String, ?> layoutBefore = layout.getAll(), cardsBefore = cards.getAll();
        try {
            final String source = getTargetContext().getPackageName();
            final TvRecommendations.Feed first = feed(source, "home-proof:first");
            final TvRecommendations.Feed second = feed(source, "home-proof:second");
            final TvRecommendations.Feed third = feed(source, "home-proof:third");
            final List<TvRecommendations.Feed> feeds = Arrays.asList(first, second, third);
            final TvRecommendations.Result ready = new TvRecommendations.Result(TvRecommendations.Status.READY, feeds);
            final String missing = "channel:home-proof:unavailable";
            CardPreferences.save(getTargetContext(), CardOptions.defaults().withGlobalSeconds(0));
            LayoutPreferences.save(getTargetContext(), new LayoutOptions(600, 180,
                    Arrays.asList(first.item.key(), second.item.key(), third.item.key(), missing, HomeSlots.CLOCK.key()),
                    Collections.<String>emptyList()));
            activity = startActivitySync(new Intent().setClassName(getTargetContext(), MAIN)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            waitForIdleSync();
            home = findHome(activity.getWindow().getDecorView());
            check(home != null, "Main must host the reusable mixed content controller.");
            step = "stable ready refresh";
            final View[] stable = new View[1];
            final ChannelCardView[] stableCard = new ChannelCardView[1];
            onMain(new Runnable() {
                @Override public void run() {
                    check(home.applyResult(home.beginRefresh(), ready), "The current ready result must apply.");
                    stable[0] = home.findViewWithTag("slot:" + first.item.key());
                    stableCard[0] = home.findCard(first.item.key());
                    check(stable[0] != null && stableCard[0] != null, "Ready feeds create one native slot each.");
                    check(stable[0].requestFocus(), "Stable shells must take D-pad focus.");
                    stableCard[0].showNextProgram();
                    String selected = stableCard[0].currentProgram().key;
                    home.applyResult(home.beginRefresh(), ready);
                    check(home.findViewWithTag("slot:" + first.item.key()) == stable[0], "Refresh must reuse the slot view.");
                    check(home.findCard(first.item.key()) == stableCard[0], "Refresh must reuse the channel renderer.");
                    check(selected.equals(stableCard[0].currentProgram().key), "Refresh must preserve the selected program.");
                    check(stable[0].isFocused(), "Refresh must preserve the same native focus target.");
                    View unavailable = home.findViewWithTag("slot:" + missing);
                    check(unavailable != null && unavailable.getVisibility() == View.VISIBLE,
                            "A saved unavailable provider must retain a visible slot.");
                }
            });
            step = "stale empty unavailable and permission states";
            onMain(new Runnable() {
                @Override public void run() {
                    long stale = home.beginRefresh();
                    long current = home.beginRefresh();
                    check(!home.applyResult(stale, new TvRecommendations.Result(TvRecommendations.Status.EMPTY,
                            Collections.<TvRecommendations.Feed>emptyList())), "Superseded results must be rejected.");
                    check(home.applyResult(current, ready), "The latest refresh must still apply.");
                    home.applyResult(home.beginRefresh(), new TvRecommendations.Result(TvRecommendations.Status.UNAVAILABLE,
                            Collections.<TvRecommendations.Feed>emptyList()));
                    check(home.findViewWithTag("slot:" + first.item.key()) == stable[0] && stable[0].isFocused(),
                            "Losing a provider must preserve its focused slot.");
                    check(stable[0].getContentDescription().toString().contains("unavailable"),
                            "Unavailable content needs an explicit visible state.");
                    home.applyResult(home.beginRefresh(), new TvRecommendations.Result(TvRecommendations.Status.PERMISSION_REQUIRED,
                            Collections.<TvRecommendations.Feed>emptyList()));
                    check(stable[0].getContentDescription().toString().contains("access required"),
                            "Permission denial must differ from an empty source.");
                    home.applyResult(home.beginRefresh(), new TvRecommendations.Result(TvRecommendations.Status.EMPTY,
                            Collections.<TvRecommendations.Feed>emptyList()));
                    check(stable[0].getContentDescription().toString().contains("No items"), "Empty feeds need their own state.");
                    home.applyResult(home.beginRefresh(), ready);
                    check(home.findCard(first.item.key()) == stableCard[0]
                                    && stableCard[0].currentProgram().key.endsWith(":two"),
                            "A returning provider must reuse its renderer and selected program.");
                }
            });
            waitForIdleSync();
            step = "native activity focus and scroll recreation";
            final int[] scroll = new int[1];
            onMain(new Runnable() {
                @Override public void run() {
                    View target = home.findViewWithTag("slot:" + third.item.key());
                    check(target.requestFocus(), "A later mixed slot must focus before recreation.");
                    home.findCard(third.item.key()).showNextProgram();
                    home.scrollTo(Math.round(700 * activity.getResources().getDisplayMetrics().density), 0);
                    scroll[0] = home.getScrollX();
                    check(scroll[0] > 0, "The fixture must actually scroll its native viewport.");
                }
            });
            ActivityMonitor monitor = addMonitor(MAIN, null, false);
            final Activity original = activity;
            onMain(new Runnable() { @Override public void run() { original.recreate(); } });
            activity = waitForMonitorWithTimeout(monitor, 5000);
            removeMonitor(monitor);
            check(activity != null && activity != original, "The activity must actually recreate.");
            awaitRestored(third.item.key(), scroll[0]);
            onMain(new Runnable() {
                @Override public void run() {
                    View restored = home.findViewWithTag("slot:" + third.item.key());
                    check(restored != null && restored.isFocused(), "Recreation restores the saved slot focus.");
                    check(home.getScrollX() == scroll[0], "Recreation restores the saved horizontal viewport.");
                    home.applyResult(home.beginRefresh(), ready);
                    check(home.findViewWithTag("slot:" + third.item.key()) == restored && restored.isFocused(),
                            "A returning payload must preserve the restored placeholder focus.");
                    check(home.findCard(third.item.key()).currentProgram().key.endsWith(":two"),
                            "Recreation restores the selected program by its stable provider key.");
                    long invalidated = home.beginRefresh();
                    home.onHostPause();
                    check(!home.applyResult(invalidated, ready), "Paused callbacks must not mutate the row.");
                    home.onHostResume();
                }
            });
            result.putString("status", "PASS");
            result.putBoolean("stable_refresh_and_source_recovery", true);
            result.putBoolean("unavailable_saved_slot_visible", true);
            result.putBoolean("stale_and_paused_results_rejected", true);
            result.putBoolean("native_activity_focus_scroll_program_restored", true);
            result.putString("limits", "Synthetic source payloads exercise the real native host; physical TV and publisher delivery remain separate.");
        } catch (Throwable error) {
            result.putString("status", "FAIL");
            result.putString("reason", step + ": " + error);
        } finally {
            if (activity != null) onMain(new Runnable() { @Override public void run() { activity.finish(); } });
            result.putBoolean("layout_restored", restore(layout, layoutBefore));
            result.putBoolean("cards_restored", restore(cards, cardsBefore));
        }
        finish(0, result);
    }

    private static TvRecommendations.Feed feed(String source, String id) {
        return new TvRecommendations.Feed(id, "Native " + id, source, Arrays.asList(
                new TvRecommendations.Program(id + ":one", source, "First program", null, ""),
                new TvRecommendations.Program(id + ":two", source, "Second program", null, "")));
    }

    private void awaitRestored(final String key, final int scroll) {
        long end = SystemClock.uptimeMillis() + 4000;
        final boolean[] restored = new boolean[1];
        while (SystemClock.uptimeMillis() < end) {
            onMain(new Runnable() {
                @Override public void run() {
                    home = findHome(activity.getWindow().getDecorView());
                    View slot = home == null ? null : home.findViewWithTag("slot:" + key);
                    restored[0] = slot != null && slot.isFocused() && home.getScrollX() == scroll;
                }
            });
            if (restored[0]) return;
            SystemClock.sleep(20);
        }
        throw new AssertionError("Recreated Home did not restore its focused slot and scroll.");
    }

    private static HomeContentView findHome(View view) {
        if (view instanceof HomeContentView) return (HomeContentView) view;
        if (view instanceof ViewGroup) for (int index = 0; index < ((ViewGroup) view).getChildCount(); index++) {
            HomeContentView found = findHome(((ViewGroup) view).getChildAt(index));
            if (found != null) return found;
        }
        return null;
    }

    private void onMain(Runnable action) { runOnMainSync(action); waitForIdleSync(); }

    @SuppressWarnings("unchecked")
    private static boolean restore(SharedPreferences preferences, Map<String, ?> values) {
        SharedPreferences.Editor editor = preferences.edit().clear();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) editor.putString(entry.getKey(), (String) value);
            else if (value instanceof Integer) editor.putInt(entry.getKey(), (Integer) value);
            else if (value instanceof Boolean) editor.putBoolean(entry.getKey(), (Boolean) value);
            else if (value instanceof Long) editor.putLong(entry.getKey(), (Long) value);
            else if (value instanceof Float) editor.putFloat(entry.getKey(), (Float) value);
            else if (value instanceof Set) editor.putStringSet(entry.getKey(), (Set<String>) value);
        }
        return editor.commit();
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
