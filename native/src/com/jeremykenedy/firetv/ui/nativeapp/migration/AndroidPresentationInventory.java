package com.jeremykenedy.firetv.ui.nativeapp.migration;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import com.jeremykenedy.firetv.ui.nativeapp.providers.AndroidRecommendationIdentityInventory;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Read-only, exact platform inventory. Call on a worker before preference consumers start. */
public final class AndroidPresentationInventory {
    private AndroidPresentationInventory() {}

    public static PresentationMappingContext read(Context context, PreferenceSnapshot legacy) throws IOException {
        Map<String, String> components = IdentityInventory.components(launchActivities(context));
        Map<String, String> labels = new LinkedHashMap<>();
        try {
            TvInputManager manager = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
            if (manager != null) for (TvInputInfo input : manager.getTvInputList()) {
                CharSequence label = input.loadLabel(context);
                labels.put(input.getId(), label == null ? "" : label.toString());
            }
        } catch (RuntimeException unavailable) {
            // An absent relationship is never guessed. A referenced unavailable input blocks mapping.
            labels.clear();
        }
        Map<String, PresentationMappingContext.Feed> feeds = new LinkedHashMap<>();
        Map<String, PresentationMappingContext.Item> items = new LinkedHashMap<>();
        boolean recommendations = false;
        for (String key : new String[]{"featured_feed_order", "featured_card_order", "hidden_featured_cards", "featured_feed_rotation_seconds"}) {
            Object value = legacy.values().get(key);
            if (value instanceof Set && !((Set<?>) value).isEmpty()) recommendations = true;
        }
        if (recommendations) AndroidRecommendationIdentityInventory.collect(context, feeds, items);
        Set<String> packs = advertisedPacks(context);
        List<PrivateMediaReference> media = new ArrayList<>();
        for (String key : new String[]{"custom_wallpaper_uri", "custom_video_uri"}) {
            Object value = legacy.values().get(key);
            if (value instanceof String && ((String) value).startsWith("file:")) {
                try {
                    PrivateMediaReference reference = AndroidPrivateMedia.verify(context, (String) value);
                    boolean duplicate = false;
                    for (PrivateMediaReference retained : media) if (retained.uri.equals(reference.uri)) duplicate = true;
                    if (!duplicate) media.add(reference);
                }
                catch (IOException unavailable) {
                    // Retain the exact original reference; active unsupported media rejects in the mapper.
                }
            }
        }
        Map<String, String> icons = new LinkedHashMap<>();
        return new PresentationMappingContext(components, feeds, items, icons,
                IdentityInventory.inputs(labels), packs, media);
    }

    static List<String> launchActivities(Context context) throws IOException {
        Set<String> found = new LinkedHashSet<>();
        PackageManager packages = context.getPackageManager();
        try {
            for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER})
                for (ResolveInfo result : packages.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                    ActivityInfo info = result.activityInfo;
                    if (info == null || !info.exported || !info.enabled || info.applicationInfo == null
                            || !info.applicationInfo.enabled || context.getPackageName().equals(info.packageName)) continue;
                    ComponentName component = new ComponentName(info.packageName, info.name);
                    int enabled = packages.getComponentEnabledSetting(component);
                    if (enabled != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && enabled != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) continue;
                    if (info.permission != null && !info.permission.isEmpty()
                            && context.checkSelfPermission(info.permission) != PackageManager.PERMISSION_GRANTED) continue;
                    found.add(component.flattenToString());
                }
        } catch (RuntimeException unavailable) { throw new IOException("Launch activity inventory is unavailable. Existing settings were kept."); }
        return new ArrayList<>(found);
    }

    private static Set<String> advertisedPacks(Context context) {
        Set<String> result = new LinkedHashSet<>();
        try {
            for (String action : new String[]{"org.adw.launcher.THEMES", "com.gau.go.launcherex.theme"})
                for (ResolveInfo candidate : context.getPackageManager().queryIntentActivities(new Intent(action), 0)) {
                    ActivityInfo info = candidate.activityInfo;
                    if (info != null && info.enabled && info.exported && info.applicationInfo != null && info.applicationInfo.enabled)
                        result.add(info.packageName);
                }
        } catch (RuntimeException unavailable) { result.clear(); }
        return result;
    }
}
