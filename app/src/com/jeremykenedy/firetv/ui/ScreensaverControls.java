package com.jeremykenedy.firetv.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.XmlResourceParser;

/** Resolves an installed screensaver's own accessible settings or launcher. */
final class ScreensaverControls {
    private ScreensaverControls() {
    }

    static Intent resolvePreview(Context context, String dream) {
        ComponentName service = ComponentName.unflattenFromString(dream == null ? "" : dream);
        if (service == null) {
            return null;
        }
        try {
            for (ScreensaverCatalog.Entry entry : ScreensaverCatalog.load(context)) {
                if (!Screensavers.fullComponent(entry.dream).equals(Screensavers.fullComponent(dream))
                        || entry.previewControls.isEmpty()) {
                    continue;
                }
                ComponentName activity = ComponentName.unflattenFromString(entry.previewControls);
                return samePackage(service, activity) && canLaunch(context, activity)
                        ? new Intent().setComponent(activity) : null;
            }
        } catch (Exception unavailable) {
            throw new IllegalStateException("The screensaver preview controls could not be read.", unavailable);
        }
        return resolve(context, dream);
    }

    static Intent resolve(Context context, String dream) {
        ComponentName serviceName = ComponentName.unflattenFromString(dream == null ? "" : dream);
        if (serviceName == null) {
            return null;
        }
        PackageManager pm = context.getPackageManager();
        try {
            ServiceInfo service = pm.getServiceInfo(serviceName, PackageManager.GET_META_DATA);
            XmlResourceParser xml = service.loadXmlMetaData(pm, "android.service.dream");
            if (xml != null) {
                try {
                    while (xml.next() != XmlResourceParser.END_DOCUMENT) {
                        if (xml.getEventType() == XmlResourceParser.START_TAG && "dream".equals(xml.getName())) {
                            String activity = xml.getAttributeValue("http://schemas.android.com/apk/res/android", "settingsActivity");
                            if (activity != null && !activity.isEmpty()) {
                                ComponentName name = activity.contains("/") ? ComponentName.unflattenFromString(activity)
                                        : new ComponentName(serviceName.getPackageName(), activity.startsWith(".")
                                                ? serviceName.getPackageName() + activity : activity);
                                if (samePackage(serviceName, name) && canLaunch(context, name)) {
                                    return new Intent().setComponent(name);
                                }
                            }
                            break;
                        }
                    }
                } finally {
                    xml.close();
                }
            }
        } catch (Exception unavailable) {
            // A missing or malformed settings resource can still have an accessible launcher.
        }
        Intent launcher = pm.getLaunchIntentForPackage(serviceName.getPackageName());
        return launcher != null && samePackage(serviceName, launcher.getComponent())
                && canLaunch(context, launcher.getComponent()) ? launcher : null;
    }

    private static boolean samePackage(ComponentName service, ComponentName activity) {
        return activity != null && service.getPackageName().equals(activity.getPackageName());
    }

    static boolean canLaunch(Context context, ComponentName component) {
        try {
            ActivityInfo activity = context.getPackageManager().getActivityInfo(component, 0);
            return activity.enabled && activity.exported && activity.applicationInfo.enabled
                    && (activity.permission == null || activity.permission.isEmpty()
                            || context.checkSelfPermission(activity.permission) == PackageManager.PERMISSION_GRANTED);
        } catch (PackageManager.NameNotFoundException | SecurityException unavailable) {
            return false;
        }
    }
}
