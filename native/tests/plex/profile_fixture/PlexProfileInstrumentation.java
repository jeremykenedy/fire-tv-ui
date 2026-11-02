package com.jeremykenedy.firetv.ui.nativeapp.providers.plex;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import com.jeremykenedy.firetv.ui.nativeapp.providers.RecommendationSources;
import com.jeremykenedy.firetv.ui.nativeapp.providers.TvRecommendations;
import com.jeremykenedy.firetv.ui.nativeapp.stateguard.GuardStore;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.concurrent.atomic.AtomicReference;

/** Guarded synthetic PMS: distinct owner and profile credentials, never a real account. */
public final class PlexProfileInstrumentation extends Instrumentation {
    private Bundle args;
    @Override public void onCreate(Bundle args) { super.onCreate(args); this.args = args; start(); }
    @Override public void onStart() {
        Bundle result = new Bundle(); PlexFixture fixture = null;
        String step = "guard";
        try {
            check("com.jeremykenedy.firetv.ui.preview".equals(getTargetContext().getPackageName()), "Disposable preview required.");
            String id = args == null ? null : args.getString("state_guard_case_id");
            String hash = args == null ? null : args.getString("state_guard_manifest_sha256");
            check(id != null && hash != null && new GuardStore(getTargetContext().getDataDir()).verify(id,
                    new LinkedHashSet<String>(Arrays.asList("shared_prefs", "files", "no_backup")), hash).matches,
                    "Durable guard required before fixture writes.");
            result.putBoolean("state_guard_verified_before_write", true);
            step = "encrypted synthetic profiles";
            fixture = PlexFixture.startProfiles(getTargetContext());
            step = "healthy owner and profiles";
            PlexRepository.Result ready = PlexRepository.read(getTargetContext(), null);
            check(ready.status == PlexRepository.Status.READY && ready.feeds.size() == 3, "Three distinct feeds required.");
            check(fixture.allProfilesRequested(), "Each scoped synthetic credential must be used.");
            assertTitle(ready, "Owner fixture", "12", null);
            assertTitle(ready, "First fixture", "21", fixture.profileSource(true));
            assertTitle(ready, "Second fixture", "22", fixture.profileSource(false));
            result.putBoolean("three_scoped_profiles_and_safe_details", true);
            step = "revoked second profile";
            fixture.failSecond(true);
            PlexRepository.Result partial = PlexRepository.read(getTargetContext(), null);
            check(partial.status == PlexRepository.Status.PARTIAL && partial.feeds.size() == 2, "Failed profile must preserve successful feeds.");
            assertTitle(partial, "Owner fixture", "12", null);
            assertTitle(partial, "First fixture", "21", fixture.profileSource(true));
            for (TvRecommendations.Feed feed : partial.feeds)
                check(!feed.item.id.contains(fixture.profileSource(false)), "Failed profile must not publish a feed.");
            TvRecommendations.Result aggregated = RecommendationSources.read(getTargetContext());
            boolean partialStatus = false;
            for (TvRecommendations.SourceStatus source : aggregated.sources)
                if ("plex".equals(source.id)) partialStatus = source.state == TvRecommendations.SourceStatus.State.PARTIAL;
            check(partialStatus, "Home source status must report partial Plex data.");
            fixture.failSecond(false); fixture.failFirst(true);
            partial = PlexRepository.read(getTargetContext(), null);
            check(partial.status == PlexRepository.Status.PARTIAL && partial.feeds.size() == 2, "Earlier failed profile must not suppress a later one.");
            assertTitle(partial, "Owner fixture", "12", null);
            assertTitle(partial, "Second fixture", "22", fixture.profileSource(false));
            for (TvRecommendations.Feed feed : partial.feeds)
                check(!feed.item.id.contains(fixture.profileSource(true)), "Failed profile must not publish a feed.");
            result.putBoolean("failed_profile_preserves_other_feeds_and_partial_status", true);
            fixture.failFirst(false);
            step = "canceled profile request";
            assertCanceled(fixture, false);
            step = "connection generation changed";
            assertCanceled(fixture, true);
            fixture.assertHealthy();
            result.putBoolean("cancellation_and_connection_change_stop_results", true);
            result.putString("status", "PASS");
            result.putString("limits", "Synthetic loopback PMS and test credentials only. No Plex account, PIN, playback or physical TV proof.");
        } catch (Throwable failure) { result.putString("status", "FAIL"); result.putString("step", step); result.putString("reason", failure.toString()); }
        finally {
            if (fixture != null) try { fixture.releaseHeldRequest(); fixture.close(); result.putBoolean("fixture_restored", true); }
            catch (Throwable failure) { result.putBoolean("fixture_restored", false); result.putString("cleanup_error", failure.toString()); }
            if (fixture != null && !result.getBoolean("fixture_restored")) result.putString("status", "FAIL");
        }
        finish(0, result);
    }
    private void assertCanceled(final PlexFixture fixture, boolean changeConnection) throws Exception {
        fixture.holdFirstProfile();
        final CancellationSignal signal = new CancellationSignal();
        final AtomicReference<Object> outcome = new AtomicReference<>();
        Thread reader = new Thread(new Runnable() { @Override public void run() {
            try { outcome.set(PlexRepository.read(getTargetContext(), signal)); }
            catch (Throwable failure) { outcome.set(failure); }
        } });
        reader.start();
        try {
            check(fixture.awaitHeldRequest(), "Profile request did not reach local fixture.");
            if (changeConnection) PlexSession.begin(); else signal.cancel();
        } finally { fixture.releaseHeldRequest(); }
        reader.join(7000);
        check(!reader.isAlive() && outcome.get() instanceof OperationCanceledException, "Canceled profile read returned stale or partial data.");
        check(fixture.awaitHeldFinished(), "Held fixture request did not settle.");
        fixture.allowFutureFailures();
    }
    private static void assertTitle(PlexRepository.Result result, String title, String id, String source) throws Exception {
        TvRecommendations.Program found = null;
        for (TvRecommendations.Feed feed : result.feeds) for (TvRecommendations.Program item : feed.programs)
            if (title.equals(item.title)) { check(found == null, "Duplicate title in fixture."); found = item; }
        check(found != null && found.key.endsWith(":" + id), "Expected scoped item is missing.");
        if (source != null) check(found.key.equals("plex:" + source + ":" + id), "Profile identity mismatch.");
        check(found.playIntentUri == null && found.detailsIntentUri != null, "Details must not imply playback.");
        Intent intent = Intent.parseUri(found.detailsIntentUri, Intent.URI_INTENT_SCHEME);
        check(Intent.ACTION_VIEW.equals(intent.getAction()) && "com.plexapp.android".equals(intent.getPackage())
                && intent.getData() != null && ("plex://libraries/fixture-machine/unmatched/%2Flibrary%2Fmetadata%2F" + id).equals(intent.getData().toString())
                && !found.detailsIntentUri.contains("fixture-first-token") && !found.detailsIntentUri.contains("fixture-server-token")
                && !found.detailsIntentUri.contains("fixture-second-token"), "Unsafe or incorrect title details route.");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
