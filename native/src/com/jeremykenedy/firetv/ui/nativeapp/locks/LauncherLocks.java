package com.jeremykenedy.firetv.ui.nativeapp.locks;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class LauncherLocks {
    private static final ThreadPoolExecutor CHECKS = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(8));

    private LauncherLocks() {}

    /** Call at the actual app dispatch or folder-open boundary, including shortcuts. */
    public static void guard(final Activity activity, String key, final Runnable granted) {
        try {
            final LockStore store = new LockStore(activity);
            if (!store.isLocked(key)) { granted.run(); return; }
            prompt(activity, "Unlock for this launch", false, false, new Check() {
                @Override public LockResult run(char[] current, char[] next) throws Exception { return store.authenticate(current); }
            }, granted);
        } catch (Exception failed) { unavailable(activity); }
    }

    public static void showControls(final Activity activity, final String key, final String label, final Runnable changed) {
        try {
            final LockStore store = new LockStore(activity);
            final boolean locked = store.isLocked(key);
            if (!store.hasPin()) {
                prompt(activity, "Create PIN and lock " + label, true, false, new Check() {
                    @Override public LockResult run(char[] current, char[] next) throws Exception {
                        store.createPinAndLock(next, key); return LockResult.GRANTED;
                    }
                }, changed);
            } else {
                prompt(activity, (locked ? "Remove lock: " : "Lock: ") + label, false, false, new Check() {
                    @Override public LockResult run(char[] current, char[] next) throws Exception { return store.setLocked(key, !locked, current); }
                }, changed);
            }
        } catch (Exception failed) { unavailable(activity); }
    }

    static void createPin(final Activity activity, Runnable changed) {
        prompt(activity, "Create launcher PIN", true, false, new Check() {
            @Override public LockResult run(char[] current, char[] next) throws Exception {
                new LockStore(activity).createPin(next); return LockResult.GRANTED;
            }
        }, changed);
    }

    static void changePin(final Activity activity, Runnable changed) {
        prompt(activity, "Change launcher PIN", true, true, new Check() {
            @Override public LockResult run(char[] current, char[] next) throws Exception { return new LockStore(activity).changePin(current, next); }
        }, changed);
    }

    static void unlockAll(final Activity activity, Runnable changed) {
        prompt(activity, "Remove all launcher locks", false, false, new Check() {
            @Override public LockResult run(char[] current, char[] next) throws Exception { return new LockStore(activity).unlockAll(current); }
        }, changed);
    }

    private interface Check { LockResult run(char[] current, char[] next) throws Exception; }

    private static void prompt(final Activity activity, String title, final boolean newPin, final boolean currentRequired,
            final Check check, final Runnable granted) {
        final LinearLayout fields = new LinearLayout(activity);
        fields.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(24 * activity.getResources().getDisplayMetrics().density);
        fields.setPadding(padding, 0, padding, 0);
        final EditText current = input(activity, "Current PIN");
        final EditText next = input(activity, "New PIN (4 through 12 digits)");
        final EditText confirm = input(activity, "Confirm new PIN");
        if (!newPin || currentRequired) fields.addView(current);
        if (newPin) { fields.addView(next); fields.addView(confirm); }
        final AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(title)
                .setMessage("These locks apply only inside this launcher. Other apps and system settings can still open apps. Your PIN stays on this device.")
                .setView(fields).setNegativeButton("Cancel", null).setPositiveButton(newPin ? "Save PIN" : "Continue", null).create();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface ignored) { current.setText(""); next.setText(""); confirm.setText(""); }
        });
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface ignored) {
                (newPin && !currentRequired ? next : current).requestFocus();
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        final char[] oldPin = copy(current);
                        final char[] nextPin = copy(next);
                        char[] confirmation = copy(confirm);
                        try {
                            if (newPin) {
                                LockState.validatePin(nextPin);
                                if (!Arrays.equals(nextPin, confirmation)) throw new IllegalArgumentException("The new PIN entries do not match.");
                            }
                        } catch (IllegalArgumentException invalid) {
                            Arrays.fill(oldPin, '\0'); Arrays.fill(nextPin, '\0');
                            (newPin ? next : current).setError(invalid.getMessage()); return;
                        } finally { Arrays.fill(confirmation, '\0'); }
                        current.setText(""); next.setText(""); confirm.setText("");
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                        try {
                            CHECKS.execute(new Runnable() {
                                @Override public void run() {
                                    LockResult outcome = null;
                                    try { outcome = check.run(oldPin, nextPin); }
                                    catch (Exception failed) { /* Display a fixed message without exception or PIN details. */ }
                                    finally { Arrays.fill(oldPin, '\0'); Arrays.fill(nextPin, '\0'); }
                                    final LockResult result = outcome;
                                    activity.runOnUiThread(new Runnable() {
                                        @Override public void run() {
                                            if (activity.isFinishing() || activity.isDestroyed() || !dialog.isShowing()) return;
                                            if (!dialog.getWindow().getDecorView().hasWindowFocus()) { dialog.dismiss(); return; }
                                            if (result == LockResult.GRANTED) { dialog.dismiss(); if (granted != null) granted.run(); }
                                            else {
                                                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                                EditText errorField = newPin && !currentRequired ? next : current;
                                                errorField.setError(result == LockResult.THROTTLED ? "Too many attempts. Wait before trying again."
                                                        : result == LockResult.WRONG_PIN ? "Incorrect PIN."
                                                        : result == LockResult.NO_PIN ? "Create a launcher PIN first." : "Lock storage is unavailable. Nothing was unlocked.");
                                                errorField.requestFocus();
                                            }
                                        }
                                    });
                                }
                            });
                        } catch (RejectedExecutionException busy) {
                            Arrays.fill(oldPin, '\0'); Arrays.fill(nextPin, '\0');
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                            current.setError("A PIN check is already running. Try again.");
                        }
                    }
                });
            }
        });
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dialog.show();
    }

    private static EditText input(Activity activity, String hint) {
        EditText input = new EditText(activity);
        input.setHint(hint);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setFilters(new InputFilter[] {new InputFilter.LengthFilter(12)});
        input.setSaveEnabled(false);
        input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        return input;
    }

    private static char[] copy(EditText input) {
        char[] value = new char[input.length()];
        input.getText().getChars(0, value.length, value, 0);
        return value;
    }

    private static void unavailable(Activity activity) {
        Toast.makeText(activity, "Launcher locks are unavailable. Nothing was opened or unlocked.", Toast.LENGTH_LONG).show();
    }
}
