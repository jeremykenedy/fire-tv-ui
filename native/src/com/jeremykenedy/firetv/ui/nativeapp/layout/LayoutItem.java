package com.jeremykenedy.firetv.ui.nativeapp.layout;

/** Stable identity belongs to the producer, not its current title or artwork. */
public final class LayoutItem {
    public enum Kind { CHANNEL_CARD, WIDGET }
    public final String id;
    public final Kind kind;

    public LayoutItem(String id, Kind kind) {
        if (kind == null || !validId(id)) throw new IllegalArgumentException("Invalid layout item.");
        this.id = id;
        this.kind = kind;
    }

    public String key() { return (kind == Kind.CHANNEL_CARD ? "channel:" : "widget:") + id; }

    static boolean validKey(String value) {
        if (value == null) return false;
        if (value.startsWith("channel:")) return validId(value.substring(8));
        if (value.startsWith("widget:")) return validId(value.substring(7));
        return false;
    }

    private static boolean validId(String value) {
        if (value == null || value.length() == 0 || value.length() > 200) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(c >= 'a' && c <= 'z') && !(c >= 'A' && c <= 'Z')
                    && !(c >= '0' && c <= '9') && ".:_/-".indexOf(c) < 0) return false;
        }
        return true;
    }
}
