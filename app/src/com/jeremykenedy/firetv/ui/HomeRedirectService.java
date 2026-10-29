package com.jeremykenedy.firetv.ui;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * Sends the Home button to the Fire TV UI launcher. Fire OS has no normal
 * Home chooser. The existing control service requests hardware key filtering;
 * this fallback watches for the Amazon home screen coming to the front
 * and opens Fire TV UI over it.
 *
 * Fire OS opens Settings by showing the home screen first and putting
 * Settings on top a moment later, and Settings is opened from Fire TV UI's own gear
 * icon. So when the home screen appears straight after Fire TV UI or Amazon's
 * settings app, the redirect waits briefly and is called off if another
 * Amazon launcher screen arrives. Coming from any other app there is nothing
 * to wait for, so the redirect is immediate. That is what keeps Settings
 * reachable without slowing Home down.
 */
public class HomeRedirectService extends AccessibilityService {
    private static final String TAG = "FireTvHomeRedirect";

    private static final String AMAZON_LAUNCHER = "com.amazon.tv.launcher";

    private static final String AMAZON_HOME_PREFIX =
            "com.amazon.tv.launcher.ui.HomeActivity";

    private static final String AMAZON_SETTINGS_PREFIX =
            "com.amazon.tv.settings";

    private static final String TARGET_LAUNCHER = "com.jeremykenedy.firetv.ui";

    private static final long SETTLE_MS = 550;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable redirect = new Runnable() {
        @Override
        public void run() {
            waiting = false;
            openTargetLauncher();
        }
    };

    private boolean waiting;

    private boolean settingsMayFollow;

    private EquipmentDialogRecovery equipmentDialogs;

    @Override
    protected void onServiceConnected() {
        equipmentDialogs = new EquipmentDialogRecovery(this);
        equipmentDialogs.check();
    }

    /**
     * Fire TV UI's gear icon hands off to Amazon's settings app, which is what
     * brings the home screen up.
     */
    static boolean canOpenSettings(CharSequence pkg) {
        return pkg != null && pkg.toString().startsWith(AMAZON_SETTINGS_PREFIX);
    }

    static boolean isHome(CharSequence pkg, CharSequence cls) {
        if (cls == null) {
            return false;
        }
        return AMAZON_LAUNCHER.contentEquals(pkg)
                && cls.toString().startsWith(AMAZON_HOME_PREFIX)
                || "com.google.android.tvlauncher".contentEquals(pkg)
                && "com.google.android.tvlauncher.MainActivity".contentEquals(cls)
                || "com.google.android.leanbacklauncher".contentEquals(pkg)
                && "com.google.android.leanbacklauncher.MainActivity".contentEquals(cls)
                || "com.google.android.apps.tv.launcherx".contentEquals(pkg)
                && cls.toString().startsWith("com.google.android.apps.tv.launcherx.home.HomeActivity");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (FactoryHome.bypass(event)) {
            cancelWait();
            return;
        }
        CharSequence pkg = event.getPackageName();
        if (equipmentDialogs != null && "com.amazon.tv.devicecontrolsettings".contentEquals(
                pkg == null ? "" : pkg)) {
            equipmentDialogs.check();
        }
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || pkg == null) {
            return;
        }
        if (!isHome(pkg, event.getClassName())) {
            cancelWait();
            settingsMayFollow = canOpenSettings(pkg);
            return;
        }
        if (waiting) {
            return;
        }
        if (settingsMayFollow) {
            waiting = true;
            handler.postDelayed(redirect, SETTLE_MS);
        } else {
            openTargetLauncher();
        }
    }

    private void cancelWait() {
        waiting = false;
        handler.removeCallbacks(redirect);
    }

    private void openTargetLauncher() {
        Log.i(TAG, "Window Home dispatch");
        try {
            Intent launch =
                    getPackageManager().getLaunchIntentForPackage(TARGET_LAUNCHER);
            if (launch == null) {
                Log.w(TAG, "Window Home unavailable");
                return;
            }
            launch.addCategory(Intent.CATEGORY_HOME);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(launch);
            Log.i(TAG, "Window Home launched");
        } catch (RuntimeException failed) {
            // Leave system Home available and the accessibility callback alive.
            Log.w(TAG, "Window Home launch failed");
        }
    }

    @Override
    public void onInterrupt() {
        cancelWait();
    }

    @Override
    public void onDestroy() {
        cancelWait();
        if (equipmentDialogs != null) {
            equipmentDialogs.stop();
        }
        super.onDestroy();
    }
}
