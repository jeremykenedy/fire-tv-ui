package com.jeremykenedy.firetv.ui;
import android.os.Handler;
import android.content.pm.PackageManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
public final class HomeRedirectTest {
    static void event(HomeRedirectService service,String pkg,String cls) {
        service.onAccessibilityEvent(new AccessibilityEvent(pkg,cls));
    }
    static void home(HomeRedirectService service) {
        event(service,"com.amazon.tv.launcher","com.amazon.tv.launcher.ui.HomeActivity_vNext");
    }
    public static void main(String[] args) {
        HomeRedirectService service=new HomeRedirectService();
        event(service,"com.jeremykenedy.firetv.ui","com.jeremykenedy.firetv.ui.ScreensaverActivity");
        home(service);
        if(service.launches!=1||Handler.pending!=null)throw new AssertionError("Home from own settings was deferred and can be swallowed by a transient window");
        if(service.lastLaunch.flags!=7||!"home".equals(service.lastLaunch.category))throw new AssertionError("Home intent flags or category changed");
        event(service,"tv.own.owntv","tv.own.owntv.MainActivity");home(service);
        if(service.launches!=2)throw new AssertionError("App-switch Home did not return");
        event(service,"com.amazon.tv.settings.v2","com.amazon.tv.settings.MainActivity");home(service);
        if(Handler.pending==null||service.launches!=2)throw new AssertionError("Native settings handoff lost its grace period");
        event(service,"com.amazon.tv.launcher","com.amazon.tv.launcher.ui.MainSettingsActivity");
        if(Handler.pending!=null)throw new AssertionError("Native settings was interrupted by pending redirect");
        event(service,"tv.own.owntv","tv.own.owntv.MainActivity");
        service.failLaunch=true;
        try { home(service); }
        catch(RuntimeException failure) { throw new AssertionError("Launch failure escaped the accessibility callback",failure); }
        if(service.launches!=2||service.attempts!=3)throw new AssertionError("Failed launch changed successful routing");
        if(!Log.entries.contains("Window Home launch failed")||Log.entries.toString().contains("synthetic private detail"))throw new AssertionError("Missing or sensitive failure diagnostics");
        service.failLaunch=false;
        home(service);
        if(service.launches!=3||service.attempts!=4)throw new AssertionError("A transient failure broke the next Home");

        PackageManager.missing=true;
        home(service);
        if(service.attempts!=4||!Log.entries.contains("Window Home unavailable"))throw new AssertionError("Missing launcher did not retain system Home");
        PackageManager.missing=false;
        PackageManager.failResolution=true;
        try { home(service); }
        catch(RuntimeException failure) { throw new AssertionError("Resolver failure escaped the accessibility callback",failure); }
        if(service.attempts!=4||Log.entries.toString().contains("synthetic resolver detail"))throw new AssertionError("Resolver failure launched or exposed details");
        PackageManager.failResolution=false;

        event(service,"com.amazon.tv.settings.v2","com.amazon.tv.settings.MainActivity");
        home(service);
        if(Handler.pending==null)throw new AssertionError("Failure handling removed the settings grace period");
        service.failLaunch=true;
        Runnable pending=Handler.pending;Handler.pending=null;
        try { pending.run(); }
        catch(RuntimeException failure) { throw new AssertionError("Deferred launch failure escaped its callback",failure); }
        service.failLaunch=false;
        event(service,"tv.own.owntv","tv.own.owntv.MainActivity");home(service);
        if(service.launches!=4||service.attempts!=6)throw new AssertionError("Deferred failure broke later Home routing");
        System.out.println("Home routing and transient launch failure recovery passed");
    }
}
