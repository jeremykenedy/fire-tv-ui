package com.jeremykenedy.firetv.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.util.Log;

/** Uses Fire OS's exported input switch service for physical TV inputs. */
public final class TvInputs {
    private TvInputs() {
    }

    public static boolean openFireTv(Context context, String inputId) {
        ComponentName service = new ComponentName("com.amazon.tv.inputpreference.service",
                "com.amazon.tv.inputpreference.service.KaminoInputSwitchService");
        try {
            ServiceInfo info = context.getPackageManager().getServiceInfo(service, 0);
            if ((info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                return false;
            }
            TvInputManager manager = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
            if (manager == null) {
                return false;
            }
            for (TvInputInfo input : manager.getTvInputList()) {
                if (!inputId.equals(input.getId())) {
                    continue;
                }
                CharSequence label = input.loadLabel(context);
                int type = InputValues.amazonType(input.getType(),
                        label == null ? null : label.toString(), -1);
                if (type < 0) {
                    return false;
                }
                context.startForegroundService(new Intent().setComponent(service)
                        .putExtra("inputType", type));
                return true;
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        } catch (RuntimeException error) {
            Log.w("FireTvInputs", "Could not switch the TV input.", error);
        }
        return false;
    }
}
