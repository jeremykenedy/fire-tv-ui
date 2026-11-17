package com.jeremykenedy.firetv.ui.nativeapp.cards;

import android.app.Activity;
import android.os.Bundle;
import com.jeremykenedy.firetv.ui.nativeapp.providers.RecommendationSources;
import com.jeremykenedy.firetv.ui.nativeapp.providers.TvRecommendations;
import com.jeremykenedy.firetv.ui.nativeapp.providers.plex.PlexFixture;

/** Preserved original identities against the actual repository and a local synthetic PMS. */
public final class LegacyPlexCardsInstrumentation extends CardControlsInstrumentation {
    @Override public void onStart() {
        Bundle result = new Bundle();
        PlexFixture fixture = null;
        try {
            check("com.jeremykenedy.firetv.ui.preview".equals(getTargetContext().getPackageName()), "Disposable preview only.");
            fixture = PlexFixture.start(getTargetContext());
            TvRecommendations.Feed selected = null;
            for (TvRecommendations.Feed feed : RecommendationSources.read(getTargetContext()).feeds)
                if (fixture.feedId().equals(feed.item.id)) selected = feed;
            check(selected != null && selected.programs.size() == 2, "Actual repository reads both local PMS episodes.");
            TvRecommendations.Program first = selected.programs.get(0);
            for (String table : new String[]{"preview", "watch-next"}) {
                String hash = digest(table + "\ncom.jeremykenedy.firetv.ui\nprovider:" + first.key.substring(5));
                CardOptions original = CardOptions.defaults().hideLegacy(hash);
                check(CardPreferences.visiblePrograms(original, selected).size() == 1
                                && CardPreferences.visiblePrograms(original, selected).get(0).key.equals(fixture.secondKey()),
                        "Actual Plex repository consumes original " + table + " hide without native aliases.");
                check(CardPreferences.restoreItem(original, first).hiddenItems().isEmpty(), "Actual Plex item restores preserved choice.");
            }
            fixture.assertHealthy();
            result.putString("status", "PASS");
            result.putString("cases", "Actual native Plex repository output consumes original preview and Watch Next identities separately; exact other episode remains visible; explicit restore");
            result.putString("limits", "Local synthetic PMS only; no owner account, real server or playback claim.");
        } catch (Throwable failure) {
            result.putString("status", "FAIL"); result.putString("reason", failure.toString());
        } finally {
            if (fixture != null) try { fixture.close(); result.putBoolean("plex_fixture_restored", true); }
            catch (Throwable failure) { result.putString("status", "FAIL"); result.putString("cleanup", failure.toString()); }
        }
        finish("PASS".equals(result.getString("status")) ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
    private String digest(String text) throws Exception {
        byte[] bytes = java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes("UTF-8"));
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }
}
