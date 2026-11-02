package com.jeremykenedy.firetv.ui.nativeapp.device;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import java.io.File;

/** Emulator-only synthetic callback fixture. No accessibility service or Home filter is registered. */
public final class RoutingFixture extends Instrumentation {
    private String phase;
    @Override public void onCreate(Bundle args) { super.onCreate(args); phase = args.getString("phase", "verify"); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        result.putInt("process_id", android.os.Process.myPid());
        try {
            final FakeService service = new FakeService(getTargetContext());
            SharedPreferences prefs = service.getSharedPreferences("launcher_prefs", 0);
            check(prefs.edit().putBoolean(HomeSetupPolicy.HOME_KEYS, true)
                    .putBoolean(HomeSetupPolicy.HOME_WINDOWS, true).commit(), "Fixture options must save");
            if ("arm".equals(phase)) {
                check(DeviceRoutes.policy(service).beginHandoff(), "Exemption must persist before native dispatch");
                result.putString("status", "PASS");
                result.putString("phase", "arm");
            } else {
                check(!DeviceRoutes.policy(service).shouldOpen(true, SystemClock.uptimeMillis()),
                        "Exemption must survive the actual process restart between phases");
                Intent factory = DeviceRoutes.resolve(service, DeviceRoutes.Destination.FACTORY_UI);
                if (factory != null) {
                    window(service, factory.getComponent().getPackageName(), factory.getComponent().getClassName());
                    SystemClock.sleep(900);
                    check(service.launches == 0, "Window fallback cannot steal explicitly opened system Home after restart");
                }
                check(service.key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME), "Explicit Home starts source Home");
                check(service.key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME), "Successful key pair remains consumed");
                check(service.launches == 1, "Exactly one source Home request");
                check(service.last != null && HomeRoutingPolicy.PRODUCTION_PACKAGE.equals(service.last.getComponent().getPackageName()),
                        "Dispatch targets production package only inside the captured fake Context");
                window(service, HomeRoutingPolicy.PRODUCTION_PACKAGE,
                        "com.jeremykenedy.firetv.ui.nativeapp.MainActivity");
                check(service.key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME), "Visible Home consumes repeated Home");
                check(service.key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME), "Visible Home key-up consumed");
                check(service.launches == 1, "Repeated Home never recreates the visible activity");
                check(prefs.edit().putBoolean(HomeSetupPolicy.HOME_KEYS, false).commit(), "Window-only mode saves");
                check(service.key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME), "Window-only mode keeps an already visible Home");
                check(service.key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME), "Window-only mode consumes the complete visible-Home pair");
                check(service.launches == 1, "Visible Home never flashes through factory in window-only mode");
                check(prefs.edit().putBoolean(HomeSetupPolicy.HOME_KEYS, true).commit(), "Direct mode restored for fixture");
                FakeService windowOnly = new FakeService(getTargetContext()) {
                    @Override protected boolean filtersHomeKeys() { return false; }
                };
                check(!windowOnly.key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME),
                        "Legacy window-only adapter must not compete for Home keys");
                check(windowOnly.launches == 0, "Window-only adapter cannot dispatch a key launch");
                check(!service.key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP), "Volume passes through");
                resetPolicy();
                service.fail = true;
                window(service, "fixture.other", "fixture.other.Screen");
                check(!service.key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME), "Failed launch leaves system Home available");
                check(!service.key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME), "Failed launch does not swallow key-up");
                resetPolicy();
                service.fail = false;
                if (factory != null) {
                    int before = service.launches;
                    for (int i = 0; i < 4; i++) {
                        window(service, factory.getComponent().getPackageName(), factory.getComponent().getClassName());
                        SystemClock.sleep(250);
                    }
                    check(service.launches == before + 1,
                            "Repeated Home window reports cannot postpone the fallback deadline");
                    resetPolicy();
                    before = service.launches;
                    window(service, factory.getComponent().getPackageName(), factory.getComponent().getClassName());
                    window(service, "com.android.tv.settings", "android.widget.FrameLayout",
                            AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED);
                    SystemClock.sleep(1100);
                    check(service.launches == before + 1, "Outgoing pane disappearance cannot cancel Home fallback");
                    resetPolicy();
                    before = service.launches;
                    window(service, factory.getComponent().getPackageName(), factory.getComponent().getClassName());
                    window(service, "com.android.tv.settings", "android.widget.FrameLayout",
                            AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_APPEARED);
                    SystemClock.sleep(1100);
                    check(service.launches == before, "Incoming Settings pane must cancel pending Home fallback");
                }
                service.preview = true;
                service.fail = false;
                check(!service.key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME), "Preview cannot redirect even with true preferences");
                check(!service.key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME), "Preview key-up passes through");
                HomeRoutingPolicy fresh = new HomeRoutingPolicy(new HomeHandoffStore(service));
                check(fresh.shouldOpen(true, SystemClock.uptimeMillis()), "Explicit Home clear also survives a new policy instance");
                result.putString("status", "PASS");
                result.putString("phase", "verify");
                result.putBoolean("process_restart_exemption", true);
                result.putBoolean("captured_home_key_pairs", true);
                result.putBoolean("preview_pass_through", true);
                result.putBoolean("legacy_window_service_key_pass_through", true);
                result.putBoolean("system_home_window_exempt", factory != null);
                result.putString("limits", "Synthetic service callbacks in a fake production Context. No service registration, secure-setting writes or OS Home takeover; real remote key delivery needs production validation.");
            }
        } catch (Throwable error) {
            result.putString("status", "FAIL"); result.putString("reason", error.toString());
        }
        finish(0, result);
    }

    private static void resetPolicy() throws Exception {
        java.lang.reflect.Field field = DeviceRoutes.class.getDeclaredField("routing");
        field.setAccessible(true);
        field.set(null, null);
    }
    private void window(final FakeService service, final String pkg, final String cls) {
        window(service, pkg, cls, 0);
    }
    private void window(final FakeService service, final String pkg, final String cls, final int changes) {
        runOnMainSync(new Runnable() {
            @Override public void run() {
                AccessibilityEvent event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
                event.setPackageName(pkg); event.setClassName(cls); event.setContentChangeTypes(changes);
                service.onAccessibilityEvent(event);
                event.recycle();
            }
        });
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    private static class FakeService extends HomeKeyService {
        int launches;
        Intent last;
        boolean fail;
        boolean preview;
        FakeService(Context base) { attachBaseContext(base); }
        @Override public String getPackageName() {
            return preview ? "com.jeremykenedy.firetv.ui.preview" : HomeRoutingPolicy.PRODUCTION_PACKAGE;
        }
        @Override public Context getApplicationContext() { return this; }
        @Override public SharedPreferences getSharedPreferences(String name, int mode) {
            return getBaseContext().getSharedPreferences("fixture-" + name, mode);
        }
        @Override public File getNoBackupFilesDir() { return getBaseContext().getNoBackupFilesDir(); }
        @Override public void startActivity(Intent intent) {
            if (fail) throw new android.content.ActivityNotFoundException("Fixture rejected launch");
            launches++; last = intent;
        }
        boolean key(int action, int code) { return onKeyEvent(new KeyEvent(action, code)); }
    }
}
