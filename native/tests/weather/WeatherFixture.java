package com.jeremykenedy.firetv.ui.nativeapp.widget.weather;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

final class WeatherFixture implements AutoCloseable {
    final long origin = System.currentTimeMillis();
    volatile long responseAt = origin;
    volatile String mode = "normal";
    volatile String cacheControl = "public, max-age=2";
    volatile boolean unsupported;
    volatile boolean unsafeEndpoint;
    final AtomicInteger requests = new AtomicInteger();
    final AtomicInteger conditionals = new AtomicInteger();
    final CountDownLatch stalled = new CountDownLatch(1);
    final CountDownLatch disconnected = new CountDownLatch(1);
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final ServerSocket server = new ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"));
    private volatile Socket active;
    private volatile boolean closed;
    private final Thread worker;
    WeatherFixture() throws Exception {
        worker = new Thread(new Runnable() { @Override public void run() {
            while (!closed) try (Socket socket = server.accept()) { active = socket; serve(socket); }
            catch (Throwable problem) { if (!closed) failure.compareAndSet(null, problem); }
            finally { active = null; }
        } }, "weather-fixture"); worker.setDaemon(true); worker.start();
    }
    WeatherHttp http() {
        return new WeatherHttp(new WeatherHttp.Connections() { @Override public HttpURLConnection open(URL url) throws java.io.IOException {
            String query = url.getQuery() == null ? "" : "?" + url.getQuery();
            return (HttpURLConnection) new URL("http://127.0.0.1:" + server.getLocalPort() + url.getPath() + query).openConnection();
        } });
    }
    WeatherRepository repository(File directory) { return new WeatherRepository(directory, http()); }
    void assertHealthy() { if (failure.get() != null) throw new AssertionError("Weather fixture failed", failure.get()); }
    private void serve(Socket socket) throws Exception {
        socket.setSoTimeout(8000); InputStream input = socket.getInputStream(); ByteArrayOutputStream headers = new ByteArrayOutputStream();
        int state = 0;
        while (headers.size() < 32768) {
            int value = input.read(); if (value < 0) throw new java.io.IOException("Incomplete request"); headers.write(value);
            if ((state == 0 || state == 2) && value == '\r') state++;
            else if ((state == 1 || state == 3) && value == '\n') state++; else state = 0;
            if (state == 4) break;
        }
        String request = new String(headers.toByteArray(), StandardCharsets.US_ASCII);
        if (!request.contains("User-Agent: " + WeatherHttp.USER_AGENT) && !request.toLowerCase(Locale.ROOT).contains("user-agent: " + WeatherHttp.USER_AGENT.toLowerCase(Locale.ROOT)))
            throw new AssertionError("Identifying User-Agent missing");
        if (!request.toLowerCase(Locale.ROOT).contains("accept: application/geo+json")) throw new AssertionError("GeoJSON Accept missing");
        requests.incrementAndGet();
        if ("stall".equals(mode)) {
            stalled.countDown(); try { while (input.read() != -1) { } } catch (java.io.IOException closedRequest) { }
            disconnected.countDown(); return;
        }
        boolean points = request.startsWith("GET /points/");
        if (!points && !request.startsWith("GET /gridpoints/TOP/32,81/forecast/hourly?units=")) throw new AssertionError("Unexpected API request");
        int code = "error".equals(mode) ? 503 : "rate-limit".equals(mode) ? 429
                : request.toLowerCase(Locale.ROOT).contains("if-none-match: \"weather-fixture\"") ? 304 : 200;
        if (code == 304) conditionals.incrementAndGet();
        String body = code != 200 ? "" : points ? points(unsupported) : hourly(origin, request.contains("units=si"));
        if (points && unsafeEndpoint) body = body.replace("https://api.weather.gov/gridpoints/TOP/32,81/forecast/hourly", "https://other.example/forecast");
        int length = "oversize".equals(mode) && !points ? WeatherCache.MAX_BODY + 1 : body.getBytes(StandardCharsets.UTF_8).length;
        String response = "HTTP/1.1 " + code + " Fixture\r\nContent-Type: application/geo+json\r\nContent-Length: " + length
                + "\r\nCache-Control: " + cacheControl + "\r\nETag: \"weather-fixture\"\r\nLast-Modified: "
                + DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(origin).atZone(ZoneOffset.UTC))
                + "\r\nDate: " + DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(responseAt).atZone(ZoneOffset.UTC))
                + (code == 429 ? "\r\nRetry-After: 300" : "") + "\r\nConnection: close\r\n\r\n";
        OutputStream output = socket.getOutputStream(); output.write(response.getBytes(StandardCharsets.US_ASCII));
        if (length <= WeatherCache.MAX_BODY) output.write(body.getBytes(StandardCharsets.UTF_8)); output.flush();
    }
    static String points(boolean unsupported) {
        return "{\"properties\":{\"timeZone\":\"America/Chicago\"" + (unsupported ? "" : ",\"forecastHourly\":\"https://api.weather.gov/gridpoints/TOP/32,81/forecast/hourly\"") + "}}";
    }
    static String hourly(long now, boolean metric) {
        long start = now - now % 3600000;
        StringBuilder periods = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            if (i > 0) periods.append(',');
            periods.append("{\"startTime\":\"").append(Instant.ofEpochMilli(start + i * 3600000L))
                    .append("\",\"endTime\":\"").append(Instant.ofEpochMilli(start + (i + 1) * 3600000L))
                    .append("\",\"temperature\":").append(metric ? 12 : 54).append(",\"temperatureUnit\":\"")
                    .append(metric ? "C" : "F").append("\",\"shortForecast\":\"Mostly cloudy\",\"windSpeed\":\"5 ")
                    .append(metric ? "km/h" : "mph").append("\",\"windDirection\":\"N\",\"probabilityOfPrecipitation\":{\"value\":null}}");
        }
        return "{\"properties\":{\"updateTime\":\"" + Instant.ofEpochMilli(now) + "\",\"periods\":[" + periods + "]}}";
    }
    @Override public void close() throws Exception { closed = true; server.close(); Socket socket = active; if (socket != null) socket.close(); worker.join(2000); }
}
