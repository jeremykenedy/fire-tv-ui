package com.jeremykenedy.firetv.ui.nativeapp.appearance;

import android.content.Context;
import com.jeremykenedy.firetv.ui.nativeapp.setups.HolidayPresetTransaction;
import java.io.IOException;

final class HolidayThemeSettings {
    private HolidayThemeSettings() {}
    static void apply(Context context, String id, boolean overlay, boolean animated, boolean icons) throws IOException {
        HolidayPresetTransaction.apply(context,id,overlay,animated,icons);
    }
}
