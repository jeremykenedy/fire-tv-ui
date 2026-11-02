package com.jeremykenedy.firetv.ui.nativeapp.providers;

import android.app.Instrumentation;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.SystemClock;
import com.jeremykenedy.firetv.ui.nativeapp.appgrid.AppGridOptions;
import com.jeremykenedy.firetv.ui.nativeapp.appgrid.AppGridPreferences;
import com.jeremykenedy.firetv.ui.nativeapp.cards.CardOptions;
import com.jeremykenedy.firetv.ui.nativeapp.cards.CardPreferences;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutOptions;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutPreferences;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class ProviderInstrumentation extends Instrumentation {
    private static final String PRODUCER = "com.jeremykenedy.firetv.ui.providerfixture";
    private boolean cleanupOnly;
    private Activity main;
    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        cleanupOnly = args != null && "cleanup".equals(args.getString("operation"));
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        if (cleanupOnly) {
            try {
                cleanupFixture(result);
                result.putString("status", "PASS");
            } catch (Throwable failure) {
                result.putString("status", "FAIL");
                result.putString("reason", failure.toString());
            }
            finish(0, result);
            return;
        }
        try {
            Context target = getTargetContext();
            check(target.checkSelfPermission(TvRecommendations.PERMISSION) == PackageManager.PERMISSION_GRANTED,
                    "Grant the preview's ordinary TV listings permission before this suite.");
            Context denied = new ContextWrapper(target) {
                @Override public int checkSelfPermission(String permission) { return PackageManager.PERMISSION_DENIED; }
            };
            check(TvRecommendations.read(denied).status == TvRecommendations.Status.PERMISSION_REQUIRED,
                    "Permission denial must remain explicit on the platform.");
            cleanupFixture(result);
            check(fixtureFeeds(TvRecommendations.read(target)) == 0, "Fixture cleanup must remove fixture feeds only.");
            fixture("publish");
            TvRecommendations.Result feeds = TvRecommendations.read(target);
            check(feeds.status == TvRecommendations.Status.READY || feeds.status == TvRecommendations.Status.PARTIAL,
                    "Cross-app provider query failed: " + feeds.status);
            check(fixtureFeeds(feeds) == 2, "Producer must yield one preview channel and one Watch Next feed.");
            TvRecommendations.Program selected = null;
            int programs = 0;
            for (TvRecommendations.Feed feed : feeds.feeds) {
                if (!PRODUCER.equals(feed.source)) continue;
                programs += feed.programs.size();
                if (feed.title.equals("Fixture channel")) {
                    check(feed.programs.size() == 2, "Titles must remain inside one channel.");
                    selected = feed.programs.get(0);
                }
                for (TvRecommendations.Program program : feed.programs)
                    check(PRODUCER.equals(program.source), "Program publisher must be retained.");
            }
            check(programs == 3 && selected != null, "Cross-app rows must retain published grouping and source.");
            RecommendationActions.Availability available = RecommendationActions.inspect(target, selected);
            check(available.title && available.app, "Title and app routes must both resolve independently.");
            check(!available.titleLabel().equals(available.appLabel()), "App fallback must not be labeled as title launch.");
            foregroundMain();
            check(RecommendationActions.launchTitle(target, selected) == RecommendationActions.Outcome.OPENED,
                    "Valid publisher title route must launch.");
            awaitOpened(selected.intentUri);
            foregroundMain();
            check(RecommendationActions.openApp(target, selected) == RecommendationActions.Outcome.OPENED,
                    "App fallback must launch independently.");
            awaitOpened("app");
            reject(target, new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName(PRODUCER, PRODUCER + ".FixtureActivity")), "Non-title action");
            reject(target, new Intent(Intent.ACTION_VIEW).setData(android.net.Uri.parse("fixture://title/unsafe"))
                    .setPackage("com.android.settings"), "Wrong package");
            reject(target, new Intent(Intent.ACTION_VIEW).setComponent(new ComponentName("com.android.settings", "com.android.settings.Settings")), "Wrong component");
            reject(target, new Intent(Intent.ACTION_VIEW).setComponent(new ComponentName(PRODUCER, PRODUCER + ".PrivateActivity")), "Unexported activity");
            reject(target, new Intent(Intent.ACTION_VIEW).setComponent(new ComponentName(PRODUCER, PRODUCER + ".MissingActivity")), "Missing activity");
            int[] unsafe = {Intent.FLAG_GRANT_READ_URI_PERMISSION, Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION, Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
                    Intent.FLAG_ACTIVITY_CLEAR_TASK, Intent.FLAG_ACTIVITY_MULTIPLE_TASK};
            for (int flag : unsafe) reject(target,
                    new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("fixture://title/unsafe")).addFlags(flag), "Unsafe flags");
            Intent selector = new Intent(Intent.ACTION_VIEW);
            selector.setSelector(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("fixture://title/unsafe")));
            reject(target, selector, "Selector");
            reject(target, new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("file:///private/title")), "File data");
            TvRecommendations.Program invalid = program("intent:#Intent;package=com.android.settings;end");
            check(!RecommendationActions.inspect(target, invalid).title
                    && RecommendationActions.inspect(target, invalid).app, "Unsafe title retains honest explicit app fallback.");
            check(RecommendationActions.launchTitle(target, invalid) == RecommendationActions.Outcome.UNAVAILABLE,
                    "Unsafe title must not silently launch app fallback.");
            result.putString("status", "PASS");
            result.putBoolean("cross_app_tvprovider_reads", true);
            result.putBoolean("publisher_scoped_title_and_separate_app_handoff", true);
            result.putBoolean("unsafe_intent_rejection", true);
            result.putString("limits", "Fixture handoff receipt is verified; no real service playback or physical TV behavior is established. Empty/partial/missing provider states also have host coverage.");
        } catch (Throwable failure) {
            result.putString("status", "FAIL");
            result.putString("reason", failure.toString());
        } finally {
            if (main != null) {
                runOnMainSync(new Runnable() {
                    @Override public void run() { main.finish(); }
                });
                waitForIdleSync();
            }
            try { cleanupFixture(result); }
            catch (Throwable failure) { result.putBoolean("fixture_rows_removed", false); result.putString("cleanup_error", failure.toString()); }
        }
        finish(0, result);
    }

    private void foregroundMain() {
        main = startActivitySync(new Intent().setClassName(getTargetContext(),
                "com.jeremykenedy.firetv.ui.nativeapp.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        waitForIdleSync();
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (main != null && !main.hasWindowFocus() && SystemClock.uptimeMillis() < deadline)
            SystemClock.sleep(50);
        check(main != null && main.hasWindowFocus(), "Main must be foreground before a user title/app handoff.");
    }

    private void cleanupFixture(Bundle result) throws Exception {
        Context context = getTargetContext();
        check(context.checkSelfPermission(TvRecommendations.PERMISSION) == PackageManager.PERMISSION_GRANTED,
                "TV listings permission is needed to identify exact fixture feeds before cleanup.");
        TvRecommendations.Result published = TvRecommendations.read(context);
        check(published.status == TvRecommendations.Status.READY || published.status == TvRecommendations.Status.EMPTY,
                "Cannot safely identify fixture slots while provider read is incomplete: " + published.status);
        Set<String> slots = new LinkedHashSet<>();
        Set<String> feeds = new LinkedHashSet<>();
        for (TvRecommendations.Feed feed : published.feeds) if (PRODUCER.equals(feed.source)) {
            slots.add(feed.item.key());
            feeds.add(feed.item.id);
        }
        LayoutOptions layout = LayoutPreferences.load(context, LayoutOptions.defaults(320, 180));
        List<String> order = new ArrayList<>(layout.order);
        Set<String> hidden = new LinkedHashSet<>(layout.hidden);
        int layoutCount = order.size() + hidden.size();
        order.removeAll(slots);
        hidden.removeAll(slots);
        layoutCount -= order.size() + hidden.size();
        if (layoutCount > 0) LayoutPreferences.save(context,
                new LayoutOptions(layout.cardWidthDp, layout.cardHeightDp, order, hidden));
        addCount(result, "fixture_layout_entries_removed", layoutCount);

        CardOptions cards = CardPreferences.load(context);
        int timers = 0, hiddenItems = 0;
        for (String feed : feeds) if (cards.feedSeconds.containsKey(feed)) {
            cards = cards.useGlobal(feed);
            timers++;
        }
        for (CardOptions.HiddenItem item : cards.hiddenItems()) if (PRODUCER.equals(item.source)) {
            cards = cards.restore(item.source, item.key);
            hiddenItems++;
        }
        if (timers + hiddenItems > 0) CardPreferences.save(context, cards);
        addCount(result, "fixture_timer_overrides_removed", timers);
        addCount(result, "fixture_hidden_items_removed", hiddenItems);

        AppGridOptions apps = AppGridPreferences.load(context);
        List<String> appOrder = new ArrayList<>();
        Set<String> appHidden = new LinkedHashSet<>(), favorites = new LinkedHashSet<>();
        for (String key : apps.order) if (!fixtureComponent(key)) appOrder.add(key);
        for (String key : apps.hidden) if (!fixtureComponent(key)) appHidden.add(key);
        for (String key : apps.favorites) if (!fixtureComponent(key)) favorites.add(key);
        int appCount = apps.order.size() + apps.hidden.size() + apps.favorites.size()
                - appOrder.size() - appHidden.size() - favorites.size();
        if (appCount > 0) {
            AppGridPreferences.save(context, new AppGridOptions(apps.columns, apps.rows, apps.horizontal,
                    appOrder, appHidden, favorites));
            check(context.getSharedPreferences("native_app_grid", Context.MODE_PRIVATE).edit().commit(),
                    "App fixture cleanup must finish saving.");
        }
        addCount(result, "fixture_app_entries_removed", appCount);
        fixture("clear");
        check(fixtureFeeds(TvRecommendations.read(context)) == 0, "Fixture rows must be removed.");
        result.putBoolean("fixture_rows_removed", true);
    }

    private boolean fixtureComponent(String key) {
        ComponentName component = ComponentName.unflattenFromString(key);
        return component != null && PRODUCER.equals(component.getPackageName());
    }
    private void addCount(Bundle result, String key, int count) {
        result.putInt(key, result.getInt(key, 0) + count);
    }

    private void reject(Context context, Intent intent, String message) {
        check(RecommendationActions.titleIntent(context, program(intent.toUri(Intent.URI_INTENT_SCHEME))) == null,
                message + " must be rejected.");
    }
    private TvRecommendations.Program program(String uri) {
        return new TvRecommendations.Program("test", PRODUCER, "Fixture", null, uri);
    }
    private int fixtureFeeds(TvRecommendations.Result result) {
        int count = 0;
        for (TvRecommendations.Feed feed : result.feeds) if (PRODUCER.equals(feed.source)) count++;
        return count;
    }
    private void awaitOpened(String expected) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < deadline) {
            if (expected.equals(fixture("status").getString("opened"))) return;
            SystemClock.sleep(50);
        }
        throw new AssertionError("Producer did not receive expected title/app route.");
    }
    private Bundle fixture(String operation) throws Exception {
        final CountDownLatch received = new CountDownLatch(1);
        final Bundle[] response = new Bundle[1];
        getTargetContext().sendOrderedBroadcast(new Intent().setComponent(new ComponentName(PRODUCER,
                PRODUCER + ".FixtureReceiver")).putExtra("operation", operation), null, new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                response[0] = getResultExtras(false);
                received.countDown();
            }
        }, null, 0, null, null);
        check(received.await(10, TimeUnit.SECONDS), "Producer did not respond.");
        check(response[0] != null && "OK".equals(response[0].getString("status")),
                "Producer failed: " + response[0]);
        return response[0];
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
