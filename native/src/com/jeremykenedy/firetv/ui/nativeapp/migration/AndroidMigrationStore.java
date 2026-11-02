package com.jeremykenedy.firetv.ui.nativeapp.migration;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;
import java.io.*;
import java.util.Map;
import java.util.Set;

/** Explicitly constructed by a future upgrade coordinator; nothing invokes this from startup. */
public final class AndroidMigrationStore implements MigrationTransaction.Store {
    private final SharedPreferences preferences;
    private final File path;
    private final AtomicFile recovery;

    public AndroidMigrationStore(Context context) {
        preferences = context.getSharedPreferences(LauncherMigration.PREFERENCES, Context.MODE_PRIVATE);
        path = new File(context.getNoBackupFilesDir(), "launcher-migration-v1.snapshot");
        recovery = new AtomicFile(path);
    }

    @Override public Object lock() { return preferences; }

    @Override public PreferenceSnapshot read() {
        return new PreferenceSnapshot(preferences.getAll());
    }

    @Override public void saveRecovery(PreferenceSnapshot before) throws IOException {
        if (path.exists() || new File(path.getPath() + ".bak").exists()) {
            throw new IOException("An existing private migration recovery must be resolved first.");
        }
        byte[] bytes = before.encode();
        FileOutputStream output = null;
        try {
            output = recovery.startWrite();
            output.write(bytes);
            recovery.finishWrite(output);
        } catch (IOException failed) {
            if (output != null) recovery.failWrite(output);
            throw new IOException("Private migration recovery could not be saved.");
        }
        if (!before.values().equals(readRecovery().values())) {
            throw new IOException("Private migration recovery could not be verified.");
        }
    }

    public PreferenceSnapshot readRecovery() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream input = recovery.openRead()) {
            byte[] chunk = new byte[8192];
            int count;
            while ((count = input.read(chunk)) != -1) {
                if ((long) bytes.size() + count > PreferenceSnapshot.MAX_BYTES) {
                    throw new IOException("Private migration recovery exceeds the supported size.");
                }
                bytes.write(chunk, 0, count);
            }
        }
        return PreferenceSnapshot.decode(bytes.toByteArray());
    }

    @Override public boolean replace(PreferenceSnapshot snapshot) {
        SharedPreferences.Editor editor = preferences.edit().clear();
        for (Map.Entry<String, Object> entry : snapshot.values().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof String) editor.putString(key, (String) value);
            else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Integer) editor.putInt(key, (Integer) value);
            else if (value instanceof Long) editor.putLong(key, (Long) value);
            else if (value instanceof Float) editor.putFloat(key, (Float) value);
            else {
                @SuppressWarnings("unchecked") Set<String> strings = (Set<String>) value;
                editor.putStringSet(key, strings);
            }
        }
        return editor.commit();
    }
}
