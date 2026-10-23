package com.jeremykenedy.firetv.ui;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

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

    static String resume(String machine, PlexLibrary.Item item) {
        if (!valid(machine, item) || !canResume(item)) return null;
        return "plex://watch/video?uri="
                + encode("server://" + machine + "/com.plexapp.plugins.library" + item.key)
                + "&offset="
                + item.position / 1000;
    }

    static boolean canResume(PlexLibrary.Item item) {
        return item != null
                && ("movie".equals(item.type) || "episode".equals(item.type))
                && item.playable
                && item.position > 0
                && item.duration > item.position;
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
