package com.jeremykenedy.firetv.ui.nativeapp.inputs;

public final class InputEntry {
    public enum State { CONNECTED, STANDBY, DISCONNECTED, MISSING, UNAVAILABLE }
    public final String id;
    public final String factoryName;
    public final String displayName;
    public final State state;
    public final boolean passthrough;
    public final boolean hidden;

    public InputEntry(String id, String factoryName, String displayName, State state, boolean passthrough) {
        this(id, factoryName, displayName, state, passthrough, false);
    }

    public InputEntry(String id, String factoryName, String displayName, State state, boolean passthrough, boolean hidden) {
        if (!validId(id)) throw new IllegalArgumentException("Select a TV input.");
        this.id = id;
        this.factoryName = factoryName == null || factoryName.trim().isEmpty() ? "TV input" : factoryName;
        this.displayName = displayName == null ? this.factoryName : name(displayName);
        this.state = state;
        this.passthrough = passthrough;
        this.hidden = hidden;
    }

    public String artworkKey() { return "input:" + id; }
    public boolean canOpen() { return state == State.CONNECTED || state == State.STANDBY; }
    public String message() {
        switch (state) {
            case CONNECTED: return "Connected";
            case STANDBY: return "Standby";
            case DISCONNECTED: return "Disconnected";
            case MISSING: return "Input is no longer available. Your name and artwork are kept.";
            default: return "Input status is unavailable.";
        }
    }
    public static boolean validId(String id) {
        if (id == null || id.isEmpty() || id.length() > 2042) return false;
        for (int i = 0; i < id.length(); i++) if (Character.isISOControl(id.charAt(i))) return false;
        return true;
    }
    public static String name(String value) {
        String result = value == null ? "" : value.trim();
        if (result.isEmpty() || result.length() > 60)
            throw new IllegalArgumentException("Enter an input name of 1 to 60 characters.");
        for (int i = 0; i < result.length(); i++) if (Character.isISOControl(result.charAt(i)))
            throw new IllegalArgumentException("Use a single line for the input name.");
        return result;
    }
}
