package com.jeremykenedy.firetv.ui.nativeapp.appgrid;

import android.content.Context;
import android.content.SharedPreferences;
import com.jeremykenedy.firetv.ui.nativeapp.setups.HolidayPresetTransaction;
import com.jeremykenedy.firetv.ui.nativeapp.setups.PresentationState;
import java.util.*;

/** Promotes verified returning identities before their native controls are displayed. */
public final class RetainedHomePreferences {
    private RetainedHomePreferences() {}

    public static boolean hasApps(Context context) {
        for (String file : new String[]{RetainedHomeChoices.GRID, RetainedHomeChoices.ICONS}) {
            Map<String, Object> pending = RetainedHomeChoices.read(file, preferences(context, file).getAll().get(RetainedHomeChoices.KEY));
            for (Map.Entry<String, Object> entry : pending.entrySet()) {
                if (entry.getKey().startsWith("hidden:")) return true;
                if (entry.getKey().startsWith("icon:") && !RetainedHomeChoices.inputAlias(entry.getKey().substring(5))) return true;
                if (entry.getValue() instanceof String && ((String) entry.getValue()).contains("legacy-app:")) return true;
            }
        }
        return false;
    }

    public static void apps(Context context, Collection<String> launchActivities) {
        synchronized (PresentationState.class) {
            HolidayPresetTransaction.assertWritable(context);
            Map<String, String> identities = RetainedHomeChoices.appIdentities(launchActivities);
            apply(preferences(context, RetainedHomeChoices.GRID), identities, RetainedHomeChoices.GRID);
            apply(preferences(context, RetainedHomeChoices.ICONS), identities, RetainedHomeChoices.ICONS);
        }
    }

    public static void inputs(Context context, Map<String, String> factoryLabelsById) {
        synchronized (PresentationState.class) {
            HolidayPresetTransaction.assertWritable(context);
            Map<String, String> identities = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : RetainedHomeChoices.inputIdentities(factoryLabelsById).entrySet())
                identities.put(entry.getKey(), "input:" + entry.getValue());
            apply(preferences(context, RetainedHomeChoices.INPUTS), identities, RetainedHomeChoices.INPUTS);
            apply(preferences(context, RetainedHomeChoices.GRID), identities, RetainedHomeChoices.GRID);
            apply(preferences(context, RetainedHomeChoices.ICONS), identities, RetainedHomeChoices.ICONS);
        }
    }

    private static SharedPreferences preferences(Context context, String file) {
        return context.getSharedPreferences(file, Context.MODE_PRIVATE);
    }

    private static void apply(SharedPreferences preferences, Map<String, String> identities, String file) {
        Map<String, ?> before = preferences.getAll();
        if (!before.containsKey(RetainedHomeChoices.KEY)) return;
        Map<String, Object> after = file.equals(RetainedHomeChoices.GRID) ? RetainedHomeChoices.promoteGrid(before, identities)
                : file.equals(RetainedHomeChoices.INPUTS) ? RetainedHomeChoices.promoteInputs(before, identities)
                : RetainedHomeChoices.promoteIcons(before, identities);
        Set<String> keys = new LinkedHashSet<>(before.keySet()); keys.addAll(after.keySet());
        for (String key : new ArrayList<>(keys))
            if (Objects.equals(before.get(key), after.get(key))) keys.remove(key);
        if (keys.isEmpty()) return;
        if (write(preferences, after, keys) && matches(preferences.getAll(), after, keys)) return;
        boolean restored = write(preferences, before, keys) && matches(preferences.getAll(), before, keys);
        throw new IllegalStateException(restored ? "Returning Home choices could not be saved. Original choices were kept."
                : "Returning Home choices could not be verified. Reopen Home before editing them.");
    }

    private static boolean write(SharedPreferences preferences, Map<String, ?> values, Set<String> keys) {
        SharedPreferences.Editor edit = preferences.edit();
        for (String key : keys) {
            Object value = values.get(key);
            if (value == null) edit.remove(key);
            else if (value instanceof String) edit.putString(key, (String) value);
            else if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
            else throw new IllegalArgumentException("Unsupported retained Home choice.");
        }
        return edit.commit();
    }

    private static boolean matches(Map<String, ?> actual, Map<String, ?> expected, Set<String> keys) {
        for (String key : keys) if (!Objects.equals(actual.get(key), expected.get(key))) return false;
        return true;
    }
}
