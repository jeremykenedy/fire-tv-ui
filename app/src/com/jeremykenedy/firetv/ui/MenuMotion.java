package com.jeremykenedy.firetv.ui;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;

public final class MenuMotion {
    private MenuMotion() {}

    private static String style(Context context) {
        Object value = DeviceSettings.prefs(context).getAll().get(MenuMotionOptions.SETTING);
        return MenuMotionOptions.valid(value) ? (String) value : "default";
    }

    public static String label(Context context) {
        return MenuMotionOptions.LABELS[MenuMotionOptions.index(style(context))];
    }

    public static void choose(final Activity activity, final Runnable refresh) {
        new AlertDialog.Builder(activity).setTitle("Settings transitions")
                .setSingleChoiceItems(MenuMotionOptions.LABELS,
                        MenuMotionOptions.index(style(activity)), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int index) {
                    SharedPreferences.Editor editor = DeviceSettings.prefs(activity).edit();
                    if (index == 0) editor.remove(MenuMotionOptions.SETTING);
                    else editor.putString(MenuMotionOptions.SETTING, MenuMotionOptions.VALUES[index]);
                    if (editor.commit()) {
                        dialog.dismiss();
                        refresh.run();
                    } else {
                        new AlertDialog.Builder(activity).setTitle("Could not save transitions")
                                .setMessage("Please try again.").setPositiveButton("OK", null).show();
                    }
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    @SuppressWarnings("deprecation")
    static void apply(Activity activity, boolean opening) {
        String value = style(activity);
        if ("default".equals(value)) return;
        if ("none".equals(value) || !ValueAnimator.areAnimatorsEnabled()) {
            activity.overridePendingTransition(0, 0);
            return;
        }
        String[] names = MenuMotionOptions.animations(value, opening);
        int enter = activity.getResources().getIdentifier(names[0], "anim", activity.getPackageName());
        int exit = activity.getResources().getIdentifier(names[1], "anim", activity.getPackageName());
        if (enter != 0 && exit != 0) activity.overridePendingTransition(enter, exit);
    }
}
