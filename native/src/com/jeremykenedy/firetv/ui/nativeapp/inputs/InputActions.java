package com.jeremykenedy.firetv.ui.nativeapp.inputs;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.tv.TvContract;
import java.util.List;
import java.util.ArrayList;
import com.jeremykenedy.firetv.ui.nativeapp.device.DeviceRoutes;

public final class InputActions {
    public enum Result { STARTED, UNAVAILABLE, FAILED }
    private InputActions() {}
    public static Intent resolve(Context context, InputEntry input) {
        if (input == null || !input.canOpen()) return null;
        Intent request = new Intent(Intent.ACTION_VIEW, input.passthrough
                ? TvContract.buildChannelUriForPassthroughInput(input.id) : TvContract.buildChannelsUriForInput(input.id));
        try {
            PackageManager manager = context.getPackageManager();
            List<ResolveInfo> matches = new ArrayList<>(manager.queryIntentActivities(request, PackageManager.MATCH_DEFAULT_ONLY));
            ResolveInfo preferred = manager.resolveActivity(request, PackageManager.MATCH_DEFAULT_ONLY);
            if (preferred != null) matches.add(0, preferred);
            for (ResolveInfo match : matches) {
                ActivityInfo info = match.activityInfo;
                if (info == null || info.applicationInfo == null || !info.exported || !info.enabled
                        || !info.applicationInfo.enabled || context.getPackageName().equals(info.packageName)
                        || (info.applicationInfo.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) == 0
                        || info.permission != null && context.checkSelfPermission(info.permission) != PackageManager.PERMISSION_GRANTED) continue;
                ComponentName component = new ComponentName(info.packageName, info.name);
                int enabled = manager.getComponentEnabledSetting(component);
                if (enabled != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && enabled != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) continue;
                // Resolve-only chooser activities are never a destination.
                boolean listed = false;
                for (ResolveInfo candidate : manager.queryIntentActivities(request, PackageManager.MATCH_DEFAULT_ONLY))
                    if (candidate.activityInfo != null && info.packageName.equals(candidate.activityInfo.packageName)
                            && info.name.equals(candidate.activityInfo.name)) { listed = true; break; }
                if (listed) return new Intent(request).setComponent(component);
            }
        } catch (RuntimeException unavailable) { return null; }
        return null;
    }
    public static Result open(Context context, InputEntry input) {
        // Re-read platform state so a stale connected tile cannot tune a removed input.
        InputEntry current = null;
        for (InputEntry candidate : InputRepository.load(context).inputs)
            if (input != null && candidate.id.equals(input.id)) { current = candidate; break; }
        Intent intent = resolve(context, current);
        if (intent == null) return Result.UNAVAILABLE;
        DeviceRoutes.Result result = DeviceRoutes.openSystemIntent(context, intent);
        return result == DeviceRoutes.Result.STARTED ? Result.STARTED
                : result == DeviceRoutes.Result.UNAVAILABLE ? Result.UNAVAILABLE : Result.FAILED;
    }
}
