package com.jeremykenedy.firetv.ui.nativeapp.providers.plex;

import java.net.URI;

/** Exact historical token-free cached-art URI contract. */
final class LegacyPlexArtworkPath {
    private LegacyPlexArtworkPath() {}
    static String name(String value, String packageName) {
        try {
            URI uri = new URI(value);
            String path = uri.getRawPath();
            if (!"content".equals(uri.getScheme()) || !(packageName + ".plex.art").equals(uri.getRawAuthority())
                    || uri.getRawQuery() != null || uri.getRawFragment() != null || path == null
                    || !path.matches("/[a-f0-9]{64}\\.jpg")) return null;
            return path.substring(1);
        } catch (Exception invalid) { return null; }
    }
}
