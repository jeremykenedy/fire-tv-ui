package com.jeremykenedy.firetv.ui;

import android.content.Context;
import android.os.Environment;
import android.util.AtomicFile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import org.json.JSONObject;

/** Authenticated computer handoff for private UI releases, without a TV token. */
final class StagedUiUpdate {
    static final String APK_NAME = "fire-tv-ui-update.apk";
    static final String METADATA_NAME = "fire-tv-ui-update.json";

    static final class Release {
        final ScreensaverCatalog.Entry entry;
        final long checkedAt;

        Release(JSONObject metadata, String pkg) throws Exception {
            entry = new ScreensaverCatalog.Entry(metadata);
            checkedAt = metadata.getLong("checked_at");
            if (!pkg.equals(entry.pkg) || !"jeremykenedy/fire-tv-ui".equals(entry.repo)
                    || !entry.tag.matches("v?[0-9]+\\.[0-9]+\\.[0-9]+")
                    || entry.bytes > 64L * 1048576
                    || !new java.net.URL(entry.url).getPath().startsWith(
                            "/jeremykenedy/fire-tv-ui/releases/download/" + entry.tag + "/")
                    || !UpdateVersions.cacheFresh(checkedAt, System.currentTimeMillis())) {
                throw new IllegalArgumentException("The staged UI release is not a recent stable release for this app.");
            }
        }
    }

    private StagedUiUpdate() {
    }

    static void stage(Context context) throws Exception {
        if (!BackupStore.hasAccess()) {
            throw new IllegalStateException("Existing TV backup/storage access is required to stage an update.");
        }
        Release release;
        try (InputStream input = new FileInputStream(incoming(METADATA_NAME))) {
            release = new Release(read(input), context.getPackageName());
        }
        File apk = incoming(APK_NAME);
        if (!apk.isFile() || apk.length() != release.entry.bytes) {
            throw new IllegalStateException("The staged APK does not match its published byte count.");
        }
        File verified = ScreensaverDownload.localUpdate(context, release.entry, apk,
                new ScreensaverDownload.Progress() {
                    @Override
                    public void update(int percent) {
                    }
                });
        verified.delete();
        JSONObject metadata = release.entry.json().put("checked_at", release.checkedAt);
        AtomicFile target = new AtomicFile(cached(context));
        FileOutputStream output = null;
        try {
            output = target.startWrite();
            output.write(metadata.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            target.finishWrite(output);
        } catch (Exception failed) {
            if (output != null) {
                target.failWrite(output);
            }
            throw failed;
        }
    }

    static Release load(Context context) throws Exception {
        if (!cached(context).isFile()) {
            return null;
        }
        try (InputStream input = new AtomicFile(cached(context)).openRead()) {
            JSONObject metadata = read(input);
            if (!UpdateVersions.cacheFresh(metadata.getLong("checked_at"), System.currentTimeMillis())) {
                return null;
            }
            return new Release(metadata, context.getPackageName());
        }
    }

    static File apk() throws Exception {
        return incoming(APK_NAME);
    }

    private static File cached(Context context) {
        return new File(context.getCacheDir(), "staged-ui-update.json");
    }

    private static File incoming(String name) throws Exception {
        File folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getCanonicalFile();
        File file = new File(folder, name).getCanonicalFile();
        if (!folder.equals(file.getParentFile())) {
            throw new IllegalArgumentException("The staged update is outside TV Downloads.");
        }
        return file;
    }

    private static JSONObject read(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] bytes = new byte[4096];
        int count;
        while ((count = input.read(bytes)) != -1) {
            if (output.size() + count > 16384) {
                throw new IllegalArgumentException("The staged metadata exceeds its allowed size.");
            }
            output.write(bytes, 0, count);
        }
        return new JSONObject(output.toString("UTF-8"));
    }
}
