package com.jeremykenedy.firetv.ui.nativeapp.cards;

import android.app.Activity;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Instrumentation;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
import com.jeremykenedy.firetv.ui.nativeapp.providers.TvRecommendations;
import com.jeremykenedy.firetv.ui.nativeapp.layout.HomeSlots;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutPreferences;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class CardControlsInstrumentation extends Instrumentation {
    protected static final String ROOT = "com.jeremykenedy.firetv.ui.nativeapp.";
    private static final String PRODUCER = "com.jeremykenedy.firetv.ui.providerfixture";
    protected Activity main;
    protected Activity cards;
    private boolean recoverNewCards;
    private String step = "startup";

    @Override public void onCreate(Bundle args) { super.onCreate(args); recoverNewCards = args != null && "true".equals(args.getString("recover_new_card_preferences")); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        SharedPreferences cardPrefs = getTargetContext().getSharedPreferences("native_cards", Context.MODE_PRIVATE);
        SharedPreferences layoutPrefs = getTargetContext().getSharedPreferences("native_layout", Context.MODE_PRIVATE);
        if (recoverNewCards) {
            boolean recovered = cardPrefs.edit().remove("global_seconds").remove("feed_seconds").remove("hidden_items").commit();
            result.putBoolean("new_card_keys_recovered_to_absent", recovered);
            if (!recovered) { result.putString("status", "FAIL"); result.putString("reason", "Initial new-card preference recovery failed."); finish(0, result); return; }
        }
        Map<String, ?> cardBefore = cardPrefs.getAll(), layoutBefore = layoutPrefs.getAll();
        boolean addedEpisode = false;
        try {
            TvRecommendations.Feed fixture = null;
            TvRecommendations.Feed itemOnly = null;
            for (TvRecommendations.Feed candidate : TvRecommendations.read(getTargetContext()).feeds) {
                if (!PRODUCER.equals(candidate.source)) continue;
                if (candidate.programs.size() == 2) fixture = candidate;
                else if (candidate.programs.size() == 1) itemOnly = candidate;
            }
            check(fixture != null, "Publish the two-title provider fixture and grant TV listings access before this suite.");
            final TvRecommendations.Feed feed = fixture;
            step = "measured TV-provider series capability";
            Bundle capabilities = fixture("diagnostics");
            check(capabilities.containsKey("series_column_exists") && capabilities.containsKey("preview_series_rows") && capabilities.containsKey("preview_rows") && capabilities.containsKey("series_supported"), "Fixture must report measured provider series capability.");
            boolean seriesColumn = capabilities.getBoolean("series_column_exists");
            int published = capabilities.getInt("preview_rows", -1), retained = capabilities.getInt("preview_series_rows", -1);
            check(published == 2, "Fixture diagnostics must cover both published preview episodes.");
            check(seriesColumn || retained == 0, "Provider without a series column cannot report retained series identities.");
            check(!seriesColumn || retained == published, "Provider advertises a series column but dropped explicit published identities.");
            final boolean tvSeriesSupported = capabilities.getBoolean("series_supported");
            check(tvSeriesSupported == seriesColumn, "Measured provider capability must match retained explicit identities.");
            if (tvSeriesSupported) check(feed.programs.get(0).seriesKey != null && feed.programs.get(0).seriesKey.equals(feed.programs.get(1).seriesKey), "Supported provider must retain the same trusted series identity across episodes.");
            else for (TvRecommendations.Program program : feed.programs) check(program.seriesKey == null, "Provider without a series column must remain item-only without inferred identity.");
            result.putBoolean("tv_series_supported", tvSeriesSupported);
            result.putString("tv_series_limit", tvSeriesSupported ? "Explicit provider series IDs retained."
                    : "This platform TV-provider schema has no series_id column. TV feeds support item hiding only; Plex series proof is separate.");
            check(itemOnly != null && itemOnly.programs.get(0).seriesKey == null, "Watch Next fixture must retain explicit item-only support.");
            CardPreferences.save(getTargetContext(), CardOptions.defaults().withGlobalSeconds(0));
            LayoutPreferences.save(getTargetContext(), LayoutPreferences.load(getTargetContext(), HomeSlots.defaults())
                    .discover(java.util.Collections.singletonList(feed.item)).setVisible(feed.item.key(), true));
            step = "launch Main and open Settings";
            main = launch("MainActivity");
            ActivityMonitor settingsMonitor = addMonitor(ROOT + "SettingsActivity", null, false);
            focus(button(main.getWindow().getDecorView(), "Settings"));
            press(KeyEvent.KEYCODE_DPAD_CENTER);
            Activity settings = waitForMonitorWithTimeout(settingsMonitor, 3000);
            removeMonitor(settingsMonitor);
            check(settings != null, "Main Settings route must open.");
            awaitWindow(settings);
            press(KeyEvent.KEYCODE_DPAD_DOWN);
            press(KeyEvent.KEYCODE_DPAD_CENTER);
            ActivityMonitor cardsMonitor = addMonitor(ROOT + "cards.CardsSettingsActivity", null, false);
            press(KeyEvent.KEYCODE_DPAD_CENTER);
            cards = waitForMonitorWithTimeout(cardsMonitor, 3000);
            removeMonitor(cardsMonitor);
            check(cards != null, "D-pad Settings -> Cards route must open.");
            awaitWindow(cards);
            awaitFocusedTag("channels");
            step = "timer controls";
            clickTag("timers");
            clickTag("global");
            clickText("Custom interval (seconds)");
            setNumber("86401"); clickText("Save");
            check(CardPreferences.load(getTargetContext()).globalSeconds == 0, "Out-of-bounds custom seconds must not save.");
            setNumber("17"); clickText("Save"); awaitWindow(cards);
            check(CardPreferences.load(getTargetContext()).globalSeconds == 17, "Custom whole seconds persist.");
            step = "D-pad custom timer Save";
            clickTag("global"); clickText("Custom interval (seconds)"); setNumber("19"); saveCustomWithDpad();
            check(CardPreferences.load(getTargetContext()).globalSeconds == 19, "D-pad Save must persist the valid custom timer after measured input obstruction dismissal.");
            step = "timer controls";
            clickTag("global"); clickText("Manual only"); awaitWindow(cards);
            check(CardPreferences.load(getTargetContext()).globalSeconds == 0, "Global Manual persists.");
            awaitTag(feed.item.id);
            clickTag(feed.item.id); clickText("Every 5 seconds"); awaitWindow(cards);
            check(CardPreferences.load(getTargetContext()).timerSeconds(feed.item.id) == 5, "Per-feed preset overrides Manual.");
            clickTag(feed.item.id); clickText("Use global: Manual only"); awaitWindow(cards);
            check(!CardPreferences.load(getTargetContext()).feedSeconds.containsKey(feed.item.id), "Use global removes the override.");
            step = "Back from timers to Main";
            press(KeyEvent.KEYCODE_BACK);
            awaitFocusedTag("timers");
            step = "channel title order and series controls";
            clickTag("channels"); awaitTag(feed.item.id); clickTag(feed.item.id);
            clickTag(feed.programs.get(1).key + ":up");
            check(CardPreferences.orderedPrograms(CardPreferences.load(getTargetContext()), feed).get(0).key.equals(feed.programs.get(1).key), "Channel title move persists source-scoped order.");
            clickTag("reset-order");
            check(CardPreferences.orderedPrograms(CardPreferences.load(getTargetContext()), feed).get(0).key.equals(feed.programs.get(0).key), "Title order reset restores source order.");
            clickTag(feed.programs.get(0).key + ":item");
            if (tvSeriesSupported) {
                clickTag(feed.programs.get(1).key + ":series");
                check(CardPreferences.visiblePrograms(CardPreferences.load(getTargetContext()), feed).isEmpty(), "Settings hides the item and trusted series.");
                addedEpisode = true; fixture("add-episode");
                TvRecommendations.Feed updated = null;
                for (TvRecommendations.Feed candidate : TvRecommendations.read(getTargetContext()).feeds)
                    if (candidate.item.id.equals(feed.item.id)) updated = candidate;
                check(updated != null && updated.programs.size() == 3, "New episode must be published without replacing old records.");
                check(CardPreferences.visiblePrograms(CardPreferences.load(getTargetContext()), updated).isEmpty(), "Persisted series rule hides a newly published episode.");
                fixture("remove-added-episode"); addedEpisode = false;
            } else {
                assertItemOnly(feed.programs.get(1).key);
                check(CardPreferences.visiblePrograms(CardPreferences.load(getTargetContext()), feed).size() == 1, "Hiding one unsupported-series item preserves the other episode.");
            }
            press(KeyEvent.KEYCODE_BACK); awaitFocusedTag(feed.item.id);
            clickTag(itemOnly.item.id);
            assertItemOnly(itemOnly.programs.get(0).key);
            press(KeyEvent.KEYCODE_BACK); press(KeyEvent.KEYCODE_BACK); awaitFocusedTag("channels");
            clickTag("hidden");
            if (tvSeriesSupported) {
                clickTag("series:" + PRODUCER + ":" + feed.programs.get(0).seriesKey);
                check(!CardPreferences.load(getTargetContext()).isSeriesHidden(PRODUCER, feed.programs.get(0).seriesKey)
                        && CardPreferences.load(getTargetContext()).isHidden(PRODUCER, feed.programs.get(0).key), "Restoring series retains separately hidden episode.");
            }
            clickTag(PRODUCER + ":" + feed.programs.get(0).key);
            press(KeyEvent.KEYCODE_BACK); press(KeyEvent.KEYCODE_BACK); awaitWindow(settings);
            press(KeyEvent.KEYCODE_BACK); awaitWindow(main);
            ChannelCardView card = awaitCard(feed.item.id);
            step = "Main previous title";
            final ChannelCardView previousCard = card;
            final String[] beforePrevious = new String[1];
            onMain(new Runnable() { @Override public void run() { beforePrevious[0] = previousCard.currentProgram().key; } });
            focusCard(card); press(KeyEvent.KEYCODE_DPAD_CENTER); clickText("Previous title"); awaitWindow(main);
            final String[] afterPrevious = new String[1];
            onMain(new Runnable() { @Override public void run() { afterPrevious[0] = previousCard.currentProgram().key; } });
            check(!beforePrevious[0].equals(afterPrevious[0]), "Previous title selects another item on the same card.");
            if (tvSeriesSupported) {
                step = "Main Select Hide series and recover";
                focusCard(card); press(KeyEvent.KEYCODE_DPAD_CENTER); clickText("Hide series");
                check(CardPreferences.load(getTargetContext()).isSeriesHidden(PRODUCER, feed.programs.get(0).seriesKey), "Main Select series action persists rule.");
                awaitWindow(main); card = awaitCard(feed.item.id);
                focusCard(card); press(KeyEvent.KEYCODE_DPAD_CENTER);
                ActivityMonitor seriesMonitor = addMonitor(ROOT + "cards.CardsSettingsActivity", null, false);
                clickText("Card settings");
                cards = waitForMonitorWithTimeout(seriesMonitor, 3000); removeMonitor(seriesMonitor);
                check(cards != null, "Series-hidden card settings recovery opens.");
                awaitWindow(cards); clickTag("hidden");
                clickTag("series:" + PRODUCER + ":" + feed.programs.get(0).seriesKey);
                press(KeyEvent.KEYCODE_BACK); press(KeyEvent.KEYCODE_BACK); awaitWindow(main);
                card = awaitCard(feed.item.id);
            } else {
                step = "Main unsupported-series item-only menu";
                focusCard(card); press(KeyEvent.KEYCODE_DPAD_CENTER);
                awaitDialogText("Hide this item");
                check(node(getUiAutomation().getRootInActiveWindow(), "Hide series", false) == null, "Unsupported provider must not offer a series action.");
                check(node(getUiAutomation().getRootInActiveWindow(), "Hide this item", false) != null, "Unsupported provider retains the exact item hide action.");
                press(KeyEvent.KEYCODE_BACK); awaitWindow(main);
            }
            step = "Select hide two titles";
            for (int i = 0; i < 2; i++) {
                card = awaitCard(feed.item.id);
                final ChannelCardView selected = card;
                final TvRecommendations.Program[] program = new TvRecommendations.Program[1];
                onMain(new Runnable() { @Override public void run() { program[0] = selected.currentProgram(); } });
                check(program[0] != null, "Next visible fixture title must exist.");
                focusCard(card); press(KeyEvent.KEYCODE_DPAD_CENTER); clickText("Hide this item");
                check(CardPreferences.load(getTargetContext()).isHidden(PRODUCER, program[0].key), "Select Hide persists exact program identity.");
            }
            card = awaitCard(feed.item.id);
            step = "all-hidden card recovery";
            final ChannelCardView emptyCard = card;
            final boolean[] empty = new boolean[1];
            onMain(new Runnable() { @Override public void run() { empty[0] = emptyCard.currentProgram() == null; } });
            check(empty[0], "All-hidden channel stays present with no visible title.");
            focusCard(card); press(KeyEvent.KEYCODE_DPAD_CENTER);
            ActivityMonitor recoveryMonitor = addMonitor(ROOT + "cards.CardsSettingsActivity", null, false);
            clickText("Card settings");
            cards = waitForMonitorWithTimeout(recoveryMonitor, 3000); removeMonitor(recoveryMonitor);
            check(cards != null, "All-hidden Select menu must recover through Card settings.");
            awaitWindow(cards); step = "restore one hidden title"; clickTag("hidden");
            clickTag(PRODUCER + ":" + feed.programs.get(0).key);
            check(!CardPreferences.load(getTargetContext()).isHidden(PRODUCER, feed.programs.get(0).key)
                    && CardPreferences.load(getTargetContext()).isHidden(PRODUCER, feed.programs.get(1).key),
                    "Restore one title preserves independently hidden title.");
            press(KeyEvent.KEYCODE_BACK); press(KeyEvent.KEYCODE_BACK); awaitWindow(main);
            card = awaitCard(feed.item.id);
            final ChannelCardView restored = card;
            final boolean[] visible = new boolean[1];
            onMain(new Runnable() { @Override public void run() {
                visible[0] = restored.currentProgram() != null && restored.currentProgram().key.equals(feed.programs.get(0).key);
            } });
            check(visible[0], "Restored title visibly returns to the existing channel card.");
            result.putString("status", "PASS");
            result.putBoolean("dpad_settings_cards_route", true);
            result.putBoolean("custom_manual_override_reset", true);
            result.putBoolean("emulator_dpad_custom_save", true);
            result.putBoolean("select_hide_all_hidden_recovery_restore", true);
            result.putBoolean("channel_title_order_reset", true);
            result.putBoolean("trusted_series_future_episode_and_independent_restore", tvSeriesSupported);
            result.putBoolean("main_select_series_hide_restore", tvSeriesSupported);
            result.putBoolean("unsupported_series_item_only", true);
            result.putString("limits", "Emulator fixture only; no physical TV or production migration proof. TV series proof applies only when the measured provider schema retains explicit IDs; Plex series proof is separate.");
        } catch (Throwable failure) {
            result.putString("status", "FAIL"); result.putString("reason", failure.toString()); result.putString("step", step);
            StringBuilder visible = new StringBuilder();
            describe(getUiAutomation().getRootInActiveWindow(), visible);
            result.putString("visible_test_ui", visible.toString());
        } finally {
            if (addedEpisode) {
                try { fixture("remove-added-episode"); result.putBoolean("added_episode_removed", true); }
                catch (Throwable failure) { result.putBoolean("added_episode_removed", false); result.putString("fixture_cleanup_error", failure.toString()); }
            }
            result.putBoolean("native_cards_restored", restore(cardPrefs, cardBefore));
            result.putBoolean("native_layout_restored", restore(layoutPrefs, layoutBefore));
        }
        finish(0, result);
    }

    private void assertItemOnly(final String key) {
        final boolean[] unsupported = new boolean[1];
        onMain(new Runnable() { @Override public void run() {
            unsupported[0] = cards.getWindow().getDecorView().findViewWithTag(key + ":series") == null
                    && containsText(cards.getWindow().getDecorView(), "Series hiding is unavailable");
        } });
        check(unsupported[0], "Unsupported series identity must show item-only explanation and no series action.");
    }
    private Bundle fixture(String operation) throws Exception {
        final CountDownLatch received = new CountDownLatch(1);
        final Bundle[] response = new Bundle[1];
        getTargetContext().sendOrderedBroadcast(new Intent().setComponent(new ComponentName(PRODUCER,
                PRODUCER + ".FixtureReceiver")).putExtra("operation", operation), null, new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) { response[0] = getResultExtras(false); received.countDown(); }
        }, null, 0, null, null);
        check(received.await(10, TimeUnit.SECONDS), "Producer did not respond.");
        check(response[0] != null && "OK".equals(response[0].getString("status")), "Producer failed: " + response[0]);
        return response[0];
    }
    protected boolean containsText(View view, String text) {
        if (view instanceof android.widget.TextView && ((android.widget.TextView) view).getText().toString().contains(text)) return true;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            if (containsText(((ViewGroup) view).getChildAt(i), text)) return true;
        return false;
    }
    protected void onMain(final Runnable action) {
        final Throwable[] failure = new Throwable[1];
        super.runOnMainSync(new Runnable() {
            @Override public void run() {
                try { action.run(); } catch (Throwable error) { failure[0] = error; }
            }
        });
        if (failure[0] != null) throw new AssertionError("Main-thread test action failed", failure[0]);
    }
    protected Activity launch(String name) {
        Activity activity = startActivitySync(new Intent().setClassName(getTargetContext(), ROOT + name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        awaitWindow(activity); return activity;
    }
    protected void press(int code) { sendKeyDownUpSync(code); waitForIdleSync(); }
    protected void focus(final View view) {
        check(view != null, "Missing focus target.");
        final boolean[] state = new boolean[2];
        onMain(new Runnable() { @Override public void run() {
            state[0] = view.isAttachedToWindow() && view.hasWindowFocus();
            state[1] = state[0] && view.requestFocus();
        } });
        check(state[0], "Remote target must be attached to the foreground window.");
        check(state[1], "Target must accept D-pad focus.");
        waitForIdleSync();
    }
    protected void focusCard(final ChannelCardView card) {
        final View[] target = new View[1];
        onMain(new Runnable() { @Override public void run() {
            View view = card;
            while (view != null && !view.isFocusable()) view = view.getParent() instanceof View ? (View) view.getParent() : null;
            target[0] = view;
        } });
        focus(target[0]);
    }
    protected void clickTag(final String tag) {
        awaitWindow(cards);
        final View[] target = new View[1];
        onMain(new Runnable() { @Override public void run() { target[0] = cards.getWindow().getDecorView().findViewWithTag(tag); } });
        focus(target[0]); press(KeyEvent.KEYCODE_DPAD_CENTER);
    }
    protected void awaitFocusedTag(final String tag) {
        awaitWindow(cards);
        long end = SystemClock.uptimeMillis() + 3000;
        while (SystemClock.uptimeMillis() < end) {
            final boolean[] focused = new boolean[1];
            onMain(new Runnable() { @Override public void run() {
                View view = cards.getCurrentFocus();
                focused[0] = cards.hasWindowFocus() && view != null && view.isAttachedToWindow() && tag.equals(view.getTag());
            } });
            if (focused[0]) return;
            SystemClock.sleep(50);
        }
        throw new AssertionError("Cards must restore focused opener: " + tag);
    }
    protected void awaitTag(final String tag) {
        long end = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < end) {
            final boolean[] found = new boolean[1];
            onMain(new Runnable() { @Override public void run() { found[0] = cards.getWindow().getDecorView().findViewWithTag(tag) != null; } });
            if (found[0]) return;
            SystemClock.sleep(50);
        }
        throw new AssertionError("Feed timer did not load.");
    }
    protected void awaitWindow(final Activity activity) {
        long end = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < end) {
            final boolean[] active = new boolean[1];
            onMain(new Runnable() { @Override public void run() { active[0] = activity.hasWindowFocus(); } });
            if (active[0]) { waitForIdleSync(); return; }
            SystemClock.sleep(50);
        }
        throw new AssertionError("Expected foreground window: " + activity.getClass().getSimpleName()
                + "; persisted global timer=" + CardPreferences.load(getTargetContext()).globalSeconds);
    }
    protected ChannelCardView awaitCard(final String id) {
        awaitWindow(main);
        long end = SystemClock.uptimeMillis() + 5000;
        ChannelCardView previous = null;
        long unchangedSince = 0;
        while (SystemClock.uptimeMillis() < end) {
            final ChannelCardView[] card = new ChannelCardView[1];
            onMain(new Runnable() { @Override public void run() {
                ChannelCardView current = findCard(main.getWindow().getDecorView(), id);
                if (main.hasWindowFocus() && current != null && current.isAttachedToWindow() && current.hasWindowFocus()) card[0] = current;
            } });
            if (card[0] != previous) { previous = card[0]; unchangedSince = SystemClock.uptimeMillis(); }
            if (card[0] != null && SystemClock.uptimeMillis() - unchangedSince >= 300) return card[0];
            SystemClock.sleep(50);
        }
        throw new AssertionError("Fixture channel did not settle in the foreground Home window.");
    }
    private ChannelCardView findCard(View view, String id) {
        if (view instanceof ChannelCardView && ((ChannelCardView) view).feed().item.id.equals(id)) return (ChannelCardView) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            ChannelCardView found = findCard(((ViewGroup) view).getChildAt(i), id); if (found != null) return found;
        }
        return null;
    }
    protected Button button(View view, String title) {
        if (view instanceof Button && ((Button) view).getText().toString().equals(title)) return (Button) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            Button found = button(((ViewGroup) view).getChildAt(i), title); if (found != null) return found;
        }
        return null;
    }
    private AccessibilityNodeInfo node(AccessibilityNodeInfo root, String text, boolean editable) {
        if (root == null) return null;
        if (editable ? root.isEditable() : text.equalsIgnoreCase(root.getText() == null ? "" : root.getText().toString())) return root;
        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo found = node(root.getChild(i), text, editable); if (found != null) return found;
        }
        return null;
    }
    private void saveCustomWithDpad() {
        AccessibilityServiceInfo info = getUiAutomation().getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        getUiAutomation().setServiceInfo(info);
        boolean ime = false;
        for (AccessibilityWindowInfo window : getUiAutomation().getWindows())
            if (window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD && (window.isActive() || window.isFocused())) ime = true;
        if (ime) { press(KeyEvent.KEYCODE_BACK); awaitDialogText("Save"); }
        AccessibilityNodeInfo save = node(getUiAutomation().getRootInActiveWindow(), "Save", false);
        check(save != null && "android.widget.Button".equals(String.valueOf(save.getClassName()))
                && getTargetContext().getPackageName().equals(String.valueOf(save.getPackageName()))
                && save.isEnabled(), "D-pad Save target must be the native preview dialog button.");
        check(save.isFocused() || save.performAction(AccessibilityNodeInfo.ACTION_FOCUS), "Native Save button must accept input focus.");
        boolean focused = false; long end = SystemClock.uptimeMillis() + 2000;
        while (SystemClock.uptimeMillis() < end) {
            save = node(getUiAutomation().getRootInActiveWindow(), "Save", false);
            if (save != null && save.isFocused()) { focused = true; break; }
            SystemClock.sleep(30);
        }
        check(focused, "Native Save button must hold actual input focus before D-pad Select.");
        press(KeyEvent.KEYCODE_DPAD_CENTER); awaitWindow(cards);
    }
    private void awaitDialogText(String text) {
        long end = SystemClock.uptimeMillis() + 3000;
        while (SystemClock.uptimeMillis() < end) {
            if (node(getUiAutomation().getRootInActiveWindow(), text, false) != null) return;
            SystemClock.sleep(50);
        }
        throw new AssertionError("Expected visible dialog text: " + text);
    }
    protected void clickText(String text) {
        long end = SystemClock.uptimeMillis() + 3000;
        while (SystemClock.uptimeMillis() < end) {
            AccessibilityNodeInfo found = node(getUiAutomation().getRootInActiveWindow(), text, false);
            if (found != null) {
                AccessibilityNodeInfo list = found;
                while (list != null && !"android.widget.ListView".contentEquals(list.getClassName())) list = list.getParent();
                if (list != null) {
                    int target = -1;
                    for (int i = 0; i < list.getChildCount(); i++)
                        if (node(list.getChild(i), text, false) != null) { target = i; break; }
                    check(target >= 0, "Dialog row must be present: " + text);
                    check(list.isFocused() || list.performAction(AccessibilityNodeInfo.ACTION_FOCUS), "Dialog list must accept remote focus.");
                    for (int i = 0; i < 16; i++) press(KeyEvent.KEYCODE_DPAD_UP);
                    for (int i = 0; i < target; i++) press(KeyEvent.KEYCODE_DPAD_DOWN);
                    press(KeyEvent.KEYCODE_DPAD_CENTER);
                } else {
                    check("android.widget.Button".equals(String.valueOf(found.getClassName()))
                            && getTargetContext().getPackageName().equals(String.valueOf(found.getPackageName()))
                            && found.isEnabled() && found.isClickable(), "Target must be an enabled native dialog button in the preview package: " + text);
                    check(found.performAction(AccessibilityNodeInfo.ACTION_CLICK), "Native dialog button must invoke its click handler: " + text);
                    waitForIdleSync();
                }
                return;
            }
            SystemClock.sleep(50);
        }
        throw new AssertionError("Missing dialog action: " + text);
    }
    private void describe(AccessibilityNodeInfo node, StringBuilder output) {
        if (node == null || output.length() >= 2000) return;
        CharSequence text = node.getText();
        if (text != null && text.length() > 0) {
            output.append(node.getClassName()).append(": ")
                    .append(text.subSequence(0, Math.min(160, text.length()))).append("; ");
        }
        for (int i = 0; i < node.getChildCount() && output.length() < 2000; i++) describe(node.getChild(i), output);
    }
    protected void setNumber(String text) {
        AccessibilityNodeInfo field = node(getUiAutomation().getRootInActiveWindow(), "", true);
        check(field != null, "Custom timer field must be visible.");
        Bundle args = new Bundle(); args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        check(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args), "Custom field must accept input."); waitForIdleSync();
    }
    @SuppressWarnings("unchecked") protected boolean restore(SharedPreferences prefs, Map<String, ?> before) {
        SharedPreferences.Editor editor = prefs.edit().clear();
        for (Map.Entry<String, ?> entry : before.entrySet()) {
            Object value = entry.getValue(); String key = entry.getKey();
            if (value instanceof Integer) editor.putInt(key, (Integer) value);
            else if (value instanceof String) editor.putString(key, (String) value);
            else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Long) editor.putLong(key, (Long) value);
            else if (value instanceof Float) editor.putFloat(key, (Float) value);
            else if (value instanceof Set) editor.putStringSet(key, (Set<String>) value);
        }
        return editor.commit() && before.equals(prefs.getAll());
    }
    protected static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
