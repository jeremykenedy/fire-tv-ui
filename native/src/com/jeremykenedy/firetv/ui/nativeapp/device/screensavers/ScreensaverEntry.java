package com.jeremykenedy.firetv.ui.nativeapp.device.screensavers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ScreensaverEntry {
    public final String component;
    public final String label;

    public ScreensaverEntry(String component, String label) {
        this.component = normalize(component);
        this.label = label;
    }

    public static String normalize(String component) {
        if (component == null) return "";
        int slash = component.indexOf('/');
        if (slash > 0 && component.substring(slash + 1).startsWith(".")) {
            return component.substring(0, slash + 1) + component.substring(0, slash)
                    + component.substring(slash + 1);
        }
        return component;
    }

    public static String firstActive(String components) {
        return normalize(components == null ? "" : components.split(",", 2)[0]);
    }

    public static List<ScreensaverEntry> sorted(List<ScreensaverEntry> entries) {
        Map<String, ScreensaverEntry> unique = new LinkedHashMap<>();
        for (ScreensaverEntry entry : entries) unique.put(entry.component, entry);
        List<ScreensaverEntry> result = new ArrayList<>(unique.values());
        Collections.sort(result, new Comparator<ScreensaverEntry>() {
            @Override public int compare(ScreensaverEntry left, ScreensaverEntry right) {
                int order = left.label.toLowerCase(Locale.ROOT).compareTo(right.label.toLowerCase(Locale.ROOT));
                return order == 0 ? left.component.compareTo(right.component) : order;
            }
        });
        return result;
    }
}
