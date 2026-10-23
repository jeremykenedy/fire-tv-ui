package com.jeremykenedy.firetv.ui;

import android.content.Context;

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
                new JSONObject().put("jwk", keys.jwk()).put("strong", false));
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
                        null);
        String token = result.optString("authToken", "");
        return token.isEmpty() || "null".equals(token) ? null : token;
    }

    String refreshToken() throws Exception {
        String nonce = json(ACCOUNT + "/auth/nonce", "GET", null, null).getString("nonce");
        return json(
                        ACCOUNT + "/auth/token",
                        "POST",
                        null,
                        new JSONObject().put("jwt", keys.jwt(nonce)))
                .getString("auth_token");
    }

    JSONObject server(PlexConnection connection, String path) throws Exception {
        if (!path.startsWith("/") || path.startsWith("//"))
            throw new IOException("The Plex request is invalid.");
        return json(PlexValues.serverBase(connection.server) + path, "GET", connection.token, null);
    }

    byte[] artwork(PlexConnection connection, String path) throws Exception {
        if (!PlexValues.libraryPath(path))
            throw new IOException("The Plex artwork path is unavailable.");
        return request(
                PlexValues.serverBase(connection.server) + path,
                "GET",
                connection.token,
                null,
                4 * 1024 * 1024);
    }

    private JSONObject json(String url, String method, String token, JSONObject body)
            throws Exception {
        return new JSONObject(
                new String(
                        request(url, method, token, body, 8 * 1024 * 1024),
                        StandardCharsets.UTF_8));
    }

    private byte[] request(String url, String method, String token, JSONObject body, int limit)
            throws Exception {
        HttpURLConnection request = null;
        try {
            request = (HttpURLConnection) new URL(url).openConnection();
            request.setInstanceFollowRedirects(false);
            request.setConnectTimeout(10000);
            request.setReadTimeout(15000);
            request.setRequestMethod(method);
            request.setRequestProperty("Accept", "application/json");
            request.setRequestProperty("X-Plex-Client-Identifier", keys.clientId());
            request.setRequestProperty("X-Plex-Product", "Fire TV UI");
            request.setRequestProperty("X-Plex-Version", "1.5.0");
            request.setRequestProperty("X-Plex-Platform", "Android");
            request.setRequestProperty("X-Plex-Device", "Fire TV");
            request.setRequestProperty("X-Plex-Container-Start", "0");
            request.setRequestProperty("X-Plex-Container-Size", "12");
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
            if (status == 401 || status == 498) throw new AuthorizationRequired();
            if (status < 200 || status >= 300)
                throw new IOException(
                        "Plex returned HTTP " + status + ". Keep the saved connection and retry.");
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
        } catch (AuthorizationRequired error) {
            throw error;
        } catch (Exception error) {
            // Network exceptions can contain the device JWT from the PIN request URL.
            throw new IOException(
                    "Plex could not complete this request. Check the server and connection, then"
                        + " retry.");
        } finally {
            if (request != null) request.disconnect();
        }
    }

    static final class AuthorizationRequired extends IOException {
        AuthorizationRequired() {
            super("Plex authorization needs to be refreshed or connected again.");
        }
    }
}
