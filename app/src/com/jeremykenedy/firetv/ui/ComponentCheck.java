package com.jeremykenedy.firetv.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;

final class ComponentCheck {
    private ComponentCheck() {
    }

    static void requireInstalledDream(Context context, String component) {
        ComponentName name = ComponentName.unflattenFromString(component == null ? "" : component);
        if (name == null) {
            throw new IllegalArgumentException("Choose an installed screensaver.");
        }
        try {
            ServiceInfo service = context.getPackageManager().getServiceInfo(name, 0);
            if (!service.enabled || !service.applicationInfo.enabled
                    || !Screensavers.isScreensaver(service.permission)) {
                throw new IllegalStateException("The selected screensaver is disabled.");
            }
        } catch (PackageManager.NameNotFoundException error) {
            throw new IllegalStateException("The selected screensaver was uninstalled. Choose another screensaver.");
        }
    }
}
