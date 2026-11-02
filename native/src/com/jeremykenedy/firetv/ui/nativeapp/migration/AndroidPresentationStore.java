package com.jeremykenedy.firetv.ui.nativeapp.migration;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;
import java.io.*;
import java.util.*;

/** Opt-in private adapter. Construction does not read or change any preference values. */
public final class AndroidPresentationStore implements PresentationTransaction.Store {
    private static final Object LOCK = new Object();
    private final Context context;
    private final File path;
    private final AtomicFile recovery;
    public AndroidPresentationStore(Context context) {
        this.context = context.getApplicationContext();
        path = new File(context.getNoBackupFilesDir(), "presentation-migration-v1.snapshot");
        recovery = new AtomicFile(path);
    }
    @Override public Object lock() { return LOCK; }
    @Override public PresentationSnapshot read() {
        Map<String, PreferenceSnapshot> values = new LinkedHashMap<>();
        for (String file : LegacyPresentationMapper.FILES) values.put(file,
                new PreferenceSnapshot(preferences(file).getAll()));
        return new PresentationSnapshot(values);
    }
    @Override public void saveRecovery(PresentationSnapshot snapshot) throws IOException {
        if (path.exists() || new File(path.getPath() + ".bak").exists()) {
            throw new IOException("Existing private presentation recovery must be resolved before another operation.");
        }
        byte[] bytes = snapshot.encode();
        FileOutputStream output = null;
        try {
            output = recovery.startWrite(); output.write(bytes); recovery.finishWrite(output);
        } catch (IOException failed) {
            if (output != null) recovery.failWrite(output);
            throw new IOException("Private presentation recovery could not be saved.");
        }
        if (!snapshot.sameValues(readRecovery())) throw new IOException("Private presentation recovery could not be verified.");
    }
    public PresentationSnapshot readRecovery() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream input = recovery.openRead()) {
            byte[] buffer = new byte[8192]; int size;
            while ((size = input.read(buffer)) != -1) {
                if ((long) bytes.size() + size > PresentationSnapshot.MAX_BYTES) throw new IOException("Presentation recovery is too large.");
                bytes.write(buffer, 0, size);
            }
        }
        return PresentationSnapshot.decode(bytes.toByteArray());
    }
    @Override public boolean replace(String file, PreferenceSnapshot snapshot) {
        SharedPreferences.Editor editor = preferences(file).edit().clear();
        for (Map.Entry<String, Object> entry : snapshot.values().entrySet()) {
            String key = entry.getKey(); Object value = entry.getValue();
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
    private SharedPreferences preferences(String file) {
        if (!LegacyPresentationMapper.FILES.contains(file)) throw new IllegalArgumentException("Unsupported presentation store.");
        return context.getSharedPreferences(file, Context.MODE_PRIVATE);
    }
}
