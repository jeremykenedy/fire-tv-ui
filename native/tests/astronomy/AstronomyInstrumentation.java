package com.jeremykenedy.firetv.ui.nativeapp.widget.astronomy;

import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider;
import java.util.Set;

/** Emulator-only fixture. Run with an exclusive device lease; restores the owned preference key. */
public final class AstronomyInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }

    @SuppressWarnings("unchecked")
    @Override public void onStart() {
        Bundle result = new Bundle();
        final Context context = getTargetContext();
        SharedPreferences prefs = context.getSharedPreferences(AstronomyPreferences.FILE, Context.MODE_PRIVATE);
        Object original = prefs.getAll().get(AstronomyPreferences.KEY);
        final AstronomyWidgetProvider provider = new AstronomyWidgetProvider();
        final AstronomyView[] view = new AstronomyView[1];
        try {
            AstronomyPreferences.save(context, null);
            check(provider.state(context) == WidgetProvider.State.SETUP_REQUIRED, "Fresh location explicitly needs setup");
            runOnMainSync(new Runnable() {
                @Override public void run() {
                    view[0] = (AstronomyView) provider.createView(context);
                    check(text(view[0]).contains("Set a location"), "Empty state is visible");
                }
            });
            AstronomyPreferences.save(context, new AstronomyLocation(51.4779, 0, "Europe/London", "Greenwich"));
            check(provider.state(context) == WidgetProvider.State.READY, "Configured list is ready");
            runOnMainSync(new Runnable() {
                @Override public void run() {
                    provider.onStart(view[0]);
                    provider.onStart(view[0]);
                    check(view[0].isRefreshing(), "Repeated start leaves one active refresh lifecycle");
                    String rendered = text(view[0]);
                    check(rendered.contains("Greenwich") && rendered.contains("Sunrise") && rendered.contains("illuminated"), "Real configured rows render");
                    provider.onStop(view[0]);
                    provider.onStop(view[0]);
                    check(!view[0].isRefreshing(), "Repeated stop unregisters time callbacks");
                }
            });
            AstronomyPreferences.save(context, new AstronomyLocation(-33.8688, 151.2093, "Australia/Sydney", "Sydney"));
            runOnMainSync(new Runnable() {
                @Override public void run() {
                    provider.onStart(view[0]);
                    check(text(view[0]).contains("Sydney") && text(view[0]).contains("Australia/Sydney"),
                            "Resume reloads saved location and timezone");
                    provider.onStop(view[0]);
                }
            });
            check(prefs.edit().putString(AstronomyPreferences.KEY, "v1\t0\t0\tNo/SuchZone\tx").commit(), "Corrupt fixture saved");
            check(provider.state(context) == WidgetProvider.State.UNAVAILABLE, "Invalid saved zone is unavailable");
            result.putString("result", "PASS: setup/ready/unavailable, saved location reload, real solar/lunar rows, idempotent refresh start/stop");
        } catch (Throwable error) {
            result.putString("failure", error.toString());
        } finally {
            runOnMainSync(new Runnable() {
                @Override public void run() { if (view[0] != null) provider.onStop(view[0]); }
            });
            SharedPreferences.Editor restore = prefs.edit();
            String key = AstronomyPreferences.KEY;
            if (original == null) restore.remove(key);
            else if (original instanceof String) restore.putString(key, (String) original);
            else if (original instanceof Integer) restore.putInt(key, (Integer) original);
            else if (original instanceof Boolean) restore.putBoolean(key, (Boolean) original);
            else if (original instanceof Long) restore.putLong(key, (Long) original);
            else if (original instanceof Float) restore.putFloat(key, (Float) original);
            else if (original instanceof Set) restore.putStringSet(key, (Set<String>) original);
            if (!restore.commit()) result.putString("failure", "Astronomy preference restoration failed");
            Object after = prefs.getAll().get(key);
            if (original == null ? after != null : !original.equals(after))
                result.putString("failure", "Astronomy preference restoration did not match original");
            else result.putString("restoration", "PASS: original astronomy preference key restored and read back");
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
