package com.jeremykenedy.firetv.ui.nativeapp.widget.weather;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

final class WeatherHttp {
    static final String USER_AGENT = "FireTVUI-SourcePreview (https://github.com/jeremykenedy/fire-tv-ui)";
    interface Connections { HttpURLConnection open(URL url) throws IOException; }
    private final Connections connections;
    WeatherHttp() { this(new Connections() { @Override public HttpURLConnection open(URL url) throws IOException { return (HttpURLConnection) url.openConnection(); } }); }
    WeatherHttp(Connections connections) { this.connections = connections; }
    static final class Response {
        final int code; final String body; final Map<String, String> headers;
        Response(int code, String body, Map<String, String> headers) { this.code = code; this.body = body; this.headers = headers; }
    }
    Response get(String address, WeatherCache.Entry cached, WeatherRequest request) throws IOException {
        request.check();
        HttpURLConnection connection = connections.open(new URL(address));
        request.attach(connection);
        try {
            connection.setConnectTimeout(request.timeoutMillis()); connection.setReadTimeout(request.timeoutMillis());
            connection.setInstanceFollowRedirects(false); connection.setUseCaches(false);
            connection.setRequestProperty("User-Agent", USER_AGENT); connection.setRequestProperty("Accept", "application/geo+json");
            if (cached != null) {
                if (cached.headers.containsKey("etag")) connection.setRequestProperty("If-None-Match", cached.headers.get("etag"));
                if (cached.headers.containsKey("last-modified")) connection.setRequestProperty("If-Modified-Since", cached.headers.get("last-modified"));
            }
            int code = connection.getResponseCode(); request.check();
            Map<String, String> headers = new LinkedHashMap<>();
            for (String name : new String[]{"cache-control", "date", "age", "expires", "etag", "last-modified", "retry-after"}) {
                String value = connection.getHeaderField(name);
                if (value != null && value.length() <= 4096 && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) headers.put(name, value);
            }
            if (code != 200) return new Response(code, "", headers);
            int size = connection.getContentLength();
            if (size > WeatherCache.MAX_BODY) throw new IOException("Forecast response is too large.");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream body = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) {
                    request.timeoutMillis();
                    if (body.size() + count > WeatherCache.MAX_BODY) throw new IOException("Forecast response is too large.");
                    body.write(buffer, 0, count);
                }
                request.check(); return new Response(code, new String(body.toByteArray(), StandardCharsets.UTF_8), headers);
            }
        } finally { request.detach(connection); connection.disconnect(); }
    }
}
