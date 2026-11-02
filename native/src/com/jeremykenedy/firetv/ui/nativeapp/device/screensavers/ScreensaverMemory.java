package com.jeremykenedy.firetv.ui.nativeapp.device.screensavers;

import android.app.ActivityManager;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Process;
import android.os.storage.StorageManager;
import java.io.File;
import java.util.List;
import java.util.Locale;

/** Measurements are scoped to installed saver packages; process visibility is never treated as complete. */
public final class ScreensaverMemory {
    private ScreensaverMemory() {}
    public static String size(long bytes) {
        if (bytes < 0) return "Unavailable";
        if (bytes < 1024) return bytes + " B";
        return String.format(Locale.ROOT, bytes < 1048576 ? "%.1f KB" : "%.1f MB",
                bytes / (bytes < 1048576 ? 1024.0 : 1048576.0));
    }
    public static String storage(Context context, String packageName) {
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(packageName, 0);
            long apk = info.sourceDir == null ? 0 : new File(info.sourceDir).length();
            if (info.splitSourceDirs != null) for (String path : info.splitSourceDirs) {
                if (path != null) apk += new File(path).length();
            }
            try {
                StorageStatsManager manager = context.getSystemService(StorageStatsManager.class);
                StorageStats stats = manager.queryStatsForPackage(info.storageUuid == null ? StorageManager.UUID_DEFAULT
                        : info.storageUuid, packageName, Process.myUserHandle());
                return "App " + size(stats.getAppBytes()) + " | Data " + size(stats.getDataBytes())
                        + " (includes cache " + size(stats.getCacheBytes()) + ")";
            } catch (Exception restricted) {
                return "Installed app files " + size(apk) + " | Data/cache unavailable without storage statistics access";
            }
        } catch (Exception removed) { return "Storage unavailable: package removed or restricted"; }
    }
    public static String process(Context context, String packageName) {
        try {
            ActivityManager manager = context.getSystemService(ActivityManager.class);
            List<ActivityManager.RunningAppProcessInfo> processes = manager.getRunningAppProcesses();
            if (processes != null) for (ActivityManager.RunningAppProcessInfo process : processes) {
                if (process.pkgList == null) continue;
                for (String pkg : process.pkgList) if (packageName.equals(pkg)) {
                    return "Visible running process: " + process.processName + " | PID " + process.pid
                            + " | RAM measurement restricted by Android for other apps";
                }
            }
        } catch (RuntimeException restricted) { }
        return "Process state unavailable: Android restricts visibility of other apps";
    }
}
