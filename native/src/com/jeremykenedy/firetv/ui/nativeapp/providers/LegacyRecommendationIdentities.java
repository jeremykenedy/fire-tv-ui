package com.jeremykenedy.firetv.ui.nativeapp.providers;

/** Historical owned card identities, retained only for exact preference migration. */
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

    static String feed(String source, boolean watchNext, String providerId, String displayName) {
        String stable = providerId == null || providerId.isEmpty() ? "name:" + displayName : providerId;
        return item(source, watchNext, watchNext ? "feed:watch-next" : "feed:" + stable, "");
    }
}
