package com.jeremykenedy.firetv.ui.nativeapp.widget.calendar;

import android.content.Context;
import android.content.SharedPreferences;
import com.jeremykenedy.firetv.ui.nativeapp.migration.LegacyJson;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Only local calendar IDs and display choices are saved; event content is never stored. */
public final class CalendarPreferences {
    public static final String STORE = "native_calendar";
    public static final String KEY = "settings";
    private CalendarPreferences() {}
    public static CalendarOptions load(Context context) {
        try {
            Map<String, ?> values = context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getAll();
            if (values.isEmpty()) return CalendarOptions.defaults();
            if (values.size() != 1 || !(values.get(KEY) instanceof String)) throw invalid();
            return decode((String) values.get(KEY));
        } catch (RuntimeException error) { throw new IllegalStateException("Saved calendar choices could not be read. Stored data has been kept.", error); }
    }
    public static void save(Context context, CalendarOptions options) {
        String data = encode(options);
        SharedPreferences prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
        Map<String, ?> old = prefs.getAll();
        if (!prefs.edit().clear().putString(KEY, data).commit()) {
            SharedPreferences.Editor restore = prefs.edit().clear();
            if (old.get(KEY) instanceof String) restore.putString(KEY, (String) old.get(KEY));
            restore.commit();
            throw new IllegalStateException("Calendar choices could not be saved. Please try again.");
        }
    }
    public static CalendarOptions decode(String data) {
        if (data == null || data.length() > 4096) throw invalid();
        Map<String, Object> raw = LegacyJson.object(data);
        if (!raw.keySet().equals(new HashSet<>(Arrays.asList("version", "selected", "zone", "summaryOnly")))
                || !BigDecimal.ONE.equals(raw.get("version")) || !(raw.get("selected") instanceof List)
                || !(raw.get("zone") instanceof String) || !(raw.get("summaryOnly") instanceof Boolean)) throw invalid();
        List<?> ids = (List<?>) raw.get("selected");
        if (ids.size() > CalendarOptions.MAX_CALENDARS) throw invalid();
        List<Long> selected = new ArrayList<>();
        for (Object id : ids) {
            if (!(id instanceof BigDecimal)) throw invalid();
            try { selected.add(((BigDecimal) id).longValueExact()); } catch (ArithmeticException error) { throw invalid(); }
        }
        return new CalendarOptions(selected, (String) raw.get("zone"), (Boolean) raw.get("summaryOnly"));
    }
    public static String encode(CalendarOptions options) {
        return "{\"version\":1,\"selected\":" + options.selected.toString() + ",\"zone\":\"" + options.zone + "\",\"summaryOnly\":" + options.summaryOnly + "}";
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid calendar choices."); }
}
