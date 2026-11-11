package com.jeremykenedy.firetv.ui.nativeapp.backup;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import java.io.*;

/** Test APK only. Fixed sanitized files, never a general filesystem bridge. */
public final class BackupFixtureProvider extends ContentProvider {
    public static final String AUTHORITY = "com.jeremykenedy.firetv.ui.backupproof.documents";
    public static final Uri DOCUMENT = Uri.parse("content://" + AUTHORITY + "/document/roundtrip");
    private void caller() {
        String[] packages = getContext().getPackageManager().getPackagesForUid(Binder.getCallingUid());
        if (packages != null) for (String name : packages)
            if (name.equals("com.jeremykenedy.firetv.ui.preview") || name.equals("com.jeremykenedy.firetv.ui.backupproof")) return;
        throw new SecurityException("Only the isolated preview test can use this fixture.");
    }
    private File document() { return new File(getContext().getCacheDir(), "roundtrip.txt"); }
    @Override public boolean onCreate() { return true; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        caller();
        if (!DOCUMENT.equals(uri)) throw new FileNotFoundException("Unknown fixture document.");
        return ParcelFileDescriptor.open(document(),ParcelFileDescriptor.parseMode(mode));
    }
    @Override public Bundle call(String method,String arg,Bundle extras) {
        caller(); Bundle result=new Bundle();
        try {
            if ("reset".equals(method)) { document().delete(); }
            else if ("malformed".equals(method)) { try(OutputStream output=new FileOutputStream(document())) { output.write("{broken".getBytes("UTF-8")); } }
            else if ("original".equals(method) || "original-unsupported".equals(method)) {
                String value = "original".equals(method) ? "{\"apps_per_row\":4,\"use_24h_clock\":true,\"icon_radius\":13}"
                        : "{\"apps_per_row\":6,\"fire_tv_ui_sleep\":60}";
                try(OutputStream output=new FileOutputStream(document())) { output.write(value.getBytes("UTF-8")); }
            }
            else if ("read".equals(method)) {
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                try(InputStream input=new FileInputStream(document())) { byte[] buffer=new byte[8192];int count;while((count=input.read(buffer))!=-1) { if(bytes.size()+count>PortableBackup.MAX_BYTES)throw new IOException();bytes.write(buffer,0,count); } }
                result.putByteArray("bytes",bytes.toByteArray());
            } else throw new IllegalArgumentException("Unknown fixture operation.");
        } catch(IOException failure) { throw new IllegalStateException("Fixture document could not be accessed.",failure); }
        return result;
    }
    @Override public String getType(Uri uri) { return "text/plain"; }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order) { throw new UnsupportedOperationException(); }
    @Override public Uri insert(Uri uri,ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri,String selection,String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args) { throw new UnsupportedOperationException(); }
}
