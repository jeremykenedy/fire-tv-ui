package com.jeremykenedy.firetv.ui.nativeapp.providers.discovery;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class DiscoveryOptions {
    public final Set<String> includedGenres, excludedGenres, libraries, notInterested, likedTitles;
    public final boolean movies, shows;
    public final int maximumMinutes;
    public final double minimumScore;
    public final long minimumRatingCount;

    public DiscoveryOptions(Set<String> included, Set<String> excluded, Set<String> libraries,
            Set<String> notInterested, boolean movies, boolean shows, int maximumMinutes,
            double minimumScore, long minimumRatingCount) {
        this(included, excluded, libraries, notInterested, Collections.<String>emptySet(),
                movies, shows, maximumMinutes, minimumScore, minimumRatingCount);
    }
    public DiscoveryOptions(Set<String> included, Set<String> excluded, Set<String> libraries,
            Set<String> notInterested, Set<String> likedTitles, boolean movies, boolean shows,
            int maximumMinutes, double minimumScore, long minimumRatingCount) {
        if (maximumMinutes < 0 || maximumMinutes > 10080 || Double.isNaN(minimumScore)
                || minimumScore < 0 || minimumScore > 10 || minimumRatingCount < 0 || minimumRatingCount > 1000000000)
            throw new IllegalArgumentException("Invalid discovery filters.");
        includedGenres = genres(included); excludedGenres = genres(excluded);
        this.libraries = identities(libraries, 128); this.notInterested = identities(notInterested, 512);
        this.likedTitles = identities(likedTitles, 512);
        this.movies = movies; this.shows = shows; this.maximumMinutes = maximumMinutes;
        this.minimumScore = minimumScore; this.minimumRatingCount = minimumRatingCount;
    }
    public static DiscoveryOptions defaults() {
        return new DiscoveryOptions(Collections.<String>emptySet(), Collections.<String>emptySet(),
                Collections.<String>emptySet(), Collections.<String>emptySet(), true, true, 0, 0, 0);
    }
    private static Set<String> identities(Set<String> values, int maximum) {
        if (values.size() > maximum) throw new IllegalArgumentException("Too many saved discovery choices.");
        Set<String> copy = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || !value.matches("[A-Za-z0-9.:_/-]{1,200}")) throw new IllegalArgumentException("Invalid discovery identity.");
            copy.add(value);
        }
        return Collections.unmodifiableSet(copy);
    }
    private static Set<String> genres(Set<String> values) {
        if (values.size() > 64) throw new IllegalArgumentException("Too many selected genres.");
        Set<String> copy = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.trim().isEmpty() || value.length() > 80) throw new IllegalArgumentException("Invalid genre.");
            copy.add(value.trim().toLowerCase(java.util.Locale.ROOT));
        }
        return Collections.unmodifiableSet(copy);
    }
    public DiscoveryOptions withGenres(Set<String> include, Set<String> exclude) {
        return new DiscoveryOptions(include, exclude, libraries, notInterested, likedTitles, movies, shows, maximumMinutes, minimumScore, minimumRatingCount);
    }
    public DiscoveryOptions withLibraries(Set<String> selected) {
        return new DiscoveryOptions(includedGenres, excludedGenres, selected, notInterested, likedTitles, movies, shows, maximumMinutes, minimumScore, minimumRatingCount);
    }
    public DiscoveryOptions withLimits(boolean movies, boolean shows, int minutes, double score, long count) {
        return new DiscoveryOptions(includedGenres, excludedGenres, libraries, notInterested, likedTitles, movies, shows, minutes, score, count);
    }
    public DiscoveryOptions dismiss(String key) {
        Set<String> copy = new LinkedHashSet<>(notInterested); copy.add(key);
        return new DiscoveryOptions(includedGenres, excludedGenres, libraries, copy, without(likedTitles, key), movies, shows, maximumMinutes, minimumScore, minimumRatingCount);
    }
    public DiscoveryOptions toggleLike(String key) {
        Set<String> selected = new LinkedHashSet<>(likedTitles);
        if (!selected.remove(key)) selected.add(key);
        return new DiscoveryOptions(includedGenres, excludedGenres, libraries, without(notInterested, key),
                selected, movies, shows, maximumMinutes, minimumScore, minimumRatingCount);
    }
    public DiscoveryOptions clearLikes() {
        return new DiscoveryOptions(includedGenres, excludedGenres, libraries, notInterested,
                Collections.<String>emptySet(), movies, shows, maximumMinutes, minimumScore, minimumRatingCount);
    }
    private static Set<String> without(Set<String> values, String key) {
        Set<String> copy = new LinkedHashSet<>(values); copy.remove(key); return copy;
    }
    public DiscoveryOptions restoreDismissed() {
        return new DiscoveryOptions(includedGenres, excludedGenres, libraries, Collections.<String>emptySet(), likedTitles, movies, shows, maximumMinutes, minimumScore, minimumRatingCount);
    }
}
