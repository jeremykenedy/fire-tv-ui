package com.jeremykenedy.firetv.ui.nativeapp.widget.uv;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** EPA DATE_TIME is a ZIP-local wall time, not an instant or the TV's time zone. */
public final class UvForecast {
    public static final int MAX_ROWS = 72;
    public static final int MAX_BODY = 65536;
    private static final DateTimeFormatter INPUT = new DateTimeFormatterBuilder().parseCaseInsensitive()
            .appendPattern("MMM/dd/uuuu hh a").parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
            .toFormatter(Locale.US).withResolverStyle(ResolverStyle.STRICT);
    public final String zip;
    public final List<Hour> hours;
    public static final class Hour {
        public final LocalDateTime localTime;
        public final double index;
        private Hour(LocalDateTime localTime, double index) { this.localTime = localTime; this.index = index; }
    }
    private UvForecast(String zip, List<Hour> hours) {
        this.zip = zip; this.hours = Collections.unmodifiableList(hours);
    }
    public static String zip(String value) {
        if (value == null || !value.matches("[0-9]{5}")) throw new IllegalArgumentException("Enter a five-digit U.S. ZIP code.");
        return value;
    }
    public static UvForecast parse(String zip, String body) {
        zip(zip);
        if (body == null || body.length() > MAX_BODY) throw new IllegalArgumentException("UV forecast is too large.");
        try {
            JSONArray rows = new JSONArray(body);
            if (rows.length() > MAX_ROWS) throw new IllegalArgumentException("Too many UV forecast hours.");
            List<Hour> hours = new ArrayList<>();
            Set<LocalDateTime> seen = new HashSet<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                Object returnedZip = row.get("ZIP"), value = row.get("UV_VALUE");
                Object date = row.get("DATE_TIME");
                if (!(returnedZip instanceof String) || !zip.equals(returnedZip) || !(value instanceof Number) || !(date instanceof String))
                    throw new IllegalArgumentException("UV forecast does not match this ZIP.");
                double index = ((Number) value).doubleValue();
                if (!Double.isFinite(index) || index < 0 || index > 100) throw new IllegalArgumentException("Invalid UV Index value.");
                String rawDate = (String) date;
                if (rawDate.length() > 32) throw new IllegalArgumentException("Invalid UV forecast date.");
                LocalDateTime local = LocalDateTime.parse(rawDate, INPUT);
                if (local.getYear() < 2000 || local.getYear() > 2100 || !seen.add(local))
                    throw new IllegalArgumentException("Invalid or duplicate UV forecast hour.");
                hours.add(new Hour(local, index));
            }
            Collections.sort(hours, new Comparator<Hour>() {
                @Override public int compare(Hour left, Hour right) { return left.localTime.compareTo(right.localTime); }
            });
            return new UvForecast(zip, hours);
        } catch (JSONException | java.time.DateTimeException invalid) {
            throw new IllegalArgumentException("EPA UV forecast could not be read.", invalid);
        }
    }
    public boolean outdated(long fetchedAt, long now) {
        if (now < fetchedAt || now - fetchedAt > 6 * 3600000L) return true;
        if (hours.isEmpty()) return false;
        // No source offset is supplied. Use a conservative fourteen-hour buffer before declaring source times expired.
        long latestPossible = hours.get(hours.size() - 1).localTime.toInstant(ZoneOffset.UTC).toEpochMilli() + 14 * 3600000L;
        return now > latestPossible;
    }
    public boolean expired(long fetchedAt, long now) {
        if (now < fetchedAt || now - fetchedAt >= UvRepository.MAX_STALE) return true;
        if (hours.isEmpty()) return false;
        long latestPossible = hours.get(hours.size() - 1).localTime.toInstant(ZoneOffset.UTC).toEpochMilli() + 14 * 3600000L;
        return now - latestPossible >= UvRepository.MAX_STALE;
    }
    public static String value(double index, Locale locale) {
        return java.text.NumberFormat.getNumberInstance(locale).format(index);
    }
}
