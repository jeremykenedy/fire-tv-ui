package com.jeremykenedy.firetv.ui.nativeapp.providers.plex;

import android.content.ContentProvider;
import com.jeremykenedy.firetv.ui.nativeapp.SourceStartup;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.security.MessageDigest;

/** Private token-free artwork references; encrypted credentials never enter card URIs. */
public final class PlexArtworkProvider extends ContentProvider {
    private static File directory(Context context) { return new File(context.getCacheDir(), "source-plex-art"); }
    static Uri cache(Context context, PlexApi api, PlexConnection connection, PlexLibrary.Item item) throws Exception {
        api.checkCanceled();
        if (item.art == null) return null;
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(
                (connection.sourceKey() + "\n" + item.art).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder name = new StringBuilder();
        for (byte b : hash) name.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        File folder = directory(context);
        if (!folder.isDirectory() && !folder.mkdirs()) return null;
        AtomicFile target = new AtomicFile(new File(folder, name + ".img"));
        if (!target.getBaseFile().isFile()) {
            byte[] bytes = api.artwork(connection, item.art);
            api.checkCanceled();
            // Bound the cache before writing. No recursive paths or exported files.
            File[] files = folder.listFiles();
            if (files != null) {
                java.util.Arrays.sort(files, new java.util.Comparator<File>() {
                    @Override public int compare(File a, File b) { return Long.compare(a.lastModified(), b.lastModified()); }
                });
                long size = bytes.length;
                for (File file : files) size += file.length();
                for (int i = 0; i < files.length && (files.length - i >= 192 || size > 32 * 1024 * 1024); i++) {
                    long length = files[i].length();
                    if (files[i].delete()) size -= length;
                }
                if (size > 32 * 1024 * 1024) return null;
            }
            FileOutputStream output = null;
            try { output = target.startWrite(); output.write(bytes); target.finishWrite(output); }
            catch (Exception failed) { if (output != null) target.failWrite(output); throw failed; }
        }
        return Uri.parse("content://" + context.getPackageName() + ".plex-art/" + name + ".img");
    }
    @Override public boolean onCreate() { SourceStartup.start(getContext(), new Runnable() { @Override public void run() {} }); return true; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!SourceStartup.ready(getContext())) throw new FileNotFoundException("Launcher settings are being prepared.");
        if (!"r".equals(mode) || uri.getQuery() != null || uri.getFragment() != null
                || uri.getPath() == null || !uri.getPath().matches("/[0-9a-f]{64}\\.img"))
            throw new FileNotFoundException("Plex artwork unavailable.");
        return ParcelFileDescriptor.open(new File(directory(getContext()), uri.getPath().substring(1)),
                ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public String getType(Uri uri) { return "image/*"; }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String sort) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
