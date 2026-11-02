package com.jeremykenedy.firetv.ui.nativeapp.widget.feeds;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Source URLs may contain private query values. Keep them encrypted and outside portable backups. */
public final class FeedStore {
    private static final String ALIAS = "native-custom-feed-source";
    private static final Object LOCK = new Object();
    private final AtomicFile file;
    public FeedStore(Context context) { file = new AtomicFile(new File(context.getNoBackupFilesDir(), "selected-custom-feed")); }
    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        return (SecretKey) store.getKey(ALIAS, null);
    }
    private static final class Selection {
        final FeedSource source;
        final Set<String> hidden;
        Selection(FeedSource source, Set<String> hidden) { this.source = source; this.hidden = hidden; }
    }
    public FeedSource read() throws IOException {
        Selection selected = readSelection();
        return selected == null ? null : selected.source;
    }
    public Set<String> hidden(FeedSource source) throws IOException {
        Selection selected = readSelection();
        return selected != null && selected.source.equals(source) ? selected.hidden : new LinkedHashSet<String>();
    }
    public void hide(FeedSource source, String id) throws IOException {
        synchronized (LOCK) {
            Selection selected = readSelection();
            if (selected == null || !selected.source.equals(source)) throw new IOException("The selected source changed.");
            selected.hidden.add(FeedSnapshot.hiddenKey(id));
            if (selected.hidden.size() > 60) throw new IOException("Restore hidden headlines before hiding more.");
            write(source, selected.hidden);
        }
    }
    public void restoreHidden(FeedSource source) throws IOException {
        synchronized (LOCK) {
            Selection selected = readSelection();
            if (selected == null || !selected.source.equals(source)) throw new IOException("The selected source changed.");
            write(source, new LinkedHashSet<String>());
        }
    }
    private Selection readSelection() throws IOException {
        synchronized (LOCK) {
            if (!file.getBaseFile().exists() && !new File(file.getBaseFile().getPath() + ".bak").exists()) return null;
            byte[] plain = null;
            try (DataInputStream input = new DataInputStream(file.openRead())) {
                if (input.readInt() != 2 || input.readUnsignedByte() != 12) throw new IOException("Invalid private feed settings.");
                byte[] iv = new byte[12]; input.readFully(iv);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] buffer = new byte[1024]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (bytes.size() + count > 8192) throw new IOException("Private feed settings exceed their supported size.");
                    bytes.write(buffer, 0, count);
                }
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
                plain = cipher.doFinal(bytes.toByteArray());
                try (DataInputStream value = new DataInputStream(new ByteArrayInputStream(plain))) {
                    FeedSource source = new FeedSource(value.readUTF(), value.readUTF(), FeedSource.Format.valueOf(value.readUTF()));
                    Set<String> hidden = new LinkedHashSet<>();
                    int hiddenCount = value.read();
                    if (hiddenCount > 60) throw new IOException("Invalid hidden headline count.");
                    for (int i = 0; i < hiddenCount; i++) {
                        String hash = value.readUTF();
                        if (!hash.matches("[0-9a-f]{64}") || !hidden.add(hash)) throw new IOException("Invalid hidden headline.");
                    }
                    if (value.read() != -1) throw new IOException("Invalid private feed settings.");
                    return new Selection(source, hidden);
                }
            } catch (Exception invalid) { throw new IOException("Saved feed settings are unavailable. Your stored selection was retained."); }
            finally { if (plain != null) Arrays.fill(plain, (byte) 0); }
        }
    }
    public void save(FeedSource source) throws IOException {
        synchronized (LOCK) {
            Selection previous;
            try { previous = readSelection(); } catch (IOException unreadable) { previous = null; }
            write(source, previous != null && previous.source.equals(source) ? previous.hidden : new LinkedHashSet<String>());
            FeedMemory.clear();
        }
    }
    private void write(FeedSource source, Set<String> hidden) throws IOException {
        synchronized (LOCK) {
            byte[] plain = null; FileOutputStream output = null;
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream value = new DataOutputStream(bytes);
                value.writeUTF(source.label); value.writeUTF(source.url); value.writeUTF(source.format.name());
                value.writeByte(hidden.size());
                for (String hash : hidden) value.writeUTF(hash);
                value.flush();
                plain = bytes.toByteArray();
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
                byte[] encrypted = cipher.doFinal(plain); byte[] iv = cipher.getIV();
                if (encrypted.length > 8192 || iv.length != 12) throw new IOException("Unsupported private feed settings.");
                output = file.startWrite(); DataOutputStream data = new DataOutputStream(output);
                data.writeInt(2); data.writeByte(iv.length); data.write(iv); data.write(encrypted); data.flush();
                file.finishWrite(output);
            } catch (Exception failed) {
                if (output != null) file.failWrite(output);
                throw new IOException("The selected feed could not be saved. Previous settings were retained.");
            } finally { if (plain != null) Arrays.fill(plain, (byte) 0); }
        }
    }
    public void clear() { synchronized (LOCK) { file.delete(); FeedMemory.clear(); } }
}
