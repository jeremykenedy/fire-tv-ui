package com.jeremykenedy.firetv.ui.nativeapp.providers.discovery;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class DiscoveryItem {
    public final String key, source, libraryKey, libraryTitle, title, type, detailsUri;
    public final Set<String> genres;
    public final Long durationMillis, ratingCount;
    public final Double score;
    public final String scoreLabel;

    public DiscoveryItem(String key, String source, String libraryKey, String libraryTitle,
            String title, String type, Set<String> genres, Long durationMillis,
            Double score, String scoreLabel, Long ratingCount, String detailsUri) {
        if (key == null || !key.matches("[A-Za-z0-9.:_/-]{1,200}")
                || source == null || !source.matches("[A-Za-z0-9_.]{1,200}")
                || libraryKey == null || !libraryKey.matches("[A-Za-z0-9.:_/-]{1,200}")
                || title == null || title.trim().isEmpty() || title.length() > 512
                || !("movie".equals(type) || "show".equals(type))
                || genres.size() > 64) throw new IllegalArgumentException("Invalid discovery title.");
        this.key = key; this.source = source; this.libraryKey = libraryKey;
        this.libraryTitle = libraryTitle == null || libraryTitle.length() > 200 ? "Plex library" : libraryTitle;
        this.title = title; this.type = type;
        Set<String> copy = new LinkedHashSet<>();
        for (String genre : genres) if (genre != null && !genre.trim().isEmpty() && genre.length() <= 80) copy.add(genre.trim());
        this.genres = Collections.unmodifiableSet(copy);
        this.durationMillis = durationMillis != null && durationMillis > 0 && durationMillis <= 7L * 86400000 ? durationMillis : null;
        this.score = score != null && !score.isNaN() && !score.isInfinite() && score >= 0 && score <= 10 ? score : null;
        this.scoreLabel = scoreLabel == null || scoreLabel.length() > 120 ? "Plex rating" : scoreLabel;
        this.ratingCount = ratingCount != null && ratingCount >= 0 ? ratingCount : null;
        this.detailsUri = detailsUri != null && detailsUri.length() <= 4096 && detailsUri.startsWith("plex://libraries/") ? detailsUri : null;
    }
    public String ratingText() {
        return score == null ? "Rating unknown" : scoreLabel + ": " + String.format(java.util.Locale.ROOT, "%.1f/10", score);
    }
    public String countText() { return ratingCount == null ? "Rating count unknown" : "Plex rating count: " + ratingCount; }
}
