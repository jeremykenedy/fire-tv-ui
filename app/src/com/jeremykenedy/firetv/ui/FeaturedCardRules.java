package com.jeremykenedy.firetv.ui;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class FeaturedCardRules {
    private FeaturedCardRules() {}

    static String key(String source, boolean watchNext, String providerId, String contentId) {
        if (source == null || source.isEmpty()) return null;
        String identity;
        if (providerId != null && !providerId.isEmpty()) identity = "provider:" + providerId;
        else if (contentId != null && !contentId.isEmpty()) identity = "content:" + contentId;
        else return null;
        return digest((watchNext ? "watch-next" : "preview") + "\n" + source + "\n" + identity);
    }

    static String lookup(String source, boolean watchNext, String intent) {
        return digest(source + "\n" + watchNext + "\n" + intent);
    }

    private static String digest(String value) {
        try {
            byte[] bytes =
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hash = new StringBuilder();
            for (byte item : bytes)
                hash.append(String.format(java.util.Locale.ROOT, "%02x", item & 255));
            return hash.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    static boolean validKey(String value) {
        return value != null && value.matches("[a-f0-9]{64}");
    }
}
