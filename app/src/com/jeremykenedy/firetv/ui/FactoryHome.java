package com.jeremykenedy.firetv.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import java.util.List;

/** Resolves the TV's actual Amazon system launcher for the Factory UI app tile. */
public final class FactoryHome {
    private static final String PACKAGE = "com.amazon.tv.launcher";
    private static final FactoryHomeBypass BYPASS = new FactoryHomeBypass();

    private FactoryHome() {
    }

    private static ResolveInfo resolve(PackageManager manager) {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(PACKAGE);
        ResolveInfo info = manager.resolveActivity(home, 0);
        if (info == null || info.activityInfo == null || !info.activityInfo.exported
                || !PACKAGE.equals(info.activityInfo.packageName)
                || info.activityInfo.permission != null
                || (info.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) return null;
        if (!info.activityInfo.enabled && manager.getComponentEnabledSetting(new ComponentName(
                info.activityInfo.packageName, info.activityInfo.name))
                != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return null;
        return info;
    }

    public static void addTile(List<ResolveInfo> apps, PackageManager manager) {
        ResolveInfo home = resolve(manager);
        if (home == null) return;
        for (int i = apps.size() - 1; i >= 0; i--) {
            if (apps.get(i).activityInfo != null && PACKAGE.equals(apps.get(i).activityInfo.packageName)) apps.remove(i);
        }
        home.nonLocalizedLabel = "Factory UI";
        apps.add(home);
    }

    public static Intent launchIntent(PackageManager manager, String pkg) {
        if (!PACKAGE.equals(pkg)) return null;
        ResolveInfo home = resolve(manager);
        return home == null ? null : new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setComponent(new ComponentName(home.activityInfo.packageName, home.activityInfo.name))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    }

    public static void beforeLaunch(Context context, Intent intent) {
        if (intent == null || intent.getComponent() == null
                || !PACKAGE.equals(intent.getComponent().getPackageName())) return;
        ResolveInfo home = resolve(context.getPackageManager());
        if (home != null && home.activityInfo.name.equals(intent.getComponent().getClassName())) {
            BYPASS.arm(SystemClock.uptimeMillis());
            android.util.Log.i("FireTvFactoryHome", "Explicit native Home start armed");
        }
    }

    public static boolean open(Context context, String pkg) {
        if (!PACKAGE.equals(pkg)) return false;
        ResolveInfo home = resolve(context.getPackageManager());
        if (home == null) throw new IllegalStateException("The native Amazon home screen is unavailable.");
        BYPASS.arm(SystemClock.uptimeMillis());
        android.util.Log.i("FireTvFactoryHome", "Explicit native Home launch armed");
        try {
            context.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .setComponent(new ComponentName(home.activityInfo.packageName, home.activityInfo.name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
            return true;
        } catch (RuntimeException failed) {
            BYPASS.clear();
            throw failed;
        }
    }

    public static void homeKey(KeyEvent event) {
        if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_HOME) BYPASS.clear();
    }

    public static boolean bypass(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return false;
        CharSequence pkg = event.getPackageName();
        CharSequence cls = event.getClassName();
        boolean home = pkg != null && cls != null && PACKAGE.contentEquals(pkg)
                && cls.toString().startsWith("com.amazon.tv.launcher.ui.HomeActivity");
        boolean leaving = pkg != null && !PACKAGE.contentEquals(pkg)
                && !"com.jeremykenedy.firetv.ui".contentEquals(pkg);
        boolean skipped = BYPASS.skip(home, leaving, event.getEventTime());
        android.util.Log.i("FireTvFactoryHome", "Window home=" + home + " leaving=" + leaving
                + " skipped=" + skipped + " age=" + (SystemClock.uptimeMillis() - event.getEventTime()));
        return skipped;
    }
}
