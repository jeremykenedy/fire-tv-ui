package com.jeremykenedy.firetv.ui.nativeapp.widget.uv;

import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider;
import java.util.Set;

/** Configuration/lifecycle proof only. This harness deliberately makes no external data request. */
public final class UvInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @SuppressWarnings("unchecked")
    @Override public void onStart() {
        Bundle result = new Bundle();
        final Context context = getTargetContext();
        SharedPreferences prefs = context.getSharedPreferences(UvPreferences.FILE, Context.MODE_PRIVATE);
        Object original = prefs.getAll().get(UvPreferences.KEY);
        final UvWidgetProvider provider = new UvWidgetProvider();
        final UvView[] view = new UvView[1];
        try {
            UvPreferences.save(context, null);
            check(provider.state(context) == WidgetProvider.State.SETUP_REQUIRED, "No ZIP needs explicit setup");
            check(!provider.defaultVisible() && provider.item().key().equals("widget:uv-forecast"), "Stable opt-in identity");
            runOnMainSync(new Runnable() {
                @Override public void run() {
                    view[0] = (UvView) provider.createView(context);
                    provider.onStart(view[0]); provider.onStart(view[0]);
                    check(text(view[0]).contains("Choose a ZIP"), "Visible setup state");
                    provider.onStop(view[0]); provider.onStop(view[0]);
                    check(!view[0].isRefreshing(), "Repeat stop removes refresh work");
                }
            });
            UvPreferences.save(context, "00501");
            check(UvPreferences.read(context).equals("00501"), "Leading-zero ZIP survives persistence");
            check(prefs.edit().putString(UvPreferences.KEY, "invalid").commit(), "Invalid preference fixture installed");
            check(provider.state(context) == WidgetProvider.State.UNAVAILABLE, "Invalid ZIP is unavailable");
            result.putString("result", "PASS: explicit ZIP setup, stable hidden identity, saved ZIP validation and repeated lifecycle");
        } catch (Throwable error) { result.putString("failure", error.toString()); }
        finally {
            runOnMainSync(new Runnable() { @Override public void run() { if (view[0] != null) provider.onStop(view[0]); } });
            SharedPreferences.Editor restore = prefs.edit(); String key = UvPreferences.KEY;
            if (original == null) restore.remove(key);
            else if (original instanceof String) restore.putString(key, (String) original);
            else if (original instanceof Integer) restore.putInt(key, (Integer) original);
            else if (original instanceof Boolean) restore.putBoolean(key, (Boolean) original);
            else if (original instanceof Long) restore.putLong(key, (Long) original);
            else if (original instanceof Float) restore.putFloat(key, (Float) original);
            else if (original instanceof Set) restore.putStringSet(key, (Set<String>) original);
            if (!restore.commit()) result.putString("failure", "UV preference restoration failed");
            Object after = prefs.getAll().get(key);
            if (original == null ? after != null : !original.equals(after)) result.putString("failure", "UV preference restoration mismatch");
            else result.putString("restoration", "PASS: original ZIP key restored and read back; no external data request made");
        }
        finish(result.containsKey("failure") ? 1 : 0, result);
    }
    private static String text(View view) {
        StringBuilder out = new StringBuilder();
        if (view instanceof TextView) out.append(((TextView) view).getText());
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) out.append(text(group.getChildAt(i))).append('\n');
        }
        return out.toString();
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
