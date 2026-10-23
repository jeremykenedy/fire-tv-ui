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
    final java.util.List<Profile> profiles;
    private final String profileId;

    PlexConnection(String server, String token) {
        this(server, token, "");
    }

    PlexConnection(String server, String token, String machine) {
        this(server, token, machine, PlexFeedSettings.defaults());
    }

    PlexConnection(String server, String token, String machine, PlexFeedSettings feeds) {
        this(server, token, machine, feeds, java.util.Collections.<Profile>emptyList(), "");
    }

    private PlexConnection(
            String server,
            String token,
            String machine,
            PlexFeedSettings feeds,
            java.util.List<Profile> profiles,
            String profileId) {
        this.server = server;
        this.token = token;
        this.machine = machine;
        this.feeds = feeds;
        this.profiles = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(profiles));
        this.profileId = profileId;
    }

    PlexConnection withFeeds(PlexFeedSettings options) {
        return new PlexConnection(server, token, machine, options, profiles, profileId);
    }

    PlexConnection withProfiles(java.util.List<Profile> selected) {
        if (selected.size() > 8)
            throw new IllegalArgumentException("Choose up to eight Plex Home profiles.");
        java.util.Set<String> identities = new java.util.HashSet<>();
        for (Profile profile : selected) {
            if (!PlexHomeUsers.validId(profile.id)
                    || !identities.add(profile.id)
                    || profile.name.isEmpty()
                    || profile.name.length() > 128
                    || profile.token.isEmpty()
                    || profile.token.length() > 32768)
                throw new IllegalArgumentException("The Plex Home profile is invalid.");
        }
        return new PlexConnection(server, token, machine, feeds, selected, profileId);
    }

    PlexConnection forProfile(Profile profile) {
        return new PlexConnection(
                server,
                profile.token,
                machine,
                feeds,
                java.util.Collections.<Profile>emptyList(),
                profile.id);
    }

    PlexConnection forSource(String source) throws Exception {
        if (sourceKey().equals(source)) return this;
        for (Profile profile : profiles) {
            PlexConnection candidate = forProfile(profile);
            if (candidate.sourceKey().equals(source)) return candidate;
        }
        return null;
    }

    private org.json.JSONArray profileJson() throws Exception {
        org.json.JSONArray result = new org.json.JSONArray();
        for (Profile profile : profiles)
            result.put(
                    new JSONObject()
                            .put("id", profile.id)
                            .put("name", profile.name)
                            .put("token", profile.token));
        return result;
    }

    private static java.util.List<Profile> profiles(JSONObject value) throws Exception {
        java.util.List<Profile> result = new java.util.ArrayList<>();
        org.json.JSONArray rows = value.optJSONArray("profiles");
        if (rows != null && rows.length() > 8)
            throw new java.io.IOException("Too many encrypted Plex Home profiles.");
        if (rows != null)
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                result.add(
                        new Profile(
                                row.getString("id"),
                                row.getString("name"),
                                row.getString("token")));
            }
        return result;
    }

    static final class Profile {
        final String id, name, token;

        Profile(String id, String name, String token) {
            this.id = id;
            this.name = name;
            this.token = token;
        }
    }

    String sourceKey() throws Exception {
        byte[] bytes =
                java.security.MessageDigest.getInstance("SHA-256")
                        .digest(
                                (server
                                                + "\n"
                                                + machine
                                                + (profileId.isEmpty()
                                                        ? ""
                                                        : "\nprofile:" + profileId))
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
                            .put("profiles", profileJson())
                            .toString()
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if (plain.length > 128 * 1024) {
                java.util.Arrays.fill(plain, (byte) 0);
                throw new java.io.IOException(
                        "The encrypted Plex connection exceeds its supported size.");
            }
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
                    if (bytes.size() + count > 128 * 1024)
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
                                    PlexFeedSettings.parse(value.optJSONObject("feeds")))
                            .withProfiles(profiles(value));
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
