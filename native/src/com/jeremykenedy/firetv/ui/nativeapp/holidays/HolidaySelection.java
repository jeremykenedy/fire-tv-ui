package com.jeremykenedy.firetv.ui.nativeapp.holidays;

import android.content.SharedPreferences;
import java.util.Map;

public final class HolidaySelection {
    public enum Mode { SCENE, OVERLAY, SCENE_AND_OVERLAY }

    private HolidaySelection() {}

    public static void apply(SharedPreferences preferences, String id, Mode mode) {
        HolidayScenes.Scene scene = HolidayScenes.find(id);
        if (scene == null || mode == null) throw new IllegalArgumentException("Choose a holiday scene and layer.");
        Map<String, ?> previous = preferences.getAll();
        SharedPreferences.Editor editor = preferences.edit();
        if (mode != Mode.OVERLAY) editor.putString("scene", scene.id);
        if (mode != Mode.SCENE) editor.putString("overlay", scene.overlay);
        if (editor.commit()) return;
        SharedPreferences.Editor rollback = preferences.edit();
        if (mode != Mode.OVERLAY) restore(rollback, previous, "scene");
        if (mode != Mode.SCENE) restore(rollback, previous, "overlay");
        rollback.commit();
        throw new IllegalStateException("Holiday selection could not be saved. Try again.");
    }

    private static void restore(SharedPreferences.Editor editor, Map<String, ?> previous, String key) {
        Object value = previous.get(key);
        if (value instanceof String) editor.putString(key, (String) value);
        else editor.remove(key);
    }
}
