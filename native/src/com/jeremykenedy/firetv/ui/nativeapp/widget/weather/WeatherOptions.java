package com.jeremykenedy.firetv.ui.nativeapp.widget.weather;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

public final class WeatherOptions {
    public final boolean configured;
    public final String label;
    public final int latitudeE4;
    public final int longitudeE4;
    public final boolean metric;
    public final boolean deviceTime;

    public WeatherOptions(boolean configured, String label, int latitudeE4, int longitudeE4, boolean metric, boolean deviceTime) {
        if (label == null || label.length() > 80 || latitudeE4 < -900000 || latitudeE4 > 900000
                || longitudeE4 < -1800000 || longitudeE4 > 1800000 || label.matches("(?s).*\\p{Cntrl}.*"))
            throw new IllegalArgumentException("Use a short location name and valid latitude and longitude.");
        this.configured = configured; this.label = label.trim(); this.latitudeE4 = latitudeE4;
        this.longitudeE4 = longitudeE4; this.metric = metric; this.deviceTime = deviceTime;
    }
    public static WeatherOptions defaults() { return new WeatherOptions(false, "", 0, 0, false, false); }
    /** Retains the legacy name without guessing coordinates or NWS coverage. */
    public static WeatherOptions fromLegacy(String location, String unit) {
        String label = location == null ? "" : location;
        String legacyUnit = unit == null ? "C" : unit;
        if (!label.equals(label.trim()) || !("C".equals(legacyUnit) || "F".equals(legacyUnit)))
            throw new IllegalArgumentException("Legacy weather choices need review.");
        return new WeatherOptions(false, label, 0, 0, "C".equals(legacyUnit), false);
    }
    public static WeatherOptions location(String label, String latitude, String longitude, boolean metric, boolean deviceTime) {
        return new WeatherOptions(true, label, coordinate(latitude, 90), coordinate(longitude, 180), metric, deviceTime);
    }
    private static int coordinate(String value, int limit) {
        if (value == null || !value.matches("-?[0-9]{1,3}(?:\\.[0-9]{1,8})?")) throw new IllegalArgumentException("Enter decimal latitude and longitude.");
        BigDecimal number = new BigDecimal(value);
        if (number.abs().compareTo(BigDecimal.valueOf(limit)) > 0) throw new IllegalArgumentException("Latitude must be -90 to 90 and longitude -180 to 180.");
        return number.setScale(4, RoundingMode.HALF_UP).movePointRight(4).intValueExact();
    }
    public String latitude() { return BigDecimal.valueOf(latitudeE4, 4).toPlainString(); }
    public String longitude() { return BigDecimal.valueOf(longitudeE4, 4).toPlainString(); }
    public String units() { return metric ? "si" : "us"; }
    public Map<String, Object> backupValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("configured", configured); values.put("label", label); values.put("latitude_e4", latitudeE4);
        values.put("longitude_e4", longitudeE4); values.put("metric", metric); values.put("device_time", deviceTime);
        return values;
    }
    public static WeatherOptions fromBackup(Map<String, ?> values) {
        if (values == null || values.size() != 6 || !(values.get("configured") instanceof Boolean)
                || !(values.get("label") instanceof String) || !(values.get("latitude_e4") instanceof Integer)
                || !(values.get("longitude_e4") instanceof Integer) || !(values.get("metric") instanceof Boolean)
                || !(values.get("device_time") instanceof Boolean)) throw new IllegalArgumentException("Invalid saved weather choices.");
        return new WeatherOptions((Boolean) values.get("configured"), (String) values.get("label"),
                (Integer) values.get("latitude_e4"), (Integer) values.get("longitude_e4"),
                (Boolean) values.get("metric"), (Boolean) values.get("device_time"));
    }
}
