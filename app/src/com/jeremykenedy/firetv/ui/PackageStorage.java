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
            long apk = installedApkBytes(info);
            try {
                StorageStatsManager stats = context.getSystemService(StorageStatsManager.class);
                StorageStats value = stats.queryStatsForPackage(
                        info.storageUuid == null ? StorageManager.UUID_DEFAULT : info.storageUuid,
                        pkg, Process.myUserHandle());
                long app = value.getAppBytes();
                long data = value.getDataBytes();
                long cache = value.getCacheBytes();
                long used = StorageValues.usedBytes(apk, app, data, cache);
                if (used >= 0) {
                    // Android includes cache in data, so add data only once.
                    return size(used) + " used  |  App " + size(app)
                            + "  |  Data " + size(data) + " (cache " + size(cache) + ")";
                }
            } catch (Exception unavailable) {
                // Restricted or inconsistent platform statistics use measured APK files below.
            }
            return apk > 0 ? "App files " + size(apk)
                    + "  |  Data and cache statistics unavailable on this TV"
                    : "Storage usage unavailable";
        } catch (Exception removed) {
            return "Storage usage unavailable";
        }
    }

    private static long installedApkBytes(ApplicationInfo info) {
        if (info.sourceDir == null) return 0;
        long apk = new File(info.sourceDir).length();
        if (apk <= 0) return 0;
        if (info.splitSourceDirs != null) {
            for (String path : info.splitSourceDirs) {
                if (path == null) return 0;
                long split = new File(path).length();
                if (split <= 0 || split > Long.MAX_VALUE - apk) return 0;
                apk += split;
            }
        }
        return apk;
    }
}
