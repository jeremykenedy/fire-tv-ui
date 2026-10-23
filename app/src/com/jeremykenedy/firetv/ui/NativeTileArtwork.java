package com.jeremykenedy.firetv.ui;

import android.content.Context;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Bundled artwork enters the existing image loader without replacing custom icons. */
public final class NativeTileArtwork {
    private NativeTileArtwork() {
    }

    public static String uri(Context context, String pkg, String customUri) {
        if (customUri != null && !customUri.trim().isEmpty()) return customUri;
        String name;
        if ("com.amazon.tv.livetv".equals(pkg)) {
            name = "live-tv.4d2b0f25ad0a.png";
        } else if ("com.amazon.tv.launcher".equals(pkg)) {
            name = "factory-ui.4ae64dfa204a.png";
        } else {
            return customUri;
        }
        try {
            return Uri.fromFile(extract(context, name)).toString();
        } catch (IOException unavailable) {
            return customUri;
        }
    }

    private static synchronized File extract(Context context, String name) throws IOException {
        File directory = new File(context.getFilesDir(), "native-tiles");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("The native tile folder could not be created.");
        }
        File target = new File(directory, name);
        if (target.isFile()) return target;
        File temporary = new File(directory, name + ".tmp");
        try {
            try (InputStream input = context.getAssets().open("native-tiles/" + name);
                    FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            }
            if (!temporary.renameTo(target)) {
                throw new IOException("The native tile image could not be saved.");
            }
            return target;
        } finally {
            temporary.delete();
        }
    }
}
