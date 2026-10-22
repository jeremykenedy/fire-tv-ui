package com.jeremykenedy.firetv.ui;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import java.util.ArrayList;
import java.util.List;

public final class NativeLiveTvTest {
    public static void main(String[] args) {
        Context context = new Context();
        List<ResolveInfo> apps = new ArrayList<>();
        NativeLiveTv.addTile(apps, context.manager);
        check(apps.isEmpty(), "Unavailable guide appeared");
        check(!NativeLiveTv.open(context, "other.app"), "Other app launch intercepted");
        ResolveInfo guide = new ResolveInfo();
        guide.activityInfo = new ActivityInfo();
        guide.activityInfo.packageName = "com.amazon.tv.livetv";
        guide.activityInfo.name = "com.amazon.tv.livetv.guide.GuideWrapperActivity";
        guide.activityInfo.exported = true;
        guide.activityInfo.enabled = true;
        guide.activityInfo.applicationInfo.flags = 1;
        context.manager.home = guide;
        NativeLiveTv.addTile(apps, context.manager);
        NativeLiveTv.addTile(apps, context.manager);
        check(apps.size() == 1 && "Live TV".contentEquals(apps.get(0).nonLocalizedLabel), "Guide tile duplicated or mislabeled");
        check(NativeLiveTv.launchIntent(context.manager,"other.app")==null,"Other app received native guide intent");
        check(NativeLiveTv.launchIntent(context.manager,"com.amazon.tv.livetv").getComponent().getClassName().endsWith("GuideWrapperActivity"),"Central remote resolver missed public wrapper");
        check(NativeLiveTv.open(context, "com.amazon.tv.livetv"), "Native guide launch not handled");
        check(Intent.ACTION_VIEW.equals(context.launched.action), "Guide action changed");
        check("livetv://showGuideButtonContent".equals(context.launched.data.toString()), "Public guide URI changed");
        check(guide.activityInfo.name.equals(context.launched.component.name), "Reviewed wrapper not launched explicitly");
        for (int invalid = 0; invalid < 5; invalid++) {
            guide.activityInfo.exported = invalid != 0;
            guide.activityInfo.enabled = invalid != 1;
            guide.activityInfo.packageName = invalid == 2 ? "third.party" : "com.amazon.tv.livetv";
            guide.activityInfo.applicationInfo.flags = invalid == 3 ? 0 : 1;
            guide.activityInfo.permission = invalid == 4 ? "com.amazon.tv.livetv.permission.SHOW_EPG" : null;
            apps.clear();
            NativeLiveTv.addTile(apps, context.manager);
            check(apps.isEmpty(), "Inaccessible or nonnative guide presented");
            context.launched = null;
            try {
                NativeLiveTv.open(context, "com.amazon.tv.livetv");
                throw new AssertionError("Unavailable guide launched");
            } catch (IllegalStateException expected) {
                check(context.launched == null, "Failed guide attempted a privileged launch");
            }
        }
        System.out.println("Public native Live TV URI and accessibility checks passed");
    }

    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
    }
}
