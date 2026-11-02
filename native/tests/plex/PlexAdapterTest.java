package com.jeremykenedy.firetv.ui.nativeapp.providers.plex;

import org.json.JSONObject;
import java.util.List;

public final class PlexAdapterTest {
    public static void main(String[] args) throws Exception {
        // Sanitized legacy plaintext schema. The real schema remains encrypted on device.
        PlexConnection saved = PlexConnection.parse(new JSONObject("{\"server\":\"https://fixture.invalid:32400\",\"machine\":\"fixture-machine\",\"token\":\"fixture-server-token\",\"profiles\":[{\"id\":\"7\",\"name\":\"Fixture profile\",\"token\":\"fixture-profile-token\"}]}"));
        check(saved.feeds.enabled.contains("continue"), "legacy defaults retained");
        PlexConnection restored = PlexConnection.parse(saved.json());
        check(restored.sourceKey().equals(saved.sourceKey()), "source identity survives schema roundtrip");
        check(restored.profiles.size() == 1, "selected profile retained");
        PlexConnection profile = restored.forProfile(restored.profiles.get(0));
        check(!profile.sourceKey().equals(saved.sourceKey()), "profile isolates item identity");
        check("fixture-profile-token".equals(profile.token), "profile credential scoped");
        check(restored.forSource(profile.sourceKey()).token.equals(profile.token), "retained profile source resolves");
        check(!saved.sourceKey().contains("fixture"), "credential-free source identity");
        List<PlexLibrary.Item> items = PlexLibrary.items(new JSONObject("{\"MediaContainer\":{\"Metadata\":[{\"ratingKey\":\"12\",\"type\":\"episode\",\"title\":\"Pilot\",\"grandparentTitle\":\"Fixture Show\",\"grandparentRatingKey\":\"9\",\"key\":\"/library/metadata/12\",\"viewOffset\":5000,\"duration\":9000},{\"ratingKey\":\"13\",\"type\":\"episode\",\"title\":\"Next\",\"grandparentTitle\":\"Fixture Show\",\"grandparentRatingKey\":\"bad/9\",\"key\":\"/library/metadata/13\"}]}}"));
        check(items.size() == 2, "fixture episodes read");
        check("9".equals(items.get(0).seriesId), "server series identity retained");
        check(items.get(1).seriesId.isEmpty(), "display title never supplies missing trusted identity");
        String details = PlexItemLinks.details(saved.machine, items.get(0));
        check(details.startsWith("plex://libraries/fixture-machine/"), "exact server title detail target");
        check(!details.contains("token"), "no credential in details target");
        check(PlexItemLinks.savedProgress(items.get(0)).contains("Open Plex to choose playback"), "progress is not claimed playback");
        for (String host : new String[]{"10.0.0.2", "172.16.0.2", "172.31.255.254", "192.168.1.2", "169.254.1.2", "127.0.0.1", "[::1]", "[fd12::2]", "[fe80::7]"})
            check(PlexTransportPolicy.permitsAddress("http://" + host + ":32400"), "numeric local address accepted");
        for (String host : new String[]{"example.invalid", "localhost", "192.168.1.2.example.invalid", "8.8.8.8", "172.32.0.1", "192.168.01.2", "2130706433", "[2001:4860::1]", "[::ffff:127.0.0.1]"})
            check(!PlexTransportPolicy.permitsAddress("http://" + host + ":32400"), "public, DNS and ambiguous HTTP rejected");
        check(PlexTransportPolicy.permitsAddress("https://fixture.invalid:32400"), "remote HTTPS accepted");
        String oversized = String.join("", java.util.Collections.nCopies(513, "x"));
        JSONObject huge = new JSONObject().put("ratingKey", "99").put("type", "movie").put("title", oversized);
        JSONObject hugeResponse = new JSONObject().put("MediaContainer", new JSONObject().put("Metadata", new org.json.JSONArray().put(huge)));
        check(PlexLibrary.items(hugeResponse).isEmpty(), "oversized retained title rejected");
        huge.put("title", "Short").put("summary", String.join("", java.util.Collections.nCopies(2049, "x")))
                .put("art", "/library/" + String.join("", java.util.Collections.nCopies(8192, "x")));
        check(PlexLibrary.items(hugeResponse).get(0).summary.isEmpty(), "oversized summary omitted");
        check(PlexLibrary.items(hugeResponse).get(0).art == null, "oversized artwork path omitted");
        huge.put("type", "episode").put("grandparentTitle", oversized);
        check(PlexLibrary.items(hugeResponse).isEmpty(), "oversized series title rejected");
        long canceled = PlexSession.begin();
        PlexSession.cancel(canceled);
        check(!PlexSession.commit(canceled, new PlexSession.Change() {
            @Override public void apply() { throw new AssertionError("canceled save ran"); }
        }), "stale UI action cannot replace credentials");
        System.out.println("Plex adapter fixtures passed");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
