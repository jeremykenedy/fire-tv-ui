package com.jeremykenedy.firetv.ui;

/** Keeps a local, allowlisted account of the most recent settings repair. */
public final class GuardReport {
    private String failure = "Not checked";

    public synchronized void healthy() {
        failure = "None";
    }

    public synchronized void failed(RuntimeException error) {
        // Exception messages can include private settings. Report only a fixed category.
        failure = error instanceof SecurityException ? "Permission denied" : "Settings repair failed";
    }

    public synchronized String snapshot(boolean protect, boolean pending, String previous,
            String desired, String current) {
        return "protect=" + protect + "\npreview_pending=" + pending
                + "\nprevious_dream=" + component(previous) + "\ndesired_dream=" + component(desired)
                + "\ncurrent_dream=" + component(current) + "\nlast_guard_failure=" + failure + "\n";
    }

    private static String component(String value) {
        if (value == null) return "None";
        return value.length() <= 512 && value.matches("[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+")
                ? value : "Unavailable";
    }
}
