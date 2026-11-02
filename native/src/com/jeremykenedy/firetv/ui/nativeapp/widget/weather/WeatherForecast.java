package com.jeremykenedy.firetv.ui.nativeapp.widget.weather;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public final class WeatherForecast {
    public enum State { READY, EMPTY, UNAVAILABLE, SETUP_REQUIRED }
    public static final class Hour {
        public final long start; public final long end; public final Double temperature;
        public final String unit; public final String summary; public final String wind; public final Integer precipitation;
        Hour(long start, long end, Double temperature, String unit, String summary, String wind, Integer precipitation) {
            this.start = start; this.end = end; this.temperature = temperature; this.unit = unit;
            this.summary = summary; this.wind = wind; this.precipitation = precipitation;
        }
    }
    public final State state;
    public final String message;
    public final String zone;
    public final long updatedAt;
    public final long receivedAt;
    public final long refreshAt;
    public final boolean stale;
    public final List<Hour> hours;
    WeatherForecast(State state, String message, String zone, long updatedAt, long receivedAt, long refreshAt, boolean stale, List<Hour> hours) {
        this.state = state; this.message = message; this.zone = zone; this.updatedAt = updatedAt;
        this.receivedAt = receivedAt; this.refreshAt = refreshAt; this.stale = stale;
        this.hours = Collections.unmodifiableList(new ArrayList<>(hours));
    }
    static WeatherForecast state(State state, String message, long now) {
        return new WeatherForecast(state, message, "UTC", 0, 0, now + 60000, false, Collections.<Hour>emptyList());
    }
    static WeatherForecast parse(String points, String forecast, WeatherOptions options, long received, long now, long refreshAt, boolean stale) throws Exception {
        JSONObject location = new JSONObject(points).getJSONObject("properties");
        String zone = location.getString("timeZone"); ZoneId.of(zone);
        JSONObject properties = new JSONObject(forecast).getJSONObject("properties");
        String updated = properties.optString("updateTime", properties.optString("updated", properties.optString("generatedAt", "")));
        long timestamp = OffsetDateTime.parse(updated).toInstant().toEpochMilli();
        if (timestamp > now + 3600000L || now - timestamp > 172800000L) throw new IllegalArgumentException("Forecast timestamp is outside the supported display window.");
        JSONArray periods = properties.getJSONArray("periods");
        List<Hour> hours = new ArrayList<>();
        for (int index = 0; index < Math.min(192, periods.length()); index++) {
            JSONObject period = periods.getJSONObject(index);
            long start = OffsetDateTime.parse(period.getString("startTime")).toInstant().toEpochMilli();
            long end = OffsetDateTime.parse(period.getString("endTime")).toInstant().toEpochMilli();
            if (end <= start || end - start > 7200000L) throw new IllegalArgumentException("Invalid hourly forecast interval.");
            if (end <= now || start > now + 604800000L) continue;
            String unit = period.optString("temperatureUnit", "");
            Object raw = period.opt("temperature"); Double temperature = null;
            if (raw instanceof Number) temperature = ((Number) raw).doubleValue();
            else if (raw instanceof JSONObject) {
                JSONObject quantity = (JSONObject) raw;
                if (!quantity.isNull("value")) temperature = quantity.getDouble("value");
                String code = quantity.optString("unitCode", "");
                if ("wmoUnit:degC".equals(code)) unit = "C"; else if ("wmoUnit:degF".equals(code)) unit = "F";
            }
            if (temperature != null && (!Double.isFinite(temperature) || temperature < -200 || temperature > 200
                    || !(options.metric ? "C" : "F").equals(unit))) throw new IllegalArgumentException("Unexpected forecast temperature units.");
            Integer precipitation = null;
            JSONObject probability = period.optJSONObject("probabilityOfPrecipitation");
            if (probability != null && !probability.isNull("value")) {
                double value = probability.getDouble("value");
                if (Double.isFinite(value) && value >= 0 && value <= 100) precipitation = (int) Math.round(value);
            }
            String summary = text(period.optString("shortForecast", "Forecast unavailable"), 240);
            String wind = (text(period.optString("windSpeed", ""), 80) + " " + text(period.optString("windDirection", ""), 16)).trim();
            boolean duplicate = false; for (Hour hour : hours) if (hour.start == start) duplicate = true;
            if (!duplicate) hours.add(new Hour(start, end, temperature, unit, summary, wind, precipitation));
        }
        Collections.sort(hours, new Comparator<Hour>() { @Override public int compare(Hour a, Hour b) { return Long.compare(a.start, b.start); } });
        if (hours.size() > 24) hours = new ArrayList<>(hours.subList(0, 24));
        return new WeatherForecast(hours.isEmpty() ? State.EMPTY : State.READY,
                hours.isEmpty() ? "No upcoming hourly periods were supplied for this location." : stale ? "Offline or refresh failed. Showing a past forecast, not current conditions." : "Hourly forecast from National Weather Service.",
                zone, timestamp, received, Math.max(now + 60000, refreshAt), stale, hours);
    }
    private static String text(String value, int max) {
        if (value.length() > max || value.matches("(?s).*\\p{Cntrl}.*")) throw new IllegalArgumentException("Invalid forecast text.");
        return value;
    }
    String hourLine(Hour period, WeatherOptions options, boolean time24) {
        ZoneId displayZone = options.deviceTime ? ZoneId.systemDefault() : ZoneId.of(zone);
        DateTimeFormatter format = DateTimeFormatter.ofPattern(time24 ? "HH:mm z" : "h a z", Locale.getDefault()).withZone(displayZone);
        return format.format(Instant.ofEpochMilli(period.start)) + "  "
                + (period.temperature == null ? "Temperature unavailable" : String.format(Locale.getDefault(), "%.0f °%s", period.temperature, period.unit))
                + "  " + period.summary;
    }
    String sourceText(WeatherOptions options) {
        ZoneId displayZone = options.deviceTime ? ZoneId.systemDefault() : ZoneId.of(zone);
        DateTimeFormatter stamp = DateTimeFormatter.ofPattern("MMM d HH:mm z", Locale.getDefault()).withZone(displayZone);
        return "National Weather Service\n" + (options.deviceTime ? "TV time: " : "Location time: ") + displayZone.getId()
                + "\n" + updatedText(options)
                + "\nChecked " + stamp.format(Instant.ofEpochMilli(receivedAt));
    }
    String updatedText(WeatherOptions options) {
        ZoneId displayZone = options.deviceTime ? ZoneId.systemDefault() : ZoneId.of(zone);
        DateTimeFormatter stamp = DateTimeFormatter.ofPattern("MMM d HH:mm z", Locale.getDefault()).withZone(displayZone);
        return "Updated " + stamp.format(Instant.ofEpochMilli(updatedAt));
    }
    public String display(WeatherOptions options, long now, int count, boolean time24) {
        String label = options.label.isEmpty() ? "Selected location" : options.label;
        if (state != State.READY && state != State.EMPTY) return label + "\n" + message;
        StringBuilder text = new StringBuilder(label).append(stale ? "\nStale saved forecast" : "\nHourly forecast");
        int shown = 0;
        for (Hour period : hours) {
            if (period.end <= now || shown >= count) continue;
            text.append('\n').append(hourLine(period, options, time24));
            if (!period.wind.isEmpty()) text.append("; wind ").append(period.wind);
            if (period.precipitation != null) text.append("; precipitation chance ").append(period.precipitation).append('%');
            shown++;
        }
        if (shown == 0) text.append("\nNo upcoming hourly periods. Refresh is needed.");
        text.append('\n').append(sourceText(options));
        if (stale) text.append("\nOffline or refresh failed");
        return text.toString();
    }
}
