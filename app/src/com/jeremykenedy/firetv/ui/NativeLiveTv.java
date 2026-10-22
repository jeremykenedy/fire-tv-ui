package com.jeremykenedy.firetv.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import java.util.List;

/** The public native guide entry; the underlying guide activity is privileged. */
public final class NativeLiveTv {
    private static final String PACKAGE = "com.amazon.tv.livetv";
    private static final String GUIDE = "livetv://showGuideButtonContent";

    private NativeLiveTv() {
    }

    private static Intent intent() {
        return new Intent(Intent.ACTION_VIEW, Uri.parse(GUIDE)).setPackage(PACKAGE);
    }

    private static ResolveInfo resolve(PackageManager manager) {
        ResolveInfo info = manager.resolveActivity(intent(), 0);
        if (info == null || info.activityInfo == null || !info.activityInfo.exported
                || !info.activityInfo.enabled || !PACKAGE.equals(info.activityInfo.packageName)
                || info.activityInfo.permission != null
                || (info.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) return null;
        return info;
    }

    public static void addTile(List<ResolveInfo> apps, PackageManager manager) {
        ResolveInfo entry = resolve(manager);
        if (entry == null) return;
        for (int i = apps.size() - 1; i >= 0; i--) {
            if (apps.get(i).activityInfo != null && PACKAGE.equals(apps.get(i).activityInfo.packageName)) apps.remove(i);
        }
        entry.nonLocalizedLabel = "Live TV";
        apps.add(entry);
    }

    public static Intent launchIntent(PackageManager manager, String pkg) {
        if (!PACKAGE.equals(pkg)) return null;
        ResolveInfo entry = resolve(manager);
        return entry == null ? null : intent().setComponent(new ComponentName(
                entry.activityInfo.packageName, entry.activityInfo.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    public static boolean open(Context context, String pkg) {
        if (!PACKAGE.equals(pkg)) return false;
        ResolveInfo entry = resolve(context.getPackageManager());
        if (entry == null) throw new IllegalStateException("The native Live TV entry is unavailable.");
        context.startActivity(intent().setComponent(new ComponentName(entry.activityInfo.packageName,
                entry.activityInfo.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        return true;
    }
}
