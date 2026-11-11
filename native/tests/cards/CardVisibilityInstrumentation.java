package com.jeremykenedy.firetv.ui.nativeapp.providers;

import com.jeremykenedy.firetv.ui.nativeapp.cards.*;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutOptions;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutPreferences;
import com.jeremykenedy.firetv.ui.nativeapp.providers.TvRecommendations;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/** Actual Home and remote actions with synthetic feeds on a disposable emulator. */
public final class CardVisibilityInstrumentation extends CardControlsInstrumentation {
    private HomeContentView home;
    private String step = "setup";
    private final String source = "com.jeremykenedy.firetv.ui.cardsproof";
    private final TvRecommendations.Program first = new TvRecommendations.Program("item:first", source, "First title", null, "fixture://shared");
    private final TvRecommendations.Program second = new TvRecommendations.Program("item:second", source, "Second title", null, "fixture://shared");
    private final TvRecommendations.Feed feed = new TvRecommendations.Feed("proof:first", "First feed", source, Arrays.asList(first, second));
    private final TvRecommendations.Feed duplicate = new TvRecommendations.Feed("proof:duplicate", "Duplicate feed", source, Arrays.asList(first, second));
    private final TvRecommendations.Result payload = new TvRecommendations.Result(TvRecommendations.Status.READY, Arrays.asList(feed, duplicate));

