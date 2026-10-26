package com.jeremykenedy.firetv.ui;

import java.net.URI;

final class RecommendationArtworkRules {
    private RecommendationArtworkRules() {}

    static boolean allowedHttps(String source, String value) {
        if (!"com.tubitv.ott".equals(source) || value == null || value.length() > 16384)
            return false;
        try {
            URI uri = new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "canvas-lb.tubitv.com".equalsIgnoreCase(uri.getHost())
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && uri.getUserInfo() == null
                    && uri.getFragment() == null;
        } catch (java.net.URISyntaxException invalid) {
            return false;
        }
    }

    static boolean ownedContent(String source, String providerPackage) {
        return source != null && source.equals(providerPackage);
    }
}
