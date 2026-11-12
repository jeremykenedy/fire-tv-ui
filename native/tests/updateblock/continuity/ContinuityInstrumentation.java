package com.jeremykenedy.firetv.updatecontinuityproof;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import com.jeremykenedy.firetv.ui.nativeapp.SourceStartup;
import com.jeremykenedy.firetv.ui.nativeapp.device.updates.UpdateBlockPolicy;
import com.jeremykenedy.firetv.ui.nativeapp.device.updates.UpdateBlockService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Actual production-package service checks; synthetic completed startup is explicit. */
public final class ContinuityInstrumentation extends Instrumentation {
    private static final String TARGET = "com.jeremykenedy.firetv.ui";
    private static final String PROOF = "com.jeremykenedy.firetv.updatecontinuityproof";
    private Bundle arguments;
    private Activity activity;
    private String step;
    private boolean systemConsentSeen;

    @Override public void onCreate(Bundle args) { super.onCreate(args); arguments = args; start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        result.putInt("pid", android.os.Process.myPid());
        result.putInt("api", Build.VERSION.SDK_INT);
        try {
            check(TARGET.equals(getTargetContext().getPackageName()), "Wrong target.");
            check(Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish"), "Disposable emulator only.");
            String phase = arguments.getString("phase");
            step = phase;
            if ("unconsented".equals(phase) || "competing-retained".equals(phase)) unconsented();
            else if ("lifecycle".equals(phase)) lifecycle();
            else if ("compete".equals(phase)) compete();
            else if ("enable".equals(phase)) { ready(); manual(); }
            else if ("off".equals(phase)) { ready(); openControls(); if (UpdateBlockPolicy.enabled(prefs().getAll())) clickTag("update-protection:updates"); awaitActive(false); }
            else if (!"read".equals(phase)) throw new AssertionError("Unknown phase.");
            result.putBoolean("saved_updates", UpdateBlockPolicy.enabled(prefs().getAll()));
            result.putBoolean("saved_appstore", UpdateBlockPolicy.includesAppstore(prefs().getAll()));
            result.putBoolean("active", UpdateBlockService.active());
            result.putBoolean("system_consent_seen", systemConsentSeen);
            result.putString("service_status", UpdateBlockService.status());
            result.putBoolean("foreground_service_start_denied", UpdateBlockService.foregroundDenied(getTargetContext()));
            result.putString("status", "PASS");
            result.putString("scope", "Actual service and consent UI; synthetic completed source startup, not legacy migration.");
        } catch (Throwable failure) {
            result.putString("status", "FAIL"); result.putString("step", step); result.putString("reason", failure.toString());
            AccessibilityNodeInfo window = getUiAutomation().getRootInActiveWindow();
            if (window != null) {
                result.putString("window_package", String.valueOf(window.getPackageName()));
                result.putString("window_text", windowText(window)); window.recycle();
            }
        }
        finish("PASS".equals(result.getString("status")) ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
    private SharedPreferences prefs() { return getTargetContext().getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE); }
    private void ready() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        SourceStartup.start(getTargetContext(), new Runnable() { @Override public void run() { latch.countDown(); } });
        check(latch.await(8, TimeUnit.SECONDS) && SourceStartup.ready(getTargetContext()), "Actual source startup did not become ready.");
    }
    private void unconsented() throws Exception {
        ready();
        final CountDownLatch latch = new CountDownLatch(1);
        SourceStartup.startProtection(getTargetContext(), new Runnable() { @Override public void run() { latch.countDown(); } });
        check(latch.await(5, TimeUnit.SECONDS), "Retained start did not complete its dispatch.");
        SystemClock.sleep(1500);
        check(!UpdateBlockService.active(), "Never-consented fixture unexpectedly established a VPN.");
        check(UpdateBlockPolicy.enabled(prefs().getAll()), "Unavailable authorization cleared saved On choice.");
        AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
        check(root == null || !"com.android.vpndialogs".contentEquals(root.getPackageName()), "Retained start opened consent UI.");
        if (root != null) root.recycle();
    }
    private void lifecycle() throws Exception {
        ready(); manual();
        step = "explicit Off closes actual service tunnel";
        clickTag("update-protection:updates"); awaitActive(false);
        check(!UpdateBlockPolicy.enabled(prefs().getAll()), "Off was not saved.");
        step = "explicit On restores service after consent";
        manual();
        step = "include Appstore changes actual scope";
        if (!UpdateBlockPolicy.includesAppstore(prefs().getAll())) {
            clickTag("update-protection:appstore"); clickText("Include");
        }
        long end = SystemClock.uptimeMillis() + 6000;
        while (SystemClock.uptimeMillis() < end && !UpdateBlockService.status().contains("Blocking 2 ")) SystemClock.sleep(50);
        check(UpdateBlockService.active() && UpdateBlockService.status().contains("Blocking 2 "), "Appstore choice did not rebuild the two-package scope.");
    }
    private void manual() throws Exception {
        stage("open native protection controls");
        openControls();
        stage("open native warning");
        clickTag(UpdateBlockPolicy.enabled(prefs().getAll()) ? "update-protection:retry" : "update-protection:updates");
        clickText("Continue");
        stage("accept platform consent when required");
        acceptConsentIfPresent(true);
        awaitActive(true);
        check(UpdateBlockPolicy.enabled(prefs().getAll()), "Explicit consent did not preserve On.");
    }
    private void compete() throws Exception {
        ready();
        getTargetContext().startForegroundService(new Intent().setClassName(TARGET, TARGET + ".ProtectionService")
                .setAction(UpdateBlockService.RETAINED_START));
        awaitActive(true);
        getTargetContext().startActivity(new Intent().setClassName(PROOF, PROOF + ".CompetingActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        clickText("Start competing VPN"); acceptConsentIfPresent(false);
        awaitActive(false);
        check(UpdateBlockPolicy.enabled(prefs().getAll()) && UpdateBlockPolicy.includesAppstore(prefs().getAll()), "Revoke changed retained saved choices.");
        check(UpdateBlockService.status().contains("revoked"), "Actual onRevoke was not observed.");
    }
    private void openControls() {
        if (activity != null && !activity.isFinishing() && activity.hasWindowFocus()) return;
        activity = startActivitySync(new Intent().setClassName(TARGET, TARGET + ".nativeapp.device.updates.UpdateBlockActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)); waitForIdleSync();
        long end = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < end && !activity.hasWindowFocus()) SystemClock.sleep(30);
        check(activity.hasWindowFocus(), "Update protection controls are not foreground.");
    }
    private void clickTag(final String tag) {
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
        final View[] target = new View[1];
        long end = SystemClock.uptimeMillis() + 6000;
        final boolean[] focused = new boolean[1];
        while (SystemClock.uptimeMillis() < end && !focused[0]) {
            runOnMainSync(new Runnable() { @Override public void run() {
                target[0] = activity.getWindow().getDecorView().findViewWithTag(tag);
                focused[0] = target[0] != null && target[0].requestFocus();
            } });
            if (!focused[0]) SystemClock.sleep(40);
        }
        check(focused[0], "Missing focus target " + tag);
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER); waitForIdleSync();
    }
    private void acceptConsentIfPresent(boolean allowActive) {
        long end = SystemClock.uptimeMillis() + 2500;
        while (SystemClock.uptimeMillis() < end) {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            boolean consent = root != null && "com.android.vpndialogs".contentEquals(root.getPackageName());
            if (root != null) root.recycle();
            if (consent) { systemConsentSeen = true; clickText("OK"); return; }
            if (allowActive && UpdateBlockService.active()) return;
            SystemClock.sleep(40);
        }
    }
    private void clickText(String text) {
        long end = SystemClock.uptimeMillis() + 6000;
        while (SystemClock.uptimeMillis() < end) {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text)) {
                    boolean exact = node.getText() != null && text.equalsIgnoreCase(String.valueOf(node.getText())) && node.isEnabled() && node.isClickable();
                    if (exact && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { node.recycle(); root.recycle(); waitForIdleSync(); return; }
                    node.recycle();
                }
                root.recycle();
            }
            SystemClock.sleep(40);
        }
        throw new AssertionError("Missing native UI action " + text);
    }
    private void awaitActive(boolean wanted) {
        long end = SystemClock.uptimeMillis() + 7000;
        while (SystemClock.uptimeMillis() < end && UpdateBlockService.active() != wanted) SystemClock.sleep(40);
        check(UpdateBlockService.active() == wanted, "Actual VPN state did not become " + wanted + ": " + UpdateBlockService.status());
    }
    private void stage(String value) { step = value; Bundle progress = new Bundle(); progress.putString("stage", value); sendStatus(1, progress); }
    private static String windowText(AccessibilityNodeInfo node) {
        StringBuilder text = new StringBuilder();
        if (node.getText() != null) text.append(node.getText()).append(" | ");
        for (int index = 0; index < node.getChildCount() && text.length() < 2000; index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child != null) { text.append(windowText(child)); child.recycle(); }
        }
        return text.toString();
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
