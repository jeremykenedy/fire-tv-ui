package com.jeremykenedy.firetv.ui.nativeapp.widget.weather;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import org.json.JSONObject;

final class WeatherRepository {
    private final WeatherCache cache;
    private final WeatherHttp http;
    WeatherRepository(File directory) { this(directory, new WeatherHttp()); }
    WeatherRepository(File directory, WeatherHttp http) { cache = new WeatherCache(directory); this.http = http; }
    private static final class Document {
        final String url; final WeatherCache.Entry entry; final boolean stale; final long retryAt;
        Document(String url, WeatherCache.Entry entry, boolean stale) { this(url, entry, stale, 0); }
        Document(String url, WeatherCache.Entry entry, boolean stale, long retryAt) { this.url = url; this.entry = entry; this.stale = stale; this.retryAt = retryAt; }
    }
    private static final class ServiceFailure extends IOException {
        final int code; final long retryAt;
        ServiceFailure(int code, long retryAt) { super("Forecast service response " + code); this.code = code; this.retryAt = retryAt; }
    }
    WeatherForecast read(WeatherOptions options, long now, WeatherRequest request) {
        if (!options.configured) return WeatherForecast.state(WeatherForecast.State.SETUP_REQUIRED,
                "Choose a supported U.S. location in weather settings. No device location permission is used.", now);
        try {
            String pointUrl = "https://api.weather.gov/points/" + options.latitude() + "," + options.longitude();
            Document points = fetch(pointUrl, 86400000, now, request);
            JSONObject properties = new JSONObject(points.entry.body).getJSONObject("properties");
            java.time.ZoneId.of(properties.getString("timeZone"));
            if (!points.stale) cache.write(points.url, points.entry);
            String hourly = properties.optString("forecastHourly", "");
            if (!hourly.matches("https://api\\.weather\\.gov/gridpoints/[A-Z0-9]{2,5}/[0-9]{1,5},[0-9]{1,5}/forecast/hourly"))
                return WeatherForecast.state(WeatherForecast.State.UNAVAILABLE,
                        "NWS does not supply an hourly forecast for this location. Select a supported U.S. forecast location; coastal marine and global coverage are not provided here.", now);
            String forecastUrl = hourly + "?units=" + options.units();
            Document forecast;
            if (points.retryAt > now) {
                WeatherCache.Entry previous = cache.read(forecastUrl, 3600000);
                if (previous == null || (!previous.fresh(now) && !previous.staleAllowed(now))) throw new ServiceFailure(503, points.retryAt);
                forecast = new Document(forecastUrl, previous, !previous.fresh(now), points.retryAt);
            } else forecast = fetch(forecastUrl, 3600000, now, request);
            boolean stale = points.stale || forecast.stale;
            WeatherForecast result = WeatherForecast.parse(points.entry.body, forecast.entry.body, options,
                    forecast.entry.savedAt, now, Math.max(Math.min(points.entry.freshUntil, forecast.entry.freshUntil), Math.max(points.retryAt, forecast.retryAt)), stale);
            request.check();
            if (!forecast.stale) cache.write(forecast.url, forecast.entry);
            request.check(); return result;
        } catch (CancellationException canceled) { throw canceled; }
        catch (ServiceFailure failure) {
            String message = failure.code == 404 || failure.code == 400
                    ? "NWS hourly forecasts are unavailable for this location. Choose a supported U.S. forecast location."
                    : failure.code == 429 ? "NWS rate limit reached. Forecast refresh will retry later."
                    : "NWS forecasts are unavailable right now. Refresh will retry later.";
            return new WeatherForecast(WeatherForecast.State.UNAVAILABLE, message, "UTC", 0, 0,
                    failure.retryAt, false, java.util.Collections.<WeatherForecast.Hour>emptyList());
        } catch (Exception unavailable) {
            request.check();
            return WeatherForecast.state(WeatherForecast.State.UNAVAILABLE,
                    "Hourly forecast unavailable. Check the connection or location and try again later. Saved location choices have been kept.", now);
        }
    }
    private Document fetch(String url, long maxFresh, long now, WeatherRequest request) throws IOException {
        request.check(); WeatherCache.Entry previous = cache.read(url, maxFresh);
        if (previous != null && previous.fresh(now)) return new Document(url, previous, false);
        try {
            WeatherHttp.Response response = http.get(url, previous, request); request.check();
            if (response.code == 304 && previous != null) {
                Map<String, String> headers = new LinkedHashMap<>(previous.headers);
                headers.remove("date"); headers.remove("age"); headers.putAll(response.headers);
                return new Document(url, new WeatherCache.Entry(previous.body, headers, now, maxFresh), false);
            }
            if (response.code != 200) throw new ServiceFailure(response.code, retryAt(response.headers.get("retry-after"), now));
            WeatherCache.Entry fresh = new WeatherCache.Entry(response.body, response.headers, now, maxFresh);
            if (new WeatherCachePolicy(response.headers, now, maxFresh).noStore) cache.remove(url);
            return new Document(url, fresh, false);
        } catch (IOException unavailable) {
            request.check();
            if (previous != null && previous.staleAllowed(now)) return new Document(url, previous, true, unavailable instanceof ServiceFailure ? ((ServiceFailure) unavailable).retryAt : now + 60000);
            throw unavailable;
        }
    }
    static long retryAt(String value, long now) {
        long delay;
        try { delay = Math.multiplyExact(Long.parseLong(value), 1000); }
        catch (RuntimeException invalid) { delay = WeatherCachePolicy.date(value, now + 60000) - now; }
        return now + Math.max(60000, Math.min(3600000, delay));
    }
}
