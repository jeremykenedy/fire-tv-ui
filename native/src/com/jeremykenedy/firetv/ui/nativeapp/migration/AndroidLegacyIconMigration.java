package com.jeremykenedy.firetv.ui.nativeapp.migration;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Prepares exact legacy image references without modifying their sources or preferences. */
public final class AndroidLegacyIconMigration {
    private static final int MAX_BYTES = 8 * 1024 * 1024;
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
            byte[] bytes;
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                if (input == null) throw invalid();
                bytes = read(input);
            } catch (SecurityException | IllegalArgumentException unavailable) {
                throw new IOException("A selected legacy icon is unavailable. Existing settings were kept.", unavailable);
            }
            validate(bytes);
            String choice = choice(reference, bytes);
            synchronized (LOCK) {
                if (create) install(context, choice, bytes);
                else verify(new File(new File(context.getFilesDir(), "icons"), choice.substring(6)), bytes);
            }
            result.put(reference, choice);
        }
        return result;
    }

    private static byte[] read(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted() || count > MAX_BYTES - output.size()) throw invalid();
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static void validate(byte[] bytes) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (options.outWidth < 1 || options.outHeight < 1
                || (long) options.outWidth * options.outHeight > 40000000) throw invalid();
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 512) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (bitmap == null) throw invalid();
        bitmap.recycle();
    }

    private static String choice(String reference, byte[] bytes) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(reference.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            byte[] hash = digest.digest(bytes);
            StringBuilder value = new StringBuilder("image:");
            for (int i = 0; i < 16; i++) {
                if (i == 4 || i == 6 || i == 8 || i == 10) value.append('-');
                value.append(Character.forDigit((hash[i] & 255) >>> 4, 16));
                value.append(Character.forDigit(hash[i] & 15, 16));
            }
            return value.append(".png").toString();
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IOException("Image verification is unavailable.", unavailable);
        }
    }

    private static void install(Context context, String choice, byte[] bytes) throws IOException {
        File directory = new File(context.getFilesDir().getCanonicalFile(), "icons");
        if (!directory.isDirectory() && !directory.mkdirs()) throw invalid();
        if (!directory.getCanonicalFile().equals(directory.getAbsoluteFile())) throw invalid();
        File target = new File(directory, choice.substring(6));
        if (target.exists()) {
            verify(target, bytes);
            return;
        }
        File temporary = File.createTempFile("migration-", ".tmp", directory);
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write(bytes);
                output.getFD().sync();
            }
            verify(temporary, bytes);
            if (target.exists()) verify(target, bytes);
            else if (!temporary.renameTo(target)) throw invalid();
            verify(target, bytes);
        } finally {
            temporary.delete();
        }
    }

    private static void verify(File file, byte[] expected) throws IOException {
        if (!file.isFile() || !file.getCanonicalFile().equals(file.getAbsoluteFile()) || file.length() != expected.length) throw invalid();
        try (InputStream input = new FileInputStream(file)) {
            if (!Arrays.equals(expected, read(input))) throw invalid();
        }
    }

    private static IOException invalid() {
        return new IOException("A selected legacy icon is invalid or unavailable. Existing settings were kept.");
    }
}
