package com.jeremykenedy.firetv.ui.nativeapp.device.remotebuttons;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.KeyEvent;

/** Uses an isolated preferences store and records launches without opening any app. */
public final class RemoteButtonsInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    private void check(boolean result, String reason) { if (!result) throw new AssertionError(reason); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        final Context target = getTargetContext();
        final String file = "remote_button_isolated_proof";
        final int[] launches = {0};
        final boolean[] fail = {false};
        Context isolated = new ContextWrapper(target) {
            @Override public SharedPreferences getSharedPreferences(String name, int mode) { return target.getSharedPreferences(file, mode); }
            @Override public void startActivity(Intent intent) {
                if (fail[0]) throw new android.content.ActivityNotFoundException("Fixture missing app");
                launches[0]++;
            }
        };
        SharedPreferences store = target.getSharedPreferences(file, Context.MODE_PRIVATE);
        try {
            check(store.getAll().isEmpty(), "Proof store already exists; preserve it and stop");
            RemoteButtonPreferences preferences = new RemoteButtonPreferences(isolated);
            check(!preferences.hasSystemMappings(), "Mappings enabled by default");
            preferences.learn(289, "Netflix"); preferences.learn(290, "Disney+");
            preferences.map(289, "action:settings", false);
            preferences.map(290, "action:apps", true);
            check(preferences.hasSystemMappings(), "System scope not persisted");
            RemoteButtonDispatcher dispatch = new RemoteButtonDispatcher(isolated);
            check(!dispatch.dispatch(new KeyEvent(0, 289), true), "Launcher mapping leaked into system scope");
            check(dispatch.dispatch(new KeyEvent(0, 289), false), "Launcher mapping did not launch");
            check(dispatch.dispatch(new KeyEvent(1, 289), false), "Launcher release escaped");
            check(launches[0] == 1, "Unexpected launch count");
            RemoteButtonDispatcher.learning(true);
            check(!dispatch.dispatch(new KeyEvent(0, 290), true), "Learning allowed service launch");
            RemoteButtonDispatcher.learning(false);
            fail[0] = true;
            check(!dispatch.dispatch(new KeyEvent(0, 290), true), "Unavailable action suppressed original");
            check(!dispatch.dispatch(new KeyEvent(1, 290), true), "Unavailable release suppressed original");
            fail[0] = false;
            check(dispatch.dispatch(new KeyEvent(0, 290), true), "System mapping did not launch");
            check(dispatch.dispatch(new KeyEvent(1, 290), true), "System release escaped");
            store.edit().putString("unrelated", "preserve").commit();
            preferences.restore(289);
            check(preferences.target(289).isEmpty() && !preferences.target(290).isEmpty(), "Per-key restore changed another mapping");
            preferences.restoreAll();
            check(!preferences.hasSystemMappings(), "Restore all left system filtering demand");
            check(preferences.learned().size() == 2, "Reset deleted learned labels");
            check("preserve".equals(store.getString("unrelated", "")), "Reset deleted unrelated setting");
            check(!dispatch.dispatch(new KeyEvent(0, 290), true), "Factory reset still intercepted");
            check(!dispatch.dispatch(new KeyEvent(1, 290), true), "Factory reset intercepted release");
            try { preferences.learn(KeyEvent.KEYCODE_HOME, "Home"); throw new AssertionError("Protected key learned"); }
            catch (IllegalArgumentException expected) { }
            try { preferences.map(291, "action:apps", true); throw new AssertionError("Unlearned key mapped"); }
            catch (IllegalArgumentException expected) { }
            result.putString("stream", "Remote preference/scope/reset/launch-failure behavior passed. This does not prove physical remote interception or vendor cancellation.\n");
            store.edit().clear().commit();
            finish(ActivityResult.OK, result);
        } catch (Throwable failure) {
            result.putString("stream", "Remote proof failed: " + failure + "\n");
            finish(ActivityResult.FAILED, result);
        } finally { RemoteButtonDispatcher.learning(false); }
    }
    private static final class ActivityResult { static final int OK = -1; static final int FAILED = 0; }
}
