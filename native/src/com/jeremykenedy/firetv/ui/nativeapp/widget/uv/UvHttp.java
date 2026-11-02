package com.jeremykenedy.firetv.ui.nativeapp.widget.uv;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class UvHttp {
    interface Connections { HttpURLConnection open(URL url) throws IOException; }
    private final Connections connections;
    UvHttp() { this(new Connections() {
        @Override public HttpURLConnection open(URL url) throws IOException { return (HttpURLConnection) url.openConnection(); }
    }); }
    UvHttp(Connections connections) { this.connections = connections; }
    static final class Response {
        final int code; final String body; final boolean noStore;
        Response(int code, String body, boolean noStore) { this.code = code; this.body = body; this.noStore = noStore; }
    }
    Response fetch(String zip, UvRequest request) throws IOException {
        String endpoint = "https://data.epa.gov/dmapservice/getEnvirofactsUVHOURLY/ZIP/" + UvForecast.zip(zip) + "/JSON";
        request.check();
        HttpURLConnection connection = connections.open(new URL(endpoint));
        request.attach(connection);
        try {
            connection.setConnectTimeout(request.timeoutMillis()); connection.setReadTimeout(request.timeoutMillis());
            connection.setInstanceFollowRedirects(false); connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "FireTVUI-SourcePreview (https://github.com/jeremykenedy/fire-tv-ui)");
            int code = connection.getResponseCode(); request.check();
            String cache = connection.getHeaderField("Cache-Control");
            boolean noStore = cache != null && cache.toLowerCase(java.util.Locale.ROOT).contains("no-store");
            if (code != 200) return new Response(code, "", noStore);
            if (connection.getContentLength() > UvForecast.MAX_BODY) throw new IOException("UV forecast is too large.");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream body = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                while (true) {
                    connection.setReadTimeout(request.timeoutMillis());
                    int count = input.read(buffer);
                    if (count == -1) break;
                    request.timeoutMillis();
                    if (body.size() + count > UvForecast.MAX_BODY) throw new IOException("UV forecast is too large.");
                    body.write(buffer, 0, count);
                }
                request.check();
                return new Response(code, new String(body.toByteArray(), StandardCharsets.UTF_8), noStore);
            }
        } finally { request.detach(connection); connection.disconnect(); }
    }
}
