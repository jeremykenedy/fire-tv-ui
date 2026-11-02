package com.jeremykenedy.firetv.ui.nativeapp.inputs;

import android.app.Activity;
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
import com.jeremykenedy.firetv.ui.nativeapp.locks.LauncherLocks;
import com.jeremykenedy.firetv.ui.nativeapp.locks.LockStore;

public final class InputActions {
    public enum Result { STARTED, UNAVAILABLE, FAILED, LOCKED }
    public interface Callback { void complete(Result result); }
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
    private static Intent currentIntent(Context context, InputEntry input) {
        // Re-read platform state so a stale connected tile cannot tune a removed input.
        for (InputEntry candidate : InputRepository.load(context).inputs)
            if (input != null && candidate.id.equals(input.id)) return resolve(context, candidate);
        return null;
    }

    /** Non-interactive callers cannot bypass a launcher lock. */
    public static Result open(Context context, InputEntry input) {
        Intent intent = currentIntent(context, input);
        if (intent == null) return Result.UNAVAILABLE;
        try {
            if (new LockStore(context).isLocked(intent.getComponent().flattenToString())) return Result.LOCKED;
        } catch (Exception unavailable) { return Result.FAILED; }
        return dispatch(context, intent);
    }

    public static void open(final Activity activity, final InputEntry input, final Callback callback) {
        final Intent intent = currentIntent(activity, input);
        if (intent == null) { callback.complete(Result.UNAVAILABLE); return; }
        LauncherLocks.guard(activity, intent.getComponent().flattenToString(), new Runnable() {
            @Override public void run() {
                Intent current = currentIntent(activity, input);
                if (current == null || !intent.getComponent().equals(current.getComponent())) {
                    callback.complete(Result.UNAVAILABLE);
                    return;
                }
                callback.complete(dispatch(activity, current));
            }
        });
    }

    private static Result dispatch(Context context, Intent intent) {
        DeviceRoutes.Result result = DeviceRoutes.openSystemIntent(context, intent);
        return result == DeviceRoutes.Result.STARTED ? Result.STARTED
                : result == DeviceRoutes.Result.UNAVAILABLE ? Result.UNAVAILABLE : Result.FAILED;
    }
}
