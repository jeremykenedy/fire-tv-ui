package com.jeremykenedy.firetv.ui.nativeapp.widget.worldclock;

import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider;
import java.util.Arrays;
import java.util.Set;

/** Emulator-only fixture. Run with an exclusive device lease; restores the owned preference key. */
public final class WorldClockInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }

    @SuppressWarnings("unchecked")
    @Override public void onStart() {
        Bundle result = new Bundle();
        final Context context = getTargetContext();
        SharedPreferences prefs = context.getSharedPreferences(WorldClockPreferences.FILE, Context.MODE_PRIVATE);
        Object original = prefs.getAll().get(WorldClockPreferences.KEY);
        final WorldClockWidgetProvider provider = new WorldClockWidgetProvider();
        final WorldClockView[] view = new WorldClockView[1];
        try {
            WorldClockPreferences.save(context, WorldClocks.empty());
            check(provider.state(context) == WidgetProvider.State.EMPTY, "Fresh list is explicitly empty");
            runOnMainSync(new Runnable() {
                @Override public void run() {
                    view[0] = (WorldClockView) provider.createView(context);
                    check(text(view[0]).contains("Choose time zones"), "Empty state is visible");
                }
            });
            WorldClockPreferences.save(context, new WorldClocks(Arrays.asList(
                    new WorldClocks.Entry("America/Los_Angeles", "West coast"),
                    new WorldClocks.Entry("Asia/Tokyo", "Tokyo"))));
            check(provider.state(context) == WidgetProvider.State.READY, "Configured list is ready");
            runOnMainSync(new Runnable() {
                @Override public void run() {
                    provider.onStart(view[0]);
                    provider.onStart(view[0]);
                    check(view[0].isRefreshing(), "Repeated start leaves one active refresh lifecycle");
                    String rendered = text(view[0]);
                    check(rendered.contains("West coast") && rendered.contains("Asia/Tokyo"), "Real configured rows render");
                    provider.onStop(view[0]);
                    provider.onStop(view[0]);
                    check(!view[0].isRefreshing(), "Repeated stop unregisters time callbacks");
                }
            });
            WorldClocks saved = WorldClockPreferences.read(context);
            WorldClockPreferences.save(context, saved.move(1, 0).replace(0, new WorldClocks.Entry("Asia/Tokyo", "Japan")));
            runOnMainSync(new Runnable() {
                @Override public void run() {
                    provider.onStart(view[0]);
                    check(text(view[0].getChildAt(0)).startsWith("Japan"),
                            "Resume reloads label and saved row order");
                    provider.onStop(view[0]);
                }
            });
            check(prefs.edit().putString(WorldClockPreferences.KEY, "v1\nNo/SuchZone\tx").commit(), "Corrupt fixture saved");
            check(provider.state(context) == WidgetProvider.State.UNAVAILABLE, "Bad saved zone does not become GMT");
            result.putString("result", "PASS: empty/ready/unavailable, saved order and labels, real rows, idempotent refresh start/stop");
        } catch (Throwable error) {
            result.putString("failure", error.toString());
        } finally {
            runOnMainSync(new Runnable() {
                @Override public void run() { if (view[0] != null) provider.onStop(view[0]); }
            });
            SharedPreferences.Editor restore = prefs.edit();
            String key = WorldClockPreferences.KEY;
            if (original == null) restore.remove(key);
            else if (original instanceof String) restore.putString(key, (String) original);
            else if (original instanceof Integer) restore.putInt(key, (Integer) original);
            else if (original instanceof Boolean) restore.putBoolean(key, (Boolean) original);
            else if (original instanceof Long) restore.putLong(key, (Long) original);
            else if (original instanceof Float) restore.putFloat(key, (Float) original);
            else if (original instanceof Set) restore.putStringSet(key, (Set<String>) original);
            if (!restore.commit()) result.putString("failure", "Clock preference restoration failed");
            Object after = prefs.getAll().get(key);
            if (original == null ? after != null : !original.equals(after))
                result.putString("failure", "Clock preference restoration did not match original");
            else result.putString("restoration", "PASS: original clock preference key restored and read back");
        }
        finish(result.containsKey("failure") ? 1 : 0, result);
    }

    private static String text(View view) {
        StringBuilder text = new StringBuilder();
        if (view instanceof TextView) text.append(((TextView) view).getText());
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) text.append(text(group.getChildAt(i))).append('\n');
        }
        return text.toString();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
