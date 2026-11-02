package com.jeremykenedy.firetv.ui.nativeapp.widget.watchlist;

import com.jeremykenedy.firetv.ui.nativeapp.migration.LegacyJson;
import com.jeremykenedy.firetv.ui.nativeapp.widget.feeds.FeedSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/** Private saved titles; unknown JSON fields survive edits. */
public final class Watchlist {
    public static final int MAX_ITEMS = 100;
    public static final int MAX_DOCUMENT = 262144;
    public static final class Item {
        public final String id, title, url, plexKey;
        public final boolean watched;
        private Item(JSONObject value) throws Exception {
            if (!(value.get("id") instanceof String) || !(value.get("title") instanceof String) || !(value.get("url") instanceof String)) throw invalid();
            id = value.getString("id"); title = value.getString("title"); url = value.getString("url");
            Object savedPlex = value.opt("plexKey");
            if (savedPlex != null && !(savedPlex instanceof String)) throw invalid();
            plexKey = savedPlex == null ? "" : (String) savedPlex;
            if (!plexKey.isEmpty() && !plexKey.matches("plex:[a-f0-9]{64}:[0-9]{1,18}")) throw invalid();
            Object flag = value.get("watched");
            if (!(flag instanceof Boolean) || !id.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")) throw invalid();
            watched = (Boolean) flag; validateTitle(title); validateUrl(url);
        }
    }
    private final JSONObject document;
    public final List<Item> items;
    private Watchlist(JSONObject root) throws Exception {
        if (!(root.get("version") instanceof Number) || root.getDouble("version") != 1.0) throw invalid();
        JSONArray values = root.getJSONArray("items");
        if (values.length() > MAX_ITEMS) throw invalid();
        List<Item> found = new ArrayList<>(); HashSet<String> ids = new HashSet<>();
        for (int i = 0; i < values.length(); i++) {
            Item item = new Item(values.getJSONObject(i));
            if (!ids.add(item.id)) throw invalid();
            found.add(item);
        }
        document = root; items = Collections.unmodifiableList(found);
    }
    public static Watchlist empty() { return decode("{\"version\":1,\"items\":[]}"); }
    public static Watchlist decode(String raw) {
        try {
            if (raw == null || raw.length() > MAX_DOCUMENT) throw invalid();
            LegacyJson.object(raw);
            return new Watchlist(new JSONObject(raw));
        } catch (Exception failure) { throw invalid(); }
    }
    public String encode() { String raw = document.toString(); if (raw.length() > MAX_DOCUMENT) throw invalid(); return raw; }
    public Watchlist add(String title, String url) { return change(null, title, url, null, false); }
    public Watchlist addPlex(String title, String plexKey) {
        if (plexKey == null || !plexKey.matches("plex:[a-f0-9]{64}:[0-9]{1,18}")) throw invalid();
        Watchlist added = add(title, "");
        try {
            JSONObject copy = new JSONObject(added.encode()); JSONArray items = copy.getJSONArray("items");
            items.getJSONObject(items.length() - 1).put("plexKey", plexKey);
            return decode(copy.toString());
        } catch (Exception error) { throw invalid(); }
    }
    public Watchlist edit(String id, String title, String url) { return change(id, title, url, null, false); }
    public Watchlist watched(String id, boolean watched) { return change(id, null, null, watched, false); }
    public Watchlist remove(String id) { return change(id, null, null, null, true); }
    private Watchlist change(String id, String title, String url, Boolean watched, boolean remove) {
        try {
            JSONObject copy = new JSONObject(encode()); JSONArray old = copy.getJSONArray("items");
            JSONArray next = new JSONArray(); boolean matched = false;
            for (int i = 0; i < old.length(); i++) {
                JSONObject item = old.getJSONObject(i);
                if (id != null && id.equals(item.getString("id"))) {
                    matched = true; if (remove) continue;
                    if (title != null) item.put("title", validateTitle(title));
                    if (url != null) item.put("url", validateUrl(url));
                    if (watched != null) item.put("watched", watched);
                }
                next.put(item);
            }
            if (id != null && !matched) throw invalid();
            if (id == null) {
                if (next.length() >= MAX_ITEMS) throw invalid();
                next.put(new JSONObject().put("id", UUID.randomUUID().toString()).put("title", validateTitle(title))
                        .put("url", validateUrl(url)).put("watched", false));
            }
            copy.put("items", next); return decode(copy.toString());
        } catch (Exception error) { throw invalid(); }
    }
    private static String validateTitle(String value) {
        if (value == null || value.trim().isEmpty() || value.length() > 512 || value.matches("(?s).*\\p{Cntrl}.*")) throw invalid();
        return value.trim();
    }
    private static String validateUrl(String value) {
        if (value == null) throw invalid();
        if (!value.isEmpty()) FeedSource.validateUrl(value);
        return value;
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Enter a title and an optional valid HTTPS link. Saved items were kept."); }
}
