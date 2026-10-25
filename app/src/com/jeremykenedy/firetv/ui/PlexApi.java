package com.jeremykenedy.firetv.ui;

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

    PlexApi(Context context, long request) {
        keys = new PlexKeys(context, request);
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
                url, method, token, body, limit, phase, pageSize, "Artwork".equals(phase) ? 3 : 0);
    }

    private byte[] request(
            String url,
            String method,
            String token,
            JSONObject body,
            int limit,
            String phase,
            int pageSize,
            int redirects)
            throws Exception {
        HttpURLConnection request = null;
        try {
            request = (HttpURLConnection) new URL(url).openConnection();
            request.setInstanceFollowRedirects(false);
            request.setConnectTimeout(10000);
            request.setReadTimeout(15000);
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
            int status = request.getResponseCode();
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
                return request(
                        next, "GET", credential, null, limit, phase, pageSize, redirects - 1);
            }
            if ((status == 401 || status == 498) && (!artwork || token != null))
                throw new AuthorizationRequired(phase, status);
            if (status < 200 || status >= 300) throw new RequestFailure(phase, status);
            try (InputStream input = request.getInputStream();
                    ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > limit)
                        throw new IOException("The Plex response exceeds the supported size.");
                    output.write(buffer, 0, count);
                }
                return output.toByteArray();
            }
        } catch (RequestFailure error) {
            throw error;
        } catch (Exception error) {
            // Network exceptions can contain the device JWT from the PIN request URL.
            throw new RequestFailure(phase, 0);
        } finally {
            if (request != null) request.disconnect();
        }
    }

    static class RequestFailure extends IOException {
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
