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
        HomeRoutingPolicy quickReturn = new HomeRoutingPolicy();
        quickReturn.opened(1000);
        quickReturn.launcherVisibility(true);
        quickReturn.launcherVisibility(false);
        check(!quickReturn.launchPending(1400) && quickReturn.shouldOpen(true, 1400),
                "A completed Home launch cannot swallow another Home after the user enters a different app");
        MemoryStore store = new MemoryStore();
        HomeRoutingPolicy firstProcess = new HomeRoutingPolicy(store);
        check(firstProcess.beginHandoff(), "Handoff must be durable before dispatch");
        HomeRoutingPolicy restarted = new HomeRoutingPolicy(store);
        check(!restarted.shouldOpen(true, 1000000), "Process restart retains native escape");
        check(restarted.homePressed(), "Explicit Home durably clears native escape");
        check(new HomeRoutingPolicy(store).shouldOpen(true, 1000001), "Cleared state survives restart");
        store.fail = true;
        check(!restarted.beginHandoff(), "Failed arm must abort dispatch");
        check(!restarted.shouldOpen(true, 1000002), "Persistence errors protect system UI");
        check(!restarted.homePressed(), "Failed clear must not enable redirect");
        check(!restarted.shouldOpen(true, 1000003), "Failed clear remains exempt");
        store.fail = false;
        restarted.launcherVisibility(true);
        restarted.launcherVisibility(false);
        check(restarted.shouldOpen(true, 1000004), "Returning Main can recover failed persistence");
        System.out.println("Home routing policy checks passed");
    }
    private static final class MemoryStore implements HomeRoutingPolicy.HandoffPersistence {
        boolean active;
        boolean fail;
        @Override public boolean read() { return active; }
        @Override public boolean write(boolean value) { if (fail) return false; active = value; return true; }
    }
}
