package com.jeremykenedy.firetv.ui.nativeapp.device;

public final class HomeRoutingPolicyTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        HomeRoutingPolicy policy = new HomeRoutingPolicy();
        check(!HomeRoutingPolicy.allowed("com.jeremykenedy.firetv.ui.preview", true), "Preview cannot take Home");
        check(!HomeRoutingPolicy.allowed(HomeRoutingPolicy.PRODUCTION_PACKAGE, null), "Absent preserves opt-out");
        check(!HomeRoutingPolicy.allowed(HomeRoutingPolicy.PRODUCTION_PACKAGE, "true"), "Wrong type cannot enable");
        check(HomeRoutingPolicy.allowed(HomeRoutingPolicy.PRODUCTION_PACKAGE, true), "Explicit production opt-in");
        check(!policy.shouldOpen(false, 0), "Routing must be opt in");
        check(policy.shouldOpen(true, 0), "First Home from an app");
        policy.opened(0);
        check(policy.launchPending(749), "Repeated Home while launch is pending stays consumed");
        check(!policy.launchPending(750), "Pending interval is bounded");
        check(!policy.shouldOpen(true, 749), "Duplicate event suppression");
        check(policy.shouldOpen(true, 750), "Later Home allowed");
        policy.launcherVisibility(true);
        check(!policy.shouldOpen(true, 900), "Repeated Home cannot recreate visible launcher");
        policy.launcherVisibility(false);
        policy.beginHandoff();
        check(!policy.shouldOpen(true, 1000), "Settings intermediate system Home must be bypassed");
        check(!policy.shouldOpen(true, 60000), "Factory UI browsing must remain available");
        policy.homePressed();
        check(policy.shouldOpen(true, 60001), "Explicit Home ends handoff");
        policy.beginHandoff();
        policy.cancelHandoff();
        check(policy.shouldOpen(true, 60001), "Failed native launch restores routing");
        policy.beginHandoff();
        policy.launcherVisibility(true);
        policy.launcherVisibility(false);
        check(policy.shouldOpen(true, 60002), "Returning to launcher ends handoff");
        check(policy.shouldOpen(true, -1), "Clock reset cannot permanently lock routing");
        System.out.println("Home routing policy checks passed");
    }
}
