package com.jeremykenedy.firetv.ui.nativeapp.widget.photos;

import android.content.Context;
import android.content.SharedPreferences;
import com.jeremykenedy.firetv.ui.nativeapp.migration.LegacyJson;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Device-local document grants are deliberately outside portable settings. */
public final class PhotoPreferences {
    public static final String STORE = "native_photo_frame", KEY = "document";
    private PhotoPreferences() {}
    public static PhotoOptions load(Context context) {
        try {
            Map<String, ?> values = context.getSharedPreferences(STORE, 0).getAll();
            if (values.isEmpty()) return PhotoOptions.defaults();
            if (values.size() != 1 || !(values.get(KEY) instanceof String)) throw invalid();
            return decode((String) values.get(KEY));
        } catch (RuntimeException error) { throw new IllegalStateException("Saved photo settings could not be read. Your selections have been kept.", error); }
    }
    public static void save(Context context, PhotoOptions value) {
        SharedPreferences preferences = context.getSharedPreferences(STORE, 0);
        Map<String, ?> old = preferences.getAll();
        if (!preferences.edit().clear().putString(KEY, encode(value)).commit()) {
            SharedPreferences.Editor restore = preferences.edit().clear();
            if (old.get(KEY) instanceof String) restore.putString(KEY, (String) old.get(KEY));
            restore.commit(); throw new IllegalStateException("Photo settings could not be saved. Try again.");
        }
    }
    public static String encode(PhotoOptions value) {
        StringBuilder result = new StringBuilder("{\"version\":1,\"configured\":").append(value.configured)
                .append(",\"seconds\":").append(value.intervalSeconds).append(",\"fit\":\"").append(value.fit.name()).append("\",\"photos\":[");
        for (PhotoOptions.Photo photo : value.photos) {
            if (result.charAt(result.length() - 1) != '[') result.append(',');
            result.append("{\"id\":"); quote(result, photo.id); result.append(",\"uri\":"); quote(result, photo.uri);
            result.append(",\"label\":"); quote(result, photo.label); result.append('}');
        }
        return result.append("]}").toString();
    }
    public static PhotoOptions decode(String document) {
        if (document == null || document.length() > 100000) throw invalid();
        Map<String, Object> raw = LegacyJson.object(document);
        keys(raw, "version", "configured", "seconds", "fit", "photos");
        if (!BigDecimal.ONE.equals(raw.get("version")) || !(raw.get("configured") instanceof Boolean)
                || !(raw.get("seconds") instanceof BigDecimal) || !(raw.get("photos") instanceof List)) throw invalid();
        List<?> photos = (List<?>) raw.get("photos");
        if (photos.size() > PhotoOptions.MAX_PHOTOS) throw invalid();
        List<PhotoOptions.Photo> checked = new ArrayList<>();
        for (Object entry : photos) {
            if (!(entry instanceof Map)) throw invalid();
            @SuppressWarnings("unchecked") Map<String, Object> photo = (Map<String, Object>) entry;
            keys(photo, "id", "uri", "label");
            checked.add(new PhotoOptions.Photo(string(photo.get("id")), string(photo.get("uri")), string(photo.get("label"))));
        }
        return new PhotoOptions((Boolean) raw.get("configured"), ((BigDecimal) raw.get("seconds")).intValueExact(),
                PhotoOptions.Fit.valueOf(string(raw.get("fit"))), checked);
    }
    private static void quote(StringBuilder out, String text) { out.append('"').append(text.replace("\\", "\\\\").replace("\"", "\\\"")).append('"'); }
    private static String string(Object value) { if (!(value instanceof String)) throw invalid(); return (String) value; }
    private static void keys(Map<String, Object> map, String... keys) { if (!map.keySet().equals(new HashSet<>(Arrays.asList(keys)))) throw invalid(); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid photo frame settings."); }
}
