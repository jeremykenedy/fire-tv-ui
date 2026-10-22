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
        content.put(API, release.toString());
        return ReleaseUpdates.latest("Example", "example.saver", REPO, "example.apk", "example.saver/.Dream", "");
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
