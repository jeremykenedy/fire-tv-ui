package com.jeremykenedy.firetv.ui;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/** Explicit checks of published stable releases; bundled pins are never called latest. */
final class ReleaseUpdates {
    static final class AccessUnavailableException extends IllegalStateException {
        AccessUnavailableException() {
            super("No accessible published release. Private releases require the computer updater.");
        }
    }
    static final class RateLimitException extends IllegalStateException {
        RateLimitException() {
            super("GitHub limited update checks. Remaining network checks were paused; try again later.");
        }
    }
    private ReleaseUpdates() {
    }

    static ScreensaverCatalog.Entry latest(ScreensaverCatalog.Entry installed) throws Exception {
        String filename = new URL(installed.url).getPath();
        filename = filename.substring(filename.lastIndexOf('/') + 1);
        return latest(installed.name, installed.pkg, installed.repo, filename,
                installed.dream, installed.preview);
    }

    static ScreensaverCatalog.Entry latest(String name, String pkg, String repo,
            String filename, String dream, String preview) throws Exception {
        if (!ScreensaverCatalog.knownRepository(repo)) {
            throw new IllegalArgumentException("Unknown update repository.");
        }
        JSONObject release = new JSONObject(read(new URL("https://api.github.com/repos/"
                + repo + "/releases/latest"), 2 * 1048576, false));
        if (release.getBoolean("draft") || release.getBoolean("prerelease")) {
            throw new IllegalStateException("The repository did not return a published stable release.");
        }
        return fromRelease(name, pkg, repo, filename, dream, preview, release,
                !filename.contains("{tag}") && !filename.contains("{version}"));
    }

    static ScreensaverCatalog.Entry latestContainingAsset(String name, String pkg, String repo,
            String filename, String dream, String preview) throws Exception {
        if (!ScreensaverCatalog.knownRepository(repo)) {
            throw new IllegalArgumentException("Unknown update repository.");
        }
        JSONArray releases = new JSONArray(read(new URL("https://api.github.com/repos/"
                + repo + "/releases?per_page=100"), 2 * 1048576, false));
        JSONObject selected = null;
        Instant newest = null;
        for (int i = 0; i < Math.min(releases.length(), 100); i++) {
            JSONObject release = releases.getJSONObject(i);
            if (release.getBoolean("draft") || release.getBoolean("prerelease")) continue;
            Instant published;
            try {
                published = Instant.parse(release.optString("published_at", ""));
            } catch (java.time.format.DateTimeParseException unavailable) {
                continue;
            }
            if (exactAsset(release.getJSONArray("assets"), filename) != null
                    && (newest == null || published.isAfter(newest))) {
                selected = release;
                newest = published;
            }
        }
        if (selected == null) {
            throw new IllegalStateException("No exact Android APK was found in the last 100 published releases.");
        }
        return fromRelease(name, pkg, repo, filename, dream, preview, selected, false);
    }

    static boolean matchesRequestedAsset(ScreensaverCatalog.Entry entry, String filename) throws Exception {
        String expected = expandFilename(filename, entry.tag);
        String path = new URL(entry.url).getPath();
        return expected.equals(path.substring(path.lastIndexOf('/') + 1));
    }

    private static String expandFilename(String filename, String tag) {
        if (!filename.contains("{tag}") && !filename.contains("{version}")) return filename;
        if (UpdateVersions.compare(tag, tag) == null) {
            throw new IllegalArgumentException("The release artifact requires a numeric version tag.");
        }
        String version = tag.startsWith("v") ? tag.substring(1) : tag;
        return filename.replace("{tag}", tag).replace("{version}", version);
    }

