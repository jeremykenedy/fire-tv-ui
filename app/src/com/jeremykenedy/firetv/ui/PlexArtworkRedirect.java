package com.jeremykenedy.firetv.ui;

import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Locale;

final class PlexArtworkRedirect {
    private PlexArtworkRedirect() {}

    static String next(String current, String location) throws IOException {
        if (location == null || location.isEmpty())
            throw new IOException("Artwork location is missing.");
        URL from = new URL(current);
        URL target = new URL(from, location);
        if (target.getUserInfo() != null || target.getRef() != null)
            throw new IOException("Artwork location is invalid.");
        if (sameOrigin(current, target.toString())) return target.toString();
        String host = target.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equals(target.getProtocol())
                || (target.getPort() != -1 && target.getPort() != 443)
                || !("images.plex.tv".equals(host) || "metadata-static.plex.tv".equals(host)))
            throw new IOException("Artwork moved outside the supported image hosts.");
        String query = target.getQuery();
        if (query != null) {
            String decoded = URLDecoder.decode(query, "UTF-8").toLowerCase(Locale.ROOT);
            if (decoded.contains("token")
                    || decoded.contains("password")
                    || decoded.contains("authorization"))
                throw new IOException("Artwork location contains private credentials.");
        }
        return target.toString();
    }

    static boolean sameOrigin(String left, String right) throws IOException {
        URL from = new URL(left);
        URL to = new URL(right);
        int fromPort = from.getPort() < 0 ? from.getDefaultPort() : from.getPort();
        int toPort = to.getPort() < 0 ? to.getDefaultPort() : to.getPort();
        return from.getProtocol().equalsIgnoreCase(to.getProtocol())
                && from.getHost().equalsIgnoreCase(to.getHost())
                && fromPort == toPort;
    }
}
