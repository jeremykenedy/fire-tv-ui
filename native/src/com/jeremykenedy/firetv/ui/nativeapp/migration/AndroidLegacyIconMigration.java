package com.jeremykenedy.firetv.ui.nativeapp.migration;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Prepares exact legacy image references without modifying their sources or preferences. */
public final class AndroidLegacyIconMigration {
    private static final int MAX_BYTES = 20 * 1024 * 1024;
    private static final Object LOCK = new Object();

    private AndroidLegacyIconMigration() {}

    /**
     * Call before committing preferences and keep returned values in the transaction plan.
     * Prepared files are intentionally retained across rollback and process death. Repeating
     * preparation with unchanged source bytes returns the same value, including after recovery.
     */
    public static Map<String, String> prepare(Context context, Iterable<String> references) throws IOException {
        return prepare(context, references, true);
    }

    public static Map<String, String> verifyExisting(Context context, Iterable<String> references) throws IOException {
        return prepare(context, references, false);
    }

    private static Map<String, String> prepare(Context context, Iterable<String> references, boolean create) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        for (String reference : references) {
            if (result.containsKey(reference)) continue;
            if (result.size() >= 2048 || reference == null || reference.length() > 4096) throw invalid();
            Uri uri = Uri.parse(reference);
            if (!"content".equals(uri.getScheme()) && !"file".equals(uri.getScheme())) throw invalid();
            Fingerprint fingerprint;
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                if (input == null) throw invalid();
                fingerprint = fingerprint(reference, input, null);
            } catch (SecurityException | IllegalArgumentException unavailable) {
                throw new IOException("A selected legacy icon is unavailable. Existing settings were kept.", unavailable);
            }
            String choice = choice(fingerprint.hash);
            synchronized (LOCK) {
                if (create) install(context, reference, choice, fingerprint);
                else {
                    File target = new File(new File(context.getFilesDir().getCanonicalFile(), "icons"), choice.substring(6));
                    verify(target, reference, fingerprint);
                    validate(target);
                }
            }
            result.put(reference, choice);
        }
        return result;
    }

    private static final class Fingerprint {
        final byte[] hash;
        final long length;
        Fingerprint(byte[] hash, long length) { this.hash = hash; this.length = length; }
    }

    private static Fingerprint fingerprint(String reference, InputStream input, FileOutputStream output) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(reference.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            byte[] buffer = new byte[8192];
            long length = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted() || count > MAX_BYTES - length) throw invalid();
                digest.update(buffer, 0, count);
                if (output != null) output.write(buffer, 0, count);
                length += count;
            }
            return new Fingerprint(digest.digest(), length);
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IOException("Image verification is unavailable.", unavailable);
        }
    }

    private static void validate(File file) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (options.outWidth < 1 || options.outHeight < 1
                || (long) options.outWidth * options.outHeight > 40000000) throw invalid();
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 512) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (bitmap == null) throw invalid();
        bitmap.recycle();
    }

    private static String choice(byte[] hash) {
        StringBuilder value = new StringBuilder("image:");
        for (int i = 0; i < 16; i++) {
            if (i == 4 || i == 6 || i == 8 || i == 10) value.append('-');
            value.append(Character.forDigit((hash[i] & 255) >>> 4, 16));
            value.append(Character.forDigit(hash[i] & 15, 16));
        }
        return value.append(".png").toString();
    }

    private static void install(Context context, String reference, String choice, Fingerprint expected) throws IOException {
        File directory = new File(context.getFilesDir().getCanonicalFile(), "icons");
        if (!directory.isDirectory() && !directory.mkdirs()) throw invalid();
        if (!directory.getCanonicalFile().equals(directory.getAbsoluteFile())) throw invalid();
        File target = new File(directory, choice.substring(6));
        if (target.exists()) {
            verify(target, reference, expected);
            validate(target);
            return;
        }
        File temporary = File.createTempFile("migration-", ".tmp", directory);
        try {
            try (InputStream input = context.getContentResolver().openInputStream(Uri.parse(reference));
                    FileOutputStream output = new FileOutputStream(temporary)) {
                if (input == null || !same(expected, fingerprint(reference, input, output))) throw invalid();
                output.getFD().sync();
            } catch (SecurityException | IllegalArgumentException unavailable) {
                throw new IOException("A selected legacy icon is unavailable. Existing settings were kept.", unavailable);
            }
            verify(temporary, reference, expected);
            validate(temporary);
            if (target.exists()) verify(target, reference, expected);
            else if (!temporary.renameTo(target)) throw invalid();
            verify(target, reference, expected);
        } finally {
            temporary.delete();
        }
    }

    private static void verify(File file, String reference, Fingerprint expected) throws IOException {
        if (!file.isFile() || !file.getCanonicalFile().equals(file.getAbsoluteFile()) || file.length() != expected.length) throw invalid();
        try (InputStream input = new FileInputStream(file)) {
            if (!same(expected, fingerprint(reference, input, null))) throw invalid();
        }
    }

    private static boolean same(Fingerprint expected, Fingerprint actual) {
        return expected.length == actual.length && MessageDigest.isEqual(expected.hash, actual.hash);
    }

    private static IOException invalid() {
        return new IOException("A selected legacy icon is invalid or unavailable. Existing settings were kept.");
    }
}
