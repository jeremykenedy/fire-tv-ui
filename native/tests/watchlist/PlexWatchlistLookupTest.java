package com.jeremykenedy.firetv.ui.nativeapp.providers.plex;

import org.json.JSONObject;

public final class PlexWatchlistLookupTest {
    public static void main(String[] args) throws Exception {
        String source = repeat('a'); String key = "plex:" + source + ":12";
        Reader reader = new Reader(source, "machine", "12", false);
        PlexWatchlistLookup.Result matched = PlexWatchlistLookup.evaluate(key, reader);
        check(matched.found && matched.detailsUri.startsWith("plex://libraries/machine/") && matched.checkedAtMillis > 0, "Exact item verified.");
        check(!PlexWatchlistLookup.evaluate(key, new Reader(repeat('b'), "machine", "12", false)).found, "Another profile is unknown.");
        check(!PlexWatchlistLookup.evaluate(key, new Reader(source, "other", "12", false)).found, "Another server is unknown.");
        check(!PlexWatchlistLookup.evaluate(key, new Reader(source, "machine", "13", false)).found, "Another item is unknown.");
        check(!PlexWatchlistLookup.evaluate(key, new Reader(source, "machine", "12", true)).found, "Transport errors remain unknown.");
        check(!PlexWatchlistLookup.evaluate(key + "?token=x", reader).found, "Credential-bearing identity rejected.");
        check(PlexWatchlistLookup.current(matched), "Current session retains a verified result.");
        PlexSession.begin(); check(!PlexWatchlistLookup.current(matched), "Connection change invalidates a prior result.");
        System.out.println("Plex watchlist exact source, server, item, error and session checks passed.");
    }
    private static final class Reader implements LegacyPlexCardLookup.Reader {
        final String source, machine, item; final boolean fail;
        Reader(String source, String machine, String item, boolean fail) { this.source=source; this.machine=machine; this.item=item; this.fail=fail; }
        public String sourceKey() { return source; }
        public String expectedMachine() { return "machine"; }
        public JSONObject get(String path) throws Exception {
            if (fail) throw new java.io.IOException("synthetic");
            if (path.equals("/identity")) return new JSONObject().put("MediaContainer", new JSONObject().put("machineIdentifier", machine));
            return new JSONObject("{\"MediaContainer\":{\"Metadata\":[{\"ratingKey\":\"" + item + "\",\"type\":\"movie\",\"title\":\"Fixture\",\"key\":\"/library/metadata/" + item + "\"}]}}");
        }
    }
    private static String repeat(char value) { return new String(new char[64]).replace('\0', value); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
