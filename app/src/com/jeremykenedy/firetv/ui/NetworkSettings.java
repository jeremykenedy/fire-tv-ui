package com.jeremykenedy.firetv.ui;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

final class NetworkSettings {
    private NetworkSettings() {}

    static boolean openAvailable(Context context, String[] actions) {
        PackageManager packages = context.getPackageManager();
        for (String action : actions) {
            Intent intent = new Intent(action);
            try {
                ResolveInfo resolved = packages.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
                ActivityInfo activity = resolved == null ? null : resolved.activityInfo;
                if (activity == null || !activity.exported || !activity.enabled
                        || activity.applicationInfo == null || !activity.applicationInfo.enabled
                        || activity.permission != null && context.checkSelfPermission(activity.permission)
                            != PackageManager.PERMISSION_GRANTED) continue;
                context.startActivity(intent);
                return true;
            } catch (android.content.ActivityNotFoundException | SecurityException unavailable) {
                // Try the next public network settings route on this TV.
            }
        }
        return false;
    }
}
