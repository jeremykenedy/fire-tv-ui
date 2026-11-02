package com.jeremykenedy.firetv.ui.nativeapp.widget.uv;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class UvTest {
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    static void invalid(Runnable action) {
        try { action.run(); throw new AssertionError("Invalid input accepted"); } catch (IllegalArgumentException expected) { }
    }
    static final class Fake extends HttpURLConnection {
        final String body; final int code; boolean disconnected; String cache = "";
        final CountDownLatch closed = new CountDownLatch(1);
        Fake(URL url, String body, int code) { super(url); this.body = body; this.code = code; }
        @Override public int getResponseCode() { return code; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)); }
        @Override public String getHeaderField(String name) { return name.equals("Cache-Control") ? cache : null; }
        @Override public void connect() { }
        @Override public void disconnect() { disconnected = true; closed.countDown(); }
        @Override public boolean usingProxy() { return false; }
    }
    public static void main(String[] args) throws Exception {
        final String historical = new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8);
        UvForecast data = UvForecast.parse("97201", historical);
        check(data.hours.size() == 9, "Historical captured EPA sample has nine rows");
        check(data.hours.get(0).localTime.toString().equals("2026-11-01T07:00"), "ZIP local wall time retained across DST date");
        check(data.hours.get(8).localTime.toString().equals("2026-11-01T15:00"), "12 PM and PM hours parsed correctly");
        check(data.hours.get(0).index == 0 && data.hours.get(4).index == 2, "Zero UV is a real forecast, not missing data");
        check(UvForecast.zip("00501").equals("00501"), "Leading zero ZIP preserved");
        invalid(() -> UvForecast.zip("97201/JSON"));
        invalid(() -> UvForecast.zip("9720"));
        invalid(() -> UvForecast.parse("00501", historical));
        invalid(() -> UvForecast.parse("97201", historical.replace("Nov/01/2026 07 AM", "Nov/31/2026 07 AM")));
        invalid(() -> UvForecast.parse("97201", historical.replace("\"UV_VALUE\": 0", "\"UV_VALUE\": null")));
        invalid(() -> UvForecast.parse("97201", historical.replace("\"UV_VALUE\": 0", "\"UV_VALUE\": -1")));
        invalid(() -> UvForecast.parse("97201", historical.replace("08 AM", "07 AM")));
        check(UvForecast.parse("97201", "[]").hours.isEmpty(), "Empty response is explicit");
        long now = Instant.parse("2026-11-01T16:00:00Z").toEpochMilli();
        check(!data.outdated(now, now), "Fetched schedule is dated without assuming TV zone");
        check(data.outdated(now, now + 7 * 3600000L), "Old retrieval is stale");
        check(data.outdated(now, now - 1), "Clock rollback does not make cache fresh");
        check(data.outdated(now + 3 * 86400000L, now + 3 * 86400000L), "Old source date stays stale even after a fresh fetch");
        check(data.expired(now + 3 * 86400000L, now + 3 * 86400000L), "Old source schedule is withheld even after a fresh retrieval");
        final int[] calls = new int[1];
        final Fake[] connection = new Fake[1];
        UvHttp http = new UvHttp(new UvHttp.Connections() {
            @Override public HttpURLConnection open(URL url) {
                calls[0]++;
                check(url.toString().equals("https://data.epa.gov/dmapservice/getEnvirofactsUVHOURLY/ZIP/97201/JSON"), "Only fixed EPA URL receives chosen ZIP");
                connection[0] = new Fake(url, historical, calls[0] == 1 ? 200 : 503);
                return connection[0];
            }
        });
        UvRepository repository = new UvRepository(http);
        check(repository.load("97201", new UvRequest(), now).state == UvRepository.State.READY, "Actual sample loads");
        check(connection[0].disconnected, "Successful connection closed");
        repository.load("97201", new UvRequest(), now + 1000);
        check(calls[0] == 1, "Rapid refresh shares per-ZIP throttle");
        check(repository.load("97201", new UvRequest(), now + UvRepository.REFRESH).state == UvRepository.State.STALE, "Server failure labels older schedule stale");
        check(calls[0] == 2, "One retry after interval");
        UvRepository.Snapshot expired = repository.load("97201", new UvRequest(), now + UvRepository.MAX_STALE);
        check(expired.state == UvRepository.State.UNAVAILABLE && expired.forecast == null,
                "Offline schedule is withheld after one day rather than retained indefinitely");
        check(repository.load("97201", new UvRequest(), now + UvRepository.MAX_STALE + 1000).forecast == null,
                "Throttle cannot resurrect an expired schedule");
        UvRepository missing = new UvRepository(new UvHttp(new UvHttp.Connections() {
            @Override public HttpURLConnection open(URL url) { return new Fake(url, "", 404); }
        }));
        check(missing.load("20050", new UvRequest(), now).state == UvRepository.State.EMPTY, "404 means no forecast, not invented UV zero");
        UvRequest canceled = new UvRequest(); canceled.cancel();
        try { repository.load("97201", canceled, now); throw new AssertionError("Canceled read proceeded"); }
        catch (CancellationException expected) { }
        UvRequest active = new UvRequest();
        Fake socket = new Fake(new URL("https://data.epa.gov/"), "", 200);
        active.attach(socket); active.cancel();
        check(socket.closed.await(2, TimeUnit.SECONDS), "Cancellation disconnects attached connection off the UI thread");
        UvRepository noStore = new UvRepository(new UvHttp(new UvHttp.Connections() {
            @Override public HttpURLConnection open(URL url) {
                Fake response = new Fake(url, historical.replace("97201", "00501"), 200);
                response.cache = "no-store"; return response;
            }
        }));
        check(noStore.load("00501", new UvRequest(), now).forecast != null, "No-store response can be displayed immediately");
        check(noStore.load("00501", new UvRequest(), now + 1000).forecast == null, "No-store forecast is not retained in cache");
        UvHttp oversized = new UvHttp(new UvHttp.Connections() {
            @Override public HttpURLConnection open(URL url) { return new Fake(url, new String(new char[UvForecast.MAX_BODY + 1]), 200); }
        });
        try { oversized.fetch("00501", new UvRequest()); throw new AssertionError("Oversized response accepted"); }
        catch (IOException expected) { }
        System.out.println("UV: historical EPA parsing, local date/DST, ZIP/zero/missing validation, stale bounds, throttle, 404, cancellation and response-size checks passed.");
    }
}
