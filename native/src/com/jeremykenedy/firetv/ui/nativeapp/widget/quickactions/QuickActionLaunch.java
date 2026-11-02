package com.jeremykenedy.firetv.ui.nativeapp.widget.quickactions;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.widget.Toast;
import com.jeremykenedy.firetv.ui.nativeapp.locks.LauncherLocks;

public final class QuickActionLaunch {
    private QuickActionLaunch() {}

    public static boolean available(Context context, QuickActions.Entry entry) { return resolve(context, entry) != null; }

    public static void open(final Activity activity, final QuickActions.Entry entry) {
        final Intent target = resolve(activity, entry);
        if (target == null) { unavailable(activity); return; }
        final String source = ComponentName.unflattenFromString(entry.component).flattenToString();
        final String destination = target.getComponent().flattenToString();
        final Runnable launch = new Runnable() {
            @Override public void run() {
                Intent checked = resolve(activity, entry);
                if (checked == null || !destination.equals(checked.getComponent().flattenToString())) { unavailable(activity); return; }
                try { activity.startActivity(checked); } catch (RuntimeException removed) { unavailable(activity); }
            }
        };
        LauncherLocks.guard(activity, source, new Runnable() {
            @Override public void run() {
                if (destination.equals(source)) launch.run();
                else LauncherLocks.guard(activity, destination, launch);
            }
        });
    }

    static Intent resolve(Context context, QuickActions.Entry entry) {
        try {
            ComponentName app = ComponentName.unflattenFromString(entry.component);
            if (app == null || !usable(context.getPackageManager().getActivityInfo(app, 0), app.getPackageName())) return null;
            if (entry.url.isEmpty()) return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setComponent(app);
            QuickActions.validateUrl(entry.url);
            Intent request = new Intent(Intent.ACTION_VIEW, Uri.parse(entry.url)).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(app.getPackageName());
            ResolveInfo found = context.getPackageManager().resolveActivity(request, PackageManager.MATCH_DEFAULT_ONLY);
            ActivityInfo info = found == null ? null : found.activityInfo;
            if (!usable(info, app.getPackageName())) return null;
            return request.setComponent(new ComponentName(info.packageName, info.name));
        } catch (PackageManager.NameNotFoundException | RuntimeException removed) { return null; }
    }

    private static boolean usable(ActivityInfo info, String source) {
        return info != null && source.equals(info.packageName) && info.exported && info.enabled
                && info.applicationInfo != null && info.applicationInfo.enabled
                && (info.permission == null || info.permission.isEmpty());
    }

    private static void unavailable(Activity activity) {
        Toast.makeText(activity, "This app or content link is unavailable. Review Quick actions settings.", Toast.LENGTH_LONG).show();
    }
}
