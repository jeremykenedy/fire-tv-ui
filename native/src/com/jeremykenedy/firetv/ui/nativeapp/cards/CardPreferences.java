package com.jeremykenedy.firetv.ui.nativeapp.cards;

import android.content.Context;
import android.content.SharedPreferences;
import com.jeremykenedy.firetv.ui.nativeapp.providers.TvRecommendations;
import com.jeremykenedy.firetv.ui.nativeapp.setups.PresentationState;
import com.jeremykenedy.firetv.ui.nativeapp.setups.HolidayPresetTransaction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

public final class CardPreferences {
    private static final String FILE = "native_cards";
    private CardPreferences() {}

    public static Set<String> hiddenPackages(Context context) {
        synchronized (PresentationState.class) {
            HolidayPresetTransaction.assertReadable(context);
            Object value = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getAll().get("hidden_packages");
            try {
                if (value != null && !(value instanceof String)) throw new IllegalArgumentException();
                return CardOptions.decodePackages(value == null ? "" : (String) value);
            } catch (IllegalArgumentException invalid) {
                throw new IllegalStateException("Saved card source choices could not be read. Existing settings were kept.", invalid);
            }
        }
    }

    public static boolean isPackageHidden(Context context, String source) {
        CardOptions.validatePackage(source);
        return hiddenPackages(context).contains(source);
    }

    public static void setPackageHidden(Context context, String source, boolean hidden) {
        setPackagesHidden(context, Collections.singletonMap(source, hidden));
    }

    public static void setPackagesHidden(Context context, Map<String, Boolean> changes) {
        synchronized (PresentationState.class) {
            HolidayPresetTransaction.assertWritable(context);
            if (changes == null) throw new IllegalArgumentException("Choose an application.");
            Set<String> hidden = new LinkedHashSet<>(hiddenPackages(context));
            for (Map.Entry<String, Boolean> entry : changes.entrySet()) {
                CardOptions.validatePackage(entry.getKey());
                if (entry.getValue() == null) throw new IllegalArgumentException("Choose whether cards are hidden.");
                if (entry.getValue()) hidden.add(entry.getKey()); else hidden.remove(entry.getKey());
            }
            String encoded = CardOptions.encodePackages(hidden);
            SharedPreferences preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
            Object previous = preferences.getAll().get("hidden_packages");
            if (encoded.equals(previous) || previous == null && encoded.isEmpty()) return;
            if (preferences.edit().putString("hidden_packages", encoded).commit()) return;
            SharedPreferences.Editor rollback = preferences.edit();
            put(rollback, "hidden_packages", previous);
            if (!rollback.commit() || !java.util.Objects.equals(previous, preferences.getAll().get("hidden_packages")))
                throw new IllegalStateException("Card source choice recovery could not be verified.");
            throw new IllegalStateException("Card source choices could not be saved. Existing settings were kept.");
        }
    }

    public static Set<String> hiddenFeedKeys(Context context, List<TvRecommendations.Feed> feeds) {
        Set<String> hidden;
        try { hidden = hiddenPackages(context); }
        catch (IllegalStateException unreadable) {
            Set<String> all = new LinkedHashSet<>();
            for (TvRecommendations.Feed feed : feeds) all.add(feed.item.key());
            return all;
        }
        Set<String> result = new LinkedHashSet<>();
        for (TvRecommendations.Feed feed : feeds) if (hidden.contains(feed.source)) result.add(feed.item.key());
        return result;
    }

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
        return (program.legacyKey != null && options.isHidden(CardOptions.LEGACY_RECOMMENDATIONS, program.legacyKey))
                || options.isHidden(program.source, program.key) || (program.compatibilityKey != null
                && options.isHidden(program.source, program.compatibilityKey));
    }

    public static CardOptions restoreItem(CardOptions options, TvRecommendations.Program program) {
        CardOptions restored = options.restore(program.source, program.key);
        if (program.legacyKey != null) restored = restored.restore(CardOptions.LEGACY_RECOMMENDATIONS, program.legacyKey);
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
