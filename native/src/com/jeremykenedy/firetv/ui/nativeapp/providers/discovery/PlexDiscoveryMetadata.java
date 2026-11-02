package com.jeremykenedy.firetv.ui.nativeapp.providers.discovery;

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Only metadata actually supplied by the authorized server is retained. */
public final class PlexDiscoveryMetadata {
    private PlexDiscoveryMetadata() {}
    public static List<DiscoveryItem> parse(String sourceKey, String machine, JSONObject response) {
        if (sourceKey == null || !sourceKey.matches("[a-f0-9]{64}") || machine == null
                || !machine.matches("[A-Za-z0-9_-]{1,128}")) throw new IllegalArgumentException("Invalid Plex discovery source.");
        JSONObject container = response.optJSONObject("MediaContainer");
        JSONArray rows = container == null ? null : container.optJSONArray("Metadata");
        List<DiscoveryItem> result = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        for (int i = 0; rows != null && i < Math.min(100, rows.length()); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            String id = row.optString("ratingKey", ""), type = row.optString("type", "");
            String title = row.optString("title", "").trim();
            if (!id.matches("[0-9]{1,18}") || !visited.add(id) || title.isEmpty() || title.length() > 512
                    || !("movie".equals(type) || "show".equals(type))) continue;
            String path = "/library/metadata/" + id;
            if (!(path.equals(row.optString("key", "")) || ("show".equals(type)
                    && (path + "/children").equals(row.optString("key", ""))))) continue;
            String library = row.optString("librarySectionID", "");
            if (!library.matches("[0-9]{1,18}")) library = "unknown";
            Set<String> genres = new LinkedHashSet<>();
            JSONArray tags = row.optJSONArray("Genre");
            for (int j = 0; tags != null && j < Math.min(64, tags.length()); j++) {
                JSONObject tag = tags.optJSONObject(j);
                String genre = tag == null ? "" : tag.optString("tag", "").trim();
                if (!genre.isEmpty() && genre.length() <= 80) genres.add(genre);
            }
            Double critic = score(row, "rating"), audience = score(row, "audienceRating");
            String kind = critic != null ? "critic" : "audience";
            Double rating = critic != null ? critic : audience;
            String image = row.optString(critic != null ? "ratingImage" : "audienceRatingImage", "");
            String label = image.startsWith("imdb://") ? "IMDb via Plex"
                    : image.startsWith("rottentomatoes://") ? "Rotten Tomatoes " + kind + " via Plex"
                    : image.startsWith("themoviedb://") ? "TMDB via Plex" : "Plex " + kind + " rating";
            String details;
            try { details = "plex://libraries/" + machine + "/unmatched/" + ("show".equals(type) ? "tv/" : "")
                    + URLEncoder.encode(path, "UTF-8"); }
            catch (java.io.UnsupportedEncodingException impossible) { throw new AssertionError(impossible); }
            result.add(new DiscoveryItem("plex:" + sourceKey + ":" + id, "com.plexapp.android",
                    "plex:" + sourceKey + ":library:" + library, row.optString("librarySectionTitle", "Plex library"),
                    title, type, genres, count(row, "duration"), rating, label, count(row, "ratingCount"), details));
        }
        return Collections.unmodifiableList(result);
    }
    private static Double score(JSONObject value, String field) {
        Object raw = value.opt(field);
        if (!(raw instanceof Number)) return null;
        double score = ((Number) raw).doubleValue();
        return !Double.isNaN(score) && !Double.isInfinite(score) && score >= 0 && score <= 10 ? score : null;
    }
    private static Long count(JSONObject value, String field) {
        Object raw = value.opt(field);
        if (!(raw instanceof Number)) return null;
        double number = ((Number) raw).doubleValue();
        if (Double.isNaN(number) || Double.isInfinite(number) || number < 0 || number > Long.MAX_VALUE
                || number != Math.floor(number)) return null;
        return ((Number) raw).longValue();
    }
    public static String seriesKey(DiscoveryItem item) {
        if (!"show".equals(item.type) || !item.key.matches("plex:[a-f0-9]{64}:[0-9]{1,18}")) return null;
        int last = item.key.lastIndexOf(':');
        return item.key.substring(0, last) + ":series:" + item.key.substring(last + 1);
    }
}
