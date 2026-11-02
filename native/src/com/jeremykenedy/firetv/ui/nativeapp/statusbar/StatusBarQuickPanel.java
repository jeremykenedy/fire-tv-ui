package com.jeremykenedy.firetv.ui.nativeapp.statusbar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.widget.Toast;
import com.jeremykenedy.firetv.ui.nativeapp.SettingsActivity;
import com.jeremykenedy.firetv.ui.nativeapp.device.DeviceRoutes;
import com.jeremykenedy.firetv.ui.nativeapp.device.screensavers.ScreensaverSettingsActivity;
import java.text.DateFormat;
import java.util.Date;

/** Quick access to supported device destinations without changing settings. */
public final class StatusBarQuickPanel {
    private StatusBarQuickPanel() {}

    public static void show(final Activity activity) {
        final String[] labels = {"Network", "Settings", "Preferences", "Sound", "Accessibility",
                "Display", "Factory Home", "Screensavers"};
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(DateFormat.getDateInstance(DateFormat.FULL).format(new Date()))
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface ignored, int which) {
                        if (which == 2) { openLocal(activity, SettingsActivity.class); return; }
                        if (which == 7) { openLocal(activity, ScreensaverSettingsActivity.class); return; }
                        DeviceRoutes.Result result;
                        if (which == 0) result = DeviceRoutes.open(activity, DeviceRoutes.Destination.NETWORK_SETTINGS);
                        else if (which == 1) result = DeviceRoutes.open(activity, DeviceRoutes.Destination.SYSTEM_SETTINGS);
                        else if (which == 6) result = DeviceRoutes.open(activity, DeviceRoutes.Destination.FACTORY_UI);
                        else result = openSystem(activity, which == 3 ? Settings.ACTION_SOUND_SETTINGS
                                    : which == 4 ? Settings.ACTION_ACCESSIBILITY_SETTINGS : Settings.ACTION_DISPLAY_SETTINGS);
                        if (result != DeviceRoutes.Result.STARTED)
                            Toast.makeText(activity, "This device destination is unavailable.", Toast.LENGTH_LONG).show();
                    }
                }).setNegativeButton("Close", null).show();
        dialog.getListView().requestFocus();
        dialog.getListView().setSelection(0);
    }

    private static DeviceRoutes.Result openSystem(Activity activity, String action) {
        try {
            Intent request = new Intent(action);
            ResolveInfo resolved = activity.getPackageManager().resolveActivity(request, 0);
            if (resolved == null || resolved.activityInfo == null) return DeviceRoutes.Result.UNAVAILABLE;
            request.setComponent(new ComponentName(resolved.activityInfo.packageName, resolved.activityInfo.name));
            return DeviceRoutes.openSystemIntent(activity, request);
        } catch (RuntimeException unavailable) { return DeviceRoutes.Result.UNAVAILABLE; }
    }

    private static void openLocal(Activity activity, Class<?> destination) {
        try { activity.startActivity(new Intent(activity, destination)); }
        catch (RuntimeException unavailable) {
            Toast.makeText(activity, "This destination is unavailable.", Toast.LENGTH_LONG).show();
        }
    }
}
