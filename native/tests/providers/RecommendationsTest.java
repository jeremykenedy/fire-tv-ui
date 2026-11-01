package com.jeremykenedy.firetv.ui.nativeapp.providers;

import android.content.Context;
import android.database.Cursor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RecommendationsTest {
    public static void main(String[] args) {
        Context denied = context();
        denied.permission = false;
        check(TvRecommendations.read(denied).status == TvRecommendations.Status.PERMISSION_REQUIRED
                && denied.resolver.queries == 0, "Denied access is explicit and does not query data.");
        Context missing = context(); missing.packages.available = false;
        check(TvRecommendations.read(missing).status == TvRecommendations.Status.UNAVAILABLE, "Missing TV provider is explicit.");
        check(TvRecommendations.read(context()).status == TvRecommendations.Status.EMPTY, "An accessible empty provider is not an access failure.");
        Context populated = context();
        Rows channels = rows(row("_id", 7L, "package_name", "tv.example.movies", "display_name", "Recently added"));
        Rows previews = rows(
                program(1, "tv.example.movies", 7, "Movie A", "asset-a"),
                program(2, "tv.example.movies", 7, "Movie B", "asset-b"),
                program(3, "tv.other.app", 7, "Wrong publisher", "asset-c"));
        Rows next = rows(program(4, "tv.example.movies", 0, "Next episode", "asset-a"));
        populated.resolver.tables.put("channel", channels);
        populated.resolver.tables.put("preview_program", previews);
        populated.resolver.tables.put("watch_next_program", next);
        TvRecommendations.Result result = TvRecommendations.read(populated);
        check(result.status == TvRecommendations.Status.READY && result.feeds.size() == 2, "Channel and Watch Next have independent feed slots.");
        check(result.feeds.get(0).programs.size() == 2, "Publisher must match its real channel; multiple programs stay inside one feed.");
        check(!result.feeds.get(0).programs.get(0).key.equals(result.feeds.get(1).programs.get(0).key),
                "Identical provider IDs in different feeds do not imply the same item.");
        check(channels.closed && previews.closed && next.closed, "All provider cursors are closed.");
        check(result.feeds.get(0).programs.get(0).artwork != null, "HTTPS artwork metadata is retained without fetching it.");
        check(result.feeds.get(0).programs.get(0).intentUri.equals("intent:#Intent;package=tv.example.movies;end"),
                "Published handoff is metadata only and remains associated with publisher.");
        Context partial = context();
        partial.resolver.fail = "preview_program";
        partial.resolver.tables.put("watch_next_program", rows(program(2, "tv.example.movies", 0, "", "a")));
        result = TvRecommendations.read(partial);
        check(result.status == TvRecommendations.Status.PARTIAL && result.feeds.size() == 1, "One unavailable table must not discard another readable feed.");
        check(result.feeds.get(0).programs.get(0).title.equals("Untitled item"), "Blank titles receive an honest fallback.");
        Context bounded = context();
        List<Map<String,Object>> many = new ArrayList<>();
        for (int i = 1; i <= 40; i++) many.add(program(i, "tv.example.movies", 0, "Item " + i, "item" + i));
        bounded.resolver.tables.put("watch_next_program", new Rows(many));
        result = TvRecommendations.read(bounded);
        check(result.status == TvRecommendations.Status.READY && result.feeds.get(0).programs.size() == 24,
                "Expected per-feed limits do not masquerade as a permission failure.");
        Context unsafe = context();
        Map<String,Object> entry = program(1, "tv.example.movies", 0, "Item", "id");
        entry.put("poster_art_uri", "file:///private/image");
        unsafe.resolver.tables.put("watch_next_program", rows(entry));
        check(TvRecommendations.read(unsafe).feeds.get(0).programs.get(0).artwork == null, "File artwork is not forwarded.");
        String a = RecommendationKeys.feed("tv.example.movies", false, 1);
        check(!a.equals(RecommendationKeys.feed("tv.other.app", false, 1))
                && !a.equals(RecommendationKeys.feed("tv.example.movies", true, 0)), "Feed identities are source and table isolated.");
        check(RecommendationKeys.program(a, 1, "stable").equals(RecommendationKeys.program(a, 200, "stable")),
                "Stable provider item ID survives a row replacement within the same feed.");
        System.out.println("TV recommendation permission, empty/partial states, source isolation, grouping, bounds and cursor cleanup passed.");
    }

    private static Context context() {
        Context value = new Context();
        for (String table : Arrays.asList("channel", "preview_program", "watch_next_program")) value.resolver.tables.put(table, rows());
        return value;
    }
    private static Map<String,Object> program(long id, String source, long channel, String title, String stable) {
        return row("_id", id, "package_name", source, "channel_id", channel, "title", title,
                "internal_provider_id", stable, "poster_art_uri", "https://example.test/art.jpg",
                "intent_uri", "intent:#Intent;package=" + source + ";end");
    }
    private static Map<String,Object> row(Object... values) {
        Map<String,Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]);
        return result;
    }
    @SafeVarargs private static Rows rows(Map<String,Object>... values) { return new Rows(Arrays.asList(values)); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    private static final class Rows implements Cursor {
        final List<Map<String,Object>> data;
        final List<String> columns;
        int index = -1;
        boolean closed;
        Rows(List<Map<String,Object>> data) {
            this.data = data;
            columns = data.isEmpty() ? new ArrayList<String>() : new ArrayList<>(data.get(0).keySet());
        }
        public boolean moveToNext() { return ++index < data.size(); }
        public int getColumnIndex(String name) { return columns.indexOf(name); }
        public int getColumnIndexOrThrow(String name) {
            int result = getColumnIndex(name); if (result < 0) throw new IllegalArgumentException(name); return result;
        }
        public boolean isNull(int column) { return data.get(index).get(columns.get(column)) == null; }
        public String getString(int column) { return String.valueOf(data.get(index).get(columns.get(column))); }
        public long getLong(int column) { return ((Number) data.get(index).get(columns.get(column))).longValue(); }
        public void close() { closed = true; }
    }
}
