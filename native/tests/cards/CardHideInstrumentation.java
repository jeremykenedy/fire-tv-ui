package com.jeremykenedy.firetv.ui.nativeapp.providers;

import android.app.Activity;
import android.content.*;
import android.os.*;
import android.view.*;
import com.jeremykenedy.firetv.ui.nativeapp.cards.*;
import com.jeremykenedy.firetv.ui.nativeapp.layout.*;
import com.jeremykenedy.firetv.ui.nativeapp.appgrid.AppGridSettingsActivity;
import java.util.*;
import java.util.concurrent.*;

/** Real publishers, Home actions and existing restoration screens on an owned emulator. */
public final class CardHideInstrumentation extends CardControlsInstrumentation {
    private static final String PRODUCER = "com.jeremykenedy.firetv.ui.providerfixture";
    private static final String OTHER = "com.jeremykenedy.firetv.ui.othercardfixture";
    private HomeContentView home;
    private TvRecommendations.Feed first, sibling, control;
    private String step = "publish owned recommendation fixtures";
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            check("com.jeremykenedy.firetv.ui.preview".equals(getTargetContext().getPackageName()), "Preview only.");
            fixture(PRODUCER, "legacy-publish"); fixture(OTHER, "publish");
            TvRecommendations.Result payload = TvRecommendations.read(getTargetContext());
            List<TvRecommendations.Feed> primary = new ArrayList<>(), others = new ArrayList<>();
            for (TvRecommendations.Feed feed : payload.feeds) {
                if (PRODUCER.equals(feed.source)) primary.add(feed);
                else if (OTHER.equals(feed.source)) others.add(feed);
            }
            check(primary.size() == 3 && others.size() == 2, "Two real publishers expose separate channels and Watch Next.");
            first = primary.get(0); sibling = primary.get(1); control = others.get(0);
            List<String> order = new ArrayList<>(); order.add(control.item.key());
            for (TvRecommendations.Feed feed : primary) order.add(feed.item.key());
            order.add(others.get(1).item.key());
            CardPreferences.save(getTargetContext(), CardOptions.defaults().withGlobalSeconds(0));
            LayoutPreferences.save(getTargetContext(), new LayoutOptions(320, 180, order,
                    Collections.<String>emptyList()).withMode(LayoutOptions.Mode.CARDS_ONLY));
            main = launch("MainActivity"); SystemClock.sleep(1000); readHome();
            step = "Select exposes distinct controls and original provider Open";
            select(first, KeyEvent.KEYCODE_DPAD_CENTER);
            check("".equals(fixture(PRODUCER, "diagnostics").getString("opened")), "Select opens actions without launching provider.");
            capture("select-card-controls.png");
            clickText("Open title in Recommendation test producer");
            SystemClock.sleep(400);
            check("fixture://shared".equals(fixture(PRODUCER, "diagnostics").getString("opened")), "Original exact provider link still launches.");
            press(KeyEvent.KEYCODE_BACK); awaitWindow(main); SystemClock.sleep(400); readHome();
            step = "next title and hide exact middle feed through Menu";
            select(first, KeyEvent.KEYCODE_MENU); clickText("Next title"); awaitWindow(main);
            final String[] selected = new String[1];
            onMain(new Runnable() { @Override public void run() { selected[0] = home.findCard(first.item.key()).currentProgram().key; } });
            select(first, KeyEvent.KEYCODE_MENU); clickText("Hide this card"); awaitWindow(main);
            check(LayoutPreferences.load(getTargetContext(), HomeSlots.defaults()).hidden.contains(first.item.key()), "Only exact feed visibility is saved.");
            check(CardPreferences.load(getTargetContext()).hiddenItems().isEmpty(), "Feed hiding creates no title hides.");
            check(!CardPreferences.isPackageHidden(getTargetContext(), PRODUCER), "Feed hiding leaves publisher choice alone.");
            assertShown(first, false); assertShown(sibling, true); assertShown(control, true);
            onMain(new Runnable() { @Override public void run() {
                check(main.getCurrentFocus() != null && ("slot:" + sibling.item.key()).equals(main.getCurrentFocus().getTag()),
                        "Hiding middle feed focuses its next visible sibling, not the first unrelated slot.");
            }});
            capture("one-feed-hidden.png");
            step = "restore exact feed through existing Cards layout route";
            ActivityMonitor monitor = addMonitor(ROOT + "cards.CardsSettingsActivity", null, false);
            onMain(new Runnable() { @Override public void run() { main.startActivity(new Intent(main, CardsSettingsActivity.class)); } });
            cards = waitForMonitorWithTimeout(monitor, 5000); removeMonitor(monitor); check(cards != null, "Cards settings opened."); awaitWindow(cards);
            clickTag("channels");
            ActivityMonitor layoutMonitor = addMonitor(ROOT + "layout.LayoutSettingsActivity", null, false);
            clickTag("layout");
            Activity layout = waitForMonitorWithTimeout(layoutMonitor, 5000); removeMonitor(layoutMonitor);
            check(layout != null, "Existing layout restoration route opened."); cards = layout; awaitWindow(cards);
            awaitTag(first.item.key() + ":visibility"); clickTag(first.item.key() + ":visibility");
            capture("restore-feed-layout.png");
            press(KeyEvent.KEYCODE_BACK); press(KeyEvent.KEYCODE_BACK); press(KeyEvent.KEYCODE_BACK); awaitWindow(main); readHome();
            assertShown(first, true); assertShown(sibling, true); assertShown(control, true);
            onMain(new Runnable() { @Override public void run() {
                check(selected[0].equals(home.findCard(first.item.key()).currentProgram().key), "Restored card retains its selected visible title.");
            }});
            step = "publisher-wide hide through actual Select";
            select(first, KeyEvent.KEYCODE_DPAD_CENTER);
            clickText("Hide all cards from Recommendation test producer"); awaitWindow(main);
            check(CardPreferences.isPackageHidden(getTargetContext(), PRODUCER), "Existing publisher setting is saved.");
            for (TvRecommendations.Feed feed : primary) assertShown(feed, false);
            for (TvRecommendations.Feed feed : others) assertShown(feed, true);
            capture("publisher-hidden-control-retained.png");
            step = "restore publisher through existing App Manager checkbox and Save";
            ActivityMonitor managerMonitor = addMonitor(ROOT + "appgrid.AppGridSettingsActivity", null, false);
            onMain(new Runnable() { @Override public void run() { main.startActivity(new Intent(main, AppGridSettingsActivity.class)); } });
            cards = waitForMonitorWithTimeout(managerMonitor, 5000); removeMonitor(managerMonitor);
            check(cards != null, "Existing App Manager opens."); awaitWindow(cards);
            awaitTag("app-manager:cards:" + PRODUCER); clickTag("app-manager:cards:" + PRODUCER);
            capture("restore-publisher-app-manager.png"); clickTag("app-manager:save"); awaitWindow(main); SystemClock.sleep(300); readHome();
            check(!CardPreferences.isPackageHidden(getTargetContext(), PRODUCER), "App Manager restores source setting.");
            for (TvRecommendations.Feed feed : primary) assertShown(feed, true);
            for (TvRecommendations.Feed feed : others) assertShown(feed, true);
            check(!LayoutPreferences.load(getTargetContext(), HomeSlots.defaults()).hidden.contains(first.item.key()), "Source restore preserves independent restored feed choice.");
            step = "last visible publisher returns focus to an app or header";
            select(control, KeyEvent.KEYCODE_MENU); clickText("Hide all cards from Other recommendation producer"); awaitWindow(main);
            assertShown(first, true); assertShown(control, false);
            select(first, KeyEvent.KEYCODE_DPAD_CENTER); clickText("Hide all cards from Recommendation test producer"); awaitWindow(main);
            for (TvRecommendations.Feed feed : primary) assertShown(feed, false);
            for (TvRecommendations.Feed feed : others) assertShown(feed, false);
            onMain(new Runnable() { @Override public void run() {
                View focused = main.getCurrentFocus();
                check(focused != null && focused.isShown() && focused.isFocusable()
                        && !(String.valueOf(focused.getTag()).startsWith("slot:")), "Hiding final cards leaves visible app/header focus.");
            }});
            capture("all-cards-hidden-app-focus.png");
            ActivityMonitor finalManager = addMonitor(ROOT + "appgrid.AppGridSettingsActivity", null, false);
            onMain(new Runnable() { @Override public void run() { main.startActivity(new Intent(main, AppGridSettingsActivity.class)); } });
            cards = waitForMonitorWithTimeout(finalManager, 5000); removeMonitor(finalManager);
            check(cards != null, "App Manager remains reachable with all cards hidden."); awaitWindow(cards);
            awaitTag("app-manager:cards:" + PRODUCER); clickTag("app-manager:cards:" + PRODUCER);
            awaitTag("app-manager:cards:" + OTHER); clickTag("app-manager:cards:" + OTHER); clickTag("app-manager:save");
            awaitWindow(main); SystemClock.sleep(300); readHome();
            for (TvRecommendations.Feed feed : primary) assertShown(feed, true);
            for (TvRecommendations.Feed feed : others) assertShown(feed, true);
            capture("all-feeds-restored.png");
            result.putString("status", "PASS");
            result.putString("cases", "Actual Select and Menu; original provider link; next title retention; exact feed hide retains same-source sibling and unrelated source; logical next focus; existing layout Show; whole publisher hide across preview/WatchNext; App Manager checkbox+Save restores all");
        } catch (Throwable failure) {
            result.putString("status", "FAIL"); result.putString("step", step); result.putString("reason", failure.toString());
        }
        finish("PASS".equals(result.getString("status")) ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
    private void readHome() {
        onMain(new Runnable() { @Override public void run() {
            home = main.getWindow().getDecorView().findViewWithTag("home:content"); check(home != null, "Actual Home exists.");
        }});
    }
    private void select(final TvRecommendations.Feed feed, int key) {
        awaitWindow(main);
        final View[] view = new View[1];
        onMain(new Runnable() { @Override public void run() { view[0] = home.findViewWithTag("slot:" + feed.item.key()); } });
        focus(view[0]); press(key);
    }
    private void assertShown(final TvRecommendations.Feed feed, final boolean expected) {
        onMain(new Runnable() { @Override public void run() {
            View slot = home.findViewWithTag("slot:" + feed.item.key());
            check((slot != null && slot.isShown()) == expected, "Unexpected visibility: " + feed.title + ", expected=" + expected);
        }});
    }
    private Bundle fixture(String publisher, String operation) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1); final Bundle[] response = new Bundle[1];
        getTargetContext().sendOrderedBroadcast(new Intent().setComponent(new ComponentName(publisher, PRODUCER + ".FixtureReceiver"))
                .putExtra("operation", operation), null, new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) { response[0] = getResultExtras(false); latch.countDown(); }
        }, null, 0, null, null);
        check(latch.await(10, TimeUnit.SECONDS) && response[0] != null && "OK".equals(response[0].getString("status")), "Owned producer responds: " + publisher);
        return response[0];
    }
    private void capture(String name) throws Exception {
        waitForIdleSync(); SystemClock.sleep(500);
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(new java.io.File(getTargetContext().getExternalFilesDir(null), name))) {
            check(getUiAutomation().takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output), "Capture succeeds.");
        }
    }
}
