package com.jeremykenedy.firetv.ui.nativeapp.widget.reminders;

import android.content.Context;
import android.content.SharedPreferences;
import com.jeremykenedy.firetv.ui.nativeapp.migration.LegacyJson;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public final class RemindersPreferences {
    public static final String STORE = "native_household_reminders";
    public static final String KEY = "document";
    public static final int MAX_DOCUMENT = 131072;
    private RemindersPreferences() {}
    public static RemindersOptions load(Context context) {
        try {
            Map<String, ?> values = context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getAll();
            if (values.isEmpty()) return RemindersOptions.defaults();
            if (values.size() != 1 || !(values.get(KEY) instanceof String)) throw invalid();
            return decode((String) values.get(KEY));
        } catch (RuntimeException error) { throw new IllegalStateException("Saved reminders could not be read. Stored data has been kept.", error); }
    }
    public static void save(Context context, RemindersOptions value) {
        String document = encode(value);
        SharedPreferences preferences = context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
        Map<String, ?> old = preferences.getAll();
        if (!preferences.edit().clear().putString(KEY, document).commit()) {
            SharedPreferences.Editor restore = preferences.edit().clear();
            if (old.get(KEY) instanceof String) restore.putString(KEY, (String) old.get(KEY));
            restore.commit();
            throw new IllegalStateException("Reminders could not be saved. Please try again.");
        }
    }
    public static boolean exportAllowed(Context context) { return load(context).allowExport; }
    public static String portableDocument(Context context) {
        RemindersOptions value = load(context);
        if (!value.allowExport) throw new IllegalStateException("Private reminders are excluded from portable backups.");
        return encode(value);
    }
    public static RemindersOptions decode(String document) {
        if (document == null || document.length() > MAX_DOCUMENT) throw invalid();
        try {
            Map<String, Object> raw = LegacyJson.object(document);
            keys(raw, "version", "enabled", "summaryOnly", "allowExport", "items");
            if (!BigDecimal.ONE.equals(raw.get("version")) || !(raw.get("items") instanceof List)) throw invalid();
            List<?> rows = (List<?>) raw.get("items");
            if (rows.size() > RemindersOptions.MAX_ITEMS) throw invalid();
            List<RemindersOptions.Item> items = new ArrayList<>();
            for (Object row : rows) {
                if (!(row instanceof Map)) throw invalid();
                @SuppressWarnings("unchecked") Map<String, Object> item = (Map<String, Object>) row;
                keys(item, "id", "label", "date", "time", "zone", "repeat", "completed", "snoozeUntil");
                items.add(new RemindersOptions.Item(string(item.get("id")), string(item.get("label")), LocalDate.parse(string(item.get("date"))),
                        LocalTime.parse(string(item.get("time"))), string(item.get("zone")), RemindersOptions.Repeat.valueOf(string(item.get("repeat"))),
                        bool(item.get("completed")), number(item.get("snoozeUntil"))));
            }
            return new RemindersOptions(bool(raw.get("enabled")), bool(raw.get("summaryOnly")), bool(raw.get("allowExport")), items);
        } catch (RuntimeException error) { throw new IllegalArgumentException("Invalid household reminders data.", error); }
    }
    public static String encode(RemindersOptions value) {
        StringBuilder out = new StringBuilder("{\"version\":1,\"enabled\":").append(value.enabled)
                .append(",\"summaryOnly\":").append(value.summaryOnly).append(",\"allowExport\":").append(value.allowExport).append(",\"items\":[");
        for (int i = 0; i < value.items.size(); i++) {
            if (i > 0) out.append(','); RemindersOptions.Item item = value.items.get(i);
            out.append("{\"id\":"); quote(out, item.id); out.append(",\"label\":"); quote(out, item.label);
            out.append(",\"date\":"); quote(out, item.date.toString()); out.append(",\"time\":"); quote(out, item.time.toString());
            out.append(",\"zone\":"); quote(out, item.zone.getId()); out.append(",\"repeat\":"); quote(out, item.repeat.name());
            out.append(",\"completed\":").append(item.completed).append(",\"snoozeUntil\":").append(item.snoozeUntil).append('}');
        }
        String document = out.append("]}").toString(); if (document.length() > MAX_DOCUMENT) throw invalid(); return document;
    }
    private static void quote(StringBuilder out, String text) {
        out.append('"'); for (int i = 0; i < text.length(); i++) { char c = text.charAt(i); if (c == '"' || c == '\\') out.append('\\'); out.append(c); } out.append('"');
    }
    private static void keys(Map<String, Object> values, String... keys) { if (!values.keySet().equals(new HashSet<>(Arrays.asList(keys)))) throw invalid(); }
    private static String string(Object value) { if (!(value instanceof String)) throw invalid(); return (String) value; }
    private static boolean bool(Object value) { if (!(value instanceof Boolean)) throw invalid(); return (Boolean) value; }
    private static long number(Object value) { if (!(value instanceof BigDecimal)) throw invalid(); return ((BigDecimal) value).longValueExact(); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid household reminders data."); }
}
