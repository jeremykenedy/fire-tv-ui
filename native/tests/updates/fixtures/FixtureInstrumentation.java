package com.jeremykenedy.firetv.ui.updatefixture;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.updates.AndroidPackages;
import com.jeremykenedy.firetv.ui.nativeapp.updates.StagedUpdateStore;
import com.jeremykenedy.firetv.ui.nativeapp.updates.UpdateCoordinator;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import org.json.JSONObject;

/** Isolated fixture package only. Never targets the preview or production launcher. */
public final class FixtureInstrumentation extends Instrumentation {
    private String phase;
    private Activity activity;
    @Override public void onCreate(Bundle args) { super.onCreate(args); phase = args.getString("phase", "engine"); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            String pkg = getTargetContext().getPackageName();
            check("com.jeremykenedy.firetv.ui.updatefixture".equals(pkg), "Refuse any launcher package");
            UpdateCoordinator coordinator = new UpdateCoordinator(pkg, getTargetContext().getCacheDir(), new AndroidPackages(getTargetContext()));
            StagedUpdateStore store = new StagedUpdateStore(getTargetContext().getCacheDir());
            if ("verify".equals(phase) || "recover".equals(phase)) {
                UpdateCoordinator.Verified recovered = store.restore(coordinator, new UpdateCoordinator.Cancellation());
                check(recovered != null, "No retained staged fixture journal");
                if ("verify".equals(phase)) {
                    check(coordinator.installationConfirmed(recovered), "Installed fixture version/signer not verified");
                    recovered.discard(); store.clear(); store.cleanup(null);
                    result.putString("installed", "Verified fixture v2 package and signer after process restart");
                } else {
                    check(!coordinator.installationConfirmed(recovered), "Recover phase must not claim staged fixture is installed");
                    coordinator.prepareInstall(recovered, new UpdateCoordinator.Cancellation());
                    activity = startActivitySync(new Intent().setClassName(pkg, FixtureActivity.class.getName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    waitForStatus("Staged APK reverified", 5000);
                    result.putString("recovery", "Retained fixture bytes and native staged screen verified after operator process restart");
                }
            } else {
                check(installedVersion(pkg) == 1, "Fixture must begin at version 1");
                store.clear(); store.cleanup(null);
                for (String variant : new String[]{"wrong-package", "wrong-signer", "older", "wrong-digest"}) {
                    boolean rejected = false;
                    try { coordinator.stage(release(variant), getTargetContext().getAssets().open(variant + ".apk"),
                            new UpdateCoordinator.Cancellation(), progress()); }
                    catch (Exception expected) { rejected = true; }
                    check(rejected, "Unsafe fixture accepted: " + variant);
                    check(ownedArtifacts() == 0, "Failed fixture left partial bytes");
                }
                UpdateCoordinator.Cancellation cancelled = new UpdateCoordinator.Cancellation();
                boolean stopped = false;
                try {
                    coordinator.stage(release("upgrade"), getTargetContext().getAssets().open("upgrade.apk"), cancelled,
                            new UpdateCoordinator.Progress() { @Override public void update(int percent) { cancelled.cancel(); } });
                } catch (InterruptedException expected) { stopped = true; }
                check(stopped && ownedArtifacts() == 0, "Cancelled staged fixture must clean partial bytes");
                UpdateCoordinator.Verified valid = coordinator.stage(release("upgrade"), getTargetContext().getAssets().open("upgrade.apk"),
                        new UpdateCoordinator.Cancellation(), progress());
                store.save(valid);
                check(!coordinator.installationConfirmed(valid), "A staged artifact is not installation success");
                check(new StagedUpdateStore(getTargetContext().getCacheDir()).restore(coordinator,
                        new UpdateCoordinator.Cancellation()) != null, "Private staged state must survive coordinator recreation");
                result.putString("engine", "PASS package/signer/version/digest rejection, staged acceptance, cancellation, cleanup, journal recovery");
                if ("stage".equals(phase)) {
                    activity = startActivitySync(new Intent().setClassName(pkg, FixtureActivity.class.getName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    waitForStatus("Staged APK reverified", 5000);
                    result.putString("recovery", "Staged fixture retained. Operator may force-stop only this fixture, then run phase=recover.");
                } else if (!"engine".equals(phase)) {
                    check(getTargetContext().getPackageManager().canRequestPackageInstalls(), "Operator must grant install permission to isolated fixture before installer phase");
                    activity = startActivitySync(new Intent().setClassName(pkg, FixtureActivity.class.getName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    waitForStatus("Staged APK reverified", 5000);
                    runOnMainSync(new Runnable() { @Override public void run() { view("install").requestFocus(); } });
                    sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
                    waitForExternalInstaller(pkg);
                    if ("cancel".equals(phase)) {
                        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
                        waitForStatus("No verified installation was observed", 6000);
                        check(installedVersion(pkg) == 1,
                                "Installer cancellation must preserve fixture version 1");
                        result.putString("installer", "Native installer opened; Back cancelled; installed fixture remains v1; no success claimed");
                    } else if ("install".equals(phase)) {
                        result.putString("status", "WAITING_OWNER");
                        result.putString("installer", "Native installer open for isolated fixture only. Owner must explicitly confirm. Run phase=verify after restart.");
                        finish(0, result); return;
                    } else throw new IllegalArgumentException("Unknown fixture phase");
                } else { valid.discard(); store.clear(); store.cleanup(null); }
            }
            result.putString("status", "PASS");
        } catch (Throwable failed) { result.putString("status", "FAIL"); result.putString("reason", failed.toString()); }
        finish(0, result);
    }
    private long installedVersion(String pkg) throws Exception {
        android.content.pm.PackageInfo info = getTargetContext().getPackageManager().getPackageInfo(pkg, 0);
        return android.os.Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
    }
    private int ownedArtifacts() {
        File[] files = getTargetContext().getCacheDir().listFiles(); int count = 0;
        if (files != null) for (File file : files) if (file.getName().startsWith("verified-update-") && file.getName().endsWith(".apk")) count++;
        return count;
    }
    private UpdateCoordinator.Release release(String variant) throws Exception {
        JSONObject metadata;
        try (InputStream input = getTargetContext().getAssets().open(variant + ".json")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] buffer = new byte[1024]; int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            metadata = new JSONObject(bytes.toString("UTF-8"));
        }
        return new UpdateCoordinator.Release("fixture/update-tests", "v2", getTargetContext().getPackageName(),
                "https://github.com/fixture/update-tests/releases/download/v2/fixture.apk", metadata.getString("sha256"),
                metadata.getLong("bytes"), 2, "Local fixture 2", "LOCAL TEST ONLY. No real release exists.");
    }
    private UpdateCoordinator.Progress progress() { return new UpdateCoordinator.Progress() { @Override public void update(int percent) {} }; }
    private View view(String tag) { return activity.getWindow().getDecorView().findViewWithTag("updates:" + tag); }
    private void waitForStatus(final String text, long timeout) {
        long deadline = SystemClock.uptimeMillis() + timeout; final boolean[] seen = {false};
        while (!seen[0] && SystemClock.uptimeMillis() < deadline) {
            runOnMainSync(new Runnable() { @Override public void run() { seen[0] = ((TextView) view("status")).getText().toString().contains(text); } });
            SystemClock.sleep(40);
        }
        check(seen[0], "Missing native status: " + text);
    }
    private void waitForExternalInstaller(String ownPackage) {
        long deadline = SystemClock.uptimeMillis() + 6000;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                CharSequence pkg = root.getPackageName();
                boolean external = pkg != null && !ownPackage.contentEquals(pkg) && pkg.toString().contains("packageinstaller");
                root.recycle(); if (external) return;
            }
            SystemClock.sleep(40);
        }
        throw new AssertionError("Actual external package installer window not observed");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
