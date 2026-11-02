package com.jeremykenedy.firetv.ui.nativeapp.widget.radar;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

final class RadarHttp {
    interface Connections { HttpURLConnection open(URL address) throws IOException; }
    private final Connections connections;
    RadarHttp() { this(new Connections() { @Override public HttpURLConnection open(URL address) throws IOException { return (HttpURLConnection) address.openConnection(); } }); }
    RadarHttp(Connections connections) { this.connections = connections; }

    RadarCache.Entry get(RadarSource source, RadarCache.Entry cached, long now, RadarRequest request) throws IOException {
        request.check();
        HttpURLConnection connection = connections.open(new URL(source.imageUrl()));
        request.attach(connection);
        try {
            connection.setConnectTimeout(request.timeoutMillis()); connection.setReadTimeout(request.timeoutMillis());
            connection.setInstanceFollowRedirects(false); connection.setUseCaches(false);
            connection.setRequestProperty("User-Agent", "FireTVUI-SourcePreview (https://github.com/jeremykenedy/fire-tv-ui)");
            connection.setRequestProperty("Accept", "image/gif");
            if (cached != null && !cached.etag.isEmpty()) connection.setRequestProperty("If-None-Match", cached.etag);
            if (cached != null && cached.modifiedAt > 0) connection.setIfModifiedSince(cached.modifiedAt);
            int code = connection.getResponseCode(); request.check();
            if (code == 304 && cached != null) return new RadarCache.Entry(source, cached.bytes, now, cached.modifiedAt, cached.etag, freshUntil(connection, now), cacheable(connection));
            if (code != 200) throw new IOException("Radar source unavailable");
            String type = connection.getContentType();
            if (type == null || !type.toLowerCase(java.util.Locale.US).startsWith("image/gif")) throw new IOException("Unexpected radar format");
            if (connection.getContentLengthLong() > RadarCache.MAX_BYTES) throw new IOException("Radar image is too large");
            byte[] bytes;
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) {
                    request.timeoutMillis();
                    if (output.size() + count > RadarCache.MAX_BYTES) throw new IOException("Radar image is too large");
                    output.write(buffer, 0, count);
                }
                bytes = output.toByteArray();
            }
            request.check();
            if (bytes.length < 10 || bytes[0] != 'G' || bytes[1] != 'I' || bytes[2] != 'F') throw new IOException("Invalid radar image");
            int width = (bytes[6] & 255) | (bytes[7] & 255) << 8, height = (bytes[8] & 255) | (bytes[9] & 255) << 8;
            if (width <= 0 || height <= 0 || width > 1200 || height > 1200 || (long) width * height > 720000) throw new IOException("Radar dimensions exceed this widget's limit");
            return new RadarCache.Entry(source, bytes, now, connection.getLastModified(), connection.getHeaderField("ETag"), freshUntil(connection, now), cacheable(connection));
        } finally { request.detach(connection); connection.disconnect(); }
    }
    private static boolean cacheable(HttpURLConnection connection) {
        String control = connection.getHeaderField("Cache-Control");
        return control == null || !control.toLowerCase(java.util.Locale.US).contains("no-store");
    }
    private static long freshUntil(HttpURLConnection connection, long now) {
        String control = connection.getHeaderField("Cache-Control");
        long seconds = 300;
        if (control != null) {
            for (String directive : control.toLowerCase(java.util.Locale.US).split(",")) {
                String value = directive.trim();
                if (value.equals("no-cache") || value.equals("no-store")) return now;
                if (value.startsWith("max-age=")) {
                    try { seconds = Math.max(0, Math.min(300, Long.parseLong(value.substring(8).replace("\"", "")))); }
                    catch (NumberFormatException invalid) { return now; }
                }
            }
        } else if (connection.getExpiration() > 0) {
            seconds = Math.max(0, Math.min(300, (connection.getExpiration() - now) / 1000));
        }
        String age = connection.getHeaderField("Age");
        if (age != null) {
            try { seconds = Math.max(0, seconds - Math.max(0, Long.parseLong(age))); }
            catch (NumberFormatException invalid) { return now; }
        }
        return now + seconds * 1000;
    }

}
