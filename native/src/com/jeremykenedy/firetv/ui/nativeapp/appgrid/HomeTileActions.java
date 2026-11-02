package com.jeremykenedy.firetv.ui.nativeapp.appgrid;

import android.content.Context;
import com.jeremykenedy.firetv.ui.nativeapp.folders.FolderPreferences;
import com.jeremykenedy.firetv.ui.nativeapp.inputs.InputRepository;
import java.util.List;

public final class HomeTileActions {
    private HomeTileActions() {}

    public static void move(Context context, String key, int delta) {
        AppGridOptions apps = AppGridPreferences.load(context);
        List<String> visible = HomeTilePreferences.load(context).visible(apps,
                AppInventory.keys(AppInventory.load(context)), FolderPreferences.load(context),
                InputRepository.load(context).visibleInputs);
        HomeTilePreferences.move(context, key, delta, visible, apps.favorites);
    }
}
