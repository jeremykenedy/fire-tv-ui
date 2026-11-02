package com.jeremykenedy.firetv.ui.nativeapp.cards;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import com.jeremykenedy.firetv.ui.nativeapp.providers.TvRecommendations;
import com.jeremykenedy.firetv.ui.nativeapp.layout.HomeSlots;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutPreferences;
import java.util.Map;
import java.util.Set;

public final class CardControlsInstrumentation extends Instrumentation {
    private static final String ROOT = "com.jeremykenedy.firetv.ui.nativeapp.";
    private static final String PRODUCER = "com.jeremykenedy.firetv.ui.providerfixture";
    private Activity main;
    private Activity cards;
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
        try {
            TvRecommendations.Feed fixture = null;
            for (TvRecommendations.Feed feed : TvRecommendations.read(getTargetContext()).feeds)
                if (PRODUCER.equals(feed.source) && feed.programs.size() == 2) fixture = feed;
            check(fixture != null, "Publish the two-title provider fixture and grant TV listings access before this suite.");
            final TvRecommendations.Feed feed = fixture;
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
            press(KeyEvent.KEYCODE_BACK); awaitWindow(settings);
            press(KeyEvent.KEYCODE_BACK); awaitWindow(main);
            ChannelCardView card = awaitCard(feed.item.id);
            step = "Select hide two titles";
            for (int i = 0; i < 2; i++) {
                card = awaitCard(feed.item.id);
                final ChannelCardView selected = card;
                final TvRecommendations.Program[] program = new TvRecommendations.Program[1];
                onMain(new Runnable() { @Override public void run() { program[0] = selected.currentProgram(); } });
                check(program[0] != null, "Next visible fixture title must exist.");
                focus(card); press(KeyEvent.KEYCODE_DPAD_CENTER); clickText("Hide this item");
                check(CardPreferences.load(getTargetContext()).isHidden(PRODUCER, program[0].key), "Select Hide persists exact program identity.");
            }
            card = awaitCard(feed.item.id);
            step = "all-hidden card recovery";
            final ChannelCardView emptyCard = card;
            final boolean[] empty = new boolean[1];
            onMain(new Runnable() { @Override public void run() { empty[0] = emptyCard.currentProgram() == null; } });
            check(empty[0], "All-hidden channel stays present with no visible title.");
            focus(card); press(KeyEvent.KEYCODE_DPAD_CENTER);
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
            result.putBoolean("select_hide_all_hidden_recovery_restore", true);
            result.putString("limits", "Emulator fixture only; no physical TV, production migration or series identity proof.");
        } catch (Throwable failure) {
            result.putString("status", "FAIL"); result.putString("reason", failure.toString()); result.putString("step", step);
            StringBuilder visible = new StringBuilder();
            describe(getUiAutomation().getRootInActiveWindow(), visible);
            result.putString("visible_test_ui", visible.toString());
        } finally {
            result.putBoolean("native_cards_restored", restore(cardPrefs, cardBefore));
            result.putBoolean("native_layout_restored", restore(layoutPrefs, layoutBefore));
        }
        finish(0, result);
    }

    private void onMain(final Runnable action) {
        final Throwable[] failure = new Throwable[1];
        super.runOnMainSync(new Runnable() {
            @Override public void run() {
                try { action.run(); } catch (Throwable error) { failure[0] = error; }
            }
        });
        if (failure[0] != null) throw new AssertionError("Main-thread test action failed", failure[0]);
    }
    private Activity launch(String name) {
        Activity activity = startActivitySync(new Intent().setClassName(getTargetContext(), ROOT + name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        awaitWindow(activity); return activity;
    }
    private void press(int code) { sendKeyDownUpSync(code); waitForIdleSync(); }
    private void focus(final View view) {
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
    private void clickTag(final String tag) {
        awaitWindow(cards);
        final View[] target = new View[1];
        onMain(new Runnable() { @Override public void run() { target[0] = cards.getWindow().getDecorView().findViewWithTag(tag); } });
        focus(target[0]); press(KeyEvent.KEYCODE_DPAD_CENTER);
    }
    private void awaitFocusedTag(final String tag) {
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
    private void awaitTag(final String tag) {
        long end = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < end) {
            final boolean[] found = new boolean[1];
            onMain(new Runnable() { @Override public void run() { found[0] = cards.getWindow().getDecorView().findViewWithTag(tag) != null; } });
            if (found[0]) return;
            SystemClock.sleep(50);
        }
        throw new AssertionError("Feed timer did not load.");
    }
    private void awaitWindow(final Activity activity) {
        long end = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < end) {
            final boolean[] active = new boolean[1];
            onMain(new Runnable() { @Override public void run() { active[0] = activity.hasWindowFocus(); } });
            if (active[0]) { waitForIdleSync(); return; }
            SystemClock.sleep(50);
        }
        throw new AssertionError("Expected foreground window: " + activity.getClass().getSimpleName());
    }
    private ChannelCardView awaitCard(final String id) {
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
    private Button button(View view, String title) {
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
    private void clickText(String text) {
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
                    while (found != null && !found.isFocusable()) found = found.getParent();
                    check(found != null && (found.isFocused() || found.performAction(AccessibilityNodeInfo.ACTION_FOCUS)), "Dialog button must accept remote focus: " + text);
                    press(KeyEvent.KEYCODE_DPAD_CENTER);
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
    private void setNumber(String text) {
        AccessibilityNodeInfo field = node(getUiAutomation().getRootInActiveWindow(), "", true);
        check(field != null, "Custom timer field must be visible.");
        Bundle args = new Bundle(); args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        check(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args), "Custom field must accept input."); waitForIdleSync();
    }
    @SuppressWarnings("unchecked") private boolean restore(SharedPreferences prefs, Map<String, ?> before) {
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
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
