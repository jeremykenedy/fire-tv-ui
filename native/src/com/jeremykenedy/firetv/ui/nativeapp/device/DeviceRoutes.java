package com.jeremykenedy.firetv.ui.nativeapp.device;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.Settings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Explicit, capability-checked native destinations. Resolution never changes device settings. */
public final class DeviceRoutes {
    public enum Destination { FACTORY_UI, SYSTEM_SETTINGS, LIVE_TV, NETWORK_SETTINGS }
    public enum Result { STARTED, UNAVAILABLE, FAILED }
    private static final String[] NETWORK_SETTINGS_ACTIONS = {"android.settings.ETHERNET_SETTINGS",
            Settings.ACTION_WIFI_IP_SETTINGS, Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS};
    private static HomeRoutingPolicy routing;
    static synchronized HomeRoutingPolicy policy(Context context) {
        if (routing == null) routing = new HomeRoutingPolicy(new HomeHandoffStore(context.getApplicationContext()));
        return routing;
    }
    private DeviceRoutes() {}

    public static Intent resolve(Context context, Destination destination) {
        try {
            return resolveAvailable(context, destination);
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    private static Intent resolveAvailable(Context context, Destination destination) {
        PackageManager manager = context.getPackageManager();
        if (destination == Destination.FACTORY_UI) {
            return firstSystem(context, new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), null);
        }
        if (destination == Destination.SYSTEM_SETTINGS) {
            return firstSystem(context, new Intent(Settings.ACTION_SETTINGS), null);
        }
        if (destination == Destination.NETWORK_SETTINGS) {
            for (String action : NETWORK_SETTINGS_ACTIONS) {
                Intent network = firstSystem(context, new Intent(action), null);
                if (network != null) return network;
            }
            return null;
        }
        if (destination == Destination.LIVE_TV) {
            Intent guide = firstSystem(context, new Intent(Intent.ACTION_VIEW,
                    Uri.parse("livetv://showGuideButtonContent")).setPackage("com.amazon.tv.livetv"),
                    "com.amazon.tv.livetv");
            if (guide != null) return guide;
            for (String pkg : new String[] {"com.android.tv", "com.google.android.tv"}) {
                Intent launch = manager.getLeanbackLaunchIntentForPackage(pkg);
                if (launch == null) launch = manager.getLaunchIntentForPackage(pkg);
                if (launch != null) {
                    Intent resolved = firstSystem(context, launch, pkg);
                    if (resolved != null) return resolved;
                }
            }
        }
        return null;
    }

    static Intent firstSystem(Context context, Intent request, String requiredPackage) {
        PackageManager manager = context.getPackageManager();
        List<ResolveInfo> candidates = manager.queryIntentActivities(request, 0);
        List<ResolveInfo> matches = new ArrayList<>(candidates);
        Collections.sort(matches, new Comparator<ResolveInfo>() {
            @Override public int compare(ResolveInfo left, ResolveInfo right) {
                String a = left.activityInfo == null ? "" : left.activityInfo.packageName + "/" + left.activityInfo.name;
                String b = right.activityInfo == null ? "" : right.activityInfo.packageName + "/" + right.activityInfo.name;
                return a.compareTo(b);
            }
        });
        // Prefer the selected system Home when one exists; otherwise use a stable candidate.
        ResolveInfo preferred = manager.resolveActivity(request, 0);
        if (preferred != null) matches.add(0, preferred);
        for (ResolveInfo match : matches) {
            ActivityInfo activity = match.activityInfo;
            if (activity == null || activity.applicationInfo == null || !activity.exported
                    || context.getPackageName().equals(activity.packageName)
                    || (requiredPackage != null && !requiredPackage.equals(activity.packageName))
                    || (activity.applicationInfo.flags & (ApplicationInfo.FLAG_SYSTEM
                        | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) == 0
                    || !activity.applicationInfo.enabled
                    || (activity.permission != null && context.checkSelfPermission(activity.permission)
                        != PackageManager.PERMISSION_GRANTED)) continue;
            ComponentName component = new ComponentName(activity.packageName, activity.name);
            int state = manager.getComponentEnabledSetting(component);
            if (state != PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    && (state != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT || !activity.enabled)) continue;
            // A resolver/chooser returned only by resolveActivity must never become the destination.
            boolean listed = false;
            for (ResolveInfo listedInfo : candidates) {
                if (listedInfo.activityInfo != null && activity.packageName.equals(listedInfo.activityInfo.packageName)
                        && activity.name.equals(listedInfo.activityInfo.name)) { listed = true; break; }
            }
            if (!listed) continue;
            return new Intent(request).setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        return null;
    }

    public static Result open(Context context, Destination destination) {
        if (destination == Destination.NETWORK_SETTINGS) return openNetworkSettings(context);
        return dispatch(context, resolve(context, destination));
    }

    private static Result openNetworkSettings(Context context) {
        List<ComponentName> attempted = new ArrayList<>();
        boolean failed = false;
        for (String action : NETWORK_SETTINGS_ACTIONS) {
            Intent intent;
            try {
                intent = firstSystem(context, new Intent(action), null);
            } catch (RuntimeException unavailable) {
                continue;
            }
            if (intent == null || attempted.contains(intent.getComponent())) continue;
            attempted.add(intent.getComponent());
            try {
                if (!policy(context).beginHandoff()) return Result.FAILED;
            } catch (RuntimeException handoffFailed) {
                policy(context).cancelHandoff();
                return Result.FAILED;
            }
            try {
                context.startActivity(intent);
                return Result.STARTED;
            } catch (RuntimeException launchFailed) {
                policy(context).cancelHandoff();
                failed = true;
            }
        }
        return failed ? Result.FAILED : Result.UNAVAILABLE;
    }

    /** Validate an already resolved system destination before arming the shared Home exemption. */
    public static Result openSystemIntent(Context context, Intent request) {
        if (request == null || request.getComponent() == null) return Result.UNAVAILABLE;
        try {
            return dispatch(context, firstSystem(context, request, request.getComponent().getPackageName()));
        } catch (RuntimeException unavailable) {
            return Result.FAILED;
        }
    }

    private static Result dispatch(Context context, Intent intent) {
        if (intent == null) return Result.UNAVAILABLE;
        try {
            // Persist before Android can display Settings' intermediate system Home window.
            if (!policy(context).beginHandoff()) return Result.FAILED;
            context.startActivity(intent);
            return Result.STARTED;
        } catch (RuntimeException failed) {
            policy(context).cancelHandoff();
            return Result.FAILED;
        }
    }

    public static void launcherVisibility(Context context, boolean visible) {
        policy(context).launcherVisibility(visible);
    }

    /** Compatibility for callers while their lifecycle integration is updated. */
    public static synchronized void launcherVisibility(boolean visible) {
        if (routing != null) routing.launcherVisibility(visible);
    }
}
