package com.jeremykenedy.firetv.ui.nativeapp.providers.plex;

import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public final class LegacyPlexCardLookupTest {
    public static void main(String[] args) throws Exception {
        String source = repeat('a');
        LegacyPlexCardLookup.Reference valid = LegacyPlexCardLookup.parse("firetvui://plex/item/" + source + "/12");
        check(valid != null && valid.source.equals(source) && valid.item.equals("12"));
        for (String invalid : new String[]{"https://plex/item/" + source + "/12", "firetvui://plex/item/" + source + "/12?token=bad",
                "firetvui://plex/item/" + source + "/12#fragment", "firetvui://plex/item/" + source + "/12/extra",
                "firetvui://plex/item/" + source + "/%31%32", "firetvui://plex@other/item/" + source + "/12",
                "firetvui://plex:32400/item/" + source + "/12", "firetvui://user@plex/item/" + source + "/12"})
            check(LegacyPlexCardLookup.parse(invalid) == null);
        Fake reader = new Fake(source);
        LegacyPlexCardLookup.Result result = LegacyPlexCardLookup.read(valid, reader);
        check(result != null && result.title.equals("Fixture Show: Pilot") && result.link.startsWith("plex://libraries/machine/unmatched/tv/"));
        check(result.progress.contains("Saved progress") && reader.paths.equals(java.util.Arrays.asList("/identity", "/library/metadata/12")));
        reader = new Fake(repeat('b')); check(LegacyPlexCardLookup.read(valid, reader) == null && reader.paths.isEmpty());
        reader = new Fake(source); reader.machine = "other";
        check(LegacyPlexCardLookup.read(valid, reader) == null && reader.paths.equals(java.util.Collections.singletonList("/identity")));
        reader = new Fake(source); reader.item = "13";
        check(LegacyPlexCardLookup.read(valid, reader) == null);
        reader = new Fake(source); reader.key = "/library/metadata/12?token=bad";
        check(LegacyPlexCardLookup.read(valid, reader) == null);
        System.out.println("Legacy Plex card path, exact connection/server/item verification and details-only handoff passed.");
    }
    static final class Fake implements LegacyPlexCardLookup.Reader {
        final String source; String machine = "machine", item = "12", key = "/library/metadata/12";
        final List<String> paths = new ArrayList<>();
        Fake(String source) { this.source = source; }
        public String sourceKey() { return source; }
        public String expectedMachine() { return "machine"; }
        public JSONObject get(String path) throws Exception {
            paths.add(path);
            if (path.equals("/identity")) return new JSONObject().put("MediaContainer", new JSONObject().put("machineIdentifier", machine));
            return new JSONObject("{\"MediaContainer\":{\"Metadata\":[{\"ratingKey\":\"" + item + "\",\"type\":\"episode\",\"title\":\"Pilot\",\"grandparentTitle\":\"Fixture Show\",\"key\":\"" + key + "\",\"viewOffset\":5000,\"duration\":9000}]}}");
        }
    }
    static String repeat(char value) { return new String(new char[64]).replace('\0', value); }
    static void check(boolean value) { if (!value) throw new AssertionError("Legacy Plex card regression."); }
}
