package com.jeremykenedy.firetv.ui;

public final class PlexArtworkRedirectTest {
    public static void main(String[] args) throws Exception {
        String server = "http://fixture.invalid:32400/library/metadata/123/thumb";
        String cdn = PlexArtworkRedirect.next(server, "https://images.plex.tv/image/public.jpg");
        check(!PlexArtworkRedirect.sameOrigin(server, cdn), "PMS credentials must be dropped for the image CDN.");
        check(PlexArtworkRedirect.sameOrigin(server, PlexArtworkRedirect.next(server, "/library/metadata/123/thumb/2")),
                "Relative artwork on the same server keeps its authorized scope.");
        check(PlexArtworkRedirect.sameOrigin("https://images.plex.tv/a", "https://images.plex.tv:443/b"),
                "Explicit default ports retain the same origin.");
        for (String location : new String[] {
                "https://images.plex.tv.evil.invalid/a", "https://other.invalid/a",
                "http://images.plex.tv/a", "https://images.plex.tv:8443/a",
                "https://name:password@images.plex.tv/a", "https://images.plex.tv/a#fragment",
                "https://images.plex.tv/a?X-Plex-Token=private",
                "https://images.plex.tv/a?url=https%3A%2F%2Fexample.invalid%2Fa%3Ftoken%3Dprivate"
        }) {
            boolean denied = false;
            try { PlexArtworkRedirect.next(server, location); }
            catch (java.io.IOException expected) { denied = true; }
            check(denied, "An unsupported origin or credential-bearing redirect is denied.");
        }
        System.out.println("11 artwork redirect checks passed.");
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
