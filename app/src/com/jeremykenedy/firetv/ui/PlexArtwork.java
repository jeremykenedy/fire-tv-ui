package com.jeremykenedy.firetv.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.AtomicFile;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class PlexArtwork {
    private PlexArtwork() {}

    static File directory(Context context) {
        return new File(context.getNoBackupFilesDir(), "plex-card-art");
    }

    static void prune(Context context, java.util.Set<String> referenced) {
        File[] files = directory(context).listFiles();
        if (files == null) return;
        for (File file : files) {
            if (!file.getName().matches("[a-f0-9]{64}\\.jpg")) continue;
            String uri =
                    new Uri.Builder()
                            .scheme("content")
                            .authority(context.getPackageName() + ".plex.art")
                            .appendPath(file.getName())
                            .build()
                            .toString();
            if (!referenced.contains(uri)) file.delete();
        }
    }

    static Uri cache(
            Context context,
            PlexApi api,
            PlexConnection connection,
            PlexLibrary.Item item,
            long request)
            throws Exception {
        if (item.art == null) return null;
        byte[] digest =
                MessageDigest.getInstance("SHA-256")
                        .digest(
                                (connection.server + ":" + item.id + ":" + item.art)
                                        .getBytes(StandardCharsets.UTF_8));
        StringBuilder name = new StringBuilder();
        for (byte value : digest)
            name.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        name.append(".jpg");
        File folder = directory(context);
        if (!folder.isDirectory() && !folder.mkdirs())
            throw new java.io.IOException("Plex card artwork could not be cached.");
        File image = new File(folder, name.toString());
        if (!image.isFile()) {
            File[] current = folder.listFiles();
            if (current != null && current.length >= 48) {
                throw new java.io.IOException(
                        "The Plex artwork cache is full. Retry refreshing the library cards.");
            }
            byte[] data = api.artwork(connection, item.art);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, options);
            if (options.outWidth <= 0
                    || options.outHeight <= 0
                    || options.outWidth > 32768
                    || options.outHeight > 32768) return null;
            options.inJustDecodeBounds = false;
            options.inSampleSize = 1;
            while (options.outWidth / options.inSampleSize > 1280
                    || options.outHeight / options.inSampleSize > 1280) options.inSampleSize *= 2;
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
            if (bitmap == null) return null;
            try {
                if (!PlexSession.commit(
                        request,
                        new PlexSession.Change() {
                            @Override
                            public void apply() throws Exception {
                                AtomicFile target = new AtomicFile(image);
                                FileOutputStream output = null;
                                try {
                                    output = target.startWrite();
                                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)) {
                                        throw new java.io.IOException(
                                                "Plex artwork could not be decoded.");
                                    }
                                    target.finishWrite(output);
                                } catch (Exception error) {
                                    if (output != null) target.failWrite(output);
                                    throw error;
                                }
                            }
                        })) return null;
            } finally {
                bitmap.recycle();
            }
        }
        return new Uri.Builder()
                .scheme("content")
                .authority(context.getPackageName() + ".plex.art")
                .appendPath(name.toString())
                .build();
    }
}
