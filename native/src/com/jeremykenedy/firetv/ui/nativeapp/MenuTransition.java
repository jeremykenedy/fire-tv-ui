package com.jeremykenedy.firetv.ui.nativeapp;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;

/** Preserves the existing settings page transition choice. */
public final class MenuTransition {
    private static final String FILE = "launcher_prefs", KEY = "menu_transition_style";
    private static final String[] VALUES = {"default", "none", "fade", "slide"};
    private static final String[] LABELS = {"Default", "None", "Fade", "Short slide"};
    private MenuTransition() {}
    static int index(Object value) {
        for (int i = 0; i < VALUES.length; i++) if (VALUES[i].equals(value)) return i;
        return 0;
    }
    public static String label(Activity activity) { return LABELS[index(prefs(activity).getAll().get(KEY))]; }
    private static SharedPreferences prefs(Activity activity) { return activity.getSharedPreferences(FILE, 0); }
    @SuppressWarnings("unchecked") public static void choose(final Activity activity, final Runnable refresh) {
        new AlertDialog.Builder(activity).setTitle("Settings transitions")
                .setSingleChoiceItems(LABELS, index(prefs(activity).getAll().get(KEY)), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int selected) {
                        if (selected < 0 || selected >= VALUES.length) return;
                        SharedPreferences preferences = prefs(activity);
                        java.util.Map<String, ?> previous = preferences.getAll();
                        Object old = previous.get(KEY); boolean existed = previous.containsKey(KEY);
                        SharedPreferences.Editor edit = preferences.edit();
                        if (selected == 0) edit.remove(KEY); else edit.putString(KEY, VALUES[selected]);
                        if (edit.commit()) { dialog.dismiss(); refresh.run(); }
                        else {
                            SharedPreferences.Editor rollback = preferences.edit();
                            if (!existed) rollback.remove(KEY);
                            else if (old instanceof String) rollback.putString(KEY, (String) old);
                            else if (old instanceof Boolean) rollback.putBoolean(KEY, (Boolean) old);
                            else if (old instanceof Integer) rollback.putInt(KEY, (Integer) old);
                            else if (old instanceof Long) rollback.putLong(KEY, (Long) old);
                            else if (old instanceof Float) rollback.putFloat(KEY, (Float) old);
                            else if (old instanceof java.util.Set) rollback.putStringSet(KEY, (java.util.Set<String>) old);
                            rollback.commit();
                            new AlertDialog.Builder(activity).setTitle("Could not save transitions")
                                    .setMessage("Please try again.").setPositiveButton("OK", null).show();
                        }
                    }
                }).setNegativeButton("Cancel", null).show();
    }
    @SuppressWarnings("deprecation") static void apply(Activity activity, boolean opening) {
        int style = index(prefs(activity).getAll().get(KEY));
        if (style == 0) return;
        if (style == 1 || !ValueAnimator.areAnimatorsEnabled()) { activity.overridePendingTransition(0, 0); return; }
        String enter = style == 2 ? "settings_fade_in" : opening ? "settings_slide_in_right" : "settings_slide_in_left";
        String exit = style == 2 ? "settings_fade_out" : opening ? "settings_slide_out_left" : "settings_slide_out_right";
        int enterId = activity.getResources().getIdentifier(enter, "anim", activity.getPackageName());
        int exitId = activity.getResources().getIdentifier(exit, "anim", activity.getPackageName());
        if (enterId != 0 && exitId != 0) activity.overridePendingTransition(enterId, exitId);
    }
}
