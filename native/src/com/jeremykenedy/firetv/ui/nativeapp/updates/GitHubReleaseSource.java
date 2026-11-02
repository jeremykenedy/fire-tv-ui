package com.jeremykenedy.firetv.ui.nativeapp.updates;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.json.JSONArray;
import org.json.JSONObject;

/** Discovers only source releases from this repository and the installed package's channel. */
public final class GitHubReleaseSource implements UpdateCoordinator.Source {
    static final String REPOSITORY = "jeremykenedy/fire-tv-ui";
    private static final String PREVIEW = "com.jeremykenedy.firetv.ui.preview";
    private static final String STABLE = "com.jeremykenedy.firetv.ui";
    private final String packageName, channel;
    private final long installedVersion;
    private final Reader reader;

    interface Reader { byte[] releases(UpdateCoordinator.Cancellation cancellation) throws Exception;
        byte[] manifest(String url, long size, UpdateCoordinator.Cancellation cancellation) throws Exception; }

    public GitHubReleaseSource(String packageName, long installedVersion) {
        this(packageName, installedVersion, new NetworkReader());
    }
    GitHubReleaseSource(String packageName, long installedVersion, Reader reader) {
        this.packageName = packageName;
        this.channel = PREVIEW.equals(packageName) ? "preview" : STABLE.equals(packageName) ? "stable" : "";
        this.installedVersion = installedVersion;
        this.reader = reader;
    }
    @Override public UpdateCoordinator.Release check(UpdateCoordinator.Cancellation cancellation) throws Exception {
        if (channel.isEmpty()) return null;
        JSONArray releases = new JSONArray(new String(reader.releases(cancellation), StandardCharsets.UTF_8));
        UpdateCoordinator.Release best = null;
        for (int i = 0; i < releases.length(); i++) {
            cancellation.check();
            JSONObject entry = releases.getJSONObject(i);
            if (entry.getBoolean("draft") || entry.getBoolean("prerelease") != channel.equals("preview")) continue;
            String tag = entry.getString("tag_name");
            if (!tag.matches("v[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?")
                    || (channel.equals("stable") && tag.contains("-"))) continue;
            JSONArray assets = entry.getJSONArray("assets");
            String manifestName = "release-artifact.json";
            JSONObject manifestAsset = asset(assets, manifestName);
            if (manifestAsset == null) continue;
            long manifestSize = manifestAsset.getLong("size");
            String manifestDigest = manifestAsset.isNull("digest") ? "" : manifestAsset.optString("digest", "");
            if (!"uploaded".equals(manifestAsset.getString("state")) || manifestSize <= 0 || manifestSize > 131072 || (!manifestDigest.isEmpty() && !manifestDigest.matches("sha256:[a-f0-9]{64}")))
                throw new IOException("Invalid source manifest asset.");
            String manifestUrl = releaseUrl(tag, manifestName);
            byte[] raw = reader.manifest(manifestUrl, manifestSize, cancellation);
            if (raw.length != manifestSize || (!manifestDigest.isEmpty() && !manifestDigest.substring(7).equals(UpdateCoordinator.hex(MessageDigest.getInstance("SHA-256").digest(raw)))))
                throw new IOException("Source manifest digest mismatch.");
            JSONObject manifest = new JSONObject(new String(raw, StandardCharsets.UTF_8));
            String versionName = manifest.getString("version");
            String apkName = channel.equals("preview") ? "fire-tv-ui-source-preview-" + versionName + ".apk"
                    : "fire-tv-ui-" + versionName + ".apk";
            long version = manifest.getLong("versionCode");
            if (!packageName.equals(manifest.getString("applicationId")) || !tag.equals("v" + versionName)
                    || !apkName.equals(manifest.getString("apk")) || manifest.getBoolean("sourceTreeDirty")
                    || !manifest.getBoolean("apkManifestVerified") || manifest.getBoolean("debuggable")
                    || manifest.getJSONArray("binaryLauncherInputs").length() != 0
                    || manifest.getJSONObject("sourceInputs").length() == 0
                    || !manifest.getString("sourceCommit").matches("[a-f0-9]{40}")
                    || !manifest.getString("signerSha256").matches("[a-f0-9]{64}")
                    || !manifest.getString("signerSha256").equals(manifest.getString("expectedSignerSha256"))
                    || !manifest.getString("signing").equals(channel.equals("preview") ? "maintained-preview" : "production"))
                throw new IOException("Incompatible source manifest.");
            if (version <= installedVersion || (best != null && version <= best.version)) continue;
            JSONObject apk = asset(assets, apkName);
            if (apk == null || !"uploaded".equals(apk.getString("state"))) throw new IOException("Source APK missing.");
            String hash = manifest.getString("sha256");
            long size = manifest.getLong("bytes");
            String apkDigest = apk.isNull("digest") ? "" : apk.optString("digest", "");
            if (apk.getLong("size") != size || (!apkDigest.isEmpty() && !apkDigest.equals("sha256:" + hash)))
                throw new IOException("Source APK metadata mismatch.");
            String notes = entry.optString("body", "");
            if (notes.length() > 16384) notes = notes.substring(0, 16384);
            best = new UpdateCoordinator.Release(REPOSITORY, tag, packageName, releaseUrl(tag, apkName), hash,
                    size, version, versionName, notes);
        }
        return best;
    }
    private static JSONObject asset(JSONArray assets, String name) throws Exception {
        JSONObject found = null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject item = assets.getJSONObject(i);
            if (name.equals(item.getString("name"))) {
                if (found != null) throw new IOException("Duplicate release asset.");
                found = item;
            }
        }
        return found;
    }
    private static String releaseUrl(String tag, String name) {
        return "https://github.com/" + REPOSITORY + "/releases/download/" + tag + "/" + name;
    }
    private static final class NetworkReader implements Reader {
        @Override public byte[] releases(UpdateCoordinator.Cancellation cancellation) throws Exception {
            URL url = new URL("https://api.github.com/repos/" + REPOSITORY + "/releases?per_page=100");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000); connection.setReadTimeout(15000);
            connection.setRequestProperty("User-Agent", "Fire-TV-UI-Source");
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            try {
                cancellation.check();
                if (connection.getResponseCode() != 200) throw new IOException("Release discovery unavailable.");
                try (InputStream input = connection.getInputStream()) { return read(input, 2097152, cancellation); }
            } finally { connection.disconnect(); }
        }
        @Override public byte[] manifest(String url, long size, UpdateCoordinator.Cancellation cancellation) throws Exception {
            try (InputStream input = new ReleaseTransport().open(url, size, cancellation)) {
                return read(input, 131072, cancellation);
            }
        }
        private static byte[] read(InputStream input, int limit, UpdateCoordinator.Cancellation cancellation) throws Exception {
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                cancellation.check();
                if (result.size() + count > limit) throw new IOException("Release metadata is too large.");
                result.write(buffer, 0, count);
            }
            return result.toByteArray();
        }
    }
}
