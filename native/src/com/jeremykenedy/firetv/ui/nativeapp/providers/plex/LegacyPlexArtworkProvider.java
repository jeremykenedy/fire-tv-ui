package com.jeremykenedy.firetv.ui.nativeapp.providers.plex;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import com.jeremykenedy.firetv.ui.nativeapp.SourceStartup;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Read-only access to the maintained launcher's retained, private Plex artwork cache. */
public final class LegacyPlexArtworkProvider extends ContentProvider {
    @Override public boolean onCreate() { SourceStartup.start(getContext(), new Runnable() { @Override public void run() {} }); return true; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!SourceStartup.ready(getContext())) throw new FileNotFoundException("Launcher settings are being prepared.");
        String name = uri == null ? null : LegacyPlexArtworkPath.name(uri.toString(), getContext().getPackageName());
        if (!"r".equals(mode) || name == null) throw new FileNotFoundException("Plex artwork is unavailable.");
        try {
            File directory = new File(getContext().getNoBackupFilesDir(), "plex-card-art").getCanonicalFile();
            File image = new File(directory, name).getCanonicalFile();
            if (!directory.equals(image.getParentFile())) throw new FileNotFoundException("Plex artwork is unavailable.");
            return ParcelFileDescriptor.open(image, ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (IOException unavailable) { throw new FileNotFoundException("Plex artwork is unavailable."); }
    }
    @Override public String getType(Uri uri) { return "image/jpeg"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
