package com.jeremykenedy.firetv.ui.nativeapp.widget.weather;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

public final class WeatherTest {
    public static void main(String[] args) throws Exception {
        WeatherOptions options = WeatherOptions.location("Example", "39.7456", "-97.0892", false, false);
        check(!WeatherOptions.defaults().configured, "No default location or silent location lookup.");
        check(WeatherOptions.fromBackup(options.backupValues()).backupValues().equals(options.backupValues()), "Typed location/unit/time settings round trip.");
        for (String invalid : new String[]{"NaN", "Infinity", "91", "1e2", "1,2", "", "-90.00001"}) {
            try { WeatherOptions.location("A", invalid, "0", false, false); throw new AssertionError("Invalid latitude accepted"); } catch (IllegalArgumentException expected) { }
        }
        long now = System.currentTimeMillis();
        WeatherForecast parsed = WeatherForecast.parse(WeatherFixture.points(false), WeatherFixture.hourly(now, false), options, now, now, now + 60000, false);
        check(parsed.state == WeatherForecast.State.READY && parsed.hours.size() == 12 && parsed.hours.get(0).precipitation == null, "Actual hourly values retain missing precipitation rather than inventing zero.");
        check(parsed.display(options, now, 4, true).contains("America/Chicago") && parsed.display(options, now, 4, true).contains("Updated"), "Location timezone and original product timestamp are visible.");
        JSONObject noTemperature = new JSONObject(WeatherFixture.hourly(now, false));
        noTemperature.getJSONObject("properties").getJSONArray("periods").getJSONObject(0).put("temperature", JSONObject.NULL);
        check(WeatherForecast.parse(WeatherFixture.points(false), noTemperature.toString(), options, now, now, now + 1, false).hours.get(0).temperature == null, "Missing temperature remains unavailable.");
        JSONObject empty = new JSONObject(WeatherFixture.hourly(now, false)); empty.getJSONObject("properties").put("periods", new org.json.JSONArray());
        check(WeatherForecast.parse(WeatherFixture.points(false), empty.toString(), options, now, now, now + 1, false).state == WeatherForecast.State.EMPTY, "Empty periods have an explicit state.");
        long fallback = java.time.Instant.parse("2026-11-01T07:30:00Z").toEpochMilli();
        WeatherForecast daylight = WeatherForecast.parse(WeatherFixture.points(false).replace("America/Chicago", "America/Los_Angeles"), WeatherFixture.hourly(fallback, false), options, fallback, fallback, fallback + 1, false);
        String repeated = daylight.display(options, fallback, 4, true);
        check(repeated.contains("PDT") && repeated.contains("PST"), "Repeated daylight-saving hours retain distinct offset labels.");
        try { WeatherForecast.parse(WeatherFixture.points(false), WeatherFixture.hourly(now, true), options, now, now, now + 1, false); throw new AssertionError("Mismatched temperature units accepted"); }
        catch (IllegalArgumentException expected) { }
        Map<String,String> headers = new HashMap<>(); headers.put("cache-control", "public, max-age=3600"); headers.put("age", "3500");
        check(new WeatherCachePolicy(headers, now, 3600000).freshUntil == now + 100000, "Age reduces provider freshness.");
        headers.put("cache-control", "no-cache, max-age=3600");
        check(new WeatherCachePolicy(headers, now, 3600000).freshUntil == now, "No-cache requires validation.");
        try (WeatherFixture fixture = new WeatherFixture()) {
            File directory = Files.createTempDirectory("weather-cache-").toFile(); WeatherRepository source = fixture.repository(directory);
            WeatherForecast first = source.read(options, fixture.origin, new WeatherRequest());
            check(first.state == WeatherForecast.State.READY && fixture.requests.get() == 2, "Real HTTP path resolves points and hourly data with required headers.");
            source.read(options, fixture.origin + 1000, new WeatherRequest()); check(fixture.requests.get() == 2, "Fresh cache avoids network requests.");
            fixture.responseAt = fixture.origin + 4000;
            WeatherForecast revalidated = source.read(options, fixture.responseAt, new WeatherRequest());
            check(revalidated.state == WeatherForecast.State.READY && !revalidated.stale && fixture.conditionals.get() == 2, "Expired cache sends validators and accepts 304 without losing source timestamps.");
            fixture.mode = "error";
            WeatherForecast stale = source.read(options, fixture.origin + 8000, new WeatherRequest());
            check(stale.state == WeatherForecast.State.READY && stale.stale && stale.display(options, fixture.origin + 8000, 4, false).contains("Stale"), "Offline permitted cache is visibly stale.");
            fixture.mode = "rate-limit";
            WeatherForecast limited = source.read(options, fixture.origin + 8000, new WeatherRequest());
            check(limited.stale && limited.refreshAt >= fixture.origin + 308000, "Stale display preserves Retry-After backoff.");
            fixture.assertHealthy(); delete(directory);
        }
        for (String control : new String[]{"no-store", "no-cache, max-age=2", "must-revalidate, max-age=2"}) try (WeatherFixture fixture = new WeatherFixture()) {
            fixture.cacheControl = control; File directory = Files.createTempDirectory("weather-policy-").toFile(); WeatherRepository source = fixture.repository(directory);
            check(source.read(options, fixture.origin, new WeatherRequest()).state == WeatherForecast.State.READY, "Policy fixture must load first.");
            fixture.mode = "error";
            check(source.read(options, fixture.origin + 8000, new WeatherRequest()).state == WeatherForecast.State.UNAVAILABLE, "Restrictive cache policy cannot be shown stale: " + control);
            if (control.equals("no-store")) check(directory.listFiles().length == 0, "No-store persists no forecast bytes.");
            fixture.assertHealthy(); delete(directory);
        }
        try (WeatherFixture fixture = new WeatherFixture()) {
            fixture.unsupported = true; File directory = Files.createTempDirectory("weather-unsupported-").toFile();
            check(fixture.repository(directory).read(options, fixture.origin, new WeatherRequest()).state == WeatherForecast.State.UNAVAILABLE && fixture.requests.get() == 1, "Absent hourly endpoint is unsupported coverage, not a global fallback."); delete(directory);
        }
        try (WeatherFixture fixture = new WeatherFixture()) {
            fixture.unsafeEndpoint = true; File directory = Files.createTempDirectory("weather-endpoint-").toFile();
            check(fixture.repository(directory).read(options, fixture.origin, new WeatherRequest()).state == WeatherForecast.State.UNAVAILABLE && fixture.requests.get() == 1, "Linked forecast URLs cannot change the trusted HTTPS source."); delete(directory);
        }
        try (WeatherFixture fixture = new WeatherFixture()) {
            fixture.mode = "oversize"; File directory = Files.createTempDirectory("weather-large-").toFile();
            check(fixture.repository(directory).read(options, fixture.origin, new WeatherRequest()).state == WeatherForecast.State.UNAVAILABLE, "Oversized responses are rejected."); delete(directory);
        }
        try (WeatherFixture fixture = new WeatherFixture()) {
            fixture.mode = "stall"; final WeatherRequest request = new WeatherRequest(); final boolean[] canceled = new boolean[1];
            final File directory = Files.createTempDirectory("weather-cancel-").toFile();
            Thread reader = new Thread(new Runnable() { @Override public void run() {
                try { fixture.repository(directory).read(options, fixture.origin, request); } catch (CancellationException expected) { canceled[0] = true; }
            } }); reader.start(); check(fixture.stalled.await(2, TimeUnit.SECONDS), "Stalled request must begin.");
            long start = System.nanoTime(); request.cancel(); check(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 250, "Cancellation must not wait for socket teardown.");
            reader.join(3000); check(!reader.isAlive() && canceled[0], "Canceled reads must exit without a stale result callback."); delete(directory);
        }
        check(WeatherRepository.retryAt("300", now) == now + 300000 && WeatherRepository.retryAt("0", now) == now + 60000, "Retry-After uses bounded service-friendly delays.");
        System.out.println("Weather manual location, units/timezone, data states, HTTP headers, cache/304/stale policy, Retry-After, size bounds and cancellation passed.");
    }
    private static void delete(File directory) { File[] files = directory.listFiles(); if (files != null) for (File file : files) file.delete(); directory.delete(); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
