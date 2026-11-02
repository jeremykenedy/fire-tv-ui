package com.jeremykenedy.firetv.ui.nativeapp.providers.plex;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

final class PlexApi {
    private static final String ACCOUNT = "https://clients.plex.tv/api/v2";
    private final PlexKeys keys;
    private final android.os.CancellationSignal cancellation;
    private static final java.util.concurrent.ScheduledThreadPoolExecutor TIMEOUTS = deadlines();

    private static java.util.concurrent.ScheduledThreadPoolExecutor deadlines() {
        java.util.concurrent.ScheduledThreadPoolExecutor executor = new java.util.concurrent.ScheduledThreadPoolExecutor(
                2, new java.util.concurrent.ThreadFactory() {
                    @Override public Thread newThread(Runnable task) {
                        Thread thread = new Thread(task, "plex-request-deadline");
                        thread.setDaemon(true);
                        return thread;
                    }
                });
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    PlexApi(Context context, long request) { this(context, request, null); }
    PlexApi(Context context, long request, android.os.CancellationSignal cancellation) {
        keys = new PlexKeys(context, request);
        this.cancellation = cancellation;
    }
    void checkCanceled() {
        if (cancellation != null) cancellation.throwIfCanceled();
        if (Thread.currentThread().isInterrupted()) throw new android.os.OperationCanceledException();
    }

    JSONObject createPin() throws Exception {
        return json(
                ACCOUNT + "/pins",
                "POST",
                null,
                new JSONObject().put("jwk", keys.jwk()).put("strong", false),
                "Pairing request");
    }

    String pollPin(long pin) throws Exception {
        JSONObject result =
                json(
                        ACCOUNT
                                + "/pins/"
                                + pin
                                + "?deviceJWT="
                                + URLEncoder.encode(keys.jwt(null), "UTF-8"),
                        "GET",
                        null,
                        null,
                        "Pairing approval");
        String token = result.optString("authToken", "");
        return token.isEmpty() || "null".equals(token) ? null : token;
    }

    String refreshToken() throws Exception {
        String nonce =
                json(ACCOUNT + "/auth/nonce", "GET", null, null, "Account refresh nonce")
                        .getString("nonce");
        return json(
                        ACCOUNT + "/auth/token",
                        "POST",
                        null,
                        new JSONObject().put("jwt", keys.jwt(nonce)),
                        "Account refresh")
                .getString("auth_token");
    }

    PlexConnection authorize(String server, String machine, String accountToken) throws Exception {
        JSONArray resources =
                new JSONArray(
                        new String(
                                request(
                                        ACCOUNT
                                                + "/resources?includeHttps=1&includeRelay=1&includeIPv6=1",
                                        "GET",
                                        accountToken,
                                        null,
                                        8 * 1024 * 1024,
                                        "Server access discovery"),
                                StandardCharsets.UTF_8));
        PlexConnection connection =
                new PlexConnection(server, PlexResources.serverToken(resources, machine), machine);
        try {
            verifyServer(connection);
            return connection;
        } catch (AuthorizationRequired unsupported) {
            // Some PMS versions reject the JWT returned by resources, but accept
            // the same server's legacy credential from the approved device list.
            JSONArray devices =
                    new JSONArray(
                            new String(
                                    request(
                                            ACCOUNT + "/devices",
                                            "GET",
                                            accountToken,
                                            null,
                                            8 * 1024 * 1024,
                                            "Server device access discovery"),
                                    StandardCharsets.UTF_8));
            PlexConnection compatible =
                    new PlexConnection(
                            server, PlexResources.deviceToken(devices, machine), machine);
            verifyServer(compatible);
            return compatible;
        }
    }

    java.util.List<PlexHomeUsers.User> homeUsers() throws Exception {
        return homeUsers(refreshToken());
    }

    private java.util.List<PlexHomeUsers.User> homeUsers(String accountToken) throws Exception {
        return PlexHomeUsers.read(
                new org.json.JSONTokener(
                                new String(
                                        request(
                                                ACCOUNT + "/home/users",
                                                "GET",
                                                accountToken,
                                                null,
                                                2 * 1024 * 1024,
                                                "Plex Home profiles"),
                                        StandardCharsets.UTF_8))
                        .nextValue());
    }

    PlexConnection authorizeHome(String server, String machine, String profile, String pin)
            throws Exception {
        String accountToken = refreshToken();
        PlexHomeUsers.User selected = PlexHomeUsers.require(homeUsers(accountToken), profile);
        if (selected.protectedByPin && (pin == null || !pin.matches("[0-9]{4}")))
            throw new IOException("Enter this profile's own four-digit Plex PIN on the TV.");
        if (pin != null && !pin.isEmpty() && !pin.matches("[0-9]{4}"))
            throw new IOException("A Plex profile PIN must contain four digits.");
        String suffix = selected.protectedByPin ? "?pin=" + pin : "";
        JSONObject switched =
                json(
                        ACCOUNT + "/home/users/" + selected.id + "/switch" + suffix,
                        "POST",
                        accountToken,
                        null,
                        "Plex Home profile authorization");
        String token = switched.optString("authToken", "");
        if (token.isEmpty() || "null".equals(token))
            throw new IOException("Plex did not authorize this profile. Your connection was kept.");
        JSONObject identity =
                json(ACCOUNT + "/user", "GET", token, null, "Plex Home profile identity");
        if (!PlexHomeUsers.matchesIdentity(selected, identity))
            throw new IOException("Plex authorized a different profile. Your connection was kept.");
        return authorize(server, machine, token);
    }

    private void verifyServer(PlexConnection connection) throws Exception {
        String actual =
                server(connection, "/")
                        .getJSONObject("MediaContainer")
                        .optString("machineIdentifier", "");
        if (!connection.machine.equals(actual))
            throw new IOException("The Plex server identity changed. Check its address and retry.");
    }

    JSONObject server(PlexConnection connection, String path) throws Exception {
        return server(connection, path, 12);
    }

    JSONObject server(PlexConnection connection, String path, int count) throws Exception {
        if (!path.startsWith("/") || path.startsWith("//") || count < 1 || count > 100)
            throw new IOException("The Plex request is invalid.");
        return new JSONObject(
                new String(
                        request(
                                PlexValues.serverBase(connection.server) + path,
                                "GET",
                                connection.token,
                                null,
                                8 * 1024 * 1024,
                                "/identity".equals(path)
                                        ? "Server identity"
                                        : "/".equals(path) ? "Server access" : "Library read",
                                count),
                        StandardCharsets.UTF_8));
    }

    byte[] artwork(PlexConnection connection, String path) throws Exception {
        if (!PlexValues.libraryPath(path))
            throw new IOException("The Plex artwork path is unavailable.");
        return request(
                PlexValues.serverBase(connection.server) + path,
                "GET",
                connection.token,
                null,
                4 * 1024 * 1024,
                "Artwork");
    }

    private JSONObject json(String url, String method, String token, JSONObject body, String phase)
            throws Exception {
        return new JSONObject(
                new String(
                        request(url, method, token, body, 8 * 1024 * 1024, phase),
                        StandardCharsets.UTF_8));
    }

    private byte[] request(
            String url, String method, String token, JSONObject body, int limit, String phase)
            throws Exception {
        return request(url, method, token, body, limit, phase, 12);
    }

    private byte[] request(
            String url,
            String method,
            String token,
            JSONObject body,
            int limit,
            String phase,
            int pageSize)
            throws Exception {
        return request(
                url, method, token, body, limit, phase, pageSize, "Artwork".equals(phase) ? 3 : 0,
                System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos("Artwork".equals(phase) ? 6 : 15));
    }

    private byte[] request(
            String url,
            String method,
            String token,
            JSONObject body,
            int limit,
            String phase,
            int pageSize,
            int redirects,
            long deadline)
            throws Exception {
        HttpURLConnection request = null;
        java.util.concurrent.ScheduledFuture<?> timeout = null;
        final java.util.concurrent.atomic.AtomicBoolean expired = new java.util.concurrent.atomic.AtomicBoolean();
        try {
            checkCanceled();
            long remaining = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
            if (remaining <= 0) throw new RequestFailure(phase, 0);
            URL destination = new URL(url);
            if (!PlexTransportPolicy.permitsAddress(url)) throw new TransportUnavailable(
                    "Use HTTPS for remote Plex servers. HTTP requires an explicitly selected numeric private LAN address.");
            if ("http".equalsIgnoreCase(destination.getProtocol())
                    && !android.security.NetworkSecurityPolicy.getInstance()
                            .isCleartextTrafficPermitted(destination.getHost()))
                throw new TransportUnavailable("Android blocks HTTP for this server. Use the server's HTTPS address. Your saved connection was kept.");
            request = (HttpURLConnection) destination.openConnection();
            request.setInstanceFollowRedirects(false);
            request.setConnectTimeout((int) Math.min(3000, remaining));
            request.setReadTimeout((int) Math.min(3000, remaining));
            final HttpURLConnection active = request;
            final Runnable disconnect = new Runnable() {
                @Override public void run() { active.disconnect(); }
            };
            timeout = TIMEOUTS.schedule(new Runnable() {
                @Override public void run() { expired.set(true); disconnect.run(); }
            }, remaining, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (cancellation != null) cancellation.setOnCancelListener(new android.os.CancellationSignal.OnCancelListener() {
                @Override public void onCancel() { TIMEOUTS.execute(disconnect); }
            });
            checkCanceled();
            request.setRequestMethod(method);
            boolean artwork = "Artwork".equals(phase);
            request.setRequestProperty("Accept", artwork ? "image/*" : "application/json");
            if (!artwork || token != null) {
                request.setRequestProperty("X-Plex-Client-Identifier", keys.clientId());
                request.setRequestProperty("X-Plex-Product", "Fire TV UI");
                request.setRequestProperty("X-Plex-Version", "1.6.0");
                request.setRequestProperty("X-Plex-Platform", "Android");
                request.setRequestProperty("X-Plex-Device", "Fire TV");
                request.setRequestProperty("X-Plex-Container-Start", "0");
                request.setRequestProperty("X-Plex-Container-Size", Integer.toString(pageSize));
            }
            if (token != null) request.setRequestProperty("X-Plex-Token", token);
            if (body != null) {
                request.setDoOutput(true);
                request.setRequestProperty("Content-Type", "application/json");
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                request.setFixedLengthStreamingMode(bytes.length);
                try (java.io.OutputStream output = request.getOutputStream()) {
                    output.write(bytes);
                }
            }
            checkCanceled();
            int status = request.getResponseCode();
            checkCanceled();
            if (expired.get()) throw new RequestFailure(phase, 0);
            if (artwork
                    && redirects > 0
                    && (status == 301
                            || status == 302
                            || status == 303
                            || status == 307
                            || status == 308)) {
                String next = PlexArtworkRedirect.next(url, request.getHeaderField("Location"));
                String credential = PlexArtworkRedirect.sameOrigin(url, next) ? token : null;
                request.disconnect();
                request = null;
                if (timeout != null) timeout.cancel(false);
                if (cancellation != null) cancellation.setOnCancelListener(null);
                return request(
                        next, "GET", credential, null, limit, phase, pageSize, redirects - 1, deadline);
            }
            if ((status == 401 || status == 498) && (!artwork || token != null))
                throw new AuthorizationRequired(phase, status);
            if (status < 200 || status >= 300) throw new RequestFailure(phase, status);
            try (InputStream input = request.getInputStream();
                    ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    checkCanceled();
                    if (expired.get()) throw new RequestFailure(phase, 0);
                    if (output.size() + count > limit)
                        throw new IOException("The Plex response exceeds the supported size.");
                    output.write(buffer, 0, count);
                }
                checkCanceled();
                if (expired.get()) throw new RequestFailure(phase, 0);
                return output.toByteArray();
            }
        } catch (android.os.OperationCanceledException canceled) { throw canceled; }
        catch (RequestFailure error) {
            checkCanceled();
            throw error;
        } catch (Exception error) {
            checkCanceled();
            // Network exceptions can contain the device JWT from the PIN request URL.
            throw new RequestFailure(phase, 0);
        } finally {
            if (timeout != null) timeout.cancel(false);
            if (cancellation != null) cancellation.setOnCancelListener(null);
            if (request != null) request.disconnect();
        }
    }

    static final class TransportUnavailable extends RequestFailure {
        TransportUnavailable(String message) { super(message); }
    }

    static class RequestFailure extends IOException {
        RequestFailure(String message) { super(message); }
        RequestFailure(String phase, int status) {
            super(
                    phase
                            + (status == 0
                                    ? " could not finish. Check the connection and retry."
                                    : " returned HTTP "
                                            + status
                                            + ". Retry without clearing your saved connection."));
        }
    }

    static final class AuthorizationRequired extends RequestFailure {
        AuthorizationRequired(String phase, int status) {
            super(phase, status);
        }
    }
}
