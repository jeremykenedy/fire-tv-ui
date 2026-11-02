package com.jeremykenedy.firetv.ui.nativeapp.help;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;

/** Native navigation checks. Does not modify app preferences or install fixtures. */
public final class HelpInstrumentation extends Instrumentation {
    private static final String ROOT = "com.jeremykenedy.firetv.ui.nativeapp.";
    private Activity main;
    private Activity help;

    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            main = startActivitySync(new Intent().setClassName(getTargetContext(), ROOT + "MainActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            waitForIdleSync();
            ActivityMonitor helpMonitor = addMonitor(ROOT + "HelpActivity", null, false);
            onMain(new Runnable() {
                @Override public void run() {
                    Button opener = button(main.getWindow().getDecorView(), "Help");
                    check(opener != null, "Main must expose Help.");
                    opener.requestFocus();
                }
            });
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            help = waitForMonitorWithTimeout(helpMonitor, 3000);
            removeMonitor(helpMonitor);
            check(help != null, "Main Help route must open.");
            settle();
            check(tag(help.getCurrentFocus()).equals("help:topic:start"), "Help needs initial topic focus.");
            verifyRoute("apps");
            verifyRoute("focus");
            verifyRoute("layout");
            verifyRoute("cards");
            choose("licenses");
            for (int i = 0; i < 7; i++) sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
            settle();
            final int[] position = {0};
            onMain(new Runnable() {
                @Override public void run() {
                    position[0] = scroll().getScrollY();
                    check(position[0] > 0, "Long credits must scroll through D-pad navigation.");
                }
            });
            final String focused = tag(help.getCurrentFocus());
            ActivityMonitor recreation = addMonitor(ROOT + "HelpActivity", null, false);
            onMain(new Runnable() { @Override public void run() { help.recreate(); } });
            Activity recreated = waitForMonitorWithTimeout(recreation, 3000);
            removeMonitor(recreation);
            check(recreated != null, "Help must recreate.");
            help = recreated;
            settle();
            check(focused.equals(tag(help.getCurrentFocus())), "Recreation must preserve content focus.");
            onMain(new Runnable() {
                @Override public void run() { check(scroll().getScrollY() == position[0], "Recreation must preserve scroll position."); }
            });
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT);
            settle();
            check(tag(help.getCurrentFocus()).equals("help:topic:licenses"), "Left must return to the selected topic.");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            settle();
            check(help.isFinishing(), "Back must close Help.");
            check(main.getCurrentFocus() instanceof Button
                    && "Help".contentEquals(((Button) main.getCurrentFocus()).getText()), "Main Help opener must regain focus.");
            result.putString("status", "PASS");
            result.putBoolean("all_four_settings_routes_and_back", true);
            result.putBoolean("topic_focus_scroll_restoration", true);
            result.putBoolean("credits_dpad_scroll_recreation", true);
            result.putBoolean("main_help_return", true);
            result.putString("limits", "Emulator navigation only; no physical remote performance, external document provider, or production parity claim.");
        } catch (Throwable failure) {
            result.putString("status", "FAIL");
            result.putString("reason", failure.toString());
        } finally {
            if (help != null && !help.isFinishing()) onMain(new Runnable() { @Override public void run() { help.finish(); } });
        }
        finish(0, result);
    }

    private void verifyRoute(String key) {
        choose(key);
        HelpTopics.Topic topic = HelpTopics.at(HelpTopics.indexOf(key));
        final View link = help.getWindow().getDecorView().findViewWithTag("help:settings");
        check(link != null, "Implemented settings link missing for " + key);
        onMain(new Runnable() { @Override public void run() { link.requestFocus(); } });
        settle();
        final int before = scroll().getScrollY();
        ActivityMonitor destination = addMonitor(topic.settingsClass, null, false);
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
        Activity opened = waitForMonitorWithTimeout(destination, 3000);
        removeMonitor(destination);
        check(opened != null, "Help must open actual destination for " + key);
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        settle();
        check(tag(help.getCurrentFocus()).equals("help:settings"), "Back must restore shortcut focus for " + key);
        check(scroll().getScrollY() == before, "Back must preserve Help scroll for " + key);
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT);
        settle();
        check(tag(help.getCurrentFocus()).equals("help:topic:" + key), "Back must retain selected topic for " + key);
    }

    private void choose(final String key) {
        onMain(new Runnable() {
            @Override public void run() {
                View topic = help.getWindow().getDecorView().findViewWithTag("help:topic:" + key);
                check(topic != null, "Missing topic " + key);
                topic.requestFocus();
            }
        });
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
        settle();
    }

    private ScrollView scroll() { return (ScrollView) help.getWindow().getDecorView().findViewWithTag("help:scroll"); }
    private static String tag(View view) { return view == null ? "" : String.valueOf(view.getTag()); }
    private void settle() { waitForIdleSync(); SystemClock.sleep(120); waitForIdleSync(); }
    private void onMain(final Runnable action) {
        final Throwable[] failure = {null};
        runOnMainSync(new Runnable() {
            @Override public void run() { try { action.run(); } catch (Throwable error) { failure[0] = error; } }
        });
        if (failure[0] != null) throw new AssertionError(failure[0]);
    }
    private Button button(View view, String label) {
        if (view instanceof Button && label.contentEquals(((Button) view).getText())) return (Button) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Button found = button(group.getChildAt(i), label);
                if (found != null) return found;
            }
        }
        return null;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
