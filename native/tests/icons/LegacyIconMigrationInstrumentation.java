package com.jeremykenedy.firetv.ui.nativeapp.icons;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.system.Os;
import com.jeremykenedy.firetv.ui.nativeapp.migration.AndroidLegacyIconMigration;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** Real Android decoding and managed-icon rendering in a fresh disposable preview only. */
public final class LegacyIconMigrationInstrumentation extends Instrumentation {
    private static final long LIMIT = 20L * 1024 * 1024;
    private String step = "fixture";
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        Context context = getTargetContext();
        try {
            check("com.jeremykenedy.firetv.ui.preview".equals(context.getPackageName()), "Disposable preview only.");
            File small = new File(context.getCacheDir(), "legacy-small.png");
            png(small, 160, 80, false);
            String smallUri = Uri.fromFile(small).toString();
            step = "read-only preflight without prepared copy";
            Map<String, String> before = snapshot(context);
            reject(context, smallUri, false);
            check(before.equals(snapshot(context)), "verifyExisting must create no directory, temp file or copy.");
            step = "unchanged small-image identity and bytes";
            String smallChoice = prepare(context, smallUri);
            check(expected(smallUri, small).equals(smallChoice), "Original URI+zero+raw-byte digest filename remains unchanged.");
            File smallCopy = copy(context, smallChoice);
            check(hash(small).equals(hash(smallCopy)), "Small copy preserves original bytes.");
            before = snapshot(context);
            check(smallChoice.equals(prepare(context, smallUri)), "Existing choice is reused exactly.");
            check(before.equals(snapshot(context)), "Existing copies are not rewritten.");
            step = "existing forty megapixel bound";
            File tooManyPixels = new File(context.getCacheDir(), "legacy-pixel-limit.png");
            streamCopy(small, tooManyPixels);
            try (RandomAccessFile file = new RandomAccessFile(tooManyPixels, "rw")) {
                file.seek(16); file.writeInt(7000); file.writeInt(6000);
                byte[] header = new byte[17]; file.seek(12); file.readFully(header);
                java.util.zip.CRC32 crc = new java.util.zip.CRC32(); crc.update(header);
                file.seek(29); file.writeInt((int) crc.getValue());
            }
            android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(tooManyPixels.getAbsolutePath(), bounds);
            check((long) bounds.outWidth * bounds.outHeight > 40000000, "Fixture exposes oversized image bounds without allocation.");
            reject(context, Uri.fromFile(tooManyPixels).toString(), true);
            step = "actual PNG larger than eight MiB";
            File large = new File(context.getCacheDir(), "legacy-large.png");
            png(large, 1800, 1800, true);
            check(large.length() > 8L * 1024 * 1024 && large.length() < LIMIT, "Fixture is a valid encoded PNG above old limit.");
            String largeUri = Uri.fromFile(large).toString();
            String sourceHash = hash(large);
            String largeChoice = prepare(context, largeUri);
            check(expected(largeUri, large).equals(largeChoice) && sourceHash.equals(hash(copy(context, largeChoice)))
                    && sourceHash.equals(hash(large)), "Large source and copy retain exact bytes and deterministic identity.");
            before = snapshot(context);
            check(largeChoice.equals(verifyReadOnly(context, largeUri, true).get(largeUri)),
                    "Read-only verification accepts prepared large image.");
            check(before.equals(snapshot(context)), "Successful verification changes no bytes, paths or modification times.");
            step = "actual managed icon render";
            String key = "fixture.icon/.Main";
            IconSelectionStore.save(context, key, largeChoice);
            Drawable resolved = IconSelectionStore.resolve(context, key, new ColorDrawable(Color.RED), null);
            check(resolved instanceof BitmapDrawable, "Restored large image resolves instead of factory fallback.");
            Bitmap decoded = ((BitmapDrawable) resolved).getBitmap();
            check(decoded.getWidth() > 0 && decoded.getWidth() <= 512 && decoded.getHeight() <= 512,
                    "Actual decode keeps existing sample bound.");
            Bitmap rendered = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888);
            resolved.setBounds(0, 0, 320, 180); resolved.draw(new Canvas(rendered));
            check(rendered.getPixel(100, 100) != Color.RED, "Actual Android drawable draws restored artwork.");
            try (FileOutputStream output = new FileOutputStream(new File(context.getExternalFilesDir(null), "large-icon-render.png"))) {
                check(rendered.compress(Bitmap.CompressFormat.PNG, 100, output), "Render capture succeeds.");
            } finally { rendered.recycle(); }
            try (InputStream input = new FileInputStream(large)) {
                try { IconSelectionStore.prepareImage(context, input); throw new AssertionError("New import limit changed."); }
                catch (IOException expected) {}
            }
            step = "exact twenty MiB boundary";
            File boundary = new File(context.getCacheDir(), "legacy-boundary.png");
            streamCopy(large, boundary);
            try (RandomAccessFile file = new RandomAccessFile(boundary, "rw")) { file.setLength(LIMIT); }
            String boundaryUri = Uri.fromFile(boundary).toString();
            String boundaryChoice = prepare(context, boundaryUri);
            check(copy(context, boundaryChoice).length() == LIMIT && hash(boundary).equals(hash(copy(context, boundaryChoice))),
                    "Exact original twenty MiB limit is accepted without byte changes.");
            IconSelectionStore.save(context, key, boundaryChoice);
            check(IconSelectionStore.resolve(context, key, new ColorDrawable(Color.RED), null) instanceof BitmapDrawable,
                    "Twenty MiB restored managed image actually resolves.");
            step = "oversize refusal";
            File oversized = new File(context.getCacheDir(), "legacy-oversized.png");
            streamCopy(boundary, oversized);
            try (RandomAccessFile file = new RandomAccessFile(oversized, "rw")) { file.setLength(LIMIT + 1); }
            before = snapshot(context);
            reject(context, Uri.fromFile(oversized).toString(), true);
            reject(context, Uri.fromFile(oversized).toString(), false);
            check(before.equals(snapshot(context)), "Oversize refusal creates no copy or staging writes.");
            step = "tampered existing managed copy";
            File target = copy(context, boundaryChoice);
            try (RandomAccessFile file = new RandomAccessFile(target, "rw")) { file.seek(100); int value = file.read(); file.seek(100); file.write(value ^ 0xff); }
            before = snapshot(context);
            reject(context, boundaryUri, false); reject(context, boundaryUri, true);
            check(before.equals(snapshot(context)), "Tampered same-length copy is rejected without repair or replacement.");
            streamCopy(boundary, target);
            step = "canonical file and directory aliases";
            File real = new File(target.getParentFile(), target.getName() + ".real");
            check(target.renameTo(real), "Move owned target.");
            Os.symlink(real.getAbsolutePath(), target.getAbsolutePath());
            before = snapshot(context);
            reject(context, boundaryUri, false); reject(context, boundaryUri, true);
            check(before.equals(snapshot(context)), "Managed file alias is refused without writes.");
            check(target.delete() && real.renameTo(target), "Restore owned target.");
            File directory = target.getParentFile(), realDirectory = new File(directory.getParentFile(), "icons-real");
            check(directory.renameTo(realDirectory), "Move owned icon directory.");
            Os.symlink(realDirectory.getAbsolutePath(), directory.getAbsolutePath());
            before = snapshot(context);
            reject(context, boundaryUri, false); reject(context, boundaryUri, true);
            check(before.equals(snapshot(context)), "Managed directory alias is refused without writes.");
            check(directory.delete() && realDirectory.renameTo(directory), "Restore owned icon directory.");
            before = snapshot(context);
            check(boundaryChoice.equals(verifyReadOnly(context, boundaryUri, true).get(boundaryUri)),
                    "Canonical restored copy verifies again.");
            check(before.equals(snapshot(context)), "Final verification is read-only.");
            result.putString("status", "PASS");
            result.putLong("large_png_bytes", large.length()); result.putLong("boundary_bytes", LIMIT);
            result.putString("large_choice", largeChoice); result.putString("small_choice", smallChoice);
            result.putString("decoded_size", decoded.getWidth() + "x" + decoded.getHeight());
            result.putString("cases", "Small filename compatibility; >8MiB real PNG;20MiB boundary;oversize refusal;byte preservation;actual BitmapDrawable render;new import still8MiB;tamper;file/directory aliases;read-only verifyExisting");
        } catch (Throwable failure) {
            result.putString("status", "FAIL"); result.putString("step", step); result.putString("reason", failure.toString());
        }
        finish("PASS".equals(result.getString("status")) ? -1 : 0, result);
    }
    private static String prepare(Context context, String reference) throws IOException {
        return AndroidLegacyIconMigration.prepare(context, Arrays.asList(reference, reference)).get(reference);
    }
    private static File copy(Context context, String choice) { return new File(new File(context.getFilesDir(), "icons"), choice.substring(6)); }
    private static void reject(Context context, String reference, boolean create) throws IOException {
        try {
            if (create) AndroidLegacyIconMigration.prepare(context, Collections.singletonList(reference));
            else { verifyReadOnly(context, reference, false); return; }
            throw new AssertionError("Invalid or unavailable image accepted.");
        } catch (IOException expected) {}
    }
    private static Map<String, String> verifyReadOnly(Context context, String reference, boolean success) throws IOException {
        final java.util.concurrent.atomic.AtomicInteger writes = new java.util.concurrent.atomic.AtomicInteger();
        List<android.os.FileObserver> observers = new ArrayList<>();
        for (File directory : new File[]{context.getFilesDir(), new File(context.getFilesDir(), "icons"), context.getCacheDir()}) {
            if (!directory.isDirectory()) continue;
            android.os.FileObserver observer = new android.os.FileObserver(directory.getAbsolutePath(),
                    android.os.FileObserver.CREATE | android.os.FileObserver.DELETE | android.os.FileObserver.MOVED_TO
                            | android.os.FileObserver.MODIFY | android.os.FileObserver.CLOSE_WRITE) {
                @Override public void onEvent(int event, String path) { writes.incrementAndGet(); }
            };
            observer.startWatching(); observers.add(observer);
        }
        try {
            Map<String, String> result = null;
            try {
                result = AndroidLegacyIconMigration.verifyExisting(context, Collections.singletonList(reference));
                check(success, "Read-only verification accepted invalid image.");
            } catch (IOException expected) { check(!success, "Read-only verification rejected valid image."); }
            android.os.SystemClock.sleep(100);
            check(writes.get() == 0, "verifyExisting must perform no transient staging, creation, modification or deletion.");
            return result;
        } finally { for (android.os.FileObserver observer : observers) observer.stopWatching(); }
    }
    private static void png(File file, int width, int height, boolean noise) throws IOException {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        if (noise) {
            Random random = new Random(42); int[] row = new int[width];
            for (int y = 0; y < height; y++) { for (int x = 0; x < width; x++) row[x] = 0xff000000 | random.nextInt(0x1000000); bitmap.setPixels(row, 0, width, 0, y, width, 1); }
        } else bitmap.eraseColor(Color.GREEN);
        try (FileOutputStream output = new FileOutputStream(file)) { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output), "Encode fixture."); }
        finally { bitmap.recycle(); }
    }
    private static void streamCopy(File source, File target) throws IOException {
        try (InputStream input = new FileInputStream(source); FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
    }
    private static String hash(File file) throws Exception { return digest(null, file); }
    private static String digest(String uri, File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        if (uri != null) { digest.update(uri.getBytes("UTF-8")); digest.update((byte) 0); }
        try (InputStream input = new FileInputStream(file)) { byte[] buffer = new byte[8192]; int n; while ((n = input.read(buffer)) != -1) digest.update(buffer, 0, n); }
        StringBuilder result = new StringBuilder(); for (byte value : digest.digest()) result.append(String.format(Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }
    private static String expected(String uri, File file) throws Exception {
        String hash = digest(uri, file).substring(0, 32);
        return "image:" + hash.substring(0, 8) + "-" + hash.substring(8, 12) + "-" + hash.substring(12, 16) + "-" + hash.substring(16, 20) + "-" + hash.substring(20) + ".png";
    }
    private static Map<String, String> snapshot(Context context) throws Exception {
        Map<String, String> result = new TreeMap<>();
        snapshot(context.getFilesDir(), result); snapshot(context.getCacheDir(), result); return result;
    }
    private static void snapshot(File file, Map<String, String> result) throws Exception {
        if (!file.exists()) return;
        if (file.isDirectory()) { result.put(file.getAbsolutePath(), "directory"); for (File child : file.listFiles()) snapshot(child, result); }
        else result.put(file.getAbsolutePath(), file.length() + ":" + file.lastModified() + ":" + hash(file));
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
