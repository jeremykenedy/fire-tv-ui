package com.jeremykenedy.firetv.ui.nativeapp.providers;

final class NotificationRules {
    private NotificationRules() {}
    static boolean eligible(String source, String category, String creator, int sourceUid, int creatorUid, String title) {
        return RecommendationKeys.validPackage(source) && "recommendation".equals(category)
                && source.equals(creator) && sourceUid >= 0 && creatorUid == sourceUid
                && title != null && !title.trim().isEmpty() && title.length() <= 512;
    }
}