    private static JSONObject exactAsset(JSONArray assets, String filename) throws Exception {
        JSONObject found = null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            if (filename.equals(asset.getString("name"))) {
                if (found != null) throw new IllegalStateException("The expected APK asset is ambiguous.");
                found = asset;
            }
        }
        return found;
    }

    private static ScreensaverCatalog.Entry fromRelease(String name, String pkg, String repo,
            String filename, String dream, String preview, JSONObject release,
            boolean allowSingleApk) throws Exception {
        filename = expandFilename(filename, release.getString("tag_name"));
        JSONArray assets = release.getJSONArray("assets");
        JSONObject apk = exactAsset(assets, filename);
        JSONObject onlyApk = null;
        int apkCount = 0;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            String assetName = asset.getString("name");
            if (assetName.endsWith(".apk")) {
                onlyApk = asset;
                apkCount++;
            }
        }
        if (apk == null && allowSingleApk && apkCount == 1) {
            apk = onlyApk;
        }
        if (apk == null) {
            throw new IllegalStateException("The published release has no unambiguous expected APK asset.");
        }
        filename = apk.getString("name");
        JSONObject checksum = null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            if ((filename + ".sha256").equals(asset.getString("name"))) {
                checksum = asset;
            }
        }
        String hash = null;
        if (checksum != null) {
            URL source = new URL(checksum.getString("browser_download_url"));
            requireReleaseUrl(source, repo);
            Matcher match = Pattern.compile("^\\s*([a-fA-F0-9]{64})(?:\\s|$)")
                    .matcher(read(source, 16384, true));
            if (match.find()) {
                hash = match.group(1).toLowerCase(java.util.Locale.ROOT);
            } else {
                throw new IllegalStateException("The published checksum file is invalid.");
            }
        }
        String digest = apk.optString("digest", "");
        if (digest.matches("sha256:[a-fA-F0-9]{64}")) {
            String apiHash = digest.substring(7).toLowerCase(java.util.Locale.ROOT);
            if (hash != null && !hash.equals(apiHash)) {
                throw new IllegalStateException("The published release checksums disagree.");
            }
            hash = apiHash;
        }
        if (hash == null) {
            throw new IllegalStateException("Published " + release.getString("tag_name")
                    + ": no verifiable APK checksum; installation is unavailable.");
        }
        JSONObject value = new JSONObject();
        value.put("name", name).put("package", pkg)
                .put("repo", repo).put("dream", dream)
                .put("preview", preview).put("tag", release.getString("tag_name"))
                .put("url", apk.getString("browser_download_url"))
                .put("bytes", apk.getLong("size")).put("sha256", hash);
        requireReleaseUrl(new URL(value.getString("url")), repo);
        return new ScreensaverCatalog.Entry(value);
    }

    private static void requireReleaseUrl(URL url, String repo) {
        if (!"https".equals(url.getProtocol()) || !"github.com".equals(url.getHost())
                || url.getUserInfo() != null || url.getPort() != -1
                || url.getQuery() != null || url.getRef() != null
                || !url.getPath().startsWith("/" + repo + "/releases/download/")) {
            throw new IllegalArgumentException("The release points outside its repository.");
        }
    }

    private static String read(URL source, int limit, boolean redirectsAllowed) throws Exception {
        URL location = source;
        for (int redirects = 0; redirects <= 5; redirects++) {
            String host = location.getHost();
            if (!"https".equals(location.getProtocol()) || location.getUserInfo() != null
                    || location.getPort() != -1 || !("api.github.com".equals(host)
                    || "github.com".equals(host) || "release-assets.githubusercontent.com".equals(host)
                    || "objects.githubusercontent.com".equals(host))) {
                throw new IllegalStateException("The release check redirected outside GitHub.");
            }
            HttpURLConnection connection = (HttpURLConnection) location.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("User-Agent", "Fire-TV-UI");
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            try {
                int code = connection.getResponseCode();
                if (code == 429 || code == 403 && "0".equals(connection.getHeaderField("X-RateLimit-Remaining"))) {
                    throw new RateLimitException();
                }
                if (code == 401 || code == 403 || code == 404) {
                    throw new AccessUnavailableException();
                }
                if (code == 200) {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    try (InputStream input = connection.getInputStream()) {
                        byte[] bytes = new byte[4096];
                        int count;
                        while ((count = input.read(bytes)) != -1) {
                            if (Thread.currentThread().isInterrupted()) {
                                throw new InterruptedException("Update check cancelled.");
                            }
                            if (output.size() + count > limit) {
                                throw new IllegalStateException("The release response exceeds the allowed size.");
                            }
                            output.write(bytes, 0, count);
                        }
                    }
                    return output.toString("UTF-8");
                }
                if (!redirectsAllowed || redirects == 5 || !(code == 301 || code == 302
                        || code == 303 || code == 307 || code == 308)) {
                    throw new IllegalStateException("Release check returned HTTP " + code + ".");
                }
                String next = connection.getHeaderField("Location");
                if (next == null) {
                    throw new IllegalStateException("The release redirect has no destination.");
                }
                location = new URL(location, next);
            } finally {
                connection.disconnect();
            }
        }
        throw new IllegalStateException("The release check could not finish.");
    }
}
