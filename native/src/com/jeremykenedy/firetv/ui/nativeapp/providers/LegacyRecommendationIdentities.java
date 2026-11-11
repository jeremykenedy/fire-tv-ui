package com.jeremykenedy.firetv.ui.nativeapp.providers;

/** Historical card identities consumed exactly during migration and recommendation reads. */
final class LegacyRecommendationIdentities {
    private LegacyRecommendationIdentities() {}

    static String item(String source, boolean watchNext, String providerId, String contentId) {
        if (source == null || source.isEmpty()) return null;
        String identity;
        if (providerId != null && !providerId.isEmpty()) identity = "provider:" + providerId;
        else if (contentId != null && !contentId.isEmpty()) identity = "content:" + contentId;
        else return null;
        return RecommendationKeys.hash((watchNext ? "watch-next" : "preview") + "\n" + source + "\n" + identity);
    }

    static String item(String source, boolean watchNext, String providerId, String contentId, long row) {
        String key = item(source, watchNext, providerId, contentId);
        if (key != null || source == null || source.isEmpty() || row <= 0) return key;
        return RecommendationKeys.hash((watchNext ? "watch-next" : "preview") + "\n" + source + "\nrow:" + row);
    }

    static String feed(String source, boolean watchNext, String providerId, String displayName) {
        String stable = providerId == null || providerId.isEmpty() ? "name:" + displayName : providerId;
        return item(source, watchNext, watchNext ? "feed:watch-next" : "feed:" + stable, "");
    }
}
