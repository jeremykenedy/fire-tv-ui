package com.jeremykenedy.firetv.ui.nativeapp.providers.plex;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

/** Sanitized local PMS for native UI tests. Refuses to read or overwrite an existing account. */
public final class PlexFixture implements AutoCloseable {
    private final Context context;
    private final ServerSocket server;
    private final Thread worker;
    private final String source;
    private final boolean profiles;
    private String firstProfileSource, secondProfileSource;
    private volatile boolean failFirst, failSecond;
    private volatile java.util.concurrent.CountDownLatch heldRequest, releaseRequest, heldFinished;
    private volatile boolean expectedCancellation;
    private final java.util.concurrent.atomic.AtomicInteger ownerRequests = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger firstRequests = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger secondRequests = new java.util.concurrent.atomic.AtomicInteger();
    private final boolean hadEnabled;
    private final boolean wasEnabled;
    private final boolean hadConnectionKey;
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private volatile boolean closed;

    public static PlexFixture start(Context context) throws Exception { return new PlexFixture(context, false); }
    public static PlexFixture startProfiles(Context context) throws Exception { return new PlexFixture(context, true); }
    private PlexFixture(Context context, boolean profiles) throws Exception {
        this.context = context;
        this.profiles = profiles;
        for (String name : new String[]{"plex-connection.enc", "plex-connection.enc.bak", "plex-connection.enc.new",
                "plex-client-id", "plex-client-id.bak", "plex-client-id.new"}) {
            if (new File(context.getNoBackupFilesDir(), name).exists())
                throw new IllegalStateException("Use an isolated preview without a Plex connection or client identity for this fixture.");
        }
        SharedPreferences preferences = context.getSharedPreferences("source_plex", 0);
        hadEnabled = preferences.contains("enabled");
        wasEnabled = preferences.getBoolean("enabled", false);
        KeyStore keys = KeyStore.getInstance("AndroidKeyStore");
        keys.load(null);
        hadConnectionKey = keys.containsAlias("fire_tv_ui_plex_connection");
        if (profiles && hadConnectionKey) throw new IllegalStateException("Use a fresh fixture keystore without an existing Plex key.");
        server = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
        PlexConnection connection = new PlexConnection("http://127.0.0.1:" + server.getLocalPort(),
                "fixture-server-token", "fixture-machine", new PlexFeedSettings(
                        new java.util.LinkedHashSet<>(java.util.Arrays.asList("continue")),
                        Collections.<String>emptySet(), 4));
        source = connection.sourceKey();
        if (profiles) {
            PlexConnection first = connection.forProfile(new PlexConnection.Profile("7", "First fixture", "fixture-first-token"));
            PlexConnection second = connection.forProfile(new PlexConnection.Profile("8", "Second fixture", "fixture-second-token"));
            firstProfileSource = first.sourceKey(); secondProfileSource = second.sourceKey();
        }
        worker = new Thread(new Runnable() {
            @Override public void run() {
                while (!closed) {
                    try (Socket client = server.accept()) { serve(client); }
                    catch (Throwable problem) { if (!closed && !(expectedCancellation && problem instanceof java.io.IOException)) failure.compareAndSet(null, problem); }
                    finally { if (heldRequest != null && heldRequest.getCount() == 0 && heldFinished != null) heldFinished.countDown(); }
                }
            }
        }, "local-plex-fixture");
        worker.setDaemon(true);
        try {
            writeLegacyEnvelope(connection.server);
            // The real read path retains this identity and never touches account signing keys.
            File client = new File(context.getNoBackupFilesDir(), "plex-client-id");
            try (java.io.FileOutputStream output = new java.io.FileOutputStream(client)) {
                output.write("00000000-0000-4000-8000-000000000007".getBytes(StandardCharsets.US_ASCII));
            }
            PlexRepository.setEnabled(context, true);
            worker.start();
        } catch (Exception problem) { close(); throw problem; }
    }
    private void writeLegacyEnvelope(String address) throws Exception {
        // The historical encrypted envelope and JSON schema are independent fixture input.
        KeyStore keys = KeyStore.getInstance("AndroidKeyStore");
        keys.load(null);
        if (!keys.containsAlias("fire_tv_ui_plex_connection")) {
            javax.crypto.KeyGenerator generator = javax.crypto.KeyGenerator.getInstance("AES", "AndroidKeyStore");
            generator.init(new android.security.keystore.KeyGenParameterSpec.Builder(
                    "fire_tv_ui_plex_connection", android.security.keystore.KeyProperties.PURPOSE_ENCRYPT
                            | android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes("GCM").setEncryptionPaddings("NoPadding").build());
            generator.generateKey();
        }
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,
                (javax.crypto.SecretKey) keys.getKey("fire_tv_ui_plex_connection", null));
        String json = "{\"server\":\"" + address + "\",\"token\":\"fixture-server-token\","
                + "\"machine\":\"fixture-machine\",\"profiles\":[],"
                + "\"feeds\":{\"enabled\":[\"continue\"],\"users\":[],\"cards\":4}}";
        if (profiles) {
            org.json.JSONObject object = new org.json.JSONObject(json);
            object.put("profiles", new org.json.JSONArray()
                    .put(new org.json.JSONObject().put("id", "7").put("name", "First fixture").put("token", "fixture-first-token"))
                    .put(new org.json.JSONObject().put("id", "8").put("name", "Second fixture").put("token", "fixture-second-token")));
            json = object.toString();
        }
        byte[] encrypted = cipher.doFinal(json.getBytes(StandardCharsets.UTF_8));
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(
                new File(context.getNoBackupFilesDir(), "plex-connection.enc"))) {
            output.write(cipher.getIV().length);
            output.write(cipher.getIV());
            output.write(encrypted);
        }
        PlexConnection retained = PlexConnection.load(context);
        if (retained == null || !retained.sourceKey().equals(source)
                || !"fixture-server-token".equals(retained.token)
                || retained.feeds.cards != 4 || !retained.feeds.enabled.contains("continue")
                || retained.profiles.size() != (profiles ? 2 : 0))
            throw new AssertionError("Source adapter did not read the historical encrypted fixture schema.");
    }
    public String profileSource(boolean first) { return first ? firstProfileSource : secondProfileSource; }
    public void failFirst(boolean value) { failFirst = value; }
    public void failSecond(boolean value) { failSecond = value; }
    public boolean allProfilesRequested() { return ownerRequests.get() > 0 && firstRequests.get() > 0 && secondRequests.get() > 0; }
    public void holdFirstProfile() {
        heldRequest = new java.util.concurrent.CountDownLatch(1);
        releaseRequest = new java.util.concurrent.CountDownLatch(1);
        heldFinished = new java.util.concurrent.CountDownLatch(1);
        expectedCancellation = true;
    }
    public boolean awaitHeldRequest() throws InterruptedException { return heldRequest.await(5, java.util.concurrent.TimeUnit.SECONDS); }
    public void releaseHeldRequest() { if (releaseRequest != null) releaseRequest.countDown(); }
    public boolean awaitHeldFinished() throws InterruptedException { return heldFinished.await(5, java.util.concurrent.TimeUnit.SECONDS); }
    public void allowFutureFailures() { expectedCancellation = false; }
    public String feedId() { return "plex:" + source + ":fire-tv-ui-plex-continue"; }
    public String firstKey() { return "plex:" + source + ":12"; }
    public String secondKey() { return "plex:" + source + ":13"; }
    public String discoveryKey() { return "plex:" + source + ":21"; }
    public String seriesKey() { return "plex:" + source + ":series:9"; }
    public void assertHealthy() {
        if (failure.get() != null) throw new AssertionError("Local Plex fixture failed", failure.get());
    }
    private void serve(Socket socket) throws Exception {
        socket.setSoTimeout(3000);
        InputStream input = socket.getInputStream();
        java.io.ByteArrayOutputStream headers = new java.io.ByteArrayOutputStream();
        int state = 0;
        while (headers.size() < 32768) {
            int next = input.read();
            if (next < 0) throw new java.io.IOException("Incomplete fixture request.");
            headers.write(next);
            if ((state == 0 || state == 2) && next == '\r') state++;
            else if ((state == 1 || state == 3) && next == '\n') state++;
            else state = 0;
            if (state == 4) break;
        }
        String request = new String(headers.toByteArray(), StandardCharsets.US_ASCII);
        if (!request.startsWith("GET / HTTP/1.1\r\n")
                && !request.startsWith("GET /identity HTTP/1.1\r\n")
                && !request.startsWith("GET /library/metadata/21 HTTP/1.1\r\n")
                && !request.startsWith("GET /hubs/continueWatching?count=4 HTTP/1.1\r\n")
                && !request.startsWith("GET /library/all?type=1&sort=addedAt:desc HTTP/1.1\r\n")
                && !request.startsWith("GET /library/all?type=2&sort=addedAt:desc HTTP/1.1\r\n"))
            throw new AssertionError("Fixture received an unsupported request.");
        String lower = request.toLowerCase(java.util.Locale.ROOT);
        String token;
        if (lower.contains("x-plex-token: fixture-server-token\r\n")) { token = "owner"; ownerRequests.incrementAndGet(); }
        else if (profiles && lower.contains("x-plex-token: fixture-first-token\r\n")) { token = "first"; firstRequests.incrementAndGet(); }
        else if (profiles && lower.contains("x-plex-token: fixture-second-token\r\n")) { token = "second"; secondRequests.incrementAndGet(); }
        else throw new AssertionError("Unexpected fixture credential in local server request.");
        if (profiles && "first".equals(token) && heldRequest != null && heldRequest.getCount() != 0) {
            heldRequest.countDown();
            if (!releaseRequest.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new java.io.IOException("Held fixture request timed out.");
        }
        if (profiles && (("first".equals(token) && failFirst) || ("second".equals(token) && failSecond))) {
            OutputStream output = socket.getOutputStream();
            output.write("HTTP/1.1 401 Unauthorized\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            output.flush(); return;
        }
        if (!request.toLowerCase(java.util.Locale.ROOT).contains("x-plex-client-identifier: 00000000-0000-4000-8000-000000000007\r\n"))
            throw new AssertionError("The saved fixture Plex client identity was not retained.");
        String response = request.startsWith("GET / HTTP") || request.startsWith("GET /identity HTTP")
                ? "{\"MediaContainer\":{\"machineIdentifier\":\"fixture-machine\"}}"
                : "{\"MediaContainer\":{\"Metadata\":[{\"ratingKey\":\"12\",\"type\":\"episode\",\"title\":\"Pilot\",\"grandparentTitle\":\"Fixture Show\",\"grandparentRatingKey\":\"9\",\"key\":\"/library/metadata/12\",\"viewOffset\":5000,\"duration\":9000},{\"ratingKey\":\"13\",\"type\":\"episode\",\"title\":\"Next\",\"grandparentTitle\":\"Fixture Show\",\"grandparentRatingKey\":\"9\",\"key\":\"/library/metadata/13\"}]}}";
        if (profiles && request.startsWith("GET /hubs/continueWatching")) {
            String id = "owner".equals(token) ? "12" : "first".equals(token) ? "21" : "22";
            String title = "owner".equals(token) ? "Owner fixture" : "first".equals(token) ? "First fixture" : "Second fixture";
            response = new org.json.JSONObject().put("MediaContainer", new org.json.JSONObject()
                    .put("Metadata", new org.json.JSONArray().put(new org.json.JSONObject()
                            .put("ratingKey", id).put("key", "/library/metadata/" + id)
                            .put("type", "movie").put("title", title)))).toString();
        }
        if (request.startsWith("GET /library/all?type=1")) response = new org.json.JSONObject()
                .put("MediaContainer", new org.json.JSONObject().put("Metadata", new org.json.JSONArray()
                        .put(new org.json.JSONObject().put("ratingKey", "21").put("key", "/library/metadata/21")
                                .put("type", "movie").put("title", "Fixture Discovery Movie")
                                .put("librarySectionID", "1").put("librarySectionTitle", "Fixture movies")
                                .put("rating", 8.2).put("ratingImage", "imdb://image.rating")
                                .put("Genre", new org.json.JSONArray().put(new org.json.JSONObject().put("tag", "Drama")))))).toString();
        if (request.startsWith("GET /library/all?type=2")) response = "{\"MediaContainer\":{\"Metadata\":[]}}";
        if (request.startsWith("GET /library/metadata/21")) response = "{\"MediaContainer\":{\"Metadata\":[{\"ratingKey\":\"21\",\"type\":\"movie\",\"title\":\"Fixture Discovery Movie\",\"key\":\"/library/metadata/21\"}]}}";
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        OutputStream output = socket.getOutputStream();
        output.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "
                + bytes.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        output.write(bytes);
        output.flush();
    }
    @Override public void close() throws Exception {
        if (closed) return;
        closed = true;
        server.close();
        if (worker.isAlive()) worker.join(4000);
        PlexSession.begin();
        PlexConnection.remove(context);
        for (String name : new String[]{"plex-connection.enc.new", "plex-client-id", "plex-client-id.bak", "plex-client-id.new"})
            new File(context.getNoBackupFilesDir(), name).delete();
        SharedPreferences.Editor preferences = context.getSharedPreferences("source_plex", 0).edit();
        if (hadEnabled) preferences.putBoolean("enabled", wasEnabled);
        else preferences.remove("enabled");
        if (!preferences.commit()) throw new IllegalStateException("Plex fixture preference restoration failed.");
        if (!hadConnectionKey) {
            KeyStore keys = KeyStore.getInstance("AndroidKeyStore");
            keys.load(null);
            keys.deleteEntry("fire_tv_ui_plex_connection");
        }
    }
}
