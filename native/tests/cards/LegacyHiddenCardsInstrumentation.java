package com.jeremykenedy.firetv.ui.nativeapp.providers;

import android.app.Activity;
import android.content.*;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.os.*;
import android.view.View;
import com.jeremykenedy.firetv.ui.nativeapp.cards.*;
import java.util.*;
import java.util.concurrent.*;

/** Exact original identities read from a real TV provider and restored through native Settings. */
public final class LegacyHiddenCardsInstrumentation extends CardControlsInstrumentation {
    private static final String SOURCE = "com.jeremykenedy.firetv.ui.providerfixture";
    private String step = "fixture";
    private HomeContentView home;
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            check("com.jeremykenedy.firetv.ui.preview".equals(getTargetContext().getPackageName()), "Disposable preview only.");
            publish();
            TvRecommendations.Result initial = TvRecommendations.read(getTargetContext());
            final List<TvRecommendations.Feed> feeds = owned(initial);
            check(feeds.size() == 3, "Real provider publishes duplicate preview feeds and separate Watch Next.");
            String alpha = digest("preview\n" + SOURCE + "\nprovider:legacy-0");
            String gamma = digest("preview\n" + SOURCE + "\ncontent:content-gamma");
            String unavailable = digest("preview\ncom.unavailable.player\nprovider:missing");
            CardOptions choices = CardOptions.defaults().withGlobalSeconds(0).hideLegacy(alpha).hideLegacy(gamma).hideLegacy(unavailable);
            for (TvRecommendations.Feed feed : feeds) for (TvRecommendations.Program item : feed.programs) {
                if (item.title.equals("Row Delta")) {
                    long row = rowFor(feed.item.id);
                    check(item.legacyKey.equals(digest("preview\n" + SOURCE + "\nrow:" + row)), "Real missing-ID row follows shipped fallback.");
                    choices = choices.hideLegacy(item.legacyKey);
                }
            }
            CardPreferences.save(getTargetContext(), choices);
            check(CardPreferences.load(getTargetContext()).hiddenItems().size() == 5, "Unavailable and both exact row choices persist.");
            assertChoices(feeds);
            for (TvRecommendations.Feed feed : feeds) if (feed.programs.size() == 4)
                check(CardPreferences.visiblePrograms(CardPreferences.load(getTargetContext()), feed).size() == 1,
                        "Both exact row fallback choices remain hidden alongside provider/content choices.");
            String plexKey = "plex:" + String.format("%064x", 7) + ":42";
            TvRecommendations.Program plex = new TvRecommendations.Program(plexKey, "com.plexapp.android", "Plex synthetic", null, "fixture://shared");
            String plexPreview = digest("preview\ncom.jeremykenedy.firetv.ui\nprovider:" + plexKey.substring(5));
            String plexNext = digest("watch-next\ncom.jeremykenedy.firetv.ui\nprovider:" + plexKey.substring(5));
            check(CardPreferences.itemHidden(CardOptions.defaults().hideLegacy(plexPreview), plex), "Direct canonical Plex consumes original preview hash.");
            check(CardPreferences.itemHidden(CardOptions.defaults().hideLegacy(plexNext), plex), "Direct canonical Plex consumes original Watch Next hash.");
            CardOptions plexOptions = CardOptions.defaults().hideLegacy(plexPreview).hideLegacy(plexNext);
            TvRecommendations.Program unrelatedServer = new TvRecommendations.Program("plex:" + String.format("%064x", 8) + ":42", "com.plexapp.android", "Plex synthetic", null, "fixture://shared");
            TvRecommendations.Program unrelatedSource = new TvRecommendations.Program(plexKey, "com.other.app", "Plex synthetic", null, "fixture://shared");
            check(!CardPreferences.itemHidden(plexOptions, unrelatedServer) && !CardPreferences.itemHidden(plexOptions, unrelatedSource), "Same item number/title/route on a different server or publisher stays visible.");
            check(CardPreferences.restoreItem(plexOptions, plex).hiddenItems().isEmpty(), "Plex explicit restore clears both original publication scopes.");
            main = launch("MainActivity");
            apply(initial);
            onMain(new Runnable() { @Override public void run() {
                for (TvRecommendations.Feed feed : feeds) if (feed.programs.size() == 4)
                    check("Shared route Beta".equals(home.findCard(feed.item.key()).currentProgram().title), "Actual Home hides exact old hashes, preserving the shared-route second title.");
            }});
            capture("legacy-hidden-home.png");
            step = "provider replaces rows and channels";
            publish();
            TvRecommendations.Result replaced = TvRecommendations.read(getTargetContext());
            assertChoices(owned(replaced));
            check(!feeds.get(0).item.id.equals(owned(replaced).get(0).item.id), "Fixture recreated feed IDs.");
            apply(replaced);
            step = "restore unavailable through actual hidden list";
            ActivityMonitor monitor = addMonitor(ROOT + "cards.CardsSettingsActivity", null, false);
            onMain(new Runnable() { @Override public void run() { main.startActivity(new Intent(main, CardsSettingsActivity.class)); }});
            cards = waitForMonitorWithTimeout(monitor, 5000); removeMonitor(monitor);
            check(cards != null, "Actual Cards settings opens over Home."); awaitWindow(cards);
            clickTag("hidden");
            capture("legacy-hidden-list.png");
            clickTag(CardOptions.LEGACY_RECOMMENDATIONS + ":" + unavailable);
            check(!CardPreferences.load(getTargetContext()).isHidden(CardOptions.LEGACY_RECOMMENDATIONS, unavailable), "Unavailable entry restores without a live title.");
            clickTag(CardOptions.LEGACY_RECOMMENDATIONS + ":" + alpha);
            check(!CardPreferences.load(getTargetContext()).isHidden(CardOptions.LEGACY_RECOMMENDATIONS, alpha), "Actual restore removes original Alpha choice.");
            main = launch("MainActivity");
            TvRecommendations.Result restored = TvRecommendations.read(getTargetContext());
            apply(restored);
            final List<TvRecommendations.Feed> restoredFeeds = owned(restored);
            onMain(new Runnable() { @Override public void run() {
                for (TvRecommendations.Feed feed : restoredFeeds) if (feed.programs.size() == 4)
                    check("Legacy Alpha".equals(home.findCard(feed.item.key()).currentProgram().title), "Restored Alpha renders in both feeds after restart.");
            }});
            capture("legacy-restored-home.png");
            check(!CardPreferences.load(getTargetContext()).isHidden(CardOptions.LEGACY_RECOMMENDATIONS, unavailable), "No later read or restart resurrects unavailable choice.");
            result.putString("status", "PASS");
            result.putString("cases", "Real provider raw identity, content fallback, row fallback, duplicate feeds, shared route, replacement channels, independent Watch Next, unavailable Restore, actual Home and restart");
        } catch (Throwable failure) {
            result.putString("status", "FAIL"); result.putString("step", step); result.putString("reason", failure.toString());
        }
        finish("PASS".equals(result.getString("status")) ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
    private void assertChoices(List<TvRecommendations.Feed> feeds) {
        CardOptions options = CardPreferences.load(getTargetContext());
        for (TvRecommendations.Feed feed : feeds) for (TvRecommendations.Program item : feed.programs) {
            boolean expected = item.title.equals("Legacy Alpha") || item.title.equals("Content Gamma");
            if (!item.title.equals("Row Delta")) check(CardPreferences.itemHidden(options, item) == expected,
                    "Exact filtering differs: " + item.title);
        }
    }
    private long rowFor(String feed) {
        try (Cursor rows = getTargetContext().getContentResolver().query(TvContract.PreviewPrograms.CONTENT_URI,
                new String[]{"_id", "package_name", "title", "channel_id"}, null, null, null)) {
            while (rows != null && rows.moveToNext()) if (SOURCE.equals(rows.getString(1)) && "Row Delta".equals(rows.getString(2))
                    && feed.equals(RecommendationKeys.feed(SOURCE, false, rows.getLong(3)))) return rows.getLong(0);
        }
        throw new AssertionError("Missing exact row");
    }
    private List<TvRecommendations.Feed> owned(TvRecommendations.Result result) {
        List<TvRecommendations.Feed> feeds = new ArrayList<>();
        for (TvRecommendations.Feed feed : result.feeds) if (SOURCE.equals(feed.source)) feeds.add(feed);
        return feeds;
    }
    private void apply(final TvRecommendations.Result result) {
        List<String> slots = new ArrayList<>();
        for (TvRecommendations.Feed feed : owned(result)) slots.add(feed.item.key());
        com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutPreferences.save(getTargetContext(),
                new com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutOptions(320, 180, slots,
                        Collections.<String>emptyList()));
        SystemClock.sleep(800);
        onMain(new Runnable() { @Override public void run() {
            home = main.getWindow().getDecorView().findViewWithTag("home:content");
            check(home != null && home.applyResult(home.beginRefresh(), result), "Actual Home accepts real provider snapshot.");
        }});
    }
    private String digest(String text) throws Exception {
        byte[] bytes = java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes("UTF-8"));
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }
    private void publish() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final Bundle[] result = new Bundle[1];
        getTargetContext().sendOrderedBroadcast(new Intent().setComponent(new ComponentName(SOURCE, SOURCE + ".FixtureReceiver"))
                .putExtra("operation", "legacy-publish"), null, new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) { result[0] = getResultExtras(false); latch.countDown(); }
        }, null, 0, null, null);
        check(latch.await(10, TimeUnit.SECONDS) && result[0] != null && "OK".equals(result[0].getString("status")), "Real owned fixture publish succeeds.");
    }
    private void capture(String name) throws Exception {
        waitForIdleSync(); SystemClock.sleep(600);
        java.io.File file = new java.io.File(getTargetContext().getExternalFilesDir(null), name);
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(file)) {
            check(getUiAutomation().takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output), "Capture succeeds.");
        }
    }
}
