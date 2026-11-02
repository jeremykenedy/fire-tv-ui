package com.jeremykenedy.firetv.ui.nativeapp.device;

/** Home routing decisions, including a durable exemption for explicit native handoffs. */
public final class HomeRoutingPolicy {
    public static final String PRODUCTION_PACKAGE = "com.jeremykenedy.firetv.ui";

    public interface HandoffPersistence {
        boolean read();
        boolean write(boolean active);
    }

    public static boolean allowed(String applicationId, Object preference) {
        return PRODUCTION_PACKAGE.equals(applicationId) && Boolean.TRUE.equals(preference);
    }

    private final HandoffPersistence persistence;
    private boolean handoff;
    private boolean homeVisible;
    private long lastLaunch = -1;

    public HomeRoutingPolicy() { this(null); }
    public HomeRoutingPolicy(HandoffPersistence persistence) {
        this.persistence = persistence;
        handoff = persistence != null && persistence.read();
    }

    private boolean handoff(boolean active) {
        if (persistence != null && !persistence.write(active)) {
            // A failed clear must never make a previously exempt native screen redirectable.
            handoff = true;
            return false;
        }
        handoff = active;
        return true;
    }

    public synchronized boolean beginHandoff() { return handoff(true); }
    public synchronized boolean cancelHandoff() { return handoff(false); }
    public synchronized boolean homePressed() { return handoff(false); }
    public synchronized void launcherVisibility(boolean visible) {
        homeVisible = visible;
        if (visible && handoff) handoff(false);
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
