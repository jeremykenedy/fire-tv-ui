package com.jeremykenedy.firetv.ui.nativeapp.providers;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.net.URISyntaxException;
import android.widget.Toast;
import com.jeremykenedy.firetv.ui.nativeapp.locks.LauncherLocks;
import com.jeremykenedy.firetv.ui.nativeapp.locks.LockStore;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Publisher-scoped activity handoff. A published title link does not establish playback support. */
public final class RecommendationActions {
    public enum Outcome { OPENED, PENDING, UNAVAILABLE, FAILED }

    public static final class Availability {
        public final boolean title;
        public final boolean app;
        public final boolean details;
        public final boolean play;
        public final String appLabel;
        Availability(boolean title, boolean app, boolean details, boolean play, String appLabel) {
            this.title = title;
            this.app = app;
            this.details = details;
            this.play = play;
            this.appLabel = appLabel;
        }
        public String titleLabel() { return "Open title in " + appLabel; }
        public String detailsLabel() { return "Details in " + appLabel; }
        public String playLabel() { return "Play in " + appLabel; }
        public String appLabel() { return "Open " + appLabel; }
        public String message() {
            return title ? "Open the title using the link shared by this app."
                    : app ? "This title has no supported link. You can open the app."
                    : "This app is unavailable or cannot open this title.";
        }
    }

    private RecommendationActions() {}

    public static Availability inspect(Context context, TvRecommendations.Program program) {
        String label = program.source;
        try {
            PackageManager packages = context.getPackageManager();
            label = packages.getApplicationLabel(packages.getApplicationInfo(program.source, 0)).toString();
        } catch (PackageManager.NameNotFoundException | RuntimeException unavailable) { }
        return new Availability((program.notificationKey != null ? NotificationRecommendations.canOpen(context, program)
                : titleIntent(context, program) != null), appIntent(context, program.source) != null,
                scopedIntent(context, program.source, program.detailsIntentUri) != null,
                scopedIntent(context, program.source, program.playIntentUri) != null, label);
    }

    public static Outcome launchTitle(Context context, TvRecommendations.Program program) {
        if (program.notificationKey != null) return NotificationRecommendations.open(context, program);
        return launch(context, titleIntent(context, program));
    }

    public static Outcome launchTitle(Context context, TvRecommendations.Program program, final java.util.concurrent.Callable<Boolean> stillValid) {
        if (stillValid == null || program.notificationKey != null) return Outcome.UNAVAILABLE;
        return launch(context, titleIntent(context, program), stillValid);
    }

    public static Outcome launchDetails(Context context, TvRecommendations.Program program) {
        return launch(context, scopedIntent(context, program.source, program.detailsIntentUri));
    }

    public static Outcome launchPlay(Context context, TvRecommendations.Program program) {
        return launch(context, scopedIntent(context, program.source, program.playIntentUri));
    }

    public static Outcome openApp(Context context, TvRecommendations.Program program) {
        return launch(context, appIntent(context, program.source));
    }

    private static Outcome launch(final Context context, final Intent intent) {
        return launch(context, intent, null);
    }

    private static Outcome launch(final Context context, final Intent intent, final java.util.concurrent.Callable<Boolean> stillValid) {
        if (intent == null || intent.getComponent() == null) return Outcome.UNAVAILABLE;
        final String destination = intent.getComponent().flattenToString();
        final String source = intent.getComponent().getPackageName();
        return guarded(context, source, destination, new Dispatch() {
            @Override public Outcome run() {
                try { if (stillValid != null && !Boolean.TRUE.equals(stillValid.call())) return Outcome.UNAVAILABLE; }
                catch (Exception unavailable) { return Outcome.UNAVAILABLE; }
                Intent checked = pin(context, new Intent(intent), source);
                if (checked == null || !destination.equals(checked.getComponent().flattenToString())) return Outcome.UNAVAILABLE;
                try { context.startActivity(checked); return Outcome.OPENED; }
                catch (RuntimeException unavailable) { return Outcome.FAILED; }
            }
        });
    }

    interface Dispatch { Outcome run(); }

