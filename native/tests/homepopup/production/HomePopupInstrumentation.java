package com.jeremykenedy.firetv.ui.nativeapp.providers;

import com.jeremykenedy.firetv.ui.nativeapp.device.HomeKeyService;
import com.jeremykenedy.firetv.ui.nativeapp.device.HomeSetupPolicy;
import com.jeremykenedy.firetv.ui.nativeapp.device.HomeRoutingPolicy;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.View;
import android.view.ViewGroup;
import com.jeremykenedy.firetv.ui.nativeapp.HomeDialogBuilder;
import com.jeremykenedy.firetv.ui.nativeapp.focus.FocusTileView;
import com.jeremykenedy.firetv.ui.nativeapp.stateguard.GuardStore;
import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;

/** Synthetic service callback against actual Home. Operator must restore the durable guard. */
public final class HomePopupInstrumentation extends Instrumentation {
    private Bundle args;
    private Activity home;
    private Activity lastResumed;
    private FakeService service;
    private android.app.Application diagnosticsApp;
    private final android.app.Application.ActivityLifecycleCallbacks diagnosticsLifecycle = new android.app.Application.ActivityLifecycleCallbacks() {
        @Override public void onActivityCreated(Activity activity, Bundle state) { }
        @Override public void onActivityStarted(Activity activity) { }
        @Override public void onActivityResumed(Activity activity) { lastResumed = activity; }
        @Override public void onActivityPaused(Activity activity) { if (lastResumed == activity) lastResumed = null; }
        @Override public void onActivityStopped(Activity activity) { }
        @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
        @Override public void onActivityDestroyed(Activity activity) { if (lastResumed == activity) lastResumed = null; }
    };
    @Override public void onCreate(Bundle args) { super.onCreate(args); this.args = args; start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            String id = args.getString("state_guard_case_id"), hash = args.getString("state_guard_manifest_sha256");
            check(id != null && hash != null && new GuardStore(getTargetContext().getDataDir()).verify(id,
                    new LinkedHashSet<String>(Arrays.asList("shared_prefs", "files", "no_backup")), hash).matches, "Durable guard required");
            check("com.jeremykenedy.firetv.ui".equals(getTargetContext().getPackageName()), "Production compatibility package required");
            if (!com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.ready(getTargetContext())) {
                final java.util.concurrent.CountDownLatch startup = new java.util.concurrent.CountDownLatch(1);
                onMain(new Runnable() { @Override public void run() {
                    com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.start(getTargetContext(), new Runnable() {
                        @Override public void run() { startup.countDown(); }
                    });
                }});
                check(startup.await(15, java.util.concurrent.TimeUnit.SECONDS), "Production startup did not settle");
            }
            check(com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.ready(getTargetContext()), "Production startup failed to settle");
            check(new GuardStore(getTargetContext().getDataDir()).verify(id,
                    new LinkedHashSet<String>(Arrays.asList("shared_prefs", "files", "no_backup")), hash).matches,
                    "Production startup changed guarded state after capture");
            diagnosticsApp = (android.app.Application) getTargetContext().getApplicationContext();
            onMain(new Runnable() { @Override public void run() { diagnosticsApp.registerActivityLifecycleCallbacks(diagnosticsLifecycle); }});
            service = new FakeService(getTargetContext());
            check(service.getSharedPreferences("launcher_prefs", 0).edit().putBoolean(HomeSetupPolicy.HOME_KEYS, true)
                    .putBoolean(HomeSetupPolicy.HOME_WINDOWS, true).commit(), "Fixture key choices");
            home = startActivitySync(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setClassName(getTargetContext(), "com.jeremykenedy.firetv.ui.MainActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            check(home.getIntent().getComponent() != null && "com.jeremykenedy.firetv.ui.MainActivity".equals(home.getIntent().getComponent().getClassName()),
                    "Initial Home must enter through the production alias");
            final View[] background = new View[1], focus = new View[1];
            final AlertDialog[] dialogs = new AlertDialog[2];
            onMain(new Runnable() { @Override public void run() {
                background[0] = home.getWindow().getDecorView().findViewWithTag("appearance:layers");
                check(background[0] != null, "Actual Home compositor required");
                focus[0] = home.getCurrentFocus();
                dialogs[0] = new HomeDialogBuilder(home).setTitle("Outer Home fixture").setPositiveButton("Done", null).show();
                dialogs[1] = new HomeDialogBuilder(home).setTitle("Nested Home fixture").setPositiveButton("Done", null).show();
                check(dialogs[0].isShowing() && dialogs[1].isShowing(), "Nested Home dialogs must be open");
                check(service.key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME), "Home down must be consumed");
                check(service.key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME), "Home up must be consumed");
                check(!dialogs[0].isShowing() && !dialogs[1].isShowing(), "Home must dismiss nested dialogs");
                check(service.launches == 0, "Repeated Home must not launch another activity");
                check(home.getWindow().getDecorView().findViewWithTag("appearance:layers") == background[0], "Home compositor must survive");
            }});
            waitForIdleSync();
            onMain(new Runnable() { @Override public void run() {
                check(!home.isFinishing() && !home.isDestroyed(), "Same Home activity must survive");
                if (focus[0] != null) check(home.getCurrentFocus() == focus[0], "Focus must return to the prior Home target");
                dialogs[0] = new HomeDialogBuilder(home).setTitle("Dismiss callback").create();
                dialogs[0].setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
                    @Override public void onDismiss(android.content.DialogInterface ignored) {
                        dialogs[1] = new HomeDialogBuilder(home).setTitle("Created by callback").show();
                    }
                });
                dialogs[0].show();
                check(service.key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME), "Home dismisses the callback dialog");
                check(service.key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME), "Callback Home up");
            }});
            waitForIdleSync();
            onMain(new Runnable() { @Override public void run() {
                check(dialogs[1] != null && dialogs[1].isShowing(), "Dismiss callback must be able to create a new dialog");
                check(service.key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME), "Newly registered dialog must remain tracked");
                check(service.key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME), "New dialog Home up");
                check(!dialogs[1].isShowing(), "Second Home dismisses the callback-created dialog");
            }});
            waitForIdleSync();
            onMain(new Runnable() { @Override public void run() {
                dialogs[0] = new HomeDialogBuilder(home).setTitle("Normal Back").setNegativeButton("Cancel", null).show();
            }});
            waitForDialogFocus(dialogs[0], "Normal Back");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            waitForDialogClosed(dialogs[0], "Normal Back");
            onMain(new Runnable() { @Override public void run() {
                check(!dialogs[0].isShowing(), "Normal Back must still dismiss dialogs");
                check(!HomeDialogBuilder.dismissForHome(), "Dismissed dialogs must not remain active");
                dialogs[0] = new HomeDialogBuilder(home).setTitle("Non Home intent").show();
            }});
            getTargetContext().startActivity(new Intent(Intent.ACTION_VIEW).setClassName(getTargetContext(),
                    "com.jeremykenedy.firetv.ui.nativeapp.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            waitForIdleSync();
            onMain(new Runnable() { @Override public void run() {
                check(dialogs[0].isShowing(), "Non Home intent must not dismiss a Home dialog");
                check(service.key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME), "Home closes the remaining dialog");
                check(service.key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME), "Home key up");
                check(!dialogs[0].isShowing(), "Home dismisses the remaining dialog");
            }});
            onMain(new Runnable() { @Override public void run() {
                dialogs[0] = new HomeDialogBuilder(home).setTitle("Home intent").show();
            }});
            getTargetContext().startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .setClassName(getTargetContext(), "com.jeremykenedy.firetv.ui.nativeapp.MainActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            waitForIdleSync();
            onMain(new Runnable() { @Override public void run() {
                check(!dialogs[0].isShowing(), "Home intent dismisses its popup");
                check(!home.isFinishing() && !home.isDestroyed(), "Home intent must retain the activity");
                check(home.getWindow().getDecorView().findViewWithTag("appearance:layers") == background[0], "Home intent retains background identity");
            }});
            final com.jeremykenedy.firetv.ui.nativeapp.cards.HomeContentView[] content = new com.jeremykenedy.firetv.ui.nativeapp.cards.HomeContentView[1];
            final com.jeremykenedy.firetv.ui.nativeapp.providers.TvRecommendations.Feed feed =
                    new com.jeremykenedy.firetv.ui.nativeapp.providers.TvRecommendations.Feed("home-popup-fixture", "Fixture channel", getTargetContext().getPackageName(), Arrays.asList(
                            new com.jeremykenedy.firetv.ui.nativeapp.providers.TvRecommendations.Program("fixture:one", getTargetContext().getPackageName(), "First", null, ""),
                            new com.jeremykenedy.firetv.ui.nativeapp.providers.TvRecommendations.Program("fixture:two", getTargetContext().getPackageName(), "Second", null, "")));
            final View[] cardSlot = new View[1];
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
            waitForIdleSync();
            onMain(new Runnable() { @Override public void run() {
                content[0] = findContent(home.getWindow().getDecorView());
                check(content[0] != null, "Actual Home content required");
                check(content[0].applyResult(content[0].beginRefresh(), new com.jeremykenedy.firetv.ui.nativeapp.providers.TvRecommendations.Result(
                        com.jeremykenedy.firetv.ui.nativeapp.providers.TvRecommendations.Status.READY, Arrays.asList(feed))), "Synthetic channel must apply");
                cardSlot[0] = content[0].findViewWithTag("slot:" + feed.item.key());
                check(cardSlot[0] != null && cardSlot[0].requestFocus(), "Channel slot must take focus");
                content[0].findCard(feed.item.key()).showNextProgram();
                check(cardSlot[0].performClick(), "Open actual card actions");
            }});
            waitForIdleSync();
            onMain(new Runnable() { @Override public void run() {
                check(service.key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME), "Home dismisses actual card actions");
                check(service.key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME), "Card action Home up");
            }});
            waitForIdleSync();
            onMain(new Runnable() { @Override public void run() {
                check(cardSlot[0].isFocused(), "Card focus must survive menu dismissal");
                check("fixture:two".equals(content[0].findCard(feed.item.key()).currentProgram().key), "Selected program must survive menu dismissal");
                check(home.getWindow().getDecorView().findViewWithTag("appearance:layers") == background[0], "Background identity must survive card action");
            }});
            java.util.List<com.jeremykenedy.firetv.ui.nativeapp.appgrid.AppInventory.App> apps =
                    com.jeremykenedy.firetv.ui.nativeapp.appgrid.AppInventory.load(getTargetContext());
            check(!apps.isEmpty(), "An installed Home app is required");
            final String appKey = apps.get(0).key();
            final View[] app = new View[1];
            for (int attempt = 0; attempt < 50 && app[0] == null; attempt++) {
                onMain(new Runnable() { @Override public void run() { app[0] = home.getWindow().getDecorView().findViewWithTag(appKey); }});
                if (app[0] == null) android.os.SystemClock.sleep(100);
            }
            check(app[0] != null, "App tile must be visible");
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
            waitForIdleSync();
            onMain(new Runnable() { @Override public void run() {
                check(app[0].requestFocus() && app[0].performLongClick(), "Open actual app actions");
            }});
            waitForIdleSync();
            onMain(new Runnable() { @Override public void run() {
                check(service.key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME), "Home dismisses actual app actions");
                check(service.key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME), "App action Home up");
            }});
            waitForIdleSync();
            onMain(new Runnable() { @Override public void run() {
                check(app[0].isFocused(), "App focus and identity must survive dismissal");
                check(home.getWindow().getDecorView().findViewWithTag("appearance:layers") == background[0], "Background identity must survive app action");
                check(service.key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME), "Repeated Home on visible launcher must be consumed");
                check(service.key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME), "Repeated Home up");
                check(service.launches == 0, "Repeated Home must not launch a second activity");
            }});
            onMain(new Runnable() { @Override public void run() {
                dialogs[0] = new HomeDialogBuilder(home).setTitle("Paused Home popup").show();
            }});
            final Activity backCover = startActivitySync(new Intent().setClassName(getTargetContext(),
                    "com.jeremykenedy.firetv.ui.nativeapp.SettingsActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); waitForIdleSync();
            onMain(new Runnable() { @Override public void run() {
                check(dialogs[0].isShowing(), "Ordinary Back return preserves the paused popup");
            }});
            final Activity cover = startActivitySync(new Intent().setClassName(getTargetContext(),
                    "com.jeremykenedy.firetv.ui.nativeapp.SettingsActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            onMain(new Runnable() { @Override public void run() {
                service.dispatch = true;
                check(service.key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME), "Home routes back from another activity");
                check(service.key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME), "Routed Home key up");
                check(service.last != null && Intent.ACTION_MAIN.equals(service.last.getAction())
                        && service.last.hasCategory(Intent.CATEGORY_HOME), "Service launch identifies Home intent");
            }});
            waitForIdleSync();
            onMain(new Runnable() { @Override public void run() {
                check(!dialogs[0].isShowing(), "Routed Home return dismisses the paused popup");
                check(!home.isDestroyed() && !home.isFinishing(), "Routed return retains the Home activity");
                check(home.getWindow().getDecorView().findViewWithTag("appearance:layers") == background[0], "Routed return retains compositor");
                if (!cover.isFinishing()) cover.finish();
            }});
            waitForHomeFocus();
            final boolean[] dismissedOnDestroy = {false};
            onMain(new Runnable() { @Override public void run() {
                dialogs[0] = new HomeDialogBuilder(home).setTitle("Destroy cleanup").create();
                dialogs[0].setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
                    @Override public void onDismiss(android.content.DialogInterface ignored) { dismissedOnDestroy[0] = true; }
                });
                dialogs[0].show();
            }});
            waitForDialogFocus(dialogs[0], "Destroy cleanup");
            onMain(new Runnable() { @Override public void run() { home.finish(); }});
            waitForIdleSync();
            final boolean[] cleanupDone = {false}, destroyed = {false}, finishing = {false}, showing = {false};
            for (int attempt = 0; attempt < 50 && !cleanupDone[0]; attempt++) {
                onMain(new Runnable() { @Override public void run() {
                    destroyed[0] = home.isDestroyed(); finishing[0] = home.isFinishing(); showing[0] = dialogs[0].isShowing();
                    cleanupDone[0] = destroyed[0] && !showing[0] && dismissedOnDestroy[0];
                }});
                if (!cleanupDone[0]) android.os.SystemClock.sleep(100);
            }
            check(cleanupDone[0], "Destroy cleanup: destroyed=" + destroyed[0] + ", finishing=" + finishing[0]
                    + ", dialogShowing=" + showing[0] + ", dismissedListener=" + dismissedOnDestroy[0]);
            home = startActivitySync(new Intent().setClassName(getTargetContext(),
                    "com.jeremykenedy.firetv.ui.nativeapp.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            waitForHomeFocus();
            final boolean[] paused = {false};
            final android.app.Application lifecycleApp = (android.app.Application) home.getApplication();
            final android.app.Application.ActivityLifecycleCallbacks lifecycle = new android.app.Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityCreated(Activity activity, Bundle state) { }
                @Override public void onActivityStarted(Activity activity) { }
                @Override public void onActivityResumed(Activity activity) { }
                @Override public void onActivityPaused(Activity activity) { if (activity == home) paused[0] = true; }
                @Override public void onActivityStopped(Activity activity) { }
                @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
                @Override public void onActivityDestroyed(Activity activity) { }
            };
            onMain(new Runnable() { @Override public void run() { lifecycleApp.registerActivityLifecycleCallbacks(lifecycle); }});
            final Activity pausedCover = startActivitySync(new Intent().setClassName(getTargetContext(),
                    "com.jeremykenedy.firetv.ui.nativeapp.SettingsActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            for (int attempt = 0; attempt < 50 && !paused[0]; attempt++) {
                onMain(new Runnable() { @Override public void run() { }});
                if (!paused[0]) android.os.SystemClock.sleep(100);
            }
            check(paused[0], "Home must receive onPause before the paused-owner case");
            final boolean[] pausedDismissed = {false};
            onMain(new Runnable() { @Override public void run() {
                check(!home.hasWindowFocus(), "Home must be covered before paused-owner case");
                dialogs[0] = new HomeDialogBuilder(home).setTitle("Paused-owner async popup").create();
                dialogs[0].setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
                    @Override public void onDismiss(android.content.DialogInterface ignored) { pausedDismissed[0] = true; }
                });
                dialogs[0].show();
                check(!HomeDialogBuilder.dismissForHome(), "Inactive Home is not dismissed by visible-Home signal");
                check(dialogs[0].isShowing(), "Paused-owner dialog must remain until owner cleanup");
                home.finish();
            }});
            final boolean[] pausedDone = {false}, pausedDestroyed = {false}, pausedShowing = {false};
            for (int attempt = 0; attempt < 50 && !pausedDone[0]; attempt++) {
                onMain(new Runnable() { @Override public void run() {
                    pausedDestroyed[0] = home.isDestroyed(); pausedShowing[0] = dialogs[0].isShowing();
                    pausedDone[0] = pausedDestroyed[0] && !pausedShowing[0] && pausedDismissed[0];
                }});
                if (!pausedDone[0]) android.os.SystemClock.sleep(100);
            }
            check(pausedDone[0], "Paused-owner cleanup: destroyed=" + pausedDestroyed[0] + ", showing=" + pausedShowing[0]
                    + ", dismissedListener=" + pausedDismissed[0]);
            onMain(new Runnable() { @Override public void run() {
                lifecycleApp.unregisterActivityLifecycleCallbacks(lifecycle);
                pausedCover.finish();
            }});
            result.putString("status", "PASS");
            result.putString("limits", "Production-package HOME alias and synthetic service callbacks only; real OS Home and physical TV remain separate.");
        } catch (Throwable failure) {
            result.putString("status", "FAIL");
            result.putString("reason", failure.toString() + (failure.getCause() == null ? "" : ": " + failure.getCause()));
            try { captureDiagnostics(result); } catch (Throwable diagnosticError) { result.putString("diagnostic_error", diagnosticError.toString()); }
        }
        finally {
            if (home != null || diagnosticsApp != null) try { onMain(new Runnable() { @Override public void run() {
                if (diagnosticsApp != null) diagnosticsApp.unregisterActivityLifecycleCallbacks(diagnosticsLifecycle);
                if (home != null) home.finish();
            }}); }
            catch (Throwable cleanup) { result.putString("status", "FAIL"); result.putString("cleanup_error", cleanup.toString()); }
        }
        finish(0, result);
    }
    private void captureDiagnostics(final Bundle result) {
        onMain(new Runnable() { @Override public void run() {
            if (home != null) {
                result.putString("original_home_class", home.getClass().getName());
                result.putInt("original_home_identity", System.identityHashCode(home));
                result.putInt("original_home_task", home.getTaskId());
                result.putBoolean("original_home_finishing", home.isFinishing());
                result.putBoolean("original_home_destroyed", home.isDestroyed());
                result.putBoolean("original_home_window_focus", home.hasWindowFocus());
            }
            if (lastResumed != null) {
                result.putString("last_resumed_class", lastResumed.getClass().getName());
                result.putInt("last_resumed_identity", System.identityHashCode(lastResumed));
                result.putInt("last_resumed_task", lastResumed.getTaskId());
                result.putBoolean("last_resumed_window_focus", lastResumed.hasWindowFocus());
            } else result.putString("last_resumed_class", "none");
            if (service != null && service.last != null) {
                result.putString("service_intent_action", service.last.getAction());
                result.putString("service_intent_component", String.valueOf(service.last.getComponent()));
                result.putInt("service_intent_flags", service.last.getFlags());
                result.putBoolean("service_intent_home_category", service.last.hasCategory(Intent.CATEGORY_HOME));
            }
            android.app.ActivityManager manager = (android.app.ActivityManager) getTargetContext().getSystemService(Context.ACTIVITY_SERVICE);
            if (manager != null) {
                StringBuilder tasks = new StringBuilder();
                for (android.app.ActivityManager.AppTask task : manager.getAppTasks()) {
                    android.app.ActivityManager.RecentTaskInfo info = task.getTaskInfo();
                    if (info == null) continue;
                    if (tasks.length() > 0) tasks.append("; ");
                    tasks.append("task=").append(info.id).append(" top=").append(info.topActivity)
                            .append(" base=").append(info.baseActivity).append(" count=").append(info.numActivities);
                }
                result.putString("app_tasks", tasks.toString());
            }
        }});
        AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
        if (root != null) {
            result.putString("active_root_package", String.valueOf(root.getPackageName()));
            result.putString("active_root_class", String.valueOf(root.getClassName()));
            android.view.accessibility.AccessibilityWindowInfo window = root.getWindow();
            if (window != null) {
                result.putInt("active_window_type", window.getType());
                CharSequence title = window.getTitle();
                String value = title == null ? "" : title.toString();
                result.putString("active_window_title", value.equals("Destroy cleanup") || value.equals("Paused Home popup")
                        || value.equals("Home intent") || value.equals("Normal Back") ? value : "[other or empty]");
            }
        } else result.putString("active_root_package", "none");
    }
    private void waitForHomeFocus() {
        final boolean[] focused = {false};
        for (int attempt = 0; attempt < 50 && !focused[0]; attempt++) {
            onMain(new Runnable() { @Override public void run() {
                focused[0] = !home.isFinishing() && !home.isDestroyed() && home.hasWindowFocus();
            }});
            if (!focused[0]) android.os.SystemClock.sleep(100);
        }
        check(focused[0], "Home did not regain window focus before destroy test");
    }
    private void waitForDialogFocus(final AlertDialog dialog, String step) {
        final boolean[] focused = {false};
        for (int attempt = 0; attempt < 50 && !focused[0]; attempt++) {
            onMain(new Runnable() { @Override public void run() {
                focused[0] = dialog.isShowing() && dialog.getWindow() != null
                        && dialog.getWindow().getDecorView().hasWindowFocus();
            }});
            if (!focused[0]) android.os.SystemClock.sleep(100);
        }
        check(focused[0], step + ": dialog window did not gain focus before Back");
    }
    private void waitForDialogClosed(final AlertDialog dialog, String step) {
        final boolean[] closed = {false};
        for (int attempt = 0; attempt < 50 && !closed[0]; attempt++) {
            onMain(new Runnable() { @Override public void run() { closed[0] = !dialog.isShowing(); }});
            if (!closed[0]) android.os.SystemClock.sleep(100);
        }
        check(closed[0], step + ": focused dialog did not dismiss after Back");
    }
    private void onMain(final Runnable action) {
        final Throwable[] failure = {null};
        runOnMainSync(new Runnable() { @Override public void run() {
            try { action.run(); } catch (Throwable error) { failure[0] = error; }
        }});
        if (failure[0] != null) throw new AssertionError("Home popup UI assertion", failure[0]);
    }
    private static com.jeremykenedy.firetv.ui.nativeapp.cards.HomeContentView findContent(View view) {
        if (view instanceof com.jeremykenedy.firetv.ui.nativeapp.cards.HomeContentView)
            return (com.jeremykenedy.firetv.ui.nativeapp.cards.HomeContentView) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            com.jeremykenedy.firetv.ui.nativeapp.cards.HomeContentView found = findContent(((ViewGroup) view).getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static class FakeService extends HomeKeyService {
        int launches;
        boolean dispatch;
        Intent last;
        FakeService(Context base) { attachBaseContext(base); }
        @Override public String getPackageName() { return HomeRoutingPolicy.PRODUCTION_PACKAGE; }
        @Override public Context getApplicationContext() { return this; }
        @Override public SharedPreferences getSharedPreferences(String name, int mode) { return getBaseContext().getSharedPreferences("fixture-" + name, mode); }
        @Override public File getNoBackupFilesDir() { return getBaseContext().getNoBackupFilesDir(); }
        @Override public void startActivity(Intent intent) {
            launches++; last = new Intent(intent);
            if (dispatch) getBaseContext().startActivity(new Intent(intent).setClassName(getBaseContext(),
                    "com.jeremykenedy.firetv.ui.nativeapp.MainActivity"));
        }
        boolean key(int action, int code) { return onKeyEvent(new KeyEvent(action, code)); }
    }
}
