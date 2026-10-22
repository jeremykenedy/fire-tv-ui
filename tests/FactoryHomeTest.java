package com.jeremykenedy.firetv.ui;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import java.util.ArrayList;
import java.util.List;
public final class FactoryHomeTest {
    public static void main(String[] args) {
        Context context=new Context();
        List<ResolveInfo> apps=new ArrayList<>();
        FactoryHome.addTile(apps,context.manager);
        if(!apps.isEmpty()||FactoryHome.open(context,"other.app"))throw new AssertionError();
        ResolveInfo home=new ResolveInfo();home.activityInfo=new ActivityInfo();
        home.activityInfo.packageName="com.amazon.tv.launcher";
        home.activityInfo.name="com.amazon.tv.launcher.ui.HomeActivity_vNext";
        home.activityInfo.exported=true;home.activityInfo.enabled=true;
        context.manager.home=home;
        FactoryHome.addTile(apps,context.manager);
        if(!apps.isEmpty())throw new AssertionError("Non-system app was presented as Factory UI");
        home.activityInfo.applicationInfo.flags=1;
        FactoryHome.addTile(apps,context.manager);FactoryHome.addTile(apps,context.manager);
        if(apps.size()!=1||!"Factory UI".contentEquals(apps.get(0).nonLocalizedLabel))throw new AssertionError();
        if(!FactoryHome.open(context,"com.amazon.tv.launcher")
                ||!home.activityInfo.name.equals(context.launched.component.name))throw new AssertionError("Explicit native component not launched");
        FactoryHome.homeKey(new android.view.KeyEvent());
        android.content.Intent previewIntent=FactoryHome.launchIntent(context.manager,"com.amazon.tv.launcher");
        if(previewIntent==null||FactoryHome.bypass(new android.view.accessibility.AccessibilityEvent()))throw new AssertionError("Resolving an icon armed a native launch");
        FactoryHome.beforeLaunch(context,previewIntent);
        if(!FactoryHome.bypass(new android.view.accessibility.AccessibilityEvent()))throw new AssertionError("Actual native start did not arm bypass");
        FactoryHome.homeKey(new android.view.KeyEvent());
        if(FactoryHome.bypass(new android.view.accessibility.AccessibilityEvent()))throw new AssertionError("Next Home did not resume routing");
        home.activityInfo.enabled=false;
        apps.clear();FactoryHome.addTile(apps,context.manager);
        if(!apps.isEmpty())throw new AssertionError("Manifest-disabled component presented without override");
        context.manager.componentState=1;FactoryHome.addTile(apps,context.manager);
        if(apps.size()!=1||!FactoryHome.open(context,"com.amazon.tv.launcher"))throw new AssertionError("Explicitly enabled Fire OS Home was rejected");
        home.activityInfo.exported=false;
        try{FactoryHome.open(context,"com.amazon.tv.launcher");throw new AssertionError();}
        catch(IllegalStateException expected){}
        System.out.println("Factory UI native component resolution checks passed");
    }
}
