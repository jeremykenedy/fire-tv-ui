package com.jeremykenedy.firetv.ui.nativeapp.device;

/** Process-local routing state shared by explicit handoffs and the key service. */
public final class HomeRoutingPolicy {
    public static final String PRODUCTION_PACKAGE = "com.jeremykenedy.firetv.ui";

    public static boolean allowed(String applicationId, Object preference) {
        return PRODUCTION_PACKAGE.equals(applicationId) && Boolean.TRUE.equals(preference);
    }

    private boolean handoff;
    private boolean homeVisible;
    private long lastLaunch = -1;

    public synchronized void beginHandoff() { handoff = true; }
    public synchronized void cancelHandoff() { handoff = false; }
    public synchronized void homePressed() { handoff = false; }
    public synchronized void launcherVisibility(boolean visible) {
        homeVisible = visible;
        if (visible) handoff = false;
    }

    public synchronized boolean shouldOpen(boolean enabled, long now) {
        return enabled && !handoff && !homeVisible
                && (lastLaunch < 0 || now < lastLaunch || now - lastLaunch >= 750);
    }

    public synchronized boolean launchPending(long now) {
        return lastLaunch >= 0 && now >= lastLaunch && now - lastLaunch < 750;
    }

    public synchronized void opened(long now) { lastLaunch = now; }
    public synchronized boolean isHomeVisible() { return homeVisible; }
}
