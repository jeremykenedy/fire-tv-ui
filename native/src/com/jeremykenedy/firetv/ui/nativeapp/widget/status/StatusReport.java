package com.jeremykenedy.firetv.ui.nativeapp.widget.status;

/** A readable snapshot keeps denied access distinct from missing services or an empty result. */
public final class StatusReport {
    public enum Availability { READY, EMPTY, PARTIAL, PERMISSION_DENIED, UNAVAILABLE }
    public final Availability availability;
    public final String details;
    public final String summary;
    public StatusReport(Availability availability, String details) { this(availability, details, details); }
    public StatusReport(Availability availability, String details, String summary) {
        if (availability == null || details == null) throw new IllegalArgumentException("Missing status report.");
        this.availability = availability;
        this.details = details;
        this.summary = summary;
    }
    public String label() {
        switch (availability) {
            case READY: return "Available";
            case EMPTY: return "No connection reported";
            case PARTIAL: return "Some details unavailable";
            case PERMISSION_DENIED: return "Access denied";
            default: return "Unavailable";
        }
    }

    /** Adapt the existing NetworkSnapshot public reason strings without rescanning interfaces. */
    public static StatusReport network(String snapshot) {
        if (snapshot == null || snapshot.trim().isEmpty())
            return new StatusReport(Availability.UNAVAILABLE, "Network status was not reported.");
        Availability state;
        if (snapshot.startsWith("Network details unavailable: Android denied access.")) state = Availability.PERMISSION_DENIED;
        else if (snapshot.startsWith("Network details are unavailable") || snapshot.startsWith("Network details could not be read")) state = Availability.UNAVAILABLE;
        else if (snapshot.contains("No visible connected networks.")) state = Availability.EMPTY;
        else if (snapshot.contains("Address details unavailable.")) state = Availability.PARTIAL;
        else state = Availability.READY;
        StringBuilder brief = new StringBuilder();
        if (state == Availability.EMPTY) brief.append("No connection reported.");
        else if (state == Availability.UNAVAILABLE || state == Availability.PERMISSION_DENIED) brief.append("Unavailable");
        else for (String line : snapshot.split("\n")) {
            if (line.contains(" (default)") || line.contains(" (available)") || line.startsWith("IP / prefix:") || line.startsWith("Internet:")) {
                if (brief.length() > 0) brief.append('\n');
                brief.append(line.replace("validated by Android", "validated").replace("not validated by Android", "not validated"));
            }
        }
        return new StatusReport(state, snapshot, brief.length() == 0 ? "Unavailable" : brief.toString());
    }

    public static String mediaVolume(int current, int maximum, boolean muted, boolean fixed) {
        if (current < 0 || maximum < 0 || current > maximum) return "Media volume: not reported reliably";
        return "Media volume index: " + current + " / " + maximum + "\nMedia mute: " + (muted ? "On" : "Off")
                + "\nVolume policy: " + (fixed ? "Fixed by Android" : "Adjustable by Android");
    }
}
