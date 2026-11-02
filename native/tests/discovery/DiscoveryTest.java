package com.jeremykenedy.firetv.ui.nativeapp.providers.discovery;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public final class DiscoveryTest {
    private static final String SOURCE = String.join("", Collections.nCopies(64, "a"));
    public static void main(String[] args) throws Exception {
        JSONObject movie = new JSONObject().put("ratingKey", "12").put("key", "/library/metadata/12")
                .put("type", "movie").put("title", "Fixture Movie").put("librarySectionID", "1")
                .put("librarySectionTitle", "Fixture movies").put("duration", 5400000)
                .put("rating", 8.2).put("ratingImage", "imdb://image.rating").put("ratingCount", 1000)
                .put("Genre", new JSONArray().put(new JSONObject().put("tag", "Drama")));
        JSONObject show = new JSONObject().put("ratingKey", "13").put("key", "/library/metadata/13/children")
                .put("type", "show").put("title", "Fixture Show").put("librarySectionID", "2")
                .put("Genre", new JSONArray().put(new JSONObject().put("tag", "Comedy")));
        JSONObject response = new JSONObject().put("MediaContainer", new JSONObject().put("Metadata", new JSONArray().put(movie).put(show)));
        List<DiscoveryItem> items = PlexDiscoveryMetadata.parse(SOURCE, "fixture-machine", response);
        check(items.size() == 2, "movies and shows supplied by authorized source parsed");
        check("IMDb via Plex: 8.2/10".equals(items.get(0).ratingText()), "source rating attribution retained");
        check(items.get(1).score == null && items.get(1).ratingCount == null && items.get(1).durationMillis == null,
                "missing metadata remains unknown");
        check(DiscoveryFilter.select(items, DiscoveryOptions.defaults(), null).size() == 2, "unknowns allowed without active limits");
        DiscoveryOptions rating = DiscoveryOptions.defaults().withLimits(true, true, 0, 8, 1000);
        check(DiscoveryFilter.select(items, rating, null).size() == 1, "known source thresholds include matching movie, exclude unknown show");
        check(DiscoveryFilter.select(items, rating.withLimits(true, true, 89, 8, 1000), null).isEmpty(), "runtime maximum compared without rounding");
        DiscoveryOptions genre = DiscoveryOptions.defaults().withGenres(new LinkedHashSet<>(Arrays.asList("DRAMA")), Collections.<String>emptySet());
        check(DiscoveryFilter.select(items, genre, null).size() == 1, "included genre case normalized");
        check(DiscoveryFilter.select(items, genre.withGenres(genre.includedGenres, new LinkedHashSet<>(Arrays.asList("drama"))), null).isEmpty(), "excluded genre overrides include");
        check(DiscoveryFilter.select(items, DiscoveryOptions.defaults().withLibraries(Collections.singleton(items.get(1).libraryKey)), null).size() == 1, "library filter uses server-scoped identity");
        check(DiscoveryFilter.select(items, DiscoveryOptions.defaults().dismiss(items.get(0).key), null).size() == 1, "Not interested removes only selected title");
        check(DiscoveryFilter.select(items, DiscoveryOptions.defaults(), new DiscoveryFilter.HiddenTitles() {
            @Override public boolean hidden(DiscoveryItem item) { return item.key.equals(items.get(1).key); }
        }).size() == 1, "shared hidden titles excluded");
        check(PlexDiscoveryMetadata.seriesKey(items.get(1)).equals("plex:" + SOURCE + ":series:13"), "show trusted metadata id aligns shared series identity");
        check(DiscoveryFilter.select(items, rating, null).get(0).explanation.contains("IMDb via Plex"), "suggestion explains active rating basis");
        check(!items.get(0).detailsUri.contains("token"), "details target contains no credential");
        movie.put("rating", JSONObject.NULL).put("audienceRating", 7.5).put("audienceRatingImage", "rottentomatoes://image.rating");
        DiscoveryItem audience = PlexDiscoveryMetadata.parse(SOURCE, "fixture-machine", response).get(0);
        check(audience.ratingText().contains("Rotten Tomatoes audience via Plex"), "audience fallback remains explicitly labeled");
        movie.put("rating", "9.7").remove("audienceRating");
        check(PlexDiscoveryMetadata.parse(SOURCE, "fixture-machine", response).get(0).score == null, "untyped score not guessed");
        movie.put("title", String.join("", Collections.nCopies(513, "x")));
        check(PlexDiscoveryMetadata.parse(SOURCE, "fixture-machine", response).size() == 1, "oversized metadata omitted");
        List<DiscoveryItem> otherSource = PlexDiscoveryMetadata.parse(String.join("", Collections.nCopies(64, "b")), "other-machine", response);
        check(!otherSource.get(0).key.equals(items.get(1).key), "same rating key on another server remains independent");
        DiscoveryItem liked = items.get(0);
        DiscoveryItem similar = new DiscoveryItem("plex:" + SOURCE + ":14", liked.source,
                liked.libraryKey, liked.libraryTitle, "Another drama", "movie", liked.genres,
                5400000L, 8.0, "Plex critic rating", 1000L, null);
        DiscoveryOptions likes = DiscoveryOptions.defaults().toggleLike(liked.key);
        List<DiscoveryItem> mixed = Arrays.asList(items.get(1), similar, liked);
        List<DiscoveryFilter.Suggestion> personalized = DiscoveryFilter.select(mixed, likes, null);
        check(personalized.get(0).item == liked && personalized.get(1).item == similar,
                "explicit liked title and matching genres rank ahead of unrelated recent titles");
        check(personalized.get(0).explanation.contains("you liked this title")
                && personalized.get(1).explanation.contains("shares drama with liked titles in this sample"),
                "personalization gives its actual local feedback basis");
        check(DiscoveryFilter.select(mixed, likes.withLibraries(Collections.singleton(items.get(1).libraryKey)), null).size() == 1,
                "likes cannot override a selected library");
        check(DiscoveryFilter.select(mixed, likes.withGenres(Collections.<String>emptySet(), Collections.singleton("drama")), null).size() == 1,
                "likes cannot override an excluded genre");
        List<DiscoveryFilter.Suggestion> hiddenLike = DiscoveryFilter.select(mixed, likes, new DiscoveryFilter.HiddenTitles() {
            @Override public boolean hidden(DiscoveryItem item) { return item.key.equals(liked.key); }
        });
        check(hiddenLike.get(0).item == items.get(1) && !hiddenLike.get(1).explanation.contains("liked titles"),
                "hidden liked title is excluded and cannot influence recommendations");
        check(likes.dismiss(liked.key).likedTitles.isEmpty()
                && likes.dismiss(liked.key).notInterested.contains(liked.key), "Not interested clears an earlier like");
        check(likes.dismiss(liked.key).toggleLike(liked.key).notInterested.isEmpty(),
                "explicit new like removes conflicting Not interested feedback");
        check(likes.toggleLike(liked.key).likedTitles.isEmpty() && likes.clearLikes().likedTitles.isEmpty(),
                "individual and all likes can be removed");
        check(likes.withLimits(false, true, 30, 7, 100).withLibraries(Collections.singleton(liked.libraryKey))
                .withGenres(Collections.singleton("drama"), Collections.<String>emptySet())
                .restoreDismissed().likedTitles.contains(liked.key), "filter edits preserve saved likes");
        List<DiscoveryItem> large = new java.util.ArrayList<>();
        for (int index = 100; index < 210; index++) large.add(new DiscoveryItem("plex:" + SOURCE + ":" + index,
                liked.source, liked.libraryKey, liked.libraryTitle, "Unrelated " + index, "movie",
                Collections.singleton("Comedy"), null, null, null, null, null));
        large.add(liked); large.add(similar);
        List<DiscoveryFilter.Suggestion> bounded = DiscoveryFilter.select(large, likes, null);
        check(bounded.size() == 100 && bounded.get(0).item == liked && bounded.get(1).item == similar,
                "rank before applying result limit so a later matching title can be suggested");
        System.out.println("Discovery fixtures, explicit feedback, explained ranking and filter precedence passed");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
