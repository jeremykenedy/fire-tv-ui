package com.jeremykenedy.firetv.ui;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** Only APKs and hashes bundled with this app are offered for installation. */
public final class ScreensaverCatalog {
    public static final class Entry {
        public final String name;
        public final String pkg;
        public final String url;
        public final String sha256;
        public final String tag;
        public final String dream;
        public final String preview;
        public final long bytes;

        Entry(JSONObject value) throws Exception {
            name = value.getString("name");
            pkg = value.getString("package");
            url = value.getString("url");
            sha256 = value.getString("sha256");
            tag = value.getString("tag");
            dream = value.getString("dream");
            preview = value.optString("preview", "");
            bytes = value.getLong("bytes");
            String repo = value.getString("repo");
            URL source = new URL(url);
            if (!pkg.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")
                    || !sha256.matches("[a-f0-9]{64}") || bytes <= 0 || bytes > 250L * 1048576
                    || !repo.matches("jeremykenedy/[A-Za-z0-9_.-]+")
                    || !"https".equals(source.getProtocol()) || !"github.com".equals(source.getHost())
                    || source.getUserInfo() != null || source.getPort() != -1
                    || !source.getPath().startsWith("/" + repo + "/releases/download/")) {
                throw new IllegalArgumentException("The screensaver catalog contains invalid release metadata.");
            }
        }
    }

    private ScreensaverCatalog() {
    }

    public static List<Entry> load(Context context) throws Exception {
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        try (InputStream input = context.getAssets().open("screensavers.json")) {
            byte[] bytes = new byte[4096];
            int read;
            while ((read = input.read(bytes)) != -1) {
                content.write(bytes, 0, read);
            }
        }
        JSONArray json = new JSONArray(content.toString("UTF-8"));
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < json.length(); i++) {
            entries.add(new Entry(json.getJSONObject(i)));
        }
        return entries;
    }
}
