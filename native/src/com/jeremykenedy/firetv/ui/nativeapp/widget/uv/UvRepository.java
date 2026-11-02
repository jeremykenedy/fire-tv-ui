package com.jeremykenedy.firetv.ui.nativeapp.widget.uv;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;

/** Small process-local cache and a shared per-ZIP request limit. No forecast is written to disk. */
final class UvRepository {
    static final long REFRESH = 3600000L;
    static final long MAX_STALE = 24 * REFRESH;
    enum State { READY, STALE, EMPTY, UNAVAILABLE }
    static final class Snapshot {
        final State state;
        final UvForecast forecast;
        final long fetchedAt;
        final String message;
        Snapshot(State state, UvForecast forecast, long fetchedAt, String message) {
            this.state = state; this.forecast = forecast; this.fetchedAt = fetchedAt; this.message = message;
        }
        Snapshot at(long now) {
            if (forecast != null && forecast.expired(fetchedAt, now))
                return new Snapshot(State.UNAVAILABLE, null, 0, "Saved forecast expired. EPA update unavailable.");
            return forecast != null && forecast.outdated(fetchedAt, now) && state == State.READY
                    ? new Snapshot(State.STALE, forecast, fetchedAt, "Older forecast") : this;
        }
    }
    private static final Map<String, Entry> CACHE = new LinkedHashMap<>();
    private static final class Entry { long attemptedAt; boolean attempted; boolean loading; Snapshot snapshot; }
    private final UvHttp http;
    UvRepository() { this(new UvHttp()); }
    UvRepository(UvHttp http) { this.http = http; }
    private static synchronized Entry entry(String zip) {
        Entry entry = CACHE.get(zip);
        if (entry == null) {
            if (CACHE.size() >= 4) CACHE.remove(CACHE.keySet().iterator().next());
            entry = new Entry(); CACHE.put(zip, entry);
        }
        return entry;
    }
    Snapshot load(String zip, UvRequest request, long now) {
        UvForecast.zip(zip);
        request.check();
        Entry entry = entry(zip);
        synchronized (entry) {
            if (entry.loading || (entry.attempted && now >= entry.attemptedAt && now - entry.attemptedAt < REFRESH))
                return entry.snapshot == null ? new Snapshot(State.UNAVAILABLE, null, 0, "Forecast unavailable. Try again later.") : entry.snapshot.at(now);
            entry.loading = true; entry.attempted = true; entry.attemptedAt = now;
        }
        try {
            UvHttp.Response response = http.fetch(zip, request);
            request.check();
            Snapshot result;
            if (response.code == 404 || response.code == 204)
                result = new Snapshot(State.EMPTY, null, 0, "No forecast available for ZIP " + zip + ".");
            else if (response.code == 200) {
                UvForecast forecast = UvForecast.parse(zip, response.body);
                result = new Snapshot(forecast.hours.isEmpty() ? State.EMPTY : State.READY, forecast, now,
                        forecast.hours.isEmpty() ? "No forecast hours available." : "EPA hourly forecast").at(now);
            } else result = failure(entry, "EPA forecast unavailable.", now);
            request.check();
            synchronized (entry) { entry.snapshot = response.noStore ? null : result; }
            return result;
        } catch (CancellationException canceled) { throw canceled; }
        catch (IOException | IllegalArgumentException unavailable) {
            request.check();
            Snapshot result = failure(entry, "EPA forecast unavailable.", now);
            synchronized (entry) { entry.snapshot = result; }
            return result;
        } finally {
            synchronized (entry) {
                entry.loading = false;
                if (request.wasCanceled()) entry.attempted = false;
            }
        }
    }
    private static Snapshot failure(Entry entry, String message, long now) {
        synchronized (entry) {
            if (entry.snapshot != null && entry.snapshot.forecast != null && !entry.snapshot.forecast.hours.isEmpty())
                return new Snapshot(State.STALE, entry.snapshot.forecast, entry.snapshot.fetchedAt, "Older forecast; update unavailable").at(now);
        }
        return new Snapshot(State.UNAVAILABLE, null, 0, message);
    }
}
