package com.jeremykenedy.firetv.ui.nativeapp.providers.plex;

import java.net.URI;
import java.util.List;
import org.json.JSONObject;

/** Exact retained Plex card identity; never identifies a title by its display name. */
final class LegacyPlexCardLookup {
    interface Reader {
        String sourceKey() throws Exception;
        String expectedMachine();
        JSONObject get(String path) throws Exception;
    }
    static final class Reference {
        final String source, item;
        Reference(String source, String item) { this.source = source; this.item = item; }
    }
    static final class Result {
        final String title, summary, progress, link;
        Result(PlexLibrary.Item item, String link) {
            this.title = item.title; this.summary = item.summary;
            this.progress = PlexItemLinks.savedProgress(item); this.link = link;
        }
    }
    private LegacyPlexCardLookup() {}
    static Reference parse(String value) {
        try {
            URI uri = new URI(value);
            String path = uri.getRawPath();
            if (!"firetvui".equals(uri.getScheme()) || !"plex".equals(uri.getRawAuthority())
                    || uri.getRawQuery() != null || uri.getRawFragment() != null || !PlexValues.itemPath(path)) return null;
            String[] parts = path.split("/");
            return new Reference(parts[2], parts[3]);
        } catch (Exception invalid) { return null; }
    }
    static Result read(Reference reference, Reader reader) throws Exception {
        if (reference == null || reader == null || !reference.source.equals(reader.sourceKey())) return null;
        String expected = reader.expectedMachine();
        if (expected == null || expected.isEmpty()) return null;
        JSONObject container = reader.get("/identity").optJSONObject("MediaContainer");
        if (container == null || !expected.equals(container.optString("machineIdentifier", ""))) return null;
        List<PlexLibrary.Item> items = PlexLibrary.items(reader.get("/library/metadata/" + reference.item));
        if (items.isEmpty() || !reference.item.equals(items.get(0).id)) return null;
        String link = PlexItemLinks.details(expected, items.get(0));
        return link == null ? null : new Result(items.get(0), link);
    }
}
