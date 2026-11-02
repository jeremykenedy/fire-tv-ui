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
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.providers.TvRecommendations;
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
            final TvRecommendations.Result recommendations = TvRecommendations.read(getTargetContext());
            waitForProviders();
            clickTag("layout:widget-settings");
            onMain(new Runnable() {
                @Override public void run() {
                    View decor = editor.getWindow().getDecorView();
                    for (WidgetProvider widget : HomeSlots.widgets()) {
                        check(decor.findViewWithTag(widget.item().key() + ":configure") != null,
                                "Widget settings must configure each implemented widget.");
                    }
                    check(decor.findViewWithTag("channel:emulator-proof-unavailable:visibility") == null,
                            "Widget settings must omit channel controls.");
                    check(!decor.findViewWithTag("layout:provider-action").isShown(),
                            "Widget settings must not mix in TV listings access controls.");
                }
            });
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            waitForIdleSync();
            onMain(new Runnable() {
                @Override public void run() {
                    View decor = editor.getWindow().getDecorView();
                    check(decor.findViewWithTag("channel:emulator-proof-unavailable:visibility") != null,
                            "Back must return to the shared editor.");
                    check(decor.findViewWithTag("layout:widget-settings").isFocused(),
                            "Back must restore Widget settings entry focus.");
                }
            });
            final LayoutOptions beforeMode = LayoutPreferences.load(getTargetContext(), HomeSlots.defaults());
            final LayoutOptions discoveredMode = HomeSlots.discover(beforeMode, recommendations.feeds);
            clickTag("layout:mode:CARDS_ONLY");
            LayoutOptions cardsMode = LayoutPreferences.load(getTargetContext(), HomeSlots.defaults());
            check(cardsMode.mode == LayoutOptions.Mode.CARDS_ONLY && cardsMode.order.equals(discoveredMode.order)
                    && cardsMode.hidden.equals(discoveredMode.hidden), "Cards-only mode keeps exact saved order and visibility.");
            clickTag("layout:mode:WIDGETS_ONLY");
            check(LayoutPreferences.load(getTargetContext(), HomeSlots.defaults()).mode == LayoutOptions.Mode.WIDGETS_ONLY,
                    "Widgets-only mode persists from the native editor.");
            clickTag("layout:mode:NONE");
            LayoutOptions hiddenMode = LayoutPreferences.load(getTargetContext(), HomeSlots.defaults());
            check(hiddenMode.mode == LayoutOptions.Mode.NONE && hiddenMode.order.equals(discoveredMode.order)
                    && hiddenMode.hidden.equals(discoveredMode.hidden), "Hide cards and widgets preserves each saved choice.");
            check(hiddenMode.visible(HomeSlots.available(recommendations.feeds)).isEmpty(),
                    "Hide cards and widgets excludes both content kinds.");
            clickTag("layout:mode:BOTH");
            onMain(new Runnable() {
                @Override public void run() {
                    ViewGroup decor = (ViewGroup) editor.getWindow().getDecorView();
                    TextView status = (TextView) decor.findViewWithTag("layout:provider-status");
                    check(status != null && recommendations.message().contentEquals(status.getText()),
                            "Editor distinguishes available, empty, permission and unavailable provider states.");
                    for (TvRecommendations.Feed feed : recommendations.feeds) {
                        check(decor.findViewWithTag(feed.item.key() + ":visibility") != null,
                                "Published feed must appear as one manageable stable slot.");
                    }
                    check(decor.findViewWithTag("channel:emulator-proof-unavailable:visibility") != null,
                            "Missing saved provider must remain manageable.");
                }
            });
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
            result.putString("provider_status", recommendations.status.name());
            result.putInt("published_feed_slots", recommendations.feeds.size());
            result.putBoolean("editor_provider_state", true);
            result.putBoolean("content_mode_preserves_choices", true);
            result.putBoolean("widget_settings_and_back_focus", true);
            result.putString("limits", "Feed assertions reflect the target's currently published data. Runtime permission dialog and physical card sizing require separate native proof.");
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

    private void waitForProviders() {
        long deadline = SystemClock.uptimeMillis() + 5000;
        final boolean[] ready = new boolean[1];
        while (SystemClock.uptimeMillis() < deadline) {
            onMain(new Runnable() {
                @Override public void run() {
                    View action = editor.getWindow().getDecorView().findViewWithTag("layout:provider-action");
                    ready[0] = action != null && action.isEnabled();
                }
            });
            if (ready[0]) return;
            SystemClock.sleep(25);
        }
        throw new AssertionError("Provider discovery did not finish.");
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
