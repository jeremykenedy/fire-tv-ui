package com.jeremykenedy.firetv.ui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PlexFeedData {
    interface Reader {
        JSONObject read(String path, int count) throws Exception;
    }

    static final class Feed {
        final String source;
        final String title;
        final List<PlexLibrary.Item> items;
        String credentialSource;

        Feed(String key, List<PlexLibrary.Item> items) {
            this(source(key), label(source(key)), items);
        }

        Feed(String source, String title, List<PlexLibrary.Item> items) {
            this.source = source;
            this.title = title;
            this.items = items;
        }
    }

    private PlexFeedData() {}

    static String source(String key) {
        return "fire-tv-ui-plex-" + key;
    }

    static String label(String source) {
        for (int i = 0; i < PlexFeedSettings.KEYS.length; i++) {
            if (source(PlexFeedSettings.KEYS[i]).equals(source))
                return "Plex " + PlexFeedSettings.LABELS[i];
        }
        if (source != null && source.matches("fire-tv-ui-plex-profile-[0-9a-f]{64}"))
            return "Plex Home Continue Watching";
        return "fire-tv-ui-plex-recent".equals(source) ? "Plex Recently Added" : null;
    }

    static List<Feed> read(Reader reader, PlexFeedSettings options) throws Exception {
        return read(reader, options, options.limit());
    }

    static List<Feed> read(Reader reader, PlexFeedSettings options, int limit) throws Exception {
        if (limit < 1 || limit > 12) throw new IllegalArgumentException("Invalid Plex card limit.");
        List<Feed> feeds = new ArrayList<>();
        for (String key : PlexFeedSettings.KEYS) {
            if (!options.enabled.contains(key)) continue;
            String path;
            String type = null;
            int page = limit;
            if ("continue".equals(key)) path = "/hubs/continueWatching?count=" + limit;
            else if ("movies".equals(key)) {
                path = "/library/all?type=1&sort=addedAt:desc";
                type = "movie";
            } else if ("episodes".equals(key)) {
                path = "/library/all?type=4&sort=addedAt:desc";
                type = "episode";
            } else {
                path =
                        "playing".equals(key)
                                ? "/status/sessions"
                                : "/status/sessions/history/all?sort=viewedAt:desc";
                page = 100;
            }
            JSONObject response = reader.read(path, page);
            if ("playing".equals(key) || "history".equals(key))
                response = activity(response, options);
            feeds.add(new Feed(key, PlexLibrary.items(response, type, limit)));
        }
        return feeds;
    }

    static JSONObject activity(JSONObject response, PlexFeedSettings options) throws Exception {
        JSONObject container = response.optJSONObject("MediaContainer");
        JSONArray input = container == null ? null : container.optJSONArray("Metadata");
        Map<String, JSONObject> selected = new LinkedHashMap<>();
        Map<String, java.util.Set<String>> viewers = new LinkedHashMap<>();
        if (input != null)
            for (int i = 0; i < input.length(); i++) {
                JSONObject item = input.optJSONObject(i);
                if (item == null) continue;
                JSONObject user = item.optJSONObject("User");
                String id =
                        user == null ? item.optString("accountID", "") : user.optString("id", "");
                String media = item.optString("ratingKey", "");
                if (!options.acceptsUser(id) || !media.matches("[0-9]{1,18}")) continue;
                String name = user == null ? "User " + id : user.optString("title", "User " + id);
                if (!selected.containsKey(media)) {
                    selected.put(media, new JSONObject(item.toString()));
                    viewers.put(media, new java.util.LinkedHashSet<String>());
                }
                viewers.get(media).add(name);
            }
        JSONArray result = new JSONArray();
        for (Map.Entry<String, JSONObject> entry : selected.entrySet()) {
            JSONObject item = entry.getValue();
            String names = String.join(", ", viewers.get(entry.getKey()));
            item.put("title", names + " - " + item.optString("title", ""));
            result.put(item);
        }
        return new JSONObject().put("MediaContainer", new JSONObject().put("Metadata", result));
    }

    static Map<String, String> accounts(JSONObject response) {
        Map<String, String> result = new LinkedHashMap<>();
        JSONObject container = response.optJSONObject("MediaContainer");
        JSONArray accounts = container == null ? null : container.optJSONArray("Account");
        for (int i = 0; accounts != null && i < Math.min(256, accounts.length()); i++) {
            JSONObject account = accounts.optJSONObject(i);
            if (account == null) continue;
            String id = account.optString("id", "");
            String name = account.optString("name", "").trim();
            if (id.matches("[0-9]{1,18}") && !name.isEmpty() && name.length() <= 128)
                result.put(id, name);
        }
        return result;
    }

    static JSONObject namedActivity(JSONObject response, Map<String, String> accounts)
            throws Exception {
        JSONObject named = new JSONObject(response.toString());
        JSONObject container = named.optJSONObject("MediaContainer");
        JSONArray rows = container == null ? null : container.optJSONArray("Metadata");
        for (int i = 0; rows != null && i < rows.length(); i++) {
            JSONObject item = rows.optJSONObject(i);
            if (item == null || item.optJSONObject("User") != null) continue;
            String id = item.optString("accountID", "");
            String name = accounts.get(id);
            if (name != null) item.put("User", new JSONObject().put("id", id).put("title", name));
        }
        return named;
    }

    static Map<String, String> users(JSONObject response) {
        Map<String, String> users = new LinkedHashMap<>();
        JSONObject container = response.optJSONObject("MediaContainer");
        JSONArray items = container == null ? null : container.optJSONArray("Metadata");
        if (items != null)
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                JSONObject user = item.optJSONObject("User");
                String id =
                        user == null ? item.optString("accountID", "") : user.optString("id", "");
                if (!id.matches("[0-9]{1,18}")) continue;
                String name =
                        user == null ? "User " + id : user.optString("title", "User " + id).trim();
                users.put(id, name.isEmpty() ? "User " + id : name);
            }
        return users;
    }
}
