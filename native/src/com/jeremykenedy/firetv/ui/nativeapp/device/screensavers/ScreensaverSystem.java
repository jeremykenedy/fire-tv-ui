package com.jeremykenedy.firetv.ui.nativeapp.device.screensavers;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.XmlResourceParser;
import android.provider.Settings;
import java.util.ArrayList;
import java.util.List;

/** Ordinary platform capability checks; never grants permissions or starts an idle timer. */
public final class ScreensaverSystem {
    private static final String COMPONENTS = "screensaver_components";
    private static final String ENABLED = "screensaver_enabled";
    private static final String BIND_DREAM = "android.permission.BIND_DREAM_SERVICE";
    private ScreensaverSystem() {}

    public static List<ScreensaverEntry> installed(Context context) {
        PackageManager pm = context.getPackageManager();
        List<ScreensaverEntry> result = new ArrayList<>();
        for (PackageInfo pkg : pm.getInstalledPackages(PackageManager.GET_SERVICES | PackageManager.GET_META_DATA)) {
            if (pkg.services != null) for (ServiceInfo service : pkg.services) add(pm, service, result);
        }
        for (ResolveInfo match : pm.queryIntentServices(new Intent("android.service.dreams.DreamService"), PackageManager.GET_META_DATA)) {
            add(pm, match.serviceInfo, result);
        }
        return ScreensaverEntry.sorted(result);
    }

    private static void add(PackageManager pm, ServiceInfo service, List<ScreensaverEntry> result) {
        if (service == null || !service.enabled || !service.applicationInfo.enabled
                || !BIND_DREAM.equals(service.permission) || !service.exported
                || "com.android.systemui".equals(service.packageName)
                || "com.amazon.tv.quicksettings".equals(service.packageName)) return;
        String label = "com.amazon.ftv.screensaver".equals(service.packageName)
                ? "Amazon screensaver" : service.loadLabel(pm).toString();
        result.add(new ScreensaverEntry(new ComponentName(service.packageName, service.name).flattenToString(), label));
    }

    public static String active(Context context) {
        return ScreensaverEntry.firstActive(Settings.Secure.getString(context.getContentResolver(), COMPONENTS));
    }

    public static boolean enabled(Context context) {
        return Settings.Secure.getInt(context.getContentResolver(), ENABLED, 0) == 1;
    }

    public static boolean canSelect(Context context) {
        return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
    }

    public static void enabled(Context context, boolean enabled) {
        if (!canSelect(context)) throw new IllegalStateException("Change screensaver On or Off in the TV's own settings.");
        if (!Settings.Secure.putInt(context.getContentResolver(), ENABLED, enabled ? 1 : 0)
                || enabled(context) != enabled) throw new IllegalStateException("The TV did not save the screensaver setting.");
    }

    public static int idleDelay(Context context) {
        return Settings.System.getInt(context.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, 300000);
    }

    public static void idleDelay(Context context, int milliseconds) {
        if (milliseconds <= 0) throw new IllegalArgumentException("Choose a positive idle delay.");
        if (!Settings.System.canWrite(context)) throw new IllegalStateException("Allow system settings access to change the shared idle delay.");
        if (!Settings.System.putInt(context.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, milliseconds)
                || idleDelay(context) != milliseconds) throw new IllegalStateException("The TV did not save the idle delay.");
    }

