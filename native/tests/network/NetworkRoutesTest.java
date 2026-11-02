package com.jeremykenedy.firetv.ui.nativeapp.device;

import android.content.Context;
import android.content.Intent;
import android.content.pm.*;

public final class NetworkRoutesTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static ResolveInfo handler(String name, boolean exported, boolean system, String permission) {
        ResolveInfo info = new ResolveInfo();
        info.activityInfo = new ActivityInfo();
        info.activityInfo.packageName = name;
        info.activityInfo.name = "Settings";
        info.activityInfo.exported = exported;
        info.activityInfo.permission = permission;
        info.activityInfo.applicationInfo = new ApplicationInfo();
        info.activityInfo.applicationInfo.flags = system ? ApplicationInfo.FLAG_SYSTEM : 0;
        return info;
    }
    public static void main(String[] args) {
        Context context = new Context();
        check(DeviceRoutes.open(context, DeviceRoutes.Destination.NETWORK_SETTINGS) == DeviceRoutes.Result.UNAVAILABLE
                && context.started == null, "Missing handlers do not claim to open settings");
        context.pm.add("android.settings.ETHERNET_SETTINGS", handler("fixture.private", false, true, null));
        context.pm.add("android.settings.ETHERNET_SETTINGS", handler("fixture.thirdparty", true, false, null));
        context.pm.add("android.settings.WIFI_IP_SETTINGS", handler("fixture.restricted", true, true, "denied"));
        context.pm.add("android.settings.WIFI_SETTINGS", handler("fixture.allowed", true, true, null));
        Intent route = DeviceRoutes.resolve(context, DeviceRoutes.Destination.NETWORK_SETTINGS);
        check(route != null && "fixture.allowed".equals(route.getComponent().getPackageName()),
                "Resolution skips private, non-system and restricted handlers and finds a safe fallback");
        check(DeviceRoutes.open(context, DeviceRoutes.Destination.NETWORK_SETTINGS) == DeviceRoutes.Result.STARTED
                && "fixture.allowed".equals(context.started.getComponent().getPackageName()), "Resolved destination opens");
        context.failStart = true;
        check(DeviceRoutes.open(context, DeviceRoutes.Destination.NETWORK_SETTINGS) == DeviceRoutes.Result.FAILED,
                "A launch failure reports failure rather than claiming success");

        Context fallback = new Context();
        fallback.pm.add("android.settings.ETHERNET_SETTINGS", handler("fixture.failed", true, true, null));
        fallback.pm.add("android.settings.WIFI_IP_SETTINGS", handler("fixture.failed", true, true, null));
        fallback.pm.add("android.settings.WIFI_SETTINGS", handler("fixture.private", false, true, null));
        fallback.pm.add("android.settings.WIFI_SETTINGS", handler("fixture.thirdparty", true, false, null));
        fallback.pm.add("android.settings.WIFI_SETTINGS", handler("fixture.restricted", true, true, "denied"));
        fallback.pm.add("android.settings.WIRELESS_SETTINGS", handler("fixture.allowed", true, true, null));
        fallback.failedPackages.add("fixture.failed");
        HomeRoutingPolicy policy = DeviceRoutes.policy(fallback);
        int handoffs = policy.handoffs, cancellations = policy.cancellations;
        check(DeviceRoutes.open(fallback, DeviceRoutes.Destination.NETWORK_SETTINGS) == DeviceRoutes.Result.STARTED
                && "fixture.allowed".equals(fallback.started.getComponent().getPackageName()),
                "A failed network handler falls through unsafe actions to the next safe action");
        check(fallback.attempts.size() == 2
                && "android.settings.ETHERNET_SETTINGS".equals(fallback.attempts.get(0).action)
                && "android.settings.WIRELESS_SETTINGS".equals(fallback.attempts.get(1).action),
                "A repeated component is attempted once and unsafe handlers are never launched");
        check(policy.handoffs == handoffs + 2 && policy.cancellations == cancellations + 1,
                "The failed launch cancels its handoff before the successful fallback arms one");

        Context allFail = new Context();
        allFail.pm.add("android.settings.ETHERNET_SETTINGS", handler("fixture.first", true, true, null));
        allFail.pm.add("android.settings.WIFI_IP_SETTINGS", handler("fixture.second", true, true, null));
        allFail.failStart = true;
        cancellations = policy.cancellations;
        check(DeviceRoutes.open(allFail, DeviceRoutes.Destination.NETWORK_SETTINGS) == DeviceRoutes.Result.FAILED
                && allFail.started == null && allFail.attempts.size() == 2
                && policy.cancellations == cancellations + 2,
                "When every safe action fails, each launch is tried once and failure stays explicit");

        Context unsafe = new Context();
        unsafe.pm.add("android.settings.ETHERNET_SETTINGS", handler("fixture.private", false, true, null));
        unsafe.pm.add("android.settings.WIFI_IP_SETTINGS", handler("fixture.thirdparty", true, false, null));
        unsafe.pm.add("android.settings.WIFI_SETTINGS", handler("fixture.restricted", true, true, "denied"));
        check(DeviceRoutes.open(unsafe, DeviceRoutes.Destination.NETWORK_SETTINGS) == DeviceRoutes.Result.UNAVAILABLE
                && unsafe.attempts.isEmpty(), "Only unsafe handlers remain unavailable without launch attempts");

        policy.allowHandoff = false;
        handoffs = policy.handoffs;
        check(DeviceRoutes.open(allFail, DeviceRoutes.Destination.NETWORK_SETTINGS) == DeviceRoutes.Result.FAILED
                && allFail.attempts.size() == 2 && policy.handoffs == handoffs + 1,
                "A handoff persistence failure never attempts a launch or retries another action");
        policy.allowHandoff = true;

        Context settings = new Context();
        settings.pm.add("settings", handler("fixture.failed", true, true, null));
        settings.pm.add("android.settings.WIRELESS_SETTINGS", handler("fixture.allowed", true, true, null));
        settings.failStart = true;
        check(DeviceRoutes.open(settings, DeviceRoutes.Destination.SYSTEM_SETTINGS) == DeviceRoutes.Result.FAILED
                && settings.attempts.size() == 1, "General Settings retains its single launch behavior");
        System.out.println("Native network settings resolution and failure behavior passed.");
    }
}
