package com.jeremykenedy.firetv.ui;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class RecommendationRules {
    static final String[] PACKAGES = {
        "com.netflix.ninja", "com.amazon.firebat", "com.amazon.avod.thirdpartyclient",
        "com.amazon.tv.livetv", "com.amazon.hedwig", "com.amazon.firetv.youtube",
        "com.amazon.firetv.youtube.tv", "com.google.android.youtube.tv",
        "com.spotify.tv.android", "com.tubitv.ott"
    };

    private RecommendationRules() {}

    static String label(String source) {
        if (source == null) return null;
        switch (source) {
            case "com.netflix.ninja": return "Netflix";
            case "com.amazon.firebat":
            case "com.amazon.avod.thirdpartyclient": return "Prime Video";
            case "com.amazon.tv.livetv": return "Live TV";
            case "com.amazon.hedwig": return "News";
            case "com.amazon.firetv.youtube":
            case "com.amazon.firetv.youtube.tv":
            case "com.google.android.youtube.tv": return "YouTube";
            case "com.spotify.tv.android": return "Spotify";
            case "com.tubitv.ott": return "Tubi";
            default: return null;
        }
    }

    static boolean eligible(String source, String category, String creator, String title) {
        return label(source) != null && source.equals(creator)
                && "recommendation".equals(category) && title != null
                && !title.trim().isEmpty() && title.length() <= 512;
    }

    static String identity(String source, String notificationKey, String title) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(
                    (source + "\n" + notificationKey + "\n" + title)
                            .getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte value : bytes)
                result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    static boolean validIdentity(String id) {
        return id != null && id.matches("[a-f0-9]{64}");
    }
}
