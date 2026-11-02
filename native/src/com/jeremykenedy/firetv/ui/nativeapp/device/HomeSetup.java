package com.jeremykenedy.firetv.ui.nativeapp.device;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;

/** Read-only setup diagnosis and explicit app preferences; never enables accessibility itself. */
public final class HomeSetup {
    public enum State { PREVIEW, OFF, REVIEW_LEGACY, NEEDS_SERVICE, NEEDS_KEY_CAPABILITY, RECOVERY_REQUIRED, READY }
    public final HomeSetupPolicy choices;
    public final State state;
    public final boolean serviceEnabled;
    public final boolean keyCapability;

    private HomeSetup(HomeSetupPolicy choices, State state, boolean serviceEnabled, boolean keyCapability) {
        this.choices = choices; this.state = state;
        this.serviceEnabled = serviceEnabled; this.keyCapability = keyCapability;
    }

    public static HomeSetup read(Context context) {
        boolean production = HomeRoutingPolicy.PRODUCTION_PACKAGE.equals(context.getPackageName());
        boolean recovered = !production || HomeCommandHandler.recover(context);
        HomeSetupPolicy choices = HomeSetupPolicy.read(context.getSharedPreferences("launcher_prefs", 0).getAll());
        boolean enabled = false;
        boolean keyCapability = false;
        AccessibilityManager manager = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager != null && manager.isEnabled()) {
            ComponentName expected = new ComponentName(context.getPackageName(), context.getPackageName() + ".Hra");
            for (AccessibilityServiceInfo service : manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
                if (expected.equals(ComponentName.unflattenFromString(service.getId()))) {
                    enabled = true;
                    keyCapability = (service.getCapabilities()
                            & AccessibilityServiceInfo.CAPABILITY_CAN_REQUEST_FILTER_KEY_EVENTS) != 0
                            && (service.flags & AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS) != 0;
                }
            }
        }
        State state;
        if (!production) state = State.PREVIEW;
        else if (!recovered) state = State.RECOVERY_REQUIRED;
        else if (choices.needsReview) state = State.REVIEW_LEGACY;
        else if (!choices.keys && !choices.windows) state = State.OFF;
        else if (!enabled) state = State.NEEDS_SERVICE;
        else if ((choices.keys || choices.windows) && !keyCapability) state = State.NEEDS_KEY_CAPABILITY;
        else state = State.READY;
        return new HomeSetup(choices, state, enabled, keyCapability);
    }

    /** Explicit user action only. Journal the three Home flags and preserve other preferences. */
    public static boolean saveChoices(Context context, boolean keys, boolean windows) {
        if (!HomeRoutingPolicy.PRODUCTION_PACKAGE.equals(context.getPackageName()) || !HomeCommandHandler.recover(context)) return false;
        try {
            HomeCommandPolicy.saveChoices(new HomeCommandStore(context), keys, windows);
            return true;
        } catch (RuntimeException failed) {
            return false;
        }
    }

    public static Intent accessibilitySettings(Context context) {
        return DeviceRoutes.firstSystem(context, new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), null);
    }
}
