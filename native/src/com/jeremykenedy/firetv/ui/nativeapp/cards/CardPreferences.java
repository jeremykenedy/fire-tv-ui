package com.jeremykenedy.firetv.ui.nativeapp.cards;

import android.content.Context;
import android.content.SharedPreferences;
import com.jeremykenedy.firetv.ui.nativeapp.providers.TvRecommendations;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CardPreferences {
    private static final String FILE = "native_cards";
    private CardPreferences() {}

    public static boolean artworkFits(Context context) {
        Object value = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
                .getAll().get("featured_artwork_fit");
        return !(value instanceof Boolean) || (Boolean) value;
    }

    public static void setArtworkFit(Context context, boolean fit) {
        SharedPreferences preferences = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE);
        Object previous = preferences.getAll().get("featured_artwork_fit");
        if (preferences.edit().putBoolean("featured_artwork_fit", fit).commit()) return;
        SharedPreferences.Editor rollback = preferences.edit();
        put(rollback, "featured_artwork_fit", previous);
        rollback.commit();
        throw new IllegalStateException("The artwork setting could not be saved. Please try again.");
    }

    public static CardOptions load(Context context) {
        CardOptions defaults = CardOptions.defaults();
        Map<String, ?> stored = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getAll();
        Map<String, Object> values = new LinkedHashMap<>(defaults.backupValues());
        for (String key : values.keySet()) if (stored.containsKey(key)) values.put(key, stored.get(key));
        try { return CardOptions.fromBackup(values); }
        catch (IllegalArgumentException invalid) { return defaults; }
    }

    public static List<String> programKeys(TvRecommendations.Feed feed) {
        List<String> keys = new ArrayList<>();
        for (TvRecommendations.Program program : feed.programs) if (feed.source.equals(program.source)) keys.add(program.key);
        return keys;
    }

    public static List<TvRecommendations.Program> orderedPrograms(CardOptions options, TvRecommendations.Feed feed) {
        Map<String, TvRecommendations.Program> programs = new LinkedHashMap<>();
        for (TvRecommendations.Program program : feed.programs) if (feed.source.equals(program.source)) programs.put(program.key, program);
        List<TvRecommendations.Program> result = new ArrayList<>();
        for (String key : options.orderedKeys(feed.source, feed.item.id, new ArrayList<>(programs.keySet()))) result.add(programs.get(key));
        return Collections.unmodifiableList(result);
    }

    public static List<TvRecommendations.Program> visiblePrograms(CardOptions options, TvRecommendations.Feed feed) {
        List<TvRecommendations.Program> result = new ArrayList<>();
        for (TvRecommendations.Program program : orderedPrograms(options, feed))
            if (!itemHidden(options, program) && !options.isSeriesHidden(program.source, program.seriesKey)) result.add(program);
        return Collections.unmodifiableList(result);
    }

    public static boolean itemHidden(CardOptions options, TvRecommendations.Program program) {
        return options.isHidden(program.source, program.key) || (program.compatibilityKey != null
                && options.isHidden(program.source, program.compatibilityKey));
    }

    public static CardOptions restoreItem(CardOptions options, TvRecommendations.Program program) {
        CardOptions restored = options.restore(program.source, program.key);
        return program.compatibilityKey == null ? restored : restored.restore(program.source, program.compatibilityKey);
    }

    public static void restore(Context context, Map<String, ?> backup) {
        save(context, CardOptions.fromBackup(backup));
    }

    public static void save(Context context, CardOptions options) {
        SharedPreferences preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        Map<String, ?> previous = preferences.getAll();
        Map<String, Object> values = options.backupValues();
        SharedPreferences.Editor editor = preferences.edit();
        for (Map.Entry<String, Object> entry : values.entrySet()) put(editor, entry.getKey(), entry.getValue());
        if (editor.commit()) return;
        SharedPreferences.Editor rollback = preferences.edit();
        for (String key : values.keySet()) {
            if (previous.containsKey(key)) put(rollback, key, previous.get(key));
            else rollback.remove(key);
        }
        rollback.commit();
        throw new IllegalStateException("The card settings could not be saved. Please try again.");
    }

    @SuppressWarnings("unchecked")
    private static void put(SharedPreferences.Editor editor, String key, Object value) {
        if (value instanceof Integer) editor.putInt(key, (Integer) value);
        else if (value instanceof String) editor.putString(key, (String) value);
        else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
        else if (value instanceof Long) editor.putLong(key, (Long) value);
        else if (value instanceof Float) editor.putFloat(key, (Float) value);
        else if (value instanceof java.util.Set) editor.putStringSet(key, (java.util.Set<String>) value);
        else editor.remove(key);
    }
}
