package com.jeremykenedy.firetv.ui;

import org.json.JSONObject;

public final class PlexItemLinksTest {
    public static void main(String[] args) throws Exception {
        PlexLibrary.Item movie = item("movie", "/library/metadata/42", 123456, 600000, true);
        String details = PlexItemLinks.details("server_A-12", movie);
        check(
                details.equals("plex://libraries/server_A-12/unmatched/%2Flibrary%2Fmetadata%2F42"),
                "Details must target the verified server and the exact metadata key.");
        PlexLibrary.Item episode = item("episode", "/library/metadata/42", 123456, 600000, true);
        check(
                PlexItemLinks.details("server_A-12", episode).contains("/unmatched/tv/"),
                "Episodes must use Plex's TV details route.");
        check(
                PlexItemLinks.details(
                                "server_A-12",
                                item("show", "/library/metadata/42/children", 0, 0, false))
                        .equals(
                                "plex://libraries/server_A-12/unmatched/tv/%2Flibrary%2Fmetadata%2F42"),
                "A show's children key must open that exact show's details, not its children"
                        + " path.");
        check(
                !details.equals(PlexItemLinks.details("other_server", movie)),
                "Items with the same rating key on different servers must remain distinct.");
        check(
                PlexItemLinks.savedProgress(episode)
                        .equals("\n\nSaved progress: 2:03 of 10:00. Open Plex to choose playback."),
                "Saved metadata must describe progress without advertising automatic playback.");
        check(
                PlexItemLinks.savedProgress(
                                item("movie", "/library/metadata/42", 3723999, 7200000, false))
                        .contains("1:02:03 of 2:00:00"),
                "Long saved positions must preserve hours and floor fractional seconds.");
        check(
                PlexItemLinks.savedProgress(item("movie", "/library/metadata/42", 999, 5000, true))
                        .contains("0:00 of 0:05"),
                "A subsecond saved position must not be rounded forward.");
        for (String key :
                new String[] {
                    "",
                    "/library/metadata/7",
                    "/library/metadata/42/children",
                    "/library/metadata/42?X-Plex-Token=example",
                    "https://example.invalid/42"
                }) {
            PlexLibrary.Item invalid = item("movie", key, 1000, 5000, true);
            check(
                    PlexItemLinks.details("server_A-12", invalid) == null,
                    "Metadata keys must match the selected item exactly and contain no"
                            + " credentials.");
        }
        for (String machine :
                new String[] {"", "bad/server", "server?token=example", "server#fragment"}) {
            check(
                    PlexItemLinks.details(machine, movie) == null,
                    "Server identity must not inject another route, query, or fragment.");
        }
        for (PlexLibrary.Item noProgress :
                new PlexLibrary.Item[] {
                    item("show", "/library/metadata/42", 1000, 5000, true),
                    null,
                    item("movie", "/library/metadata/42", 0, 5000, true),
                    item("episode", "/library/metadata/42", 5000, 5000, true),
                    item("movie", "/library/metadata/42", 5001, 5000, true),
                    item("movie", "/library/metadata/42", 1000, 0, true)
                }) {
            check(
                    PlexItemLinks.savedProgress(noProgress).isEmpty(),
                    "Only unfinished movies or episodes with saved positions show progress.");
        }
    }

    private static PlexLibrary.Item item(
            String type, String key, long offset, long duration, boolean media) throws Exception {
        JSONObject metadata =
                new JSONObject()
                        .put("ratingKey", "42")
                        .put("title", "Test item")
                        .put("type", type)
                        .put("key", key)
                        .put("viewOffset", offset)
                        .put("duration", duration);
        if (media)
            metadata.put(
                    "Media",
                    new org.json.JSONArray()
                            .put(
                                    new JSONObject()
                                            .put(
                                                    "Part",
                                                    new org.json.JSONArray()
                                                            .put(
                                                                    new JSONObject()
                                                                            .put(
                                                                                    "key",
                                                                                    "/library/parts/72/123/file.mkv")))));
        return PlexLibrary.items(
                        new JSONObject()
                                .put(
                                        "MediaContainer",
                                        new JSONObject()
                                                .put(
                                                        "Metadata",
                                                        new org.json.JSONArray().put(metadata))))
                .get(0);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
