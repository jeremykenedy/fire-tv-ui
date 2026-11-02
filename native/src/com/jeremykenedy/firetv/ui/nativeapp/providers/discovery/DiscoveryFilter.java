package com.jeremykenedy.firetv.ui.nativeapp.providers.discovery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class DiscoveryFilter {
    public interface HiddenTitles { boolean hidden(DiscoveryItem item); }
    public static final class Suggestion {
        public final DiscoveryItem item;
        public final String explanation;
        private final int preferenceMatches;
        Suggestion(DiscoveryItem item, String explanation, int preferenceMatches) {
            this.item = item; this.explanation = explanation; this.preferenceMatches = preferenceMatches;
        }
    }
    private DiscoveryFilter() {}
    public static List<Suggestion> select(List<DiscoveryItem> catalog, DiscoveryOptions options, HiddenTitles hidden) {
        List<Suggestion> result = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        Set<String> likedGenres = new LinkedHashSet<>();
        for (DiscoveryItem item : catalog) {
            if (!options.likedTitles.contains(item.key) || options.notInterested.contains(item.key)
                    || (hidden != null && hidden.hidden(item))) continue;
            for (String genre : item.genres) {
                String normalized = genre.toLowerCase(java.util.Locale.ROOT);
                if (!options.excludedGenres.contains(normalized)) likedGenres.add(normalized);
            }
        }
        for (DiscoveryItem item : catalog) {
            if (!visited.add(item.source + "\n" + item.key) || options.notInterested.contains(item.key)
                    || (hidden != null && hidden.hidden(item))
                    || ("movie".equals(item.type) ? !options.movies : !options.shows)
                    || (!options.libraries.isEmpty() && !options.libraries.contains(item.libraryKey))) continue;
            Set<String> genres = new LinkedHashSet<>();
            for (String genre : item.genres) genres.add(genre.toLowerCase(java.util.Locale.ROOT));
            if (!Collections.disjoint(genres, options.excludedGenres)
                    || (!options.includedGenres.isEmpty() && Collections.disjoint(genres, options.includedGenres))) continue;
            if (options.maximumMinutes > 0 && (item.durationMillis == null
                    || item.durationMillis > options.maximumMinutes * 60000L)) continue;
            if (options.minimumScore > 0 && (item.score == null || item.score < options.minimumScore)) continue;
            if (options.minimumRatingCount > 0 && (item.ratingCount == null || item.ratingCount < options.minimumRatingCount)) continue;
            String reason = "From your authorized " + item.libraryTitle + " library; matches your "
                    + ("movie".equals(item.type) ? "movie" : "show") + " choices";
            if (!options.includedGenres.isEmpty()) reason += " and selected genres";
            if (options.maximumMinutes > 0) reason += "; runtime within " + options.maximumMinutes + " minutes";
            if (options.minimumScore > 0) reason += "; " + item.ratingText() + " meets your minimum";
            if (options.minimumRatingCount > 0) reason += "; Plex supplied rating count meets your minimum";
            Set<String> matches = new java.util.TreeSet<>(genres);
            matches.retainAll(likedGenres);
            boolean liked = options.likedTitles.contains(item.key);
            if (liked) reason += "; you liked this title";
            else if (!matches.isEmpty()) reason += "; shares " + String.join(", ", matches)
                    + " with liked titles in this sample";
            result.add(new Suggestion(item, reason + ".", liked ? 65 : matches.size()));
        }
        Collections.sort(result, new java.util.Comparator<Suggestion>() {
            @Override public int compare(Suggestion first, Suggestion second) {
                return Integer.compare(second.preferenceMatches, first.preferenceMatches);
            }
        });
        return Collections.unmodifiableList(new ArrayList<>(result.subList(0, Math.min(100, result.size()))));
    }
}
