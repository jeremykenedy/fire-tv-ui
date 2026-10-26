package com.jeremykenedy.firetv.ui;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;

final class PlexItemLinks {
    private PlexItemLinks() {}

    static String details(String machine, PlexLibrary.Item item) {
        if (!valid(machine, item)) return null;
        return "plex://libraries/"
                + machine
                + "/unmatched/"
                + ("movie".equals(item.type) ? "" : "tv/")
                + encode("/library/metadata/" + item.id);
    }

    static String savedProgress(PlexLibrary.Item item) {
        if (item == null
                || !("movie".equals(item.type) || "episode".equals(item.type))
                || item.position <= 0
                || item.duration <= item.position) return "";
        return "\n\nSaved progress: "
                + time(item.position)
                + " of "
                + time(item.duration)
                + ". Open Plex to choose playback.";
    }

    private static String time(long milliseconds) {
        long seconds = milliseconds / 1000;
        if (seconds >= 3600) {
            return String.format(
                    Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
        }
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private static boolean valid(String machine, PlexLibrary.Item item) {
        return machine != null
                && machine.matches("[A-Za-z0-9_-]{1,128}")
                && item != null
                && ("movie".equals(item.type)
                        || "episode".equals(item.type)
                        || "show".equals(item.type))
                && item.id.matches("[0-9]{1,18}")
                && (("/library/metadata/" + item.id).equals(item.key)
                        || ("show".equals(item.type)
                                && ("/library/metadata/" + item.id + "/children")
                                        .equals(item.key)));
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
