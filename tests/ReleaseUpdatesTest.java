package com.jeremykenedy.firetv.ui;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

public final class ReleaseUpdatesTest {
    static final String REPO = "jeremykenedy/example";
    static final String API = "https://api.github.com/repos/" + REPO + "/releases/latest";
    static final String APK = "https://github.com/" + REPO + "/releases/download/v2.0.0/example.apk";
    static final String HASH = "a".repeat(64);
    static final Map<String, String> content = new HashMap<>();
    static int status = 200;
    static int checks;

    static JSONObject asset(String name, String url) throws Exception {
        return new JSONObject().put("name", name).put("browser_download_url", url).put("size", 100);
    }

    static JSONObject release() throws Exception {
        content.clear();
        status = 200;
        content.put(APK + ".sha256", HASH + "  example.apk\n");
        return new JSONObject().put("draft", false).put("prerelease", false).put("tag_name", "v2.0.0")
                .put("assets", new JSONArray().put(asset("example.apk", APK))
                        .put(asset("example.apk.sha256", APK + ".sha256")));
    }

    static ScreensaverCatalog.Entry check(JSONObject release) throws Exception {
        return checkAsset(release, "example.apk");
    }

    static ScreensaverCatalog.Entry checkAsset(JSONObject release, String filename) throws Exception {
        content.put(API, release.toString());
        return ReleaseUpdates.latest("Example", "example.saver", REPO, filename, "example.saver/.Dream", "");
    }

    static void reject(JSONObject release) throws Exception {
        try {
            check(release);
            throw new AssertionError("Unverified release was offered");
        } catch (IllegalStateException | IllegalArgumentException expected) {
            checks++;
        }
    }

