package com.jeremykenedy.firetv.ui;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import org.json.JSONArray;
import org.json.JSONObject;

/** Shared images come from the signed APK, never from an unverified download. */
public final class BundledIcons {
    private static final ReentrantLock LOCK = new ReentrantLock();
    private static final String TAG = "FireTvIcons";

    private BundledIcons() {}

    public static boolean hasAccess(Context context) {
        return Build.VERSION.SDK_INT >= 30 ? Environment.isExternalStorageManager()
                : context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED;
    }

    static void afterUpdate(BroadcastReceiver receiver, Context context) {
        final BroadcastReceiver.PendingResult pending = receiver.goAsync();
        final Context app = context.getApplicationContext();
        final long deadline = SystemClock.elapsedRealtime() + 6500;
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    Log.i(TAG, install(app, Math.max(0, deadline - SystemClock.elapsedRealtime())).message());
                } catch (Exception unavailable) {
                    Log.w(TAG, "Icon installation incomplete. Retry from Updates.");
                } finally {
                    pending.finish();
                }
            }
        }, "install-ui-icons").start();
    }

    static IconFiles.Result install(final Context context, long timeoutMs) throws Exception {
        final long deadline = SystemClock.elapsedRealtime() + timeoutMs;
        final Thread owner = Thread.currentThread();
        final IconFiles.Cancelled cancelled = new IconFiles.Cancelled() {
            @Override public boolean cancelled() {
                return owner.isInterrupted() || SystemClock.elapsedRealtime() >= deadline;
            }
        };
        if (!LOCK.tryLock(timeoutMs, TimeUnit.MILLISECONDS)) throw new IOException("Icon installation is busy. Retry shortly.");
        try {
            if (!hasAccess(context)) {
                return IconFiles.install(false, null, null, null, null, cancelled);
            }
            final List<IconFiles.Entry> entries = new ArrayList<>();
            byte[] index;
            try (InputStream stream = context.getAssets().open("input-icons/manifest.json")) {
                java.io.ByteArrayOutputStream data = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int size;
                while ((size = stream.read(buffer)) != -1) {
                    if (data.size() + size > 16384) throw new IOException("Invalid icon manifest size.");
                    data.write(buffer, 0, size);
                }
                index = data.toByteArray();
            }
            JSONArray manifest = new JSONArray(new String(index, StandardCharsets.UTF_8));
            if (manifest.length() != 32) throw new IOException("The bundled icon pack is incomplete.");
            for (int i = 0; i < manifest.length(); i++) {
                JSONObject row = manifest.getJSONObject(i);
                entries.add(new IconFiles.Entry(row.getString("file"), row.getInt("bytes"), row.getString("sha256")));
            }
            File destination = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES), "Icons");
            return IconFiles.install(true, destination, entries, new IconFiles.Source() {
                @Override public InputStream open(String name) throws IOException {
                    return context.getAssets().open("input-icons/" + name);
                }
            }, new IconFiles.Indexer() {
                @Override public void index(List<File> files, IconFiles.Cancelled stop) throws IOException {
                    scan(context, files, entries, stop, deadline);
                }
            }, cancelled);
        } finally {
            LOCK.unlock();
        }
    }

    private static void scan(final Context context, List<File> files, List<IconFiles.Entry> entries,
            final IconFiles.Cancelled cancelled, long deadline) throws IOException {
        if (files.isEmpty()) return;
        final java.util.Map<String, File> expected = new java.util.HashMap<>();
        String[] paths = new String[files.size()];
        String[] types = new String[files.size()];
        for (int i = 0; i < files.size(); i++) {
            paths[i] = files.get(i).getAbsolutePath();
            types[i] = "image/png";
            expected.put(paths[i], files.get(i));
        }
        final java.util.Map<String, IconFiles.Entry> identities = new java.util.HashMap<>();
        for (IconFiles.Entry entry : entries) identities.put(entry.name, entry);
        final CountDownLatch remaining = new CountDownLatch(files.size());
        final AtomicBoolean failed = new AtomicBoolean();
        final java.util.Set<String> completed = java.util.Collections.synchronizedSet(new java.util.HashSet<String>());
        MediaScannerConnection.scanFile(context, paths, types,
                new MediaScannerConnection.OnScanCompletedListener() {
                    @Override public void onScanCompleted(String path, Uri uri) {
                        if (!expected.containsKey(path) || !completed.add(path)) return;
                        try {
                            IconFiles.check(cancelled);
                            File file = expected.get(path);
                            if (!IconFiles.matches(file.toPath(), identities.get(file.getName()))) {
                                throw new IOException("An icon changed before indexing.");
                            }
                            verifyIndex(context, file, uri, cancelled);
                        } catch (Exception unavailable) {
                            failed.set(true);
                        } finally {
                            remaining.countDown();
                        }
                    }
                });
        try {
            if (!remaining.await(Math.max(0, deadline - SystemClock.elapsedRealtime()), TimeUnit.MILLISECONDS)) {
                throw new IOException("Icon indexing timed out. Retry from Updates.");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Icon indexing was cancelled. Retry from Updates.");
        }
        IconFiles.check(cancelled);
        if (failed.get()) throw new IOException("Some icon files are not visible to image pickers yet. Retry from Updates.");
    }

    private static void verifyIndex(Context context, File file, Uri uri,
            IconFiles.Cancelled cancelled) throws IOException {
        if (uri == null || !"content".equals(uri.getScheme()) || !"media".equals(uri.getAuthority())) {
            throw new IOException("The TV did not index an icon.");
        }
        String[] columns = Build.VERSION.SDK_INT >= 29
                ? new String[]{"_data", "_display_name", "mime_type", "is_pending"}
                : new String[]{"_data", "_display_name", "mime_type"};
        boolean pending;
        try (Cursor row = context.getContentResolver().query(uri, columns, null, null, null)) {
            if (row == null || !row.moveToFirst() || row.getCount() != 1
                    || !file.getCanonicalPath().equals(new File(row.getString(0)).getCanonicalPath())
                    || !file.getName().equals(row.getString(1)) || !"image/png".equals(row.getString(2))) {
                throw new IOException("The icon index does not match the installed file.");
            }
            pending = Build.VERSION.SDK_INT >= 29 && row.getInt(3) != 0;
        }
        if (pending) {
            IconFiles.check(cancelled);
            ContentValues values = new ContentValues();
            values.put("is_pending", 0);
            if (context.getContentResolver().update(uri, values, null, null) != 1) {
                throw new IOException("The TV did not publish an icon.");
            }
            try (Cursor row = context.getContentResolver().query(uri, new String[]{"is_pending"}, null, null, null)) {
                if (row == null || !row.moveToFirst() || row.getInt(0) != 0) {
                    throw new IOException("An icon is still pending publication.");
                }
            }
        }
    }
}
