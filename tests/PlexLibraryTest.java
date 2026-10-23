package com.jeremykenedy.firetv.ui;

import org.json.JSONObject;

import java.util.List;

public final class PlexLibraryTest {
    public static void main(String[] args) throws Exception {
        check(
                PlexValues.serverBase("http://192.168.1.2:32400/web/index.html#!/")
                        .equals("http://192.168.1.2:32400"),
                "Browser addresses must normalize to the server base.");
        for (String bad :
                new String[] {
                    "file:///tmp/server",
                    "http://user:secret@server:32400",
                    "https://server/?X-Plex-Token=example",
                    "https://server/not-a-server"
                }) {
            try {
                PlexValues.serverBase(bad);
                throw new AssertionError("Unsafe server address accepted.");
            } catch (IllegalArgumentException expected) {
            }
        }
        check(
                !PlexValues.libraryPath("https://outside.example/poster"),
                "Artwork must stay on the authorized server.");
        check(!PlexValues.libraryPath("/library/../account"), "Artwork must stay in the library.");
        check(
                !PlexValues.libraryPath("/library/image?token=example"),
                "Credentials must not be accepted in artwork URLs.");
        String image = "/" + new String(new char[64]).replace('\0', 'a') + ".jpg";
        check(PlexValues.artworkName(image) != null, "Own cached artwork must resolve.");
        for (String path :
                new String[] {
                    "/../connection.enc",
                    "/%2e%2e/connection.enc",
                    image + "/extra",
                    image + "?token=example"
                }) {
            check(
                    PlexValues.artworkName(path) == null,
                    "Private artwork must reject paths outside its exact file namespace.");
        }
        String metadata =
                "{\"MediaContainer\":{\"Metadata\":[{\"ratingKey\":\"42\",\"type\":\"episode\",\"title\":\"Episode\",\"grandparentTitle\":\"Series\",\"art\":\"/library/metadata/42/art/1\",\"viewOffset\":123,\"duration\":500},{\"ratingKey\":\"42\",\"type\":\"episode\",\"title\":\"Episode\"},{\"ratingKey\":\"bad/id\",\"type\":\"movie\",\"title\":\"Invalid\"}]}}";
        List<PlexLibrary.Item> items = PlexLibrary.items(new JSONObject(metadata));
        check(
                items.size() == 1 && items.get(0).id.equals("42"),
                "Invalid IDs must be rejected and duplicate media deduplicated.");
        check(items.get(0).title.contains("Series"), "Episodes need a useful series title.");
        List<PlexLibrary.Item> hubs =
                PlexLibrary.items(
                        new JSONObject(
                                "{\"MediaContainer\":{\"Hub\":[{\"Metadata\":[{\"ratingKey\":\"7\",\"type\":\"movie\",\"title\":\"Movie\",\"thumb\":\"https://outside.example/art\"}]}]}}"));
        check(
                hubs.size() == 1 && hubs.get(0).art == null,
                "External art must not receive the server credential.");
        check(
                PlexLibrary.items(new JSONObject("{\"MediaContainer\":{\"Metadata\":[]}}"))
                        .isEmpty(),
                "A successful empty library stays empty.");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
