package com.jeremykenedy.firetv.ui.nativeapp.locks;

import android.content.Context;
import android.util.AtomicFile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/** Private launcher-only locks. Never include this file in setups or portable backups. */
public final class LockStore {
    private static final Object LOCK = new Object();
    private final File directory;
    private final AtomicFile file;

    public LockStore(Context context) {
        directory = new File(context.getApplicationContext().getNoBackupFilesDir(), "launcher-locks");
        file = new AtomicFile(new File(directory, "locks-v1.bin"));
    }

    public boolean hasPin() throws IOException { synchronized (LOCK) { return read().hasPin(); } }

    public boolean isLocked(String key) throws IOException {
        synchronized (LOCK) { return read().isLocked(key); }
    }

    public Set<String> lockedTargets() throws IOException {
        synchronized (LOCK) { return Collections.unmodifiableSet(new LinkedHashSet<>(read().targets)); }
    }

    public long retryAfterMillis() throws IOException {
        synchronized (LOCK) { return read().remaining(System.currentTimeMillis()); }
    }

    public void createPin(char[] pin) throws IOException, GeneralSecurityException {
        synchronized (LOCK) { LockState state = read(); state.createPin(pin); write(state); }
    }

    public void createPinAndLock(char[] pin, String key) throws IOException, GeneralSecurityException {
        LockState.key(key);
        synchronized (LOCK) {
            LockState state = read(); state.createPin(pin); state.lock(key, true); write(state);
        }
    }

    /** Opt-in private in-place migration only. Caller must clear the supplied legacy PIN buffer. */
    public boolean importLegacyPin(char[] legacyPin, Set<String> exactTargets) throws IOException, GeneralSecurityException {
        synchronized (LOCK) {
            LockState state = read();
            if (!state.importLegacyPin(legacyPin, exactTargets)) return false;
            byte[] expected = state.encode();
            write(state);
            if (!Arrays.equals(expected, read().encode())) throw new IOException("Imported private locks could not be verified.");
            return true;
        }
    }

    public LockResult authenticate(char[] pin) throws IOException, GeneralSecurityException {
        synchronized (LOCK) { LockState state = read(); LockResult result = verify(state, pin); write(state); return result; }
    }

    public LockResult setLocked(String key, boolean locked, char[] pin) throws IOException, GeneralSecurityException {
        LockState.key(key);
        synchronized (LOCK) {
            LockState state = read();
            LockResult result = verify(state, pin);
            if (result == LockResult.GRANTED) state.lock(key, locked);
            write(state);
            return result;
        }
    }

    public LockResult changePin(char[] current, char[] next) throws IOException, GeneralSecurityException {
        LockState.validatePin(next);
        synchronized (LOCK) {
            LockState state = read();
            LockResult result = verify(state, current);
            if (result == LockResult.GRANTED) state.replacePin(next);
            write(state);
            return result;
        }
    }

    public LockResult unlockAll(char[] pin) throws IOException, GeneralSecurityException {
        synchronized (LOCK) {
            LockState state = read();
            LockResult result = verify(state, pin);
            if (result == LockResult.GRANTED) state.targets.clear();
            write(state);
            return result;
        }
    }

    private LockResult verify(LockState state, char[] pin) throws GeneralSecurityException {
        return state.verify(pin, System.currentTimeMillis());
    }

    private LockState read() throws IOException {
        // AtomicFile.openRead also restores an interrupted write's previous version.
        if (!file.getBaseFile().exists() && !new File(file.getBaseFile().getPath() + ".bak").exists()) return new LockState();
        try (FileInputStream input = file.openRead(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (bytes.size() + count > LockState.MAX_BYTES) throw new IOException("Lock storage exceeds its limit.");
                bytes.write(buffer, 0, count);
            }
            return LockState.decode(bytes.toByteArray());
        }
    }

    private void write(LockState state) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Lock storage is unavailable.");
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(state.encode());
            file.finishWrite(output);
        } catch (IOException failed) {
            if (output != null) file.failWrite(output);
            throw failed;
        }
    }
}