    static Outcome guarded(Context context, String source, String destination, final Dispatch dispatch) {
        if (!(context instanceof Activity)) return Outcome.UNAVAILABLE;
        final Activity activity = (Activity) context;
        if (activity.isFinishing() || activity.isDestroyed()) return Outcome.UNAVAILABLE;
        Set<String> required = new LinkedHashSet<>();
        Intent launcher = appIntent(context, source);
        if (launcher != null && launcher.getComponent() != null) required.add(launcher.getComponent().flattenToString());
        if (destination != null) required.add(destination);
        try {
            // A PendingIntent hides its destination; one locked publisher component requires the common PIN.
            if (destination == null) required.clear();
            for (String key : destination == null ? new LockStore(context).lockedTargets() : java.util.Collections.<String>emptySet()) {
                ComponentName component = ComponentName.unflattenFromString(key);
                if (component != null && source.equals(component.getPackageName())) {
                    required.add(component.flattenToString());
                    break;
                }
            }
        } catch (Exception unavailable) { return Outcome.UNAVAILABLE; }
        final Outcome[] result = {Outcome.PENDING};
        final boolean[] returned = {false};
        guard(activity, new ArrayList<>(required), 0, new Runnable() {
            @Override public void run() {
                if (activity.isFinishing() || activity.isDestroyed()) { result[0] = Outcome.UNAVAILABLE; return; }
                result[0] = dispatch.run();
                if (returned[0] && result[0] != Outcome.OPENED)
                    Toast.makeText(activity, "This recommendation is unavailable. Open the source app to continue.", Toast.LENGTH_SHORT).show();
            }
        });
        returned[0] = true;
        return result[0];
    }

    private static void guard(final Activity activity, final List<String> keys, final int index, final Runnable launch) {
        if (index == keys.size()) { launch.run(); return; }
        LauncherLocks.guard(activity, keys.get(index), new Runnable() {
            @Override public void run() { guard(activity, keys, index + 1, launch); }
        });
    }

    static Intent titleIntent(Context context, TvRecommendations.Program program) {
        return scopedIntent(context, program.source, program.intentUri);
    }

    private static Intent scopedIntent(Context context, String source, String intentUri) {
        if (!RecommendationKeys.validPackage(source) || intentUri == null
                || intentUri.isEmpty() || intentUri.length() > 16384) return null;
        try {
            // Inspect grant flags before Android's default URI parser strips them. No unchecked intent is launched.
            Intent intent = Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME | Intent.URI_ALLOW_UNSAFE);
            if (!Intent.ACTION_VIEW.equals(intent.getAction())
                    || intent.getSelector() != null || intent.getClipData() != null
                    || (intent.getFlags() & ~Intent.FLAG_ACTIVITY_NEW_TASK) != 0
                    || (intent.getPackage() != null && !source.equals(intent.getPackage()))
                    || (intent.getComponent() != null && !source.equals(intent.getComponent().getPackageName()))) return null;
            String scheme = intent.getData() == null ? null : intent.getData().getScheme();
            if ("file".equalsIgnoreCase(scheme) || "content".equalsIgnoreCase(scheme)
                    || "android.resource".equalsIgnoreCase(scheme) || "javascript".equalsIgnoreCase(scheme)) return null;
            intent.setPackage(source);
            return pin(context, intent, source);
        } catch (URISyntaxException | RuntimeException invalid) { return null; }
    }

    private static Intent appIntent(Context context, String source) {
        if (!RecommendationKeys.validPackage(source)) return null;
        try {
            PackageManager packages = context.getPackageManager();
            Intent original = packages.getLeanbackLaunchIntentForPackage(source);
            if (original == null) original = packages.getLaunchIntentForPackage(source);
            if (original == null) return null;
            Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setPackage(source).setComponent(original.getComponent());
            return pin(context, intent, source);
        } catch (RuntimeException unavailable) { return null; }
    }

    private static Intent pin(Context context, Intent intent, String source) {
        ResolveInfo resolved = context.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        ActivityInfo activity = resolved == null ? null : resolved.activityInfo;
        if (activity == null || !source.equals(activity.packageName) || (!activity.exported && !source.equals(context.getPackageName())) || !activity.enabled
                || activity.applicationInfo == null || !activity.applicationInfo.enabled
                || (activity.permission != null && !activity.permission.isEmpty())) return null;
        return intent.setComponent(new ComponentName(activity.packageName, activity.name))
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
}
