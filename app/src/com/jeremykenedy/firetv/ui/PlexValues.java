package com.jeremykenedy.firetv.ui;

import java.net.URI;

final class PlexValues {
    private PlexValues() {}

    static String serverBase(String value) {
        try {
            URI uri = new URI(value.trim());
            String scheme = uri.getScheme();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getRawQuery() != null
                    || uri.getPort() < -1
                    || uri.getPort() == 0
                    || uri.getPort() > 65535) throw new IllegalArgumentException();
            String path = uri.getPath();
            if (!(path == null
                    || path.isEmpty()
                    || "/".equals(path)
                    || "/web".equals(path)
                    || "/web/".equals(path)
                    || "/web/index.html".equals(path))) throw new IllegalArgumentException();
            return new URI(
                            scheme.toLowerCase(java.util.Locale.ROOT),
                            null,
                            uri.getHost(),
                            uri.getPort(),
                            null,
                            null,
                            null)
                    .toString();
        } catch (Exception error) {
            throw new IllegalArgumentException(
                    "Enter your Plex server address, such as http://your-server:32400, without"
                        + " account credentials or a token.");
        }
    }

    static boolean itemPath(String path) {
        return path != null && path.matches("/item/[a-f0-9]{64}/[0-9]{1,18}");
    }

    static String artworkName(String path) {
        return path != null && path.matches("/[a-f0-9]{64}\\.jpg") ? path.substring(1) : null;
    }

    static boolean libraryPath(String value) {
        return value != null
                && value.startsWith("/library/")
                && !value.contains("..")
                && !value.contains("%")
                && !value.contains("?")
                && !value.contains("#")
                && !value.contains("\\");
    }
}