    public static void setActive(Context context, String component) {
        boolean installed = false;
        for (ScreensaverEntry entry : installed(context)) if (entry.component.equals(ScreensaverEntry.normalize(component))) installed = true;
        if (!installed) throw new IllegalStateException("This screensaver is no longer installed or available.");
        if (!canSelect(context)) throw new IllegalStateException("Choose this saver in the TV's screensaver settings. Direct selection is unavailable to this app.");
        String previous = Settings.Secure.getString(context.getContentResolver(), COMPONENTS);
        int wasEnabled = Settings.Secure.getInt(context.getContentResolver(), ENABLED, 0);
        try {
            if (!Settings.Secure.putString(context.getContentResolver(), COMPONENTS, component)
                    || !active(context).equals(ScreensaverEntry.normalize(component))
                    || !Settings.Secure.putInt(context.getContentResolver(), ENABLED, 1) || !enabled(context)) {
                throw new IllegalStateException("The TV did not save the selected screensaver.");
            }
        } catch (RuntimeException failed) {
            try {
                Settings.Secure.putString(context.getContentResolver(), COMPONENTS, previous);
                Settings.Secure.putInt(context.getContentResolver(), ENABLED, wasEnabled);
            } catch (RuntimeException ignored) { }
            throw failed;
        }
    }

    public static Intent configuration(Context context, String component) {
        ComponentName dream = ComponentName.unflattenFromString(component);
        if (dream == null) return null;
        PackageManager pm = context.getPackageManager();
        try {
            ServiceInfo service = pm.getServiceInfo(dream, PackageManager.GET_META_DATA);
            XmlResourceParser xml = service.loadXmlMetaData(pm, "android.service.dream");
            if (xml != null) {
                try {
                    while (xml.next() != XmlResourceParser.END_DOCUMENT) {
                        if (xml.getEventType() == XmlResourceParser.START_TAG && "dream".equals(xml.getName())) {
                            String value = xml.getAttributeValue("http://schemas.android.com/apk/res/android", "settingsActivity");
                            if (value != null) {
                                ComponentName settings = value.contains("/") ? ComponentName.unflattenFromString(value)
                                        : new ComponentName(dream.getPackageName(), value.startsWith(".") ? dream.getPackageName() + value : value);
                                Intent intent = accessible(context, dream, settings);
                                if (intent != null) return intent;
                            }
                            break;
                        }
                    }
                } finally { xml.close(); }
            }
        } catch (Exception unavailable) { }
        Intent launcher = pm.getLaunchIntentForPackage(dream.getPackageName());
        if (launcher == null) launcher = pm.getLeanbackLaunchIntentForPackage(dream.getPackageName());
        return launcher == null ? null : accessible(context, dream, launcher.getComponent());
    }

    public static Intent preview(Context context, String component) {
        ComponentName dream = ComponentName.unflattenFromString(component);
        if (dream == null) return null;
        for (String[] route : ReviewedPreviews.ROUTES) {
            if (!ScreensaverEntry.normalize(route[0]).equals(ScreensaverEntry.normalize(component))) continue;
            for (int index = 1; index < route.length; index++) {
                Intent intent = accessible(context, dream, ComponentName.unflattenFromString(route[index]));
                if (intent != null) return intent;
            }
        }
        return null;
    }

    public static boolean directPreview(String component, Intent intent) {
        if (intent == null) return false;
        for (String[] route : ReviewedPreviews.ROUTES) {
            if (ScreensaverEntry.normalize(route[0]).equals(ScreensaverEntry.normalize(component))) {
                return intent.getComponent().equals(ComponentName.unflattenFromString(route[1]));
            }
        }
        return false;
    }

    private static Intent accessible(Context context, ComponentName dream, ComponentName activity) {
        if (activity == null || !activity.getPackageName().equals(dream.getPackageName())) return null;
        try {
            ActivityInfo info = context.getPackageManager().getActivityInfo(activity, 0);
            if (info.enabled && info.exported && info.applicationInfo.enabled
                    && (info.permission == null || info.permission.isEmpty()
                    || context.checkSelfPermission(info.permission) == PackageManager.PERMISSION_GRANTED)) {
                return new Intent().setComponent(activity);
            }
        } catch (PackageManager.NameNotFoundException | SecurityException unavailable) { }
        return null;
    }

    public static Intent systemSettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_DREAM_SETTINGS);
        ResolveInfo resolved = context.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (resolved == null || resolved.activityInfo == null) return null;
        ComponentName component = new ComponentName(resolved.activityInfo.packageName, resolved.activityInfo.name);
        return accessible(context, component, component);
    }
}
