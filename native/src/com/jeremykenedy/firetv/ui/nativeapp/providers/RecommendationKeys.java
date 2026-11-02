package com.jeremykenedy.firetv.ui.nativeapp.providers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class RecommendationKeys {
    private RecommendationKeys() {}
    static boolean validPackage(String value) {
        if (value == null || value.length() > 200 || value.isEmpty()) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(c >= 'a' && c <= 'z') && !(c >= 'A' && c <= 'Z')
                    && !(c >= '0' && c <= '9') && c != '.' && c != '_') return false;
        }
        return value.indexOf('.') > 0;
    }
    static String feed(String source, boolean watchNext, long channelId) {
        return "tv:" + hash(source + "\n" + (watchNext ? "watch-next" : "preview:" + channelId));
    }
    static String notificationFeed(String source) { return "notification:" + hash(source); }
    static String notification(String source, String notificationKey, String title) {
        return "notification:" + hash(source + "\n" + notificationKey + "\n" + title);
    }
    static String series(String source, String providerSeriesId) {
        return providerSeriesId == null || providerSeriesId.isEmpty() ? null
                : "series:" + hash(source + "\n" + providerSeriesId);
    }
    static String program(String feedId, long rowId, String providerId) {
        return hash(feedId + "\n" + (providerId.isEmpty() ? "row:" + rowId : "provider:" + providerId));
    }
    private static String hash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte valueByte : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", valueByte & 255));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
