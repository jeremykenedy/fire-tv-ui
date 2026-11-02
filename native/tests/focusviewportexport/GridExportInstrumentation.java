package com.jeremykenedy.firetv.ui.nativeapp.gridexport;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.util.Base64;
import java.io.File;
import java.io.FileInputStream;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.util.Arrays;

/** Read only export of the synthetic focus viewport screenshots. */
public final class GridExportInstrumentation extends Instrumentation {
    private static final int CHUNK_BYTES = 64 * 1024;
    private Bundle arguments;
    @Override public void onCreate(Bundle args) { super.onCreate(args); arguments = args; start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            check("com.jeremykenedy.firetv.ui.preview".equals(getTargetContext().getPackageName()), "Preview only");
            String filename = arguments.getString("filename");
            check(Arrays.asList("files/grid-5-first.png", "files/grid-5-edge.png", "files/grid-5-scrolled.png",
                    "files/grid-16-first.png", "files/grid-16-edge.png", "files/grid-16-scrolled.png",
                    "files/grid-20-first.png", "files/grid-20-edge.png", "files/grid-20-scrolled.png").contains(filename), "Filename is not allowed");
            String value = arguments.getString("offset", "0");
            check(value.matches("[0-9]{1,10}"), "Offset must be a nonnegative byte count");
            long offset = Long.parseLong(value);
            File file = new File(getTargetContext().getFilesDir(), filename.substring("files/".length()));
            check(file.getCanonicalFile().getParentFile().equals(getTargetContext().getFilesDir().getCanonicalFile())
                    && file.isFile() && file.length() <= 32 * 1024 * 1024, "Screenshot is unavailable");
            long length = file.length();
            check(offset <= length, "Offset exceeds screenshot length");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[CHUNK_BYTES];
            try (FileInputStream input = new FileInputStream(file)) {
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            byte[] chunk = new byte[(int) Math.min(CHUNK_BYTES, length - offset)];
            try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
                input.seek(offset); input.readFully(chunk);
            }
            check(file.length() == length, "Screenshot changed during export");
            StringBuilder hash = new StringBuilder();
            for (byte part : digest.digest()) hash.append(String.format(java.util.Locale.ROOT, "%02x", part & 255));
            result.putString("filename", filename);
            result.putLong("offset", offset);
            result.putLong("next_offset", offset + chunk.length);
            result.putLong("total_length", length);
            result.putString("sha256", hash.toString());
            result.putBoolean("eof", offset + chunk.length == length);
            result.putString("base64_chunk", Base64.encodeToString(chunk, Base64.NO_WRAP));
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("error", failure.toString()); finish(Activity.RESULT_CANCELED, result);
        }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }
}
