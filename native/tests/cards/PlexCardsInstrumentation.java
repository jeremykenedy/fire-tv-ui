package com.jeremykenedy.firetv.ui.nativeapp.cards;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.Button;
import com.jeremykenedy.firetv.ui.nativeapp.layout.HomeSlots;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutPreferences;
import com.jeremykenedy.firetv.ui.nativeapp.providers.RecommendationSources;
import com.jeremykenedy.firetv.ui.nativeapp.providers.TvRecommendations;
import com.jeremykenedy.firetv.ui.nativeapp.providers.plex.PlexFixture;
import java.util.Collections;
import java.util.Map;

/** Real shared-loader and native settings proof against a local, sanitized PMS fixture. */
public final class PlexCardsInstrumentation extends CardControlsInstrumentation {
    @Override public void onStart() {
        Bundle result = new Bundle();
        SharedPreferences cardPrefs = getTargetContext().getSharedPreferences("native_cards", Context.MODE_PRIVATE);
        SharedPreferences layoutPrefs = getTargetContext().getSharedPreferences("native_layout", Context.MODE_PRIVATE);
        Map<String, ?> cardBefore = cardPrefs.getAll(), layoutBefore = layoutPrefs.getAll();
        PlexFixture fixture = null;
        String step = "start isolated local PMS";
        try {
            fixture = PlexFixture.start(getTargetContext());
            step = "read shared TV and Plex aggregator";
            TvRecommendations.Feed selected = null;
            TvRecommendations.Result snapshot = RecommendationSources.read(getTargetContext());
            for (TvRecommendations.Feed feed : snapshot.feeds) if (fixture.feedId().equals(feed.item.id)) selected = feed;
            check(selected != null && selected.programs.size() == 2, "Shared aggregator must include the actual local PMS feed: " + snapshot.message());
            final TvRecommendations.Feed feed = selected;
            check(fixture.seriesKey().equals(feed.programs.get(0).seriesKey)
                    && fixture.seriesKey().equals(feed.programs.get(1).seriesKey), "Both PMS episodes carry trusted server-scoped series identity.");
            check(feed.programs.get(0).playIntentUri == null && feed.programs.get(0).detailsIntentUri != null,
                    "PMS details metadata must not imply playback support.");
            LayoutPreferences.save(getTargetContext(), LayoutPreferences.load(getTargetContext(), HomeSlots.defaults())
                    .discover(Collections.singletonList(feed.item)).setVisible(feed.item.key(), true));
            step = "Main Settings Cards route";
            main = launch("MainActivity");
            ActivityMonitor settingsMonitor = addMonitor(ROOT + "SettingsActivity", null, false);
            Button settingsButton = button(main.getWindow().getDecorView(), "Settings");
            focus(settingsButton); press(KeyEvent.KEYCODE_DPAD_CENTER);
            Activity settings = waitForMonitorWithTimeout(settingsMonitor, 3000); removeMonitor(settingsMonitor);
            check(settings != null, "Settings must open from Main."); awaitWindow(settings);
            press(KeyEvent.KEYCODE_DPAD_DOWN); press(KeyEvent.KEYCODE_DPAD_CENTER);
            ActivityMonitor cardsMonitor = addMonitor(ROOT + "cards.CardsSettingsActivity", null, false);
            press(KeyEvent.KEYCODE_DPAD_CENTER);
            cards = waitForMonitorWithTimeout(cardsMonitor, 3000); removeMonitor(cardsMonitor);
            check(cards != null, "Cards must open from Settings."); awaitWindow(cards);
            step = "Plex per-feed custom timer";
            clickTag("timers"); awaitTag(feed.item.id); clickTag(feed.item.id);
            clickText("Custom interval (seconds)"); setNumber("11"); clickText("Save"); awaitWindow(cards);
            check(CardPreferences.load(getTargetContext()).timerSeconds(feed.item.id) == 11, "Plex timer saves through the shared settings list.");
            press(KeyEvent.KEYCODE_BACK); awaitFocusedTag("timers");
            step = "Plex title order and series controls";
            clickTag("channels"); awaitTag(feed.item.id); clickTag(feed.item.id);
            clickTag(fixture.secondKey() + ":up");
            check(CardPreferences.orderedPrograms(CardPreferences.load(getTargetContext()), feed).get(0).key.equals(fixture.secondKey()), "Plex title order persists.");
            clickTag(fixture.firstKey() + ":item"); clickTag(fixture.secondKey() + ":series");
            check(CardPreferences.visiblePrograms(CardPreferences.load(getTargetContext()), feed).isEmpty(), "Plex item and series actions hide both episodes.");
            press(KeyEvent.KEYCODE_BACK); press(KeyEvent.KEYCODE_BACK); awaitFocusedTag("channels");
            clickTag("hidden"); clickTag("series:" + feed.source + ":" + fixture.seriesKey());
            CardOptions restored = CardPreferences.load(getTargetContext());
            check(restored.isHidden(feed.source, fixture.firstKey()) && !restored.isSeriesHidden(feed.source, fixture.seriesKey()), "Plex series restore preserves the independently hidden episode.");
            press(KeyEvent.KEYCODE_BACK); press(KeyEvent.KEYCODE_BACK); awaitWindow(settings);
            press(KeyEvent.KEYCODE_BACK); awaitWindow(main);
            step = "Plex visible card after restore";
            final ChannelCardView card = awaitCard(feed.item.id);
            final String[] current = new String[1];
            onMain(new Runnable() { @Override public void run() { current[0] = card.currentProgram() == null ? null : card.currentProgram().key; } });
            check(fixture.secondKey().equals(current[0]), "Home uses shared Plex order and series filtering after settings return.");
            fixture.assertHealthy();
            result.putString("status", "PASS");
            result.putBoolean("shared_plex_feed_native_settings", true);
            result.putBoolean("plex_timer_order_series_independent_episode", true);
            result.putBoolean("visible_plex_card_after_restore", true);
            result.putString("limits", "Local synthetic PMS only. No real Plex account, playback, remote server or physical TV proof.");
        } catch (Throwable failure) {
            result.putString("status", "FAIL"); result.putString("step", step); result.putString("reason", failure.toString());
        } finally {
            if (main != null) {
                try { onMain(new Runnable() { @Override public void run() { main.finish(); } }); waitForIdleSync(); }
                catch (Throwable failure) { result.putString("activity_cleanup_error", failure.toString()); }
            }
            if (fixture != null) {
                try { fixture.close(); result.putBoolean("plex_fixture_restored", true); }
                catch (Throwable failure) { result.putBoolean("plex_fixture_restored", false); result.putString("fixture_cleanup_error", failure.toString()); }
            }
            result.putBoolean("native_cards_restored", restore(cardPrefs, cardBefore));
            result.putBoolean("native_layout_restored", restore(layoutPrefs, layoutBefore));
        }
        finish(0, result);
    }
}
