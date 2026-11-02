package com.jeremykenedy.firetv.ui.nativeapp.widget.feeds;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.widget.Toast;
import com.jeremykenedy.firetv.ui.nativeapp.locks.LauncherLocks;

/** An item can propose only an ordinary HTTPS browser link, opened after explicit user confirmation. */
public final class FeedLinks {
    private FeedLinks() {}
    public static void confirm(final Activity activity, FeedSource source, final FeedParser.Item item) {
        confirm(activity, source, item, null);
    }
    public static void confirm(final Activity activity, FeedSource source, final FeedParser.Item item, final Runnable hide) {
        final String host;
        try { host = FeedSource.validateUrl(item.url).getHost(); }
        catch (IllegalArgumentException unsafe) { unavailable(activity); return; }
        AlertDialog.Builder builder = new AlertDialog.Builder(activity).setTitle("Open headline link?")
                .setMessage(item.title + "\n\nSource: " + source.display() + "\nLink host: " + host)
                .setNegativeButton("Cancel", null).setPositiveButton("Open link", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { open(activity, item.url); }
                });
        if (hide != null) builder.setNeutralButton("Hide headline", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) { hide.run(); }
        });
        builder.show();
    }
    private static Intent resolve(Activity activity, String url) {
        try {
            FeedSource.validateUrl(url);
            Intent request = new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE);
            ResolveInfo result = activity.getPackageManager().resolveActivity(request, PackageManager.MATCH_DEFAULT_ONLY);
            ActivityInfo info = result == null ? null : result.activityInfo;
            if (info == null || !info.enabled || !info.exported || info.applicationInfo == null || !info.applicationInfo.enabled
                    || (info.permission != null && !info.permission.isEmpty()) || activity.getPackageName().equals(info.packageName)) return null;
            // A system resolver is not a pinned destination. Require a matching actual HTTPS handler.
            boolean listed = false;
            for (ResolveInfo candidate : activity.getPackageManager().queryIntentActivities(request, PackageManager.MATCH_DEFAULT_ONLY)) {
                if (candidate.activityInfo != null && info.packageName.equals(candidate.activityInfo.packageName)
                        && info.name.equals(candidate.activityInfo.name)) listed = true;
            }
            return listed ? request.setComponent(new ComponentName(info.packageName, info.name)) : null;
        } catch (RuntimeException unavailable) { return null; }
    }
    private static void open(final Activity activity, final String url) {
        final Intent target = resolve(activity, url);
        if (target == null) { unavailable(activity); return; }
        final String destination = target.getComponent().flattenToString();
        Intent launcher = activity.getPackageManager().getLeanbackLaunchIntentForPackage(target.getComponent().getPackageName());
        if (launcher == null) launcher = activity.getPackageManager().getLaunchIntentForPackage(target.getComponent().getPackageName());
        final String app = launcher != null && launcher.getComponent() != null ? launcher.getComponent().flattenToString() : destination;
        final Runnable dispatch = new Runnable() {
            @Override public void run() {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                Intent checked = resolve(activity, url);
                if (checked == null || !destination.equals(checked.getComponent().flattenToString())) { unavailable(activity); return; }
                try { activity.startActivity(checked); } catch (RuntimeException failed) { unavailable(activity); }
            }
        };
        LauncherLocks.guard(activity, app, new Runnable() {
            @Override public void run() {
                if (app.equals(destination)) dispatch.run();
                else LauncherLocks.guard(activity, destination, dispatch);
            }
        });
    }

    private static void unavailable(Activity activity) {
        Toast.makeText(activity, "A browser for this HTTPS link is unavailable on this TV.", Toast.LENGTH_LONG).show();
    }
}
