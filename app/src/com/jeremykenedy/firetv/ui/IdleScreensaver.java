package com.jeremykenedy.firetv.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/** Starts the selected saver without changing its selection, enabled state or timers. */
public final class IdleScreensaver {
    private IdleScreensaver() {
    }

    public static void start(Context context) {
        String selected = DeviceSettings.active(context.getContentResolver());
        if (!DeviceSettings.isEnabled(context.getContentResolver())) {
            return;
        }
        try {
            ComponentCheck.requireInstalledDream(context, selected);
            String registered = "";
            String preview = "";
            for (ScreensaverCatalog.Entry entry : ScreensaverCatalog.load(context)) {
                if (Screensavers.fullComponent(selected).equals(
                        Screensavers.fullComponent(entry.dream))) {
                    registered = entry.dream;
                    preview = entry.preview;
                    break;
                }
            }
            for (String target : IdleDispatch.targets(Build.VERSION.SDK_INT, true,
                    selected, registered, preview)) {
                try {
                    Intent intent = new Intent().setComponent(
                            ComponentName.unflattenFromString(target));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY
                            | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                    context.startActivity(intent);
                    return;
                } catch (RuntimeException unsupported) {
                    Log.w("FireTvIdle", "Selected screensaver launch path unavailable.");
                }
            }
        } catch (Exception unavailable) {
            Log.w("FireTvIdle", "Selected screensaver unavailable; leaving system idle handling active.");
        }
    }
}
