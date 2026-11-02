package com.jeremykenedy.firetv.ui.nativeapp.inputs;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Button;
import java.util.HashMap;
import java.util.Map;

/** Native fixture/name/reset checks; never tunes a real port or alters hardware settings. */
public final class InputInstrumentation extends Instrumentation {
    private static final String ID = "com.jeremykenedy.firetv.ui.inputfixture/.FixtureInputService";
    private static final String REMOVED = "com.jeremykenedy.firetv.ui.inputfixture/.RemovedInput";
    private Activity activity;
    private boolean cleanup;
    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        cleanup = args != null && "cleanup".equals(args.getString("operation"));
        start();
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        SharedPreferences prefs = getTargetContext().getSharedPreferences(InputPreferences.FILE, Context.MODE_PRIVATE);
        if (cleanup) {
            SharedPreferences.Editor edit = prefs.edit();
            for (String id : new String[]{ID, REMOVED})
                for (String prefix : new String[]{"known:", "factory:", "name:"}) edit.remove(prefix + id);
            boolean cleaned = edit.commit();
            for (String id : new String[]{ID, REMOVED})
                for (String prefix : new String[]{"known:", "factory:", "name:"}) cleaned &= !prefs.contains(prefix + id);
            result.putString("status", cleaned ? "PASS" : "FAIL");
            result.putBoolean("fixture_input_metadata_removed", cleaned);
            finish(0, result);
            return;
        }
        Map<String, ?> before = new HashMap<>(prefs.getAll());
        try {
            Context target = getTargetContext();
            InputRepository.Snapshot snapshot = InputRepository.load(target);
            InputEntry discovered = find(snapshot, ID);
            check(discovered != null && discovered.factoryName.equals("Fixture tuner"), "Install isolated input fixture before running.");
            InputPreferences names = new InputPreferences(target);
            names.remember(REMOVED, "Old HDMI input");
            names.rename(REMOVED, "Retained name");
            InputEntry removed = find(InputRepository.load(target), REMOVED);
            check(removed != null && removed.state == InputEntry.State.MISSING && removed.displayName.equals("Retained name"), "Removed input and name must remain visible.");
            check(InputActions.resolve(target, removed) == null, "Missing input cannot be dispatched.");
            Context denied = new ContextWrapper(target) {
                @Override public Object getSystemService(String name) {
                    if (Context.TV_INPUT_SERVICE.equals(name)) throw new SecurityException("Fixture denial");
                    return super.getSystemService(name);
                }
            };
            InputRepository.Snapshot unavailable = InputRepository.load(denied);
            check(unavailable.capability == InputRepository.Capability.UNAVAILABLE, "Discovery denial must be explicit.");
            check(find(unavailable, REMOVED).state == InputEntry.State.UNAVAILABLE, "Denial must not pretend inputs were removed.");
            Intent route = InputActions.resolve(target, discovered);
            check(route == null || !ID.substring(0, ID.indexOf('/')).equals(route.getComponent().getPackageName()), "A third-party URI handler cannot intercept input launch.");
            activity = startActivitySync(new Intent().setClassName(target,
                    "com.jeremykenedy.firetv.ui.nativeapp.inputs.InputsSettingsActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            awaitTag(discovered.artworkKey());
            onMain(new Runnable() {
                @Override public void run() {
                View row = activity.getWindow().getDecorView().findViewWithTag("input:" + ID);
                row.requestFocus();
                }
            });
            final AlertDialog[] dialog = {null};
            onMain(new Runnable() { @Override public void run() { dialog[0] = InputViews.rename(activity, discovered, new Runnable() {
                @Override public void run() { ((InputsSettingsActivity) activity).refresh(); }
            }); } });
            settle();
            onMain(new Runnable() {
                @Override public void run() {
                EditText field = findType(dialog[0].getWindow().getDecorView(), EditText.class);
                check(field != null, "Rename must expose a native text field.");
                field.setText("Living room console");
                dialog[0].getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                }
            });
            awaitLabel(discovered.artworkKey(), "Living room console");
            InputEntry renamed = find(InputRepository.load(target), ID);
            check(renamed.displayName.equals("Living room console") && renamed.artworkKey().equals(discovered.artworkKey()), "Rename must keep platform/artwork identity.");
            try { names.rename(ID, "Bad\nname"); throw new AssertionError("Invalid native name accepted"); }
            catch (IllegalArgumentException expected) { }
            check(find(InputRepository.load(target), ID).displayName.equals("Living room console"), "Invalid name must preserve prior choice.");
            names.rename(ID, null);
            check(find(InputRepository.load(target), ID).displayName.equals("Fixture tuner"), "Factory name reset must restore service label.");
            result.putString("status", "PASS");
            result.putBoolean("real_platform_discovery", true);
            result.putBoolean("missing_and_denied_preservation", true);
            result.putBoolean("native_name_reset_identity", true);
            result.putString("limits", "Fixture tuner discovery and native preferences/settings only; no real HDMI switching or physical TV proof.");
        } catch (Throwable failure) {
            result.putString("status", "FAIL");
            result.putString("reason", failure.toString());
        } finally {
            if (activity != null) {
                onMain(new Runnable() { @Override public void run() { activity.finish(); } });
                waitForIdleSync();
            }
            SharedPreferences.Editor edit = prefs.edit().clear();
            for (Map.Entry<String, ?> entry : before.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof String) edit.putString(entry.getKey(), (String) value);
                else if (value instanceof Boolean) edit.putBoolean(entry.getKey(), (Boolean) value);
                else if (value instanceof Integer) edit.putInt(entry.getKey(), (Integer) value);
                else if (value instanceof Long) edit.putLong(entry.getKey(), (Long) value);
                else if (value instanceof Float) edit.putFloat(entry.getKey(), (Float) value);
                else if (value instanceof java.util.Set) edit.putStringSet(entry.getKey(), (java.util.Set<String>) value);
            }
            boolean restored = edit.commit() && before.equals(prefs.getAll());
            result.putBoolean("preferences_restored", restored);
            if (!restored) result.putString("status", "FAIL");
        }
        finish(0, result);
    }
    private void awaitTag(String tag) {
        long until = SystemClock.uptimeMillis() + 4000;
        while (SystemClock.uptimeMillis() < until) {
            final boolean[] found = {false};
            onMain(new Runnable() { @Override public void run() { found[0] = activity.getWindow().getDecorView().findViewWithTag(tag) != null; } });
            if (found[0]) return;
            SystemClock.sleep(50);
        }
        throw new AssertionError("Input row missing");
    }
    private void awaitLabel(final String tag, final String label) {
        long until = SystemClock.uptimeMillis() + 4000;
        while (SystemClock.uptimeMillis() < until) {
            final boolean[] found = {false};
            onMain(new Runnable() { @Override public void run() {
                View row = activity.getWindow().getDecorView().findViewWithTag(tag);
                found[0] = row instanceof Button && ((Button) row).getText().toString().startsWith(label);
            } });
            if (found[0]) return;
            SystemClock.sleep(50);
        }
        throw new AssertionError("Saved input label did not render");
    }
    private void settle() { waitForIdleSync(); SystemClock.sleep(150); }
    private void onMain(Runnable operation) {
        final Throwable[] failure = {null};
        runOnMainSync(new Runnable() { @Override public void run() { try { operation.run(); } catch (Throwable error) { failure[0] = error; } } });
        if (failure[0] != null) throw new AssertionError(failure[0]);
    }
    private static InputEntry find(InputRepository.Snapshot snapshot, String id) {
        for (InputEntry entry : snapshot.inputs) if (id.equals(entry.id)) return entry;
        return null;
    }
    private static <T> T findType(View view, Class<T> type) {
        if (type.isInstance(view)) return type.cast(view);
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            T found = findType(((ViewGroup) view).getChildAt(i), type);
            if (found != null) return found;
        }
        return null;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
