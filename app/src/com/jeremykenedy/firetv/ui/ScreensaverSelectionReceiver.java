package com.jeremykenedy.firetv.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Applies a screensaver selection made by the toolkit's on-TV picker. */
public final class ScreensaverSelectionReceiver extends BroadcastReceiver {
    public static final String ACTION =
            "com.jeremykenedy.firetv.ui.SELECT_SCREENSAVER";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            if (!ACTION.equals(intent.getAction())) {
                throw new IllegalArgumentException("Unknown screensaver action.");
            }
            String component = intent.getStringExtra("component");
            if ("null".equals(component)) {
                component = null;
            }
            String enabled = intent.getStringExtra("enabled");
            if (enabled == null) {
                DeviceSettings.choose(context, component);
            } else if ("0".equals(enabled) || "1".equals(enabled)) {
                DeviceSettings.component(context, component);
                DeviceSettings.enableDream(context, "1".equals(enabled));
            } else {
                throw new IllegalArgumentException("Invalid screensaver state.");
            }
            setResultCode(0);
            setResultData("ok");
        } catch (Exception error) {
            setResultCode(1);
            setResultData(error.getClass().getName() + ": "
                    + (error.getMessage() == null
                    ? "The screensaver could not be selected."
                    : error.getMessage()));
        }
    }
}
