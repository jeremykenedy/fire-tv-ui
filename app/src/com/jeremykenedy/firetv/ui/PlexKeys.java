package com.jeremykenedy.firetv.ui;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONObject;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

final class PlexKeys {
    private static final String ALIAS = "fire_tv_ui_plex_device";
    private final Context context;
    private final long request;

    PlexKeys(Context context, long request) {
        this.context = context;
        this.request = request;
    }

    String clientId() {
        synchronized (PlexSession.class) {
            requireCurrent();
            android.util.AtomicFile file =
                    new android.util.AtomicFile(
                            new java.io.File(context.getNoBackupFilesDir(), "plex-client-id"));
            try (java.io.FileInputStream input = file.openRead()) {
                byte[] bytes = new byte[64];
                int length = input.read(bytes);
                String value =
                        new String(bytes, 0, Math.max(0, length), StandardCharsets.US_ASCII).trim();
                return UUID.fromString(value).toString();
            } catch (java.io.FileNotFoundException missing) {
                String value = UUID.randomUUID().toString();
                java.io.FileOutputStream output = null;
                try {
                    output = file.startWrite();
                    output.write(value.getBytes(StandardCharsets.US_ASCII));
                    file.finishWrite(output);
                    return value;
                } catch (Exception error) {
                    if (output != null) file.failWrite(output);
                    throw new IllegalStateException("The Plex device identity could not be saved.");
                }
            } catch (Exception invalid) {
                throw new IllegalStateException(
                        "The Plex device identity could not be read. Disconnect and connect"
                            + " again.");
            }
        }
    }

    private KeyStore keyStore() throws Exception {
        synchronized (PlexSession.class) {
            requireCurrent();
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            if (!store.containsAlias(ALIAS)) {
                KeyPairGenerator generator =
                        KeyPairGenerator.getInstance(
                                KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");
                generator.initialize(
                        new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                                .setKeySize(2048)
                                .setDigests(KeyProperties.DIGEST_SHA256)
                                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                                .build());
                generator.generateKeyPair();
            }
            return store;
        }
    }

    static void reset(Context context) throws Exception {
        synchronized (PlexSession.class) {
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            store.deleteEntry(ALIAS);
            new android.util.AtomicFile(
                            new java.io.File(context.getNoBackupFilesDir(), "plex-client-id"))
                    .delete();
        }
    }

    JSONObject jwk() throws Exception {
        synchronized (PlexSession.class) {
            requireCurrent();
            RSAPublicKey key = (RSAPublicKey) keyStore().getCertificate(ALIAS).getPublicKey();
            String exponent = integer(key.getPublicExponent());
            String modulus = integer(key.getModulus());
            String canonical =
                    "{\"e\":\"" + exponent + "\",\"kty\":\"RSA\",\"n\":\"" + modulus + "\"}";
            String kid =
                    encode(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
            return new JSONObject()
                    .put("kty", "RSA")
                    .put("e", exponent)
                    .put("n", modulus)
                    .put("kid", kid)
                    .put("alg", "RS256")
                    .put("use", "sig");
        }
    }

    String jwt(String nonce) throws Exception {
        synchronized (PlexSession.class) {
            requireCurrent();
            JSONObject header =
                    new JSONObject()
                            .put("typ", "JWT")
                            .put("alg", "RS256")
                            .put("kid", jwk().getString("kid"));
            long now = System.currentTimeMillis() / 1000;
            JSONObject claims =
                    new JSONObject()
                            .put("aud", "plex.tv")
                            .put("iss", clientId())
                            .put("iat", now)
                            .put("exp", now + 300);
            if (nonce != null) claims.put("nonce", nonce);
            String unsigned =
                    encode(header.toString().getBytes(StandardCharsets.UTF_8))
                            + "."
                            + encode(claims.toString().getBytes(StandardCharsets.UTF_8));
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign((PrivateKey) keyStore().getKey(ALIAS, null));
            signer.update(unsigned.getBytes(StandardCharsets.US_ASCII));
            return unsigned + "." + encode(signer.sign());
        }
    }

    private void requireCurrent() {
        if (!PlexSession.current(request)) throw new java.util.concurrent.CancellationException();
    }

    private static String integer(BigInteger number) {
        byte[] bytes = number.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0)
            bytes = java.util.Arrays.copyOfRange(bytes, 1, bytes.length);
        return encode(bytes);
    }

    private static String encode(byte[] value) {
        return Base64.encodeToString(value, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }
}
