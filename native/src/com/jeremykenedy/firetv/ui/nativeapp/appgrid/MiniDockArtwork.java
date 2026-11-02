package com.jeremykenedy.firetv.ui.nativeapp.appgrid;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

/** Load the square launcher icon while preserving the existing fallback if an app disappears. */
public final class MiniDockArtwork {
    private MiniDockArtwork() {}

    public static Drawable load(Context context, ComponentName component, Drawable fallback) {
        try {
            Drawable icon = context.getPackageManager().getActivityIcon(component);
            return icon == null ? fallback : icon;
        } catch (PackageManager.NameNotFoundException | RuntimeException unavailable) {
            return fallback;
        }
    }
}
