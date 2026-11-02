package com.jeremykenedy.firetv.ui.nativeapp.widget.notes;

import android.content.Context;
import android.content.SharedPreferences;
import com.jeremykenedy.firetv.ui.nativeapp.migration.LegacyJson;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class NotesPreferences {
    public static final String STORE = "native_household_notes";
    public static final String KEY = "document";
    public static final int MAX_DOCUMENT = 262144;
    private NotesPreferences() {}
    public static NotesOptions load(Context context) {
        try {
            Map<String, ?> values = context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getAll();
            if (values.isEmpty()) return NotesOptions.defaults();
            if (values.size() != 1 || !(values.get(KEY) instanceof String)) throw invalid();
            return decode((String) values.get(KEY));
        } catch (RuntimeException error) {
            throw new IllegalStateException("Saved notes could not be read. Stored data has been kept.", error);
        }
    }
    public static void save(Context context, NotesOptions value) {
        String document = encode(value);
        SharedPreferences preferences = context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
        Map<String, ?> old = preferences.getAll();
        if (!preferences.edit().clear().putString(KEY, document).commit()) {
            SharedPreferences.Editor restore = preferences.edit().clear();
            if (old.get(KEY) instanceof String) restore.putString(KEY, (String) old.get(KEY));
            restore.commit();
            throw new IllegalStateException("Notes could not be saved. Please try again.");
        }
    }
    public static boolean exportAllowed(Context context) { return load(context).allowExport; }
    public static String portableDocument(Context context) {
        NotesOptions value = load(context);
        if (!value.allowExport) throw new IllegalStateException("Private notes are excluded from portable backups.");
        return encode(value);
    }
    public static NotesOptions decode(String document) {
        if (document == null || document.length() > MAX_DOCUMENT) throw invalid();
        Map<String, Object> raw = LegacyJson.object(document);
        keys(raw, "version", "configured", "summaryOnly", "allowExport", "items");
        if (!(raw.get("version") instanceof BigDecimal) || !BigDecimal.ONE.equals(raw.get("version"))) throw invalid();
        if (!(raw.get("items") instanceof List)) throw invalid();
        List<?> entries = (List<?>) raw.get("items");
        if (entries.size() > NotesOptions.MAX_ITEMS) throw invalid();
        List<NotesOptions.Item> items = new ArrayList<>();
        for (Object entry : entries) {
            if (!(entry instanceof Map)) throw invalid();
            @SuppressWarnings("unchecked") Map<String, Object> item = (Map<String, Object>) entry;
            keys(item, "id", "text", "checklist", "checked");
            items.add(new NotesOptions.Item(string(item.get("id")), string(item.get("text")), bool(item.get("checklist")), bool(item.get("checked"))));
        }
        return new NotesOptions(bool(raw.get("configured")), bool(raw.get("summaryOnly")), bool(raw.get("allowExport")), items);
    }
    public static String encode(NotesOptions value) {
        StringBuilder out = new StringBuilder("{\"version\":1,\"configured\":").append(value.configured)
                .append(",\"summaryOnly\":").append(value.summaryOnly).append(",\"allowExport\":").append(value.allowExport).append(",\"items\":[");
        for (int i = 0; i < value.items.size(); i++) {
            if (i > 0) out.append(','); NotesOptions.Item item = value.items.get(i);
            out.append("{\"id\":"); quote(out, item.id); out.append(",\"text\":"); quote(out, item.text);
            out.append(",\"checklist\":").append(item.checklist).append(",\"checked\":").append(item.checked).append('}');
        }
        String document = out.append("]}").toString();
        if (document.length() > MAX_DOCUMENT) throw invalid();
        return document;
    }
    private static void quote(StringBuilder out, String text) {
        out.append('"'); for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i); if (c == '"' || c == '\\') out.append('\\');
            if (c == '\n') out.append("\\n"); else if (c == '\t') out.append("\\t"); else out.append(c);
        } out.append('"');
    }
    private static String string(Object value) { if (!(value instanceof String)) throw invalid(); return (String) value; }
    private static boolean bool(Object value) { if (!(value instanceof Boolean)) throw invalid(); return (Boolean) value; }
    private static void keys(Map<String, Object> map, String... keys) {
        Set<String> expected = new HashSet<>(Arrays.asList(keys)); if (!map.keySet().equals(expected)) throw invalid();
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid household notes data."); }
}