    @Override public void onStart() {
        Bundle result = new Bundle();
        SharedPreferences layout = getTargetContext().getSharedPreferences("native_layout", Context.MODE_PRIVATE);
        SharedPreferences cardPrefs = getTargetContext().getSharedPreferences("native_cards", Context.MODE_PRIVATE);
        Map<String, ?> beforeLayout = layout.getAll(), beforeCards = cardPrefs.getAll();
        try {
            check("com.jeremykenedy.firetv.ui.preview".equals(getTargetContext().getPackageName()), "Preview fixture only.");
            CardPreferences.save(getTargetContext(), CardOptions.defaults().withGlobalSeconds(0));
            LayoutPreferences.save(getTargetContext(), new LayoutOptions(320, 180,
                    Arrays.asList(feed.item.key(), duplicate.item.key()), Collections.<String>emptyList()).withMode(LayoutOptions.Mode.CARDS_ONLY));
            main = launch("MainActivity");
            SystemClock.sleep(500);
            apply();
            step = "remote Menu opens selected title actions";
            focusSlot(); press(KeyEvent.KEYCODE_MENU);
            clickText("Hide this item"); awaitWindow(main);
            check(CardPreferences.load(getTargetContext()).isHidden(source, first.key), "Menu hides selected item.");
            check(!CardPreferences.load(getTargetContext()).isHidden(source, second.key), "Shared launch URI does not hide the other title.");
            onMain(new Runnable() { @Override public void run() {
                check(second.key.equals(home.findCard(feed.item.key()).currentProgram().key), "Selected feed immediately advances.");
                check(second.key.equals(home.findCard(duplicate.item.key()).currentProgram().key), "Same item disappears from every visible feed immediately.");
            }});
            step = "hide cards through actual Settings then Back";
            settings(); clickTag("hide-cards"); press(KeyEvent.KEYCODE_BACK); awaitWindow(main); apply();
            onMain(new Runnable() { @Override public void run() {
                check(home.findViewWithTag("slot:" + feed.item.key()) == null, "Hidden cards stay absent after a provider refresh.");
            }});
            step = "hidden cards survive Home restart";
            main = launch("MainActivity"); SystemClock.sleep(500); apply();
            onMain(new Runnable() { @Override public void run() {
                check(home.findViewWithTag("slot:" + feed.item.key()) == null, "Hidden cards stay absent after Home restart.");
            }});
            step = "show cards and retain item dismissal";
            settings(); clickTag("hide-cards"); press(KeyEvent.KEYCODE_BACK); awaitWindow(main); apply();
            onMain(new Runnable() { @Override public void run() {
                check(home.findViewWithTag("slot:" + feed.item.key()) != null, "Showing cards restores feed.");
                check(second.key.equals(home.findCard(feed.item.key()).currentProgram().key), "Dismissed item remains hidden after hide/show and restart.");
            }});
            step = "per-app card checkbox and Save";
            appCards(); apply();
            onMain(new Runnable() { @Override public void run() {
                check(home.findViewWithTag("slot:" + feed.item.key()) == null, "Per-app hide removes all feeds after Save and resume.");
            }});
            appCards(); apply();
            onMain(new Runnable() { @Override public void run() {
                check(home.findViewWithTag("slot:" + feed.item.key()) != null, "Per-app show restores feeds.");
                check(second.key.equals(home.findCard(feed.item.key()).currentProgram().key), "Per-app toggle preserves the dismissed title.");
            }});
            result.putBoolean("per_app_card_checkbox_save", true);
            result.putString("status", "PASS");
            result.putBoolean("remote_menu_dismisses_exact_item", true);
            result.putBoolean("dismiss_updates_duplicate_feeds", true);
            result.putBoolean("visibility_survives_refresh_and_restart", true);
            result.putString("limits", "Synthetic feeds in actual native preview Home on emulator; no installed production or physical-TV verification.");
        } catch (Throwable failure) {
            result.putString("status", "FAIL"); result.putString("step", step); result.putString("reason", failure.toString());
        } finally {
            if (cards != null) onMain(new Runnable() { @Override public void run() { cards.finish(); }});
            if (main != null) onMain(new Runnable() { @Override public void run() { main.finish(); }});
            boolean restored = restoreSnapshot(layout, beforeLayout) && restoreSnapshot(cardPrefs, beforeCards)
                    && layout.getAll().equals(beforeLayout) && cardPrefs.getAll().equals(beforeCards);
            result.putBoolean("preferences_restored", restored);
            if (!restored) result.putString("status", "FAIL");
        }
        finish("PASS".equals(result.getString("status")) ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }

    private void apply() {
        onMain(new Runnable() { @Override public void run() {
            home = main.getWindow().getDecorView().findViewWithTag("home:content");
            check(home != null && home.applyResult(home.beginRefresh(), payload), "Actual Home accepts current synthetic provider result.");
        }});
    }
    private void focusSlot() {
        final View[] slot = new View[1];
        onMain(new Runnable() { @Override public void run() { slot[0] = home.findViewWithTag("slot:" + feed.item.key()); }});
        focus(slot[0]);
    }
    private void appCards() {
        ActivityMonitor monitor = addMonitor("com.jeremykenedy.firetv.ui.nativeapp.appgrid.AppGridSettingsActivity", null, false);
        onMain(new Runnable() { @Override public void run() {
            main.startActivity(new Intent(main, com.jeremykenedy.firetv.ui.nativeapp.appgrid.AppGridSettingsActivity.class));
        }});
        cards = waitForMonitorWithTimeout(monitor, 5000); removeMonitor(monitor);
        check(cards != null, "App manager opens."); awaitWindow(cards);
        awaitTag("app-manager:cards:" + source);
        clickTag("app-manager:cards:" + source); clickTag("app-manager:save"); awaitWindow(main);
    }
    private void settings() {
        ActivityMonitor monitor = addMonitor("com.jeremykenedy.firetv.ui.nativeapp.cards.CardsSettingsActivity", null, false);
        onMain(new Runnable() { @Override public void run() {
            main.startActivity(new Intent(main, CardsSettingsActivity.class));
        }});
        cards = waitForMonitorWithTimeout(monitor, 5000); removeMonitor(monitor);
        check(cards != null, "Cards settings opens."); awaitWindow(cards);
    }
    @SuppressWarnings("unchecked")
    private static boolean restoreSnapshot(SharedPreferences prefs, Map<String, ?> values) {
        SharedPreferences.Editor edit = prefs.edit().clear();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            Object value = entry.getValue(); String key = entry.getKey();
            if (value instanceof String) edit.putString(key, (String) value);
            else if (value instanceof Integer) edit.putInt(key, (Integer) value);
            else if (value instanceof Long) edit.putLong(key, (Long) value);
            else if (value instanceof Float) edit.putFloat(key, (Float) value);
            else if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
            else if (value instanceof Set) edit.putStringSet(key, (Set<String>) value);
        }
        return edit.commit();
    }
}
