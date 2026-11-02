package com.jeremykenedy.firetv.ui.nativeapp.device.protection;

import android.content.Context;
import android.view.View;

/** Only affects the launcher's visible view. It never changes a system timeout or acquires a wake lock. */
public final class LauncherAwakeController {
    private final View view;
    private boolean changed;
    private boolean previous;
    public LauncherAwakeController(View view) { this.view = view; }
    public void onResume(Context context) {
        onPause();
        if (ProtectionPreferences.keepLauncherAwake(context)) {
            previous = view.getKeepScreenOn();
            if (!previous) {
                view.setKeepScreenOn(true);
                changed = true;
            }
        }
    }
    public void onPause() {
        if (changed) {
            if (view.getKeepScreenOn()) view.setKeepScreenOn(previous);
            changed = false;
        }
    }
}
