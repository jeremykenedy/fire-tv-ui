package com.jeremykenedy.firetv.ui.nativeapp.layout;

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
import android.widget.Button;
import android.widget.TextClock;
import com.jeremykenedy.firetv.ui.nativeapp.widget.ClockWidgetView;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

public final class LayoutInstrumentation extends Instrumentation {
    private Activity main;
    private Activity editor;
    private static final String ROOT = "com.jeremykenedy.firetv.ui.nativeapp.";

    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        SharedPreferences prefs = getTargetContext().getSharedPreferences("native_layout", Context.MODE_PRIVATE);
        Map<String, ?> before = prefs.getAll();
        try {
            LayoutPreferences.save(getTargetContext(), new LayoutOptions(320, 180,
                    Arrays.asList("channel:emulator-proof-unavailable", HomeSlots.CLOCK.key()), Collections.<String>emptyList()));
            main = startActivitySync(new Intent().setClassName(getTargetContext(), ROOT + "MainActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            waitForIdleSync();
            check(count(main.getWindow().getDecorView(), ClockWidgetView.class) == 1, "Real clock must be on Home.");
            editor = openEditor();
            onMain(new Runnable() {
                @Override public void run() {
                    View move = editor.getWindow().getDecorView().findViewWithTag(HomeSlots.CLOCK.key() + ":up");
                    check(move != null && move.isEnabled(), "Clock must be reorderable among saved unavailable sources.");
                    move.performClick();
                }
            });
            check(LayoutPreferences.load(getTargetContext(), HomeSlots.defaults()).order.get(0).equals(HomeSlots.CLOCK.key()),
                    "Moving clock persists mixed saved-slot order.");
            clickTag(HomeSlots.CLOCK.key() + ":visibility");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            waitForIdleSync();
            check(count(main.getWindow().getDecorView(), ClockWidgetView.class) == 0, "Hiding clock removes it from Home on return.");
            editor = openEditor();
            clickTag(HomeSlots.CLOCK.key() + ":visibility");
            onMain(new Runnable() {
                @Override public void run() {
                    Button large = button(editor.getWindow().getDecorView(), "Large");
                    check(large != null, "Size preset must be available.");
                    large.performClick();
                }
            });
            LayoutOptions saved = LayoutPreferences.load(getTargetContext(), HomeSlots.defaults());
            check(saved.cardWidthDp == 400 && saved.cardHeightDp == 225, "Global card dimensions must persist.");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            waitForIdleSync();
            check(count(main.getWindow().getDecorView(), ClockWidgetView.class) == 1, "Showing clock restores it on Home.");
            onMain(new Runnable() {
                @Override public void run() {
                    TextClock clock = firstClock(main.getWindow().getDecorView());
                    check(clock != null && clock.getText().length() > 0, "Clock must render actual platform time.");
                }
            });
            result.putString("status", "PASS");
            result.putBoolean("mixed_saved_slot_reorder", true);
            result.putBoolean("hide_show_clock_on_home", true);
            result.putBoolean("native_size_persistence", true);
            result.putBoolean("real_text_clock", true);
            result.putString("limits", "Unavailable channel is test state only; no real channel provider or card sizing visuals claimed.");
        } catch (Throwable error) {
            result.putString("status", "FAIL");
            result.putString("reason", error.toString());
        } finally {
            SharedPreferences.Editor restore = prefs.edit().clear();
            for (Map.Entry<String, ?> entry : before.entrySet()) {
                if (entry.getValue() instanceof Integer) restore.putInt(entry.getKey(), (Integer) entry.getValue());
                else if (entry.getValue() instanceof String) restore.putString(entry.getKey(), (String) entry.getValue());
            }
            result.putBoolean("review_layout_restored", restore.commit());
        }
        finish(0, result);
    }

    private Activity openEditor() {
        ActivityMonitor monitor = addMonitor(ROOT + "layout.LayoutSettingsActivity", null, false);
        onMain(new Runnable() {
            @Override public void run() {
                main.startActivity(new Intent().setClassName(getTargetContext(), ROOT + "layout.LayoutSettingsActivity"));
            }
        });
        Activity opened = waitForMonitorWithTimeout(monitor, 3000);
        removeMonitor(monitor);
        check(opened != null, "Editor must open.");
        waitForIdleSync();
        return opened;
    }

    private void clickTag(final String tag) {
        onMain(new Runnable() {
            @Override public void run() {
                View view = editor.getWindow().getDecorView().findViewWithTag(tag);
                check(view != null, "Missing editor action.");
                view.performClick();
            }
        });
    }

    private void onMain(final Runnable action) {
        final Throwable[] failure = new Throwable[1];
        runOnMainSync(new Runnable() {
            @Override public void run() {
                try { action.run(); } catch (Throwable error) { failure[0] = error; }
            }
        });
        if (failure[0] != null) throw new AssertionError(failure[0]);
    }

    private int count(View view, Class<?> type) {
        int result = type.isInstance(view) ? 1 : 0;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) result += count(group.getChildAt(i), type);
        }
        return result;
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

    private TextClock firstClock(View view) {
        if (view instanceof TextClock) return (TextClock) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextClock found = firstClock(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
