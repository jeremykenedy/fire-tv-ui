package com.jeremykenedy.firetv.ui.nativeapp.cards;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import com.jeremykenedy.firetv.ui.nativeapp.providers.discovery.DiscoveryOptions;
import com.jeremykenedy.firetv.ui.nativeapp.providers.discovery.DiscoveryPreferences;
import com.jeremykenedy.firetv.ui.nativeapp.providers.plex.PlexFixture;
import java.util.Map;

/** Native discovery controls against the authorized, isolated local PMS fixture. */
public final class DiscoveryInstrumentation extends CardControlsInstrumentation {
    @Override public void onStart() {
        Bundle result = new Bundle();
        SharedPreferences preferences = getTargetContext().getSharedPreferences("source_discovery", Context.MODE_PRIVATE);
        SharedPreferences cardPreferences = getTargetContext().getSharedPreferences("native_cards", Context.MODE_PRIVATE);
        Map<String, ?> before = preferences.getAll(), cardBefore = cardPreferences.getAll();
        PlexFixture fixture = null;
        try {
            fixture = PlexFixture.start(getTargetContext());
            DiscoveryPreferences.save(getTargetContext(), DiscoveryOptions.defaults());
            CardPreferences.save(getTargetContext(), CardOptions.defaults());
            cards = launch("providers.discovery.DiscoveryActivity");
            clickTag("discovery:refresh");
            awaitTag("discovery:like:" + fixture.discoveryKey());
            check(containsText(cards.getWindow().getDecorView(), "IMDb via Plex: 8.2/10"),
                    "Native title must show the rating source actually returned by PMS.");
            clickTag("discovery:like:" + fixture.discoveryKey());
            check(DiscoveryPreferences.load(getTargetContext()).likedTitles.contains(fixture.discoveryKey()),
                    "Native Like action must persist feedback.");
            check(containsText(cards.getWindow().getDecorView(), "you liked this title"),
                    "Native suggestions must explain feedback ranking.");
            onMain(new Runnable() { @Override public void run() { cards.finish(); } });
            waitForIdleSync();
            cards = launch("providers.discovery.DiscoveryActivity");
            clickTag("discovery:refresh");
            awaitTag("discovery:like:" + fixture.discoveryKey());
            check(containsText(cards.getWindow().getDecorView(), "Remove like: Fixture Discovery Movie"),
                    "Saved feedback must survive reopening the discovery activity and real source refresh.");
            clickTag("discovery:dismiss:" + fixture.discoveryKey());
            DiscoveryOptions dismissed = DiscoveryPreferences.load(getTargetContext());
            check(dismissed.notInterested.contains(fixture.discoveryKey()) && dismissed.likedTitles.isEmpty(),
                    "Native Not interested action must remove the title and earlier like.");
            check(!containsText(cards.getWindow().getDecorView(), "Details in Plex: Fixture Discovery Movie"),
                    "Dismissed title must disappear from native suggestion controls.");
            clickTag("discovery:restore");
            awaitTag("discovery:like:" + fixture.discoveryKey());
            clickTag("discovery:like:" + fixture.discoveryKey());
            clickTag("discovery:clear-likes");
            check(DiscoveryPreferences.load(getTargetContext()).likedTitles.isEmpty(),
                    "Native Clear liked titles action must remove saved feedback.");
            fixture.assertHealthy();
            result.putString("status", "PASS");
            result.putBoolean("authorized_fixture_discovery_feedback_reopen_and_rating_source", true);
        } catch (Throwable problem) {
            result.putString("status", "FAIL"); result.putString("reason", problem.toString());
        } finally {
            if (cards != null) onMain(new Runnable() { @Override public void run() { cards.finish(); } });
            waitForIdleSync();
            try { if (fixture != null) fixture.close(); }
            catch (Exception failed) { result.putString("status", "FAIL"); result.putString("restoration", failed.toString()); }
            boolean discoveryRestored = restore(preferences, before);
            boolean cardsRestored = restore(cardPreferences, cardBefore);
            boolean restored = discoveryRestored && cardsRestored;
            result.putBoolean("discovery_and_card_preferences_restored", restored);
            if (!restored) result.putString("status", "FAIL");
        }
        finish("PASS".equals(result.getString("status")) ? -1 : 0, result);
    }
}
