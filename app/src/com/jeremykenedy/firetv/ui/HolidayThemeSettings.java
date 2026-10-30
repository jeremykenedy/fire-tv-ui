package com.jeremykenedy.firetv.ui;

import android.content.Context;
import android.content.SharedPreferences;

/** Applies only the appearance explicitly chosen in the holiday dialog. */
final class HolidayThemeSettings {
    private HolidayThemeSettings() {}

    static void apply(Context context, String id, boolean overlay, boolean animated, boolean icons) {
        HolidayThemes.Theme theme = HolidayThemes.find(id);
        if (theme == null) throw new IllegalArgumentException("Choose a holiday theme.");
        SharedPreferences.Editor edit = DeviceSettings.prefs(context).edit();
        edit.putString(overlay ? MenuBackgroundOptions.OVERLAY : MenuBackgroundOptions.STYLE, theme.motif);
        edit.putString(overlay ? MenuBackgroundOptions.OVERLAY_PALETTE : MenuBackgroundOptions.PALETTE, id);
        edit.putBoolean(overlay ? MenuBackgroundOptions.OVERLAY_FROZEN : MenuBackgroundOptions.FROZEN, !animated);
        if (icons) {
            edit.putString("icon_pack_package", "builtin:holiday:" + id);
            edit.putString(IconFocus.STYLE, "border");
            edit.putInt(IconFocus.BORDER, theme.accent);
        }
        if (!edit.commit()) throw new IllegalStateException("The holiday theme could not be saved.");
    }
}
