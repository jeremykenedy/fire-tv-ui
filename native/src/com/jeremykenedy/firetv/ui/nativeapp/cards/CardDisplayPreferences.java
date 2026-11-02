package com.jeremykenedy.firetv.ui.nativeapp.cards;

import android.content.Context;
import android.content.SharedPreferences;
import com.jeremykenedy.firetv.ui.nativeapp.setups.HolidayPresetTransaction;
import com.jeremykenedy.firetv.ui.nativeapp.setups.PresentationState;

/** Card display choices retained in the launcher preference store. */
public final class CardDisplayPreferences {
    private static final String FILE = "launcher_prefs";
    private static final String BIG_PICTURE = "poster_mode";
    private static final String HIDE_TITLES = "hide_watch_next_titles";
    private static final String TEXT_OUTSIDE = "text_outside_cards";
    public static final String TEXT_COLOR = "color_cards_text_color_argb";
    public static final String TEXT_OPACITY = "color_cards_text_color_opacity";

    private CardDisplayPreferences() {}

    public static boolean bigPicture(Context context) {
        return flag(context, BIG_PICTURE);
    }

    public static boolean hideTitles(Context context) {
        return flag(context, HIDE_TITLES);
    }

    public static boolean textOutside(Context context) {
        return flag(context, TEXT_OUTSIDE);
    }

    public static Integer textColor(Context context) {
        synchronized (PresentationState.class) {
            HolidayPresetTransaction.assertReadable(context);
            java.util.Map<String, ?> values = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getAll();
            if (!values.containsKey(TEXT_COLOR) && !values.containsKey(TEXT_OPACITY)) return null;
            Object color = values.get(TEXT_COLOR), opacity = values.get(TEXT_OPACITY);
            int rgb = color instanceof Integer ? (Integer) color : 0xffffffff;
            int percent = opacity instanceof Integer ? Math.max(0, Math.min(100, (Integer) opacity)) : 100;
            return (Math.round(percent * 255f / 100f) << 24) | (rgb & 0xffffff);
        }
    }

    public static void setBigPicture(Context context, boolean value) {
        save(context, BIG_PICTURE, value);
    }

    public static void setHideTitles(Context context, boolean value) {
        save(context, HIDE_TITLES, value);
    }

    public static void setTextOutside(Context context, boolean value) {
        save(context, TEXT_OUTSIDE, value);
    }

    private static boolean flag(Context context, String key) {
        synchronized (PresentationState.class) {
            HolidayPresetTransaction.assertReadable(context);
            Object value = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getAll().get(key);
            return value instanceof Boolean && (Boolean) value;
        }
    }

    private static void save(Context context, String key, boolean value) {
        synchronized (PresentationState.class) {
            HolidayPresetTransaction.assertWritable(context);
            SharedPreferences preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
            Object old = preferences.getAll().get(key);
            if (old != null && !(old instanceof Boolean))
                throw new IllegalStateException("The previous card setting has an unsupported type.");
            if (preferences.edit().putBoolean(key, value).commit()) return;
            SharedPreferences.Editor rollback = preferences.edit();
            if (old instanceof Boolean) rollback.putBoolean(key, (Boolean) old);
            else rollback.remove(key);
            rollback.commit();
            throw new IllegalStateException("The card display setting could not be saved. Please try again.");
        }
    }
}
