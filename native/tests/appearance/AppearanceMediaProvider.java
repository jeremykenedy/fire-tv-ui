package com.jeremykenedy.firetv.ui.nativeapp.appearance;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Read-only synthetic fixtures; no owner files or arbitrary paths are exposed. */
public final class AppearanceMediaProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) {
        if ("/image.png".equals(uri.getPath())) return "image/png";
        if ("/motion.mp4".equals(uri.getPath())) return "video/mp4";
        return null;
    }
    @Override public AssetFileDescriptor openAssetFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode) || getType(uri) == null) throw new FileNotFoundException("Unknown fixture");
        try { return getContext().getAssets().openFd("appearance-fixtures" + uri.getPath()); }
        catch (IOException failure) { throw new FileNotFoundException("Fixture unavailable"); }
    }
    @Override public Cursor query(Uri uri, String[] columns, String selection, String[] args, String order) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
