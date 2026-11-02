package com.jeremykenedy.firetv.ui.nativeapp.updates;

import android.content.ContentProvider;
import com.jeremykenedy.firetv.ui.nativeapp.SourceStartup;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** Grants read-only access to a single verified cache artifact through Android's installer URI grant. */
public final class UpdateFileProvider extends ContentProvider {
    @Override public boolean onCreate() { SourceStartup.start(getContext(), new Runnable() { @Override public void run() {} }); return true; }
    private File resolve(Uri uri) throws FileNotFoundException {
        if (!SourceStartup.ready(getContext())) throw new FileNotFoundException("Launcher settings are being prepared.");
        if (getContext() == null || !"content".equals(uri.getScheme())
                || !(getContext().getPackageName() + ".updates").equals(uri.getAuthority())
                || uri.getPathSegments().size() != 1) throw new FileNotFoundException();
        String name = uri.getLastPathSegment();
        if (name == null || !name.matches("verified-(update|catalog)-[A-Za-z0-9_-]+\\.apk")) throw new FileNotFoundException();
        File file = new File(getContext().getCacheDir(), name);
        if (!file.isFile()) throw new FileNotFoundException();
        return file;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!SourceStartup.ready(getContext())) throw new FileNotFoundException("Launcher settings are being prepared.");
        if (!"r".equals(mode)) throw new FileNotFoundException("Read only");
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public String getType(Uri uri) { return "application/vnd.android.package-archive"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        try {
            File file = resolve(uri);
            String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
            MatrixCursor cursor = new MatrixCursor(columns);
            Object[] row = new Object[columns.length];
            for (int i = 0; i < columns.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i] = "Fire TV UI update.apk";
                else if (OpenableColumns.SIZE.equals(columns[i])) row[i] = file.length();
            }
            cursor.addRow(row); return cursor;
        } catch (FileNotFoundException absent) { return null; }
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
