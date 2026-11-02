package com.jeremykenedy.firetv.ui.nativeapp.appgrid;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import java.util.List;

public final class AppInventoryTest {
    public static void main(String[] args) {
        Context context = new Context();
        ResolveInfo tv = context.pm.add(Intent.CATEGORY_LEANBACK_LAUNCHER, "tv.app", "tv.app.Main", "TV");
        context.pm.add(Intent.CATEGORY_LAUNCHER, "tv.app", "tv.app.Main", "Duplicate");
        context.pm.add(Intent.CATEGORY_LAUNCHER, "ordinary.app", "ordinary.app.Main", "Ordinary");
        context.pm.add(Intent.CATEGORY_LAUNCHER, "ordinary.app", "ordinary.app.Other", "Other entry");
        context.pm.add(Intent.CATEGORY_LAUNCHER, "private.app", "private.app.Main", "Private").activityInfo.exported = false;
        context.pm.add(Intent.CATEGORY_LAUNCHER, "disabled.app", "disabled.app.Main", "Disabled").activityInfo.enabled = false;
        context.pm.add(Intent.CATEGORY_LAUNCHER, "permission.app", "permission.app.Main", "Permission").activityInfo.permission = "denied";
        context.pm.add(Intent.CATEGORY_LAUNCHER, "preview.app", "preview.app.Main", "Self");
        context.pm.add(Intent.CATEGORY_LAUNCHER, "removed.app", "removed.app.Main", "Removed");
        context.pm.removed = "removed.app/removed.app.Main";
        context.pm.add(Intent.CATEGORY_LAUNCHER, "override.app", "override.app.Main", "Override");
        context.pm.disabledComponent = "override.app/override.app.Main";
        List<AppInventory.App> defaultApps = AppInventory.load(context);
        check(defaultApps.size() == 1 && defaultApps.get(0).key().equals("tv.app/tv.app.Main"), "Default remains TV-only with original component");
        List<AppInventory.App> all = AppInventory.loadAllLaunchable(context);
        check(all.size() == 3, "Only accessible launchable activities remain; distinct entries in one app survive");
        AppInventory.App retained = all.get(2);
        check(retained.key().equals("tv.app/tv.app.Main") && retained.label.equals("TV"), "TV identity and metadata win duplicate discovery");
        check(retained.launchIntent().category.equals(Intent.CATEGORY_LEANBACK_LAUNCHER), "TV launches keep TV category");
        check(all.get(0).launchIntent().category.equals(Intent.CATEGORY_LAUNCHER), "Ordinary apps use their actual launcher category");
        check(AppInventory.load(context).size() == 1, "All apps discovery does not broaden later default loads");
        bundledFactoryArtwork();
        System.out.println("Opt-in launchable inventory, exact dedupe, category, access and removal checks passed");
    }
    private static void bundledFactoryArtwork() {
        Context context = new Context();
        context.pm.add(Intent.CATEGORY_LEANBACK_LAUNCHER, "com.amazon.tv.livetv", "Live", "Live TV");
        context.pm.add(Intent.CATEGORY_LEANBACK_LAUNCHER, "com.amazon.tv.launcher", "Home", "Factory UI");
        context.pm.add(Intent.CATEGORY_LEANBACK_LAUNCHER, "other.app", "Main", "Other");
        List<AppInventory.App> apps = AppInventory.load(context);
        check(apps.size() == 3, "Bundled artwork must retain both Amazon apps and unrelated apps");
        for (AppInventory.App app : apps) {
            int expected = app.component.pkg.equals("com.amazon.tv.livetv") ? 1
                    : app.component.pkg.equals("com.amazon.tv.launcher") ? 2 : 0;
            check(app.artwork.resource == expected, "Only the two Amazon apps receive owned factory artwork");
        }
        context.failArtwork = true;
        for (AppInventory.App app : AppInventory.load(context))
            check(app.artwork.resource == 0, "Unavailable bundled artwork retains package artwork");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