    public static void main(String[] args) throws Exception {
        URL.setURLStreamHandlerFactory(new URLStreamHandlerFactory() {
            @Override
            public URLStreamHandler createURLStreamHandler(String protocol) {
                return new URLStreamHandler() {
                    @Override
                    protected URLConnection openConnection(URL url) {
                        return new HttpURLConnection(url) {
                            @Override public void connect() { }
                            @Override public boolean usingProxy() { return false; }
                            @Override public void disconnect() { }
                            @Override public int getResponseCode() { return status; }
                            @Override public InputStream getInputStream() {
                                String value = content.get(url.toString());
                                if (value == null) throw new AssertionError("Unexpected network request: " + url);
                                return new ByteArrayInputStream(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            }
                        };
                    }
                };
            }
        });
        ScreensaverCatalog.Entry valid = check(release());
        if (!valid.sha256.equals(HASH) || !valid.tag.equals("v2.0.0") || !valid.url.equals(APK)) throw new AssertionError();
        checks++;
        reject(release().put("draft", true));
        reject(release().put("prerelease", true));
        JSONObject r = release(); content.put(APK + ".sha256", "invalid"); reject(r);
        r = release(); r.getJSONArray("assets").getJSONObject(0).put("digest", "sha256:" + "b".repeat(64)); reject(r);
        r = release(); r.put("assets", new JSONArray().put(asset("example.apk", APK))); reject(r);
        r = release(); r.put("assets", new JSONArray().put(asset("example.apk", APK).put("digest", "sha256:" + HASH)));
        if (!check(r).sha256.equals(HASH)) throw new AssertionError(); checks++;
        r = release(); r.getJSONArray("assets").getJSONObject(0).put("browser_download_url", "https://github.com/other/repo/releases/download/v2/example.apk"); reject(r);
        r = release(); r.getJSONArray("assets").getJSONObject(0).put("size", -1); reject(r);
        r = release(); r.put("assets", new JSONArray().put(asset("one.apk", APK)).put(asset("two.apk", APK))); reject(r);
        r = release().put("tag_name", "v5.9.0");
        String futureName = "OwnTV-v5.9.0.apk";
        String futureUrl = "https://github.com/" + REPO + "/releases/download/v5.9.0/" + futureName;
        r.put("assets", new JSONArray().put(asset("OwnTV-v5.9.0-mobile.apk", APK))
                .put(asset(futureName, futureUrl).put("digest", "sha256:" + HASH)));
        if (!checkAsset(r, "OwnTV-{tag}.apk").url.equals(futureUrl)) {
            throw new AssertionError("Versioned TV artifact was not selected over the mobile APK");
        }
        try {
            checkAsset(r.put("tag_name", "v5/9.0"), "OwnTV-{tag}.apk");
            throw new AssertionError("Non-version tag expanded into an artifact name");
        } catch (IllegalArgumentException | IllegalStateException expected) { checks++; }
        r = release().put("tag_name", "v5.9.0");
        String oldUrl = "https://github.com/" + REPO
                + "/releases/download/v5.1.0/OwnTV-v5.1.0.apk";
        r.put("assets", new JSONArray().put(asset("OwnTV-v5.1.0.apk", oldUrl)
                .put("digest", "sha256:" + HASH)));
        try {
            checkAsset(r, "OwnTV-{tag}.apk");
            throw new AssertionError("A missing current TV artifact accepted a single old APK");
        } catch (IllegalArgumentException | IllegalStateException expected) { checks++; }
        r = release().put("assets", new JSONArray().put(asset("renamed.apk",
                "https://github.com/" + REPO + "/releases/download/v2.0.0/renamed.apk")
                .put("digest", "sha256:" + HASH)));
        if (!check(r).url.endsWith("/renamed.apk")) {
            throw new AssertionError("The ordinary single-APK compatibility path changed");
        }
        checks++;
        String uiRepo = "jeremykenedy/fire-tv-ui";
        String uiApi = "https://api.github.com/repos/" + uiRepo + "/releases/latest";
        String uiUrl = "https://github.com/" + uiRepo
                + "/releases/download/v1.9.0/fire-tv-ui-1.9.0.apk";
        JSONObject uiRelease = release().put("tag_name", "v1.9.0")
                .put("assets", new JSONArray().put(asset("fire-tv-ui-1.9.0.apk", uiUrl)
                        .put("digest", "sha256:" + HASH)));
        content.put(uiApi, uiRelease.toString());
        ScreensaverCatalog.Entry publicUi = ReleaseUpdates.latest("Fire TV UI",
                "com.jeremykenedy.firetv.ui", uiRepo, "fire-tv-ui-{version}.apk", "", "");
        if (!uiUrl.equals(publicUi.url) || !HASH.equals(publicUi.sha256)
                || !ReleaseUpdates.matchesRequestedAsset(publicUi, "fire-tv-ui-{version}.apk"))
            throw new AssertionError("Public UI checks did not resolve the exact versioned APK");
        checks++;
        uiRelease.put("assets", new JSONArray().put(asset("unrelated.apk", uiUrl)
                .put("digest", "sha256:" + HASH)));
        content.put(uiApi, uiRelease.toString());
        try {
            ReleaseUpdates.latest("Fire TV UI", "com.jeremykenedy.firetv.ui", uiRepo,
                    "fire-tv-ui-{version}.apk", "", "");
            throw new AssertionError("A different single APK replaced the expected UI release");
        } catch (IllegalStateException expected) { checks++; }
        for (int inaccessible : new int[]{401, 403, 404}) {
            status = inaccessible;
            try {
                ReleaseUpdates.latest("Fire TV UI", "com.jeremykenedy.firetv.ui", uiRepo,
                        "fire-tv-ui-{version}.apk", "", "");
                throw new AssertionError("An inaccessible source became a current-version claim");
            } catch (ReleaseUpdates.AccessUnavailableException expected) { checks++; }
        }
        status = 429;
        try {
            ReleaseUpdates.latest("Fire TV UI", "com.jeremykenedy.firetv.ui", uiRepo,
                    "fire-tv-ui-{version}.apk", "", "");
            throw new AssertionError("Rate limiting was mistaken for private repository access");
        } catch (ReleaseUpdates.RateLimitException expected) { checks++; }
        JSONObject helper = release().put("tag_name", "v3.9.1")
                .put("published_at", "2026-10-10T12:00:00Z")
                .put("assets", new JSONArray().put(asset("firetv-home-redirect.apk",
                        "https://github.com/" + REPO + "/releases/download/v3.9.1/firetv-home-redirect.apk")
                        .put("digest", "sha256:" + HASH)));
        JSONObject desktop = new JSONObject(helper.toString()).put("tag_name", "v4.4.0")
                .put("published_at", "2026-10-22T12:00:00Z")
                .put("assets", new JSONArray().put(asset("desktop.exe", APK)));
        JSONObject draft = new JSONObject(helper.toString()).put("draft", true)
                .put("published_at", "2026-10-23T12:00:00Z");
        JSONObject preview = new JSONObject(helper.toString()).put("prerelease", true)
                .put("published_at", "2026-10-24T12:00:00Z");
        JSONObject old = new JSONObject(helper.toString()).put("tag_name", "v99.0.0")
                .put("published_at", "2026-10-09T12:00:00Z");
        String history = "https://api.github.com/repos/" + REPO + "/releases?per_page=100";
        content.put(history, new JSONArray().put(old).put(desktop).put(preview).put(helper).put(draft).toString());
        ScreensaverCatalog.Entry historical = ReleaseUpdates.latestContainingAsset("Home Redirect",
                "com.jeremykenedy.firetv.homeredirect", REPO, "firetv-home-redirect.apk", "", "");
        if (!historical.tag.equals("v3.9.1") || !historical.url.endsWith("/firetv-home-redirect.apk")) {
            throw new AssertionError("Desktop release or unordered tag displaced the exact Android asset");
        }
        checks++;
        JSONObject wrongOnly = new JSONObject(helper.toString()).put("assets",
                new JSONArray().put(asset("other.apk", APK).put("digest", "sha256:" + HASH)));
        JSONObject noPublishedDate = new JSONObject(helper.toString());
        noPublishedDate.remove("published_at");
        for (JSONArray missing : new JSONArray[]{new JSONArray().put(desktop),
                new JSONArray().put(draft).put(preview), new JSONArray().put(wrongOnly),
                new JSONArray().put(noPublishedDate)}) {
            content.put(history, missing.toString());
            try {
                ReleaseUpdates.latestContainingAsset("Helper", "example.helper", REPO,
                        "firetv-home-redirect.apk", "", "");
                throw new AssertionError("An unavailable or unrelated helper release was accepted");
            } catch (IllegalStateException expected) { checks++; }
        }
        JSONArray bounded = new JSONArray();
        for (int i = 0; i < 100; i++) bounded.put(desktop);
        bounded.put(helper);
        content.put(history, bounded.toString());
        try {
            ReleaseUpdates.latestContainingAsset("Helper", "example.helper", REPO,
                    "firetv-home-redirect.apk", "", "");
            throw new AssertionError("Release history exceeded the bounded lookup");
        } catch (IllegalStateException expected) { checks++; }
        if (!ReleaseUpdates.matchesRequestedAsset(historical, "firetv-home-redirect.apk")
                || ReleaseUpdates.matchesRequestedAsset(valid, "firetv-home-redirect.apk")) throw new AssertionError();
        ScreensaverCatalog.Entry stale = new ScreensaverCatalog.Entry(valid.json()
                .put("tag", "v5.9.0").put("url", oldUrl));
        if (ReleaseUpdates.matchesRequestedAsset(stale, "OwnTV-{tag}.apk")) {
            throw new AssertionError("A cached old APK satisfied the new versioned artifact policy");
        }
        if (UpdateVersions.compare("v3.9.1", "1.2.5", 125L, 125) != 0
                || UpdateVersions.compare("v3.9.1", "1.2.5", 126L, 125) <= 0
                || UpdateVersions.compare("v99.0.0", "1.2.5", 124L, 125) >= 0
                || UpdateVersions.compare("v3.9.1", "1.2.5", 0L, 125) != null
                || UpdateVersions.compare("v2.0.0", "1.0.0", null, 1) <= 0) {
            throw new AssertionError("Repository tags were mistaken for verified Android app versions");
        }
        checks += 7;
        for (int code : new int[]{404,403,429,301}) { r = release(); status=code; reject(r); }
        if (UpdateVersions.compare("v1.10.0", "1.9.9") <= 0
                || UpdateVersions.compare("1.0", "v1.0.0") != 0
                || UpdateVersions.compare("v1.0.0", "1.1") >= 0
                || UpdateVersions.compare("mirror-1.0", "1.0") != null
                || UpdateVersions.compare("1.0-beta", "1.0") != null
                || UpdateVersions.compare(null, "1.0") != null
                || UpdateVersions.compare("1".repeat(65), "1.0") != null) throw new AssertionError();
        if (!UpdateVersions.cacheFresh(1000, 2000)
                || UpdateVersions.cacheFresh(1000, 1000 + 6 * 60 * 60 * 1000L)
                || UpdateVersions.cacheFresh(2000, 1000)
                || UpdateVersions.cacheFresh(0, 1000)) throw new AssertionError();
        checks += 11;
        System.out.println("Published release and version checks passed: " + checks);
    }
}
