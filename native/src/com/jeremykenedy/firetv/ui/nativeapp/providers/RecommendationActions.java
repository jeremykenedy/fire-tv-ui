package com.jeremykenedy.firetv.ui.nativeapp.providers;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.net.URISyntaxException;

/** Publisher-scoped activity handoff. A published title link does not establish playback support. */
public final class RecommendationActions {
    public enum Outcome { OPENED, UNAVAILABLE, FAILED }

    public static final class Availability {
        public final boolean title;
        public final boolean app;
        public final String appLabel;
        Availability(boolean title, boolean app, String appLabel) {
            this.title = title;
            this.app = app;
            this.appLabel = appLabel;
        }
        public String titleLabel() { return "Open title in " + appLabel; }
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
        return new Availability(titleIntent(context, program) != null, appIntent(context, program.source) != null, label);
    }

    public static Outcome launchTitle(Context context, TvRecommendations.Program program) {
        return launch(context, titleIntent(context, program));
    }

    public static Outcome openApp(Context context, TvRecommendations.Program program) {
        return launch(context, appIntent(context, program.source));
    }

    private static Outcome launch(Context context, Intent intent) {
        if (intent == null) return Outcome.UNAVAILABLE;
        try {
            context.startActivity(intent);
            return Outcome.OPENED;
        } catch (RuntimeException unavailable) { return Outcome.FAILED; }
    }

    static Intent titleIntent(Context context, TvRecommendations.Program program) {
        if (!RecommendationKeys.validPackage(program.source) || program.intentUri == null
                || program.intentUri.isEmpty() || program.intentUri.length() > 16384) return null;
        try {
            // Inspect grant flags before Android's default URI parser strips them. No unchecked intent is launched.
            Intent intent = Intent.parseUri(program.intentUri, Intent.URI_INTENT_SCHEME | Intent.URI_ALLOW_UNSAFE);
            if (!Intent.ACTION_VIEW.equals(intent.getAction())
                    || intent.getSelector() != null || intent.getClipData() != null
                    || (intent.getFlags() & ~Intent.FLAG_ACTIVITY_NEW_TASK) != 0
                    || (intent.getPackage() != null && !program.source.equals(intent.getPackage()))
                    || (intent.getComponent() != null && !program.source.equals(intent.getComponent().getPackageName()))) return null;
            String scheme = intent.getData() == null ? null : intent.getData().getScheme();
            if ("file".equalsIgnoreCase(scheme) || "content".equalsIgnoreCase(scheme)
                    || "android.resource".equalsIgnoreCase(scheme) || "javascript".equalsIgnoreCase(scheme)) return null;
            intent.setPackage(program.source);
            return pin(context, intent, program.source);
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
        if (activity == null || !source.equals(activity.packageName) || !activity.exported || !activity.enabled
                || activity.applicationInfo == null || !activity.applicationInfo.enabled
                || (activity.permission != null && !activity.permission.isEmpty())) return null;
        return intent.setComponent(new ComponentName(activity.packageName, activity.name))
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
}
