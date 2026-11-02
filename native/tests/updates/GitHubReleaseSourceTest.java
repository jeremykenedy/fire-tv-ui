package com.jeremykenedy.firetv.ui.nativeapp.updates;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

public final class GitHubReleaseSourceTest {
    private static final String PKG = "com.jeremykenedy.firetv.ui.preview";
    private static final String HASH = "a".repeat(64);
    private static final String APK = "fire-tv-ui-source-preview-2.0.0-alpha.1.apk";
    private static final class Feed implements GitHubReleaseSource.Reader {
        JSONArray releases = new JSONArray();
        JSONObject manifest = new JSONObject();
        boolean fail;
        @Override public byte[] releases(UpdateCoordinator.Cancellation cancellation) throws Exception {
            if (fail) throw new IOException("Unavailable");
            return releases.toString().getBytes(StandardCharsets.UTF_8);
        }
        @Override public byte[] manifest(String url, long size, UpdateCoordinator.Cancellation cancellation) {
            return manifest.toString().getBytes(StandardCharsets.UTF_8);
        }
        void refresh() {
            releases.getJSONObject(0).getJSONArray("assets").getJSONObject(0).put("size", manifest.toString().getBytes(StandardCharsets.UTF_8).length);
        }
    }
    private interface Action { void run() throws Exception; }
    private static void require(boolean ok) { if (!ok) throw new AssertionError(); }
    private static void failure(Action action) throws Exception {
        try { action.run(); throw new AssertionError("Expected rejection"); } catch (IOException | IllegalArgumentException expected) { }
    }
    private static Feed fixture() {
        Feed f = new Feed();
        f.manifest = new JSONObject().put("applicationId", PKG).put("version", "2.0.0-alpha.1").put("versionCode", 3)
                .put("apk", APK).put("bytes", 1234).put("sha256", HASH).put("sourceTreeDirty", false)
                .put("sourceCommit", "b".repeat(40)).put("apkManifestVerified", true).put("debuggable", false)
                .put("binaryLauncherInputs", new JSONArray()).put("sourceInputs", new JSONObject().put("src/Main.java", HASH))
                .put("signerSha256", HASH).put("expectedSignerSha256", HASH).put("signing", "maintained-preview");
        JSONArray assets = new JSONArray().put(new JSONObject().put("name", "release-artifact.json").put("state", "uploaded").put("size", 1))
                .put(new JSONObject().put("name", APK).put("state", "uploaded").put("size", 1234).put("digest", "sha256:" + HASH));
        f.releases.put(new JSONObject().put("tag_name", "v2.0.0-alpha.1").put("draft", false).put("prerelease", true).put("body", "Alpha release").put("assets", assets));
        f.refresh(); return f;
    }
    private static UpdateCoordinator.Release check(Feed feed, long version) throws Exception {
        return new GitHubReleaseSource(PKG, version, feed).check(new UpdateCoordinator.Cancellation());
    }
    public static void main(String[] args) throws Exception {
        Feed f = fixture();
        require(check(f, 2).version == 3);
        require(check(f, 3) == null);
        f.releases.getJSONObject(0).put("prerelease", false);
        require(check(f, 1) == null);
        f = fixture(); f.releases.getJSONObject(0).getJSONArray("assets").remove(0);
        require(check(f, 1) == null);
        f = fixture(); f.manifest.put("applicationId", "com.jeremykenedy.firetv.ui"); f.refresh();
        final Feed wrongPackage = f; failure(() -> check(wrongPackage, 1));
        f = fixture(); f.manifest.put("sha256", "c".repeat(64)); f.refresh();
        final Feed tampered = f; failure(() -> check(tampered, 1));
        f = fixture(); f.manifest.put("sourceTreeDirty", true); f.refresh();
        final Feed dirty = f; failure(() -> check(dirty, 1));
        f = fixture(); f.fail = true; final Feed offline = f; failure(() -> check(offline, 1));
        f = fixture(); f.releases.getJSONObject(0).getJSONArray("assets").getJSONObject(0).put("digest", "sha256:" + HASH);
        final Feed badManifestDigest = f; failure(() -> check(badManifestDigest, 1));
        f = fixture();
        f.manifest.put("applicationId", "com.jeremykenedy.firetv.ui").put("version", "2.0.0")
                .put("apk", "fire-tv-ui-2.0.0.apk").put("signing", "production");
        f.releases.getJSONObject(0).put("tag_name", "v2.0.0").put("prerelease", false)
                .getJSONArray("assets").getJSONObject(1).put("name", "fire-tv-ui-2.0.0.apk");
        f.refresh();
        require(new GitHubReleaseSource("com.jeremykenedy.firetv.ui", 2, f).check(new UpdateCoordinator.Cancellation()).version == 3);
        require(check(f, 2) == null);
        f.manifest.put("signing", "unsigned"); f.refresh();
        final Feed unsigned = f;
        failure(() -> new GitHubReleaseSource("com.jeremykenedy.firetv.ui", 2, unsigned).check(new UpdateCoordinator.Cancellation()));
        System.out.println("GitHub source discovery fixtures passed");
    }
}
