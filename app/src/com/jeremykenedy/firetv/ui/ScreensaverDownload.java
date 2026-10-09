package com.jeremykenedy.firetv.ui;

import android.content.Context;
import android.content.pm.PackageInfo;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

final class ScreensaverDownload {
    interface Progress {
        void update(int percent);
    }

    private ScreensaverDownload() {
    }

    static File download(Context context, ScreensaverCatalog.Entry entry, Progress progress) throws Exception {
        File file = File.createTempFile("screensaver-", ".apk", context.getCacheDir());
        HttpURLConnection connection = null;
        boolean verified = false;
        try {
            URL location = new URL(entry.url);
            for (int redirects = 0; redirects <= 5; redirects++) {
                String host = location.getHost().toLowerCase(Locale.ROOT);
                if (!"https".equals(location.getProtocol()) || location.getUserInfo() != null
                        || location.getPort() != -1 || !("github.com".equals(host)
                        || "release-assets.githubusercontent.com".equals(host)
                        || "objects.githubusercontent.com".equals(host))) {
                    throw new IllegalStateException("The download redirected outside GitHub's release servers.");
                }
                connection = (HttpURLConnection) location.openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setRequestProperty("User-Agent", "Fire-TV-UI");
                int code = connection.getResponseCode();
                if (code == 200) {
                    break;
                }
                if (code != 301 && code != 302 && code != 303 && code != 307 && code != 308) {
                    throw new IllegalStateException("GitHub returned HTTP " + code + ".");
                }
                String next = connection.getHeaderField("Location");
                if (next == null || redirects == 5) {
                    throw new IllegalStateException("The download has too many or invalid redirects.");
                }
                location = new URL(location, next);
                connection.disconnect();
            }
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            long total = 0;
            int percent = -1;
            try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(file)) {
                byte[] bytes = new byte[65536];
                int count;
                while ((count = input.read(bytes)) != -1) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException("Download cancelled.");
                    }
                    total += count;
                    if (total > entry.bytes) {
                        throw new IllegalStateException("The APK exceeds the published release size.");
                    }
                    hash.update(bytes, 0, count);
                    output.write(bytes, 0, count);
                    int next = (int) (total * 100 / entry.bytes);
                    if (next != percent) {
                        percent = next;
                        progress.update(percent);
                    }
                }
                output.getFD().sync();
            }
            StringBuilder actual = new StringBuilder();
            for (byte value : hash.digest()) {
                actual.append(String.format(Locale.ROOT, "%02x", value & 255));
            }
            if (total != entry.bytes || !entry.sha256.equals(actual.toString())) {
                throw new IllegalStateException("The APK does not match its release checksum. It was not installed.");
            }
            PackageInfo pkg = context.getPackageManager().getPackageArchiveInfo(file.getPath(), 0);
            if (pkg == null || !entry.pkg.equals(pkg.packageName)) {
                throw new IllegalStateException("The APK contains a different package. It was not installed.");
            }
            verified = true;
            return file;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            if (!verified) {
                file.delete();
            }
        }
    }
}
