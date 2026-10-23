package com.jeremykenedy.firetv.ui;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class PlexConnection {
    private static final Object KEY_LOCK = new Object();
    private static final String ALIAS = "fire_tv_ui_plex_connection";
    final String server;
    final String token;
    final String machine;
    final PlexFeedSettings feeds;

    PlexConnection(String server, String token) {
        this(server, token, "");
    }

    PlexConnection(String server, String token, String machine) {
        this(server, token, machine, PlexFeedSettings.defaults());
    }

    PlexConnection(String server, String token, String machine, PlexFeedSettings feeds) {
        this.server = server;
        this.token = token;
        this.machine = machine;
        this.feeds = feeds;
    }

    PlexConnection withFeeds(PlexFeedSettings options) {
        return new PlexConnection(server, token, machine, options);
    }

    String sourceKey() throws Exception {
        byte[] bytes =
                java.security.MessageDigest.getInstance("SHA-256")
                        .digest(
                                (server + "\n" + machine)
                                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder value = new StringBuilder();
        for (byte item : bytes)
            value.append(String.format(java.util.Locale.ROOT, "%02x", item & 255));
        return value.toString();
    }

    private static AtomicFile file(Context context) {
        return new AtomicFile(new File(context.getNoBackupFilesDir(), "plex-connection.enc"));
    }

    private static SecretKey key() throws Exception {
        synchronized (KEY_LOCK) {
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            if (!store.containsAlias(ALIAS)) {
                KeyGenerator generator =
                        KeyGenerator.getInstance(
                                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
                generator.init(
                        new KeyGenParameterSpec.Builder(
                                        ALIAS,
                                        KeyProperties.PURPOSE_ENCRYPT
                                                | KeyProperties.PURPOSE_DECRYPT)
                                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                                .build());
                generator.generateKey();
            }
            return (SecretKey) store.getKey(ALIAS, null);
        }
    }

    void save(Context context) throws Exception {
        synchronized (PlexSession.class) {
            byte[] plain =
                    new JSONObject()
                            .put("server", server)
                            .put("token", token)
                            .put("machine", machine)
                            .put("feeds", feeds.json())
                            .toString()
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] encrypted;
            try {
                encrypted = cipher.doFinal(plain);
            } finally {
                java.util.Arrays.fill(plain, (byte) 0);
            }
            AtomicFile target = file(context);
            FileOutputStream output = null;
            try {
                output = target.startWrite();
                output.write(cipher.getIV().length);
                output.write(cipher.getIV());
                output.write(encrypted);
                target.finishWrite(output);
            } catch (Exception error) {
                if (output != null) target.failWrite(output);
                throw error;
            }
        }
    }

    static PlexConnection load(Context context) throws Exception {
        synchronized (PlexSession.class) {
            AtomicFile source = file(context);
            FileInputStream opened;
            try {
                opened = source.openRead();
            } catch (java.io.FileNotFoundException unavailable) {
                if (source.getBaseFile().exists()
                        || new File(source.getBaseFile().getPath() + ".bak").exists()) {
                    throw new java.io.IOException(
                            "This TV's encrypted Plex connection could not be read.");
                }
                return null;
            }
            try (FileInputStream input = opened) {
                int ivLength = input.read();
                if (ivLength != 12)
                    throw new java.io.IOException(
                            "Reconnect Plex to restore this device's encrypted connection.");
                byte[] iv = new byte[ivLength];
                if (input.read(iv) != ivLength)
                    throw new java.io.IOException("The encrypted Plex connection is incomplete.");
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (bytes.size() + count > 32768)
                        throw new java.io.IOException("The encrypted Plex connection is invalid.");
                    bytes.write(buffer, 0, count);
                }
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
                byte[] plain = cipher.doFinal(bytes.toByteArray());
                try {
                    JSONObject value =
                            new JSONObject(
                                    new String(plain, java.nio.charset.StandardCharsets.UTF_8));
                    return new PlexConnection(
                            value.getString("server"),
                            value.getString("token"),
                            value.optString("machine", ""),
                            PlexFeedSettings.parse(value.optJSONObject("feeds")));
                } finally {
                    java.util.Arrays.fill(plain, (byte) 0);
                }
            }
        }
    }

    static void remove(Context context) {
        synchronized (PlexSession.class) {
            file(context).delete();
        }
    }
}
