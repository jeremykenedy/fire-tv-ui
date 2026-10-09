package com.jeremykenedy.firetv.ui;

import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Process;
import android.os.storage.StorageManager;
import java.io.File;
import java.util.Locale;

/** Measures local storage without collecting application usage history. */
public final class PackageStorage {
    private PackageStorage() {
    }

    public static String size(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1048576) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1073741824L) {
            return String.format(Locale.US, "%.1f MB", bytes / 1048576.0);
        }
        return String.format(Locale.US, "%.1f GB", bytes / 1073741824.0);
    }

    public static String summary(Context context, String pkg) {
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(pkg, 0);
            StorageStatsManager stats = context.getSystemService(StorageStatsManager.class);
            StorageStats value = stats.queryStatsForPackage(
                    info.storageUuid == null ? StorageManager.UUID_DEFAULT : info.storageUuid,
                    pkg, Process.myUserHandle());
            // Cache is included in data bytes; counting it twice exaggerates usage.
            return size(value.getAppBytes() + value.getDataBytes()) + " used  |  App "
                    + size(value.getAppBytes()) + "  |  Data " + size(value.getDataBytes())
                    + " (cache " + size(value.getCacheBytes()) + ")";
        } catch (Exception unavailable) {
            try {
                ApplicationInfo info = context.getPackageManager().getApplicationInfo(pkg, 0);
                long apk = new File(info.sourceDir).length();
                if (info.splitSourceDirs != null) {
                    for (String file : info.splitSourceDirs) {
                        apk += new File(file).length();
                    }
                }
                return "App files " + size(apk) + "  |  Data and cache statistics unavailable on this TV";
            } catch (Exception removed) {
                return "Storage usage unavailable";
            }
        }
    }
}
