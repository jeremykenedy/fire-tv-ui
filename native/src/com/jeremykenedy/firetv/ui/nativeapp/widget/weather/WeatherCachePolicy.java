package com.jeremykenedy.firetv.ui.nativeapp.widget.weather;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

final class WeatherCachePolicy {
    final boolean noStore;
    final boolean revalidate;
    final long freshUntil;
    WeatherCachePolicy(Map<String, String> headers, long now, long maxFreshMillis) {
        String control = headers.containsKey("cache-control") ? headers.get("cache-control").toLowerCase(Locale.ROOT) : "";
        boolean store = false, check = false; long seconds = -1;
        for (String raw : control.split(",")) {
            String part = raw.trim();
            if (part.equals("no-store")) store = true;
            if (part.equals("no-cache") || part.startsWith("no-cache=") || part.equals("must-revalidate")) check = true;
            if (part.startsWith("max-age=")) {
                try { seconds = Math.min(86400, Math.max(0, Long.parseLong(part.substring(8).replace("\"", "")))); }
                catch (NumberFormatException invalid) { seconds = 0; }
            }
        }
        noStore = store; revalidate = check;
        long date = date(headers.get("date"), now);
        long age = 0;
        try { age = Math.min(86400, Math.max(0, Long.parseLong(headers.get("age")))); } catch (RuntimeException ignored) { }
        long lifetime = seconds >= 0 ? seconds * 1000 : Math.max(0, date(headers.get("expires"), date) - date);
        long currentAge = Math.max(age * 1000, Math.max(0, now - date));
        freshUntil = now + (check && control.contains("no-cache") ? 0 : Math.max(0, Math.min(maxFreshMillis, lifetime - currentAge)));
    }
    static long date(String value, long fallback) {
        try { return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli(); }
        catch (RuntimeException invalid) { return fallback; }
    }
}
