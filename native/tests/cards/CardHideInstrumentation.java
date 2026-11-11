package com.jeremykenedy.firetv.ui.nativeapp.providers;

import android.app.Activity;
import android.content.*;
import android.os.*;
import android.view.*;
import com.jeremykenedy.firetv.ui.nativeapp.cards.*;
import com.jeremykenedy.firetv.ui.nativeapp.layout.*;
import com.jeremykenedy.firetv.ui.nativeapp.appgrid.AppGridSettingsActivity;
import com.jeremykenedy.firetv.ui.nativeapp.layoutschedule.*;
import java.util.*;
import java.util.concurrent.*;

/** Real publishers, Home actions and existing restoration screens on an owned emulator. */
public final class CardHideInstrumentation extends CardControlsInstrumentation {
    private static final String PRODUCER = "com.jeremykenedy.firetv.ui.providerfixture";
    private static final String OTHER = "com.jeremykenedy.firetv.ui.othercardfixture";
    private HomeContentView home;
    private Activity cardSettings;
    private TvRecommendations.Feed first, sibling, control;
    private String step = "publish owned recommendation fixtures";
    private boolean editorOnly;
    @Override public void onCreate(Bundle args) { editorOnly = args != null && "true".equals(args.getString("editor_only")); super.onCreate(args); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            check("com.jeremykenedy.firetv.ui.preview".equals(getTargetContext().getPackageName()), "Preview only.");
            if (editorOnly) { editorBounds(result); result.putString("status", "PASS"); finish(-1, result); return; }
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
            cards = waitForMonitorWithTimeout(monitor, 5000); removeMonitor(monitor); check(cards != null, "Cards settings opened."); cardSettings = cards; awaitWindow(cards);
            clickTag("channels");
            ActivityMonitor layoutMonitor = addMonitor(ROOT + "layout.LayoutSettingsActivity", null, false);
            clickTag("layout");
            Activity layout = waitForMonitorWithTimeout(layoutMonitor, 5000); removeMonitor(layoutMonitor);
            check(layout != null, "Existing layout restoration route opened."); cards = layout; awaitWindow(cards);
            awaitTag(first.item.key() + ":visibility"); clickTag(first.item.key() + ":visibility");
            capture("restore-feed-layout.png");
            backFromLayout(); readHome();
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
            step = "active dashboard hide edits only that profile";
            Map<String, ?> baseBefore = getTargetContext().getSharedPreferences("native_layout", Context.MODE_PRIVATE).getAll();
            LayoutOptions baseLayout = LayoutPreferences.load(getTargetContext(), HomeSlots.defaults());
            LayoutOptions profileLayout = baseLayout;
            for (TvRecommendations.Feed feed : payload.feeds) profileLayout = profileLayout.setVisible(feed.item.key(), feed.item.key().equals(first.item.key()));
            LayoutScheduleStore schedules = new LayoutScheduleStore(getTargetContext());
            String activeId = schedules.save("Card proof dashboard", null, profileLayout);
            String otherId = schedules.save("Other proof dashboard", null, baseLayout.resizeCards(400, 225));
            schedules.rotation(Arrays.asList(activeId, otherId), 3600, System.currentTimeMillis());
            schedules.daily("UTC", Arrays.asList(new LayoutSchedule.Slot(0, activeId), new LayoutSchedule.Slot(720, otherId)));
            schedules.selectManual(activeId); refreshDashboard(); awaitDashboard(activeId);
            assertShown(first, true); assertShown(sibling, false); assertShown(control, false);
            LayoutSchedule.State profileBefore = schedules.read();
            String metadataBefore = scheduleMetadata(profileBefore);
            Map<String, Object> otherBefore = profileBefore.profile(otherId).layout.backupValues();
            select(first, KeyEvent.KEYCODE_MENU); clickText("Hide this card"); awaitWindow(main);
            assertShown(first, false); capture("active-dashboard-last-card-hidden.png");
            check(schedules.read().profile(activeId).layout.hidden.contains(first.item.key()), "Exact active dashboard owns feed hide.");
            check(baseBefore.equals(getTargetContext().getSharedPreferences("native_layout", Context.MODE_PRIVATE).getAll()), "Active dashboard hide does not write base layout.");
            check(otherBefore.equals(schedules.read().profile(otherId).layout.backupValues())
                    && metadataBefore.equals(scheduleMetadata(schedules.read())), "Other dashboard and all scheduling metadata remain byte-equivalent.");
            step = "restore last dashboard card through existing Cards settings";
            ActivityMonitor profileCards = addMonitor(ROOT + "cards.CardsSettingsActivity", null, false);
            onMain(new Runnable() { @Override public void run() { main.startActivity(new Intent(main, CardsSettingsActivity.class)); } });
            cards = waitForMonitorWithTimeout(profileCards, 5000); removeMonitor(profileCards); check(cards != null, "Cards remains reachable without any dashboard card."); cardSettings = cards; awaitWindow(cards);
            clickTag("channels");
            ActivityMonitor profileEditor = addMonitor(ROOT + "layout.LayoutSettingsActivity", null, false); clickTag("layout");
            cards = waitForMonitorWithTimeout(profileEditor, 5000); removeMonitor(profileEditor); check(cards != null, "Active profile editor opens."); awaitWindow(cards);
            check(activeId.equals(cards.getIntent().getStringExtra(LayoutSettingsActivity.DASHBOARD_PROFILE)), "Cards targets the current dashboard explicitly.");
            onMain(new Runnable() { @Override public void run() { check(containsText(cards.getWindow().getDecorView(), "Dashboard: Card proof dashboard"), "Visible editor heading identifies profile scope."); } });
            awaitTag(first.item.key() + ":visibility"); clickTag(first.item.key() + ":visibility"); capture("restore-active-dashboard-card.png");
            backFromLayout(); awaitDashboard(activeId); awaitShown(first, true);
            assertShown(first, true); assertShown(sibling, false); assertShown(control, false);
            check(baseBefore.equals(getTargetContext().getSharedPreferences("native_layout", Context.MODE_PRIVATE).getAll())
                    && otherBefore.equals(schedules.read().profile(otherId).layout.backupValues())
                    && metadataBefore.equals(scheduleMetadata(schedules.read())), "Profile Show preserves base, other profile and schedule bytes.");
            capture("active-dashboard-card-restored.png");
            step = "dashboard change while popup open refuses stale target";
            select(first, KeyEvent.KEYCODE_MENU); schedules.selectManual(otherId); refreshDashboard(); awaitDashboard(otherId);
            byte[] changedSelection = LayoutScheduleCodec.encode(schedules.read());
            clickText("Hide this card"); awaitWindow(main);
            check(Arrays.equals(changedSelection, LayoutScheduleCodec.encode(schedules.read())), "Stale popup cannot edit either dashboard after selection changes.");
            step = "card Settings retains captured dashboard after selection changes";
            schedules.selectManual(activeId); refreshDashboard(); awaitDashboard(activeId); awaitShown(first, true);
            openCardSettings(first);
            check(activeId.equals(cards.getIntent().getStringExtra(LayoutSettingsActivity.DASHBOARD_PROFILE)), "Card menu carries exact displayed dashboard into Settings.");
            schedules.selectManual(otherId);
            byte[] contextualState = LayoutScheduleCodec.encode(schedules.read());
            clickTag("channels");
            ActivityMonitor capturedEditor = addMonitor(ROOT + "layout.LayoutSettingsActivity", null, false); clickTag("layout");
            cards = waitForMonitorWithTimeout(capturedEditor, 5000); removeMonitor(capturedEditor); check(cards != null, "Captured editor opens."); awaitWindow(cards);
            check(activeId.equals(cards.getIntent().getStringExtra(LayoutSettingsActivity.DASHBOARD_PROFILE)), "Later selection cannot retarget captured dashboard A to B.");
            capture("captured-dashboard-editor.png");
            backFromLayout(); awaitDashboard(otherId);
            check(Arrays.equals(contextualState, LayoutScheduleCodec.encode(schedules.read())), "Contextual editor visit changes neither profile nor selection.");
            step = "captured base Settings stays base after dashboard activation";
            schedules.resumeAutomatic(); refreshDashboard(); awaitDashboard(null); awaitShown(first, true);
            openCardSettings(first);
            check(LayoutSettingsActivity.BASE_LAYOUT.equals(cards.getIntent().getStringExtra(LayoutSettingsActivity.DASHBOARD_PROFILE)), "Base card menu carries explicit base target.");
            schedules.selectManual(activeId);
            contextualState = LayoutScheduleCodec.encode(schedules.read());
            clickTag("channels");
            ActivityMonitor baseEditor = addMonitor(ROOT + "layout.LayoutSettingsActivity", null, false); clickTag("layout");
            cards = waitForMonitorWithTimeout(baseEditor, 5000); removeMonitor(baseEditor); check(cards != null, "Captured base editor opens."); awaitWindow(cards);
            check(LayoutSettingsActivity.BASE_LAYOUT.equals(cards.getIntent().getStringExtra(LayoutSettingsActivity.DASHBOARD_PROFILE)), "Base context cannot become later active profile.");
            onMain(new Runnable() { @Override public void run() { check(containsText(cards.getWindow().getDecorView(), "Your cards and widgets"), "Base editor retains its original heading."); } });
            backFromLayout(); awaitDashboard(activeId); awaitShown(first, true);
            check(Arrays.equals(contextualState, LayoutScheduleCodec.encode(schedules.read())), "Base context visit preserves all profile and schedule state.");
            step = "direct default editor remains base and deleted captured profile refuses";
            ActivityMonitor defaultEditor = addMonitor(ROOT + "layout.LayoutSettingsActivity", null, false);
            onMain(new Runnable() { @Override public void run() { main.startActivity(new Intent(main, LayoutSettingsActivity.class)); } });
            cards = waitForMonitorWithTimeout(defaultEditor, 5000); removeMonitor(defaultEditor); check(cards != null, "Default editor opens."); awaitWindow(cards);
            check(!cards.getIntent().hasExtra(LayoutSettingsActivity.DASHBOARD_PROFILE), "Direct layout route keeps absent default target.");
            onMain(new Runnable() { @Override public void run() { check(containsText(cards.getWindow().getDecorView(), "Your cards and widgets"), "Direct base editor keeps original heading."); } });
            press(KeyEvent.KEYCODE_BACK); awaitWindow(main);
            capture("dashboard-restored.png");
            openCardSettings(first);
            check(activeId.equals(cards.getIntent().getStringExtra(LayoutSettingsActivity.DASHBOARD_PROFILE)), "Capture dashboard before deletion.");
            schedules.delete(activeId);
            byte[] deletedState = LayoutScheduleCodec.encode(schedules.read());
            clickTag("channels");
            Activity capturedCards = cards;
            ActivityMonitor missingEditor = addMonitor(ROOT + "layout.LayoutSettingsActivity", null, false); clickTag("layout");
            Activity missing = waitForMonitorWithTimeout(missingEditor, 5000); removeMonitor(missingEditor); SystemClock.sleep(300);
            check(missing != null && missing.isFinishing(), "Deleted captured profile refuses without substituting base editor.");
            cards = capturedCards; awaitWindow(cards);
            check(baseBefore.equals(getTargetContext().getSharedPreferences("native_layout", Context.MODE_PRIVATE).getAll())
                    && Arrays.equals(deletedState, LayoutScheduleCodec.encode(schedules.read())), "Refused target and read-only editor visits leave base/profile/schedule state unchanged.");
            press(KeyEvent.KEYCODE_BACK); press(KeyEvent.KEYCODE_BACK); awaitWindow(main);

            result.putString("status", "PASS");
            result.putString("cases", "Actual Select and Menu; original provider link; next title retention; exact feed hide retains same-source sibling and unrelated source; logical next focus; existing layout Show; whole publisher hide across preview/WatchNext; App Manager checkbox+Save restores all; last-card app focus; exact active dashboard hide and Show; unchanged base/other profile/schedule; stale-popup refusal; default and missing editor targets");
        } catch (Throwable failure) {
            result.putString("status", "FAIL"); result.putString("step", step); result.putString("reason", failure.toString());
            try { capture("failure.png"); } catch (Exception captureError) { result.putString("capture", captureError.toString()); }
        }
        finish("PASS".equals(result.getString("status")) ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
    private String scheduleMetadata(LayoutSchedule.State state) throws Exception {
        org.json.JSONObject value = new org.json.JSONObject(new String(LayoutScheduleCodec.encode(state), "UTF-8"));
        value.remove("profiles"); return value.toString();
    }
    private void refreshDashboard() {
        onMain(new Runnable() { @Override public void run() {
            try {
                java.lang.reflect.Field field = main.getClass().getDeclaredField("dashboard"); field.setAccessible(true);
                ((LayoutScheduleController) field.get(main)).refresh();
            } catch (Exception failure) { throw new AssertionError(failure); }
        }});
    }
    private void awaitDashboard(final String id) {
        long end = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < end) {
            final boolean[] matches = new boolean[1];
            onMain(new Runnable() { @Override public void run() {
                try {
                    java.lang.reflect.Field field = main.getClass().getDeclaredField("displayedDashboard"); field.setAccessible(true);
                    LayoutSchedule.Profile value = (LayoutSchedule.Profile) field.get(main);
                    matches[0] = id == null ? value == null : value != null && id.equals(value.id);
                } catch (Exception failure) { throw new AssertionError(failure); }
            }});
            if (matches[0]) { waitForIdleSync(); return; }
            SystemClock.sleep(50);
        }
        throw new AssertionError("Dashboard did not become active: " + id);
    }
    private void backFromLayout() {
        press(KeyEvent.KEYCODE_BACK); awaitWindow(cardSettings);
        cards = cardSettings;
        press(KeyEvent.KEYCODE_BACK); awaitWindow(cardSettings);
        press(KeyEvent.KEYCODE_BACK); awaitWindow(main);
    }
    private void openCardSettings(TvRecommendations.Feed feed) {
        select(feed, KeyEvent.KEYCODE_MENU);
        ActivityMonitor monitor = addMonitor(ROOT + "cards.CardsSettingsActivity", null, false);
        clickText("Card settings");
        cards = waitForMonitorWithTimeout(monitor, 5000); removeMonitor(monitor);
        check(cards != null, "Actual card menu opens Settings."); cardSettings = cards; awaitWindow(cards);
    }
    private void awaitShown(final TvRecommendations.Feed feed, final boolean expected) {
        long end = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < end) {
            final boolean[] matches = new boolean[1];
            onMain(new Runnable() { @Override public void run() {
                View slot = home.findViewWithTag("slot:" + feed.item.key()); matches[0] = (slot != null && slot.isShown()) == expected;
            }});
            if (matches[0]) return;
            SystemClock.sleep(50);
        }
        throw new AssertionError("Feed did not settle: " + feed.title);
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
    private void editorBounds(Bundle result) throws Exception {
        fixture(PRODUCER, "legacy-publish"); fixture(OTHER, "publish");
        TvRecommendations.Result payload = TvRecommendations.read(getTargetContext());
        first = payload.feeds.get(0);
        CardPreferences.save(getTargetContext(), CardOptions.defaults().withGlobalSeconds(0));
        LayoutOptions base = HomeSlots.discover(HomeSlots.defaults(), payload.feeds);
        LayoutPreferences.save(getTargetContext(), base.setVisible(first.item.key(), false));
        main = launch("MainActivity"); SystemClock.sleep(600); readHome(); assertShown(first, false);
        step = "base editor clips scrolled content and Show restores card";
        openGenericCards(); openLayoutEditor();
        awaitTag(first.item.key() + ":visibility"); captureEditor(result, "base");
        clickTag(first.item.key() + ":visibility"); backFromLayout(); readHome(); awaitShown(first, true);
        Map<String, ?> baseBefore = getTargetContext().getSharedPreferences("native_layout", Context.MODE_PRIVATE).getAll();
        final LayoutScheduleStore store = new LayoutScheduleStore(getTargetContext());
        LayoutOptions profile = LayoutPreferences.load(getTargetContext(), HomeSlots.defaults()).withMode(LayoutOptions.Mode.BOTH);
        final String active = store.save("Card visibility proof", null, profile);
        String other = store.save("Untouched dashboard", null, profile.resizeCards(400, 225));
        store.rotation(Arrays.asList(active, other), 3600, System.currentTimeMillis());
        store.daily("UTC", Arrays.asList(new LayoutSchedule.Slot(0, active), new LayoutSchedule.Slot(720, other)));
        store.selectManual(active); refreshDashboard(); awaitDashboard(active); awaitShown(first, true);
        LayoutSchedule.State before = store.read();
        String metadata = scheduleMetadata(before);
        Map<String, Object> otherBefore = before.profile(other).layout.backupValues();
        step = "actual active-dashboard Hide featured cards toggle then Back";
        openGenericCards(); clickTag("hide-cards"); capture("active-dashboard-cards-off-setting.png");
        check(store.read().profile(active).layout.mode == LayoutOptions.Mode.WIDGETS_ONLY, "Cards Off preserves active dashboard widgets.");
        press(KeyEvent.KEYCODE_BACK); awaitWindow(main); awaitDashboard(active); readHome(); awaitShown(first, false);
        capture("active-dashboard-cards-off-home.png");
        openGenericCards(); clickTag("hide-cards");
        check(store.read().profile(active).layout.mode == LayoutOptions.Mode.BOTH, "Cards On restores dashboard cards and preserves widgets.");
        press(KeyEvent.KEYCODE_BACK); awaitWindow(main); awaitDashboard(active); readHome(); awaitShown(first, true);
        capture("active-dashboard-cards-on-home.png");
        check(baseBefore.equals(getTargetContext().getSharedPreferences("native_layout", Context.MODE_PRIVATE).getAll())
                && otherBefore.equals(store.read().profile(other).layout.backupValues())
                && metadata.equals(scheduleMetadata(store.read())), "Dashboard toggle keeps base, other profile and scheduling bytes unchanged.");
        step = "profile editor clips content and Show survives Back";
        store.save("Card visibility proof", active, profile.setVisible(first.item.key(), false));
        refreshDashboard(); awaitDashboard(active); awaitShown(first, false);
        openGenericCards(); openLayoutEditor(); awaitTag(first.item.key() + ":visibility");
        captureEditor(result, "profile"); clickTag(first.item.key() + ":visibility");
        backFromLayout(); awaitDashboard(active); readHome(); awaitShown(first, true);
        check(baseBefore.equals(getTargetContext().getSharedPreferences("native_layout", Context.MODE_PRIVATE).getAll())
                && otherBefore.equals(store.read().profile(other).layout.backupValues())
                && metadata.equals(scheduleMetadata(store.read())), "Profile Show preserves unrelated state.");
        step = "generic stale toggle refreshes before allowing another click";
        openGenericCards(); store.selectManual(other);
        byte[] changed = LayoutScheduleCodec.encode(store.read());
        clickTag("hide-cards");
        check(Arrays.equals(changed, LayoutScheduleCodec.encode(store.read())), "Stale generic toggle refuses write.");
        clickTag("hide-cards");
        check(store.read().profile(other).layout.mode == LayoutOptions.Mode.WIDGETS_ONLY
                && store.read().profile(active).layout.mode == LayoutOptions.Mode.BOTH, "Refreshed toggle acts on the newly displayed target only.");
        clickTag("hide-cards"); press(KeyEvent.KEYCODE_BACK); awaitWindow(main); awaitDashboard(other); readHome();
        step = "explicit contextual toggle retains dashboard A after selection B";
        store.selectManual(active); refreshDashboard(); awaitDashboard(active); awaitShown(first, true);
        openCardSettings(first); store.selectManual(other);
        clickTag("hide-cards");
        check(store.read().profile(active).layout.mode == LayoutOptions.Mode.WIDGETS_ONLY
                && store.read().profile(other).layout.mode == LayoutOptions.Mode.BOTH, "Captured dashboard A toggle cannot retarget to B.");
        clickTag("hide-cards"); press(KeyEvent.KEYCODE_BACK); awaitWindow(main);
        check(baseBefore.equals(getTargetContext().getSharedPreferences("native_layout", Context.MODE_PRIVATE).getAll())
                && otherBefore.equals(store.read().profile(other).layout.backupValues()), "Contextual toggle keeps base and B unchanged.");
        result.putString("cases", "Base/profile editor scroll clipping and actual Show/Back; active dashboard Cards Off/Back/On with widget mode and unrelated state preserved; generic stale refusal then refreshed retry; explicit captured toggle scope");
    }
    private void openGenericCards() {
        ActivityMonitor monitor = addMonitor(ROOT + "cards.CardsSettingsActivity", null, false);
        onMain(new Runnable() { @Override public void run() { main.startActivity(new Intent(main, CardsSettingsActivity.class)); } });
        cards = waitForMonitorWithTimeout(monitor, 5000); removeMonitor(monitor);
        check(cards != null, "Generic Cards settings opens."); cardSettings = cards; awaitWindow(cards);
    }
    private void openLayoutEditor() {
        clickTag("channels");
        ActivityMonitor monitor = addMonitor(ROOT + "layout.LayoutSettingsActivity", null, false); clickTag("layout");
        cards = waitForMonitorWithTimeout(monitor, 5000); removeMonitor(monitor);
        check(cards != null, "Actual channel layout route opens."); awaitWindow(cards);
    }
    private void captureEditor(Bundle result, String name) throws Exception {
        final StringBuilder bounds = new StringBuilder();
        onMain(new Runnable() { @Override public void run() {
            android.widget.ScrollView scroll = findScroll(cards.getWindow().getDecorView());
            android.view.ViewGroup page = (android.view.ViewGroup)scroll.getParent();
            check(page.getClipChildren(), "Editor clips scrolled content below its fixed header.");
            View explanation = page.getChildAt(1);
            check(explanation.getBottom() <= scroll.getTop() && explanation.getHeight() > 0, "Header explanation has its own measured space.");
            check(scroll.getScrollY() > 0, "Visibility control really scrolled the editor.");
            describe(cards.getWindow().getDecorView(), bounds, "");
        }});
        result.putString(name + "_editor_bounds", bounds.toString()); capture(name + "-editor-clipped.png");
    }
    private android.widget.ScrollView findScroll(View view) {
        if (view instanceof android.widget.ScrollView) return (android.widget.ScrollView)view;
        if (view instanceof android.view.ViewGroup) { android.view.ViewGroup group=(android.view.ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++) { android.widget.ScrollView found=findScroll(group.getChildAt(i)); if(found!=null)return found; } }
        return null;
    }
    private void describe(View view, StringBuilder out, String indent) {
        int[] location = new int[2]; view.getLocationOnScreen(location);
        out.append(indent).append(view.getClass().getSimpleName()).append(" xy=").append(location[0]).append(',').append(location[1])
                .append(" size=").append(view.getWidth()).append('x').append(view.getHeight()).append(" scroll=").append(view.getScrollY());
        if (view instanceof android.widget.TextView) out.append(" text=").append(((android.widget.TextView)view).getText().toString().replace('\n', '|'));
        if (view instanceof android.view.ViewGroup) out.append(" clip=").append(((android.view.ViewGroup)view).getClipChildren()).append('/').append(((android.view.ViewGroup)view).getClipToPadding());
        out.append('\n');
        if (view instanceof android.view.ViewGroup) { android.view.ViewGroup group=(android.view.ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++) describe(group.getChildAt(i),out,indent+" "); }
    }
    private void capture(String name) throws Exception {
        waitForIdleSync(); SystemClock.sleep(500);
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(new java.io.File(getTargetContext().getExternalFilesDir(null), name))) {
            check(getUiAutomation().takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output), "Capture succeeds.");
        }
    }
}
