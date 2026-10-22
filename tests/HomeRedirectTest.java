package com.jeremykenedy.firetv.ui;
import android.os.Handler;
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
        event(service,"tv.own.owntv","tv.own.owntv.MainActivity");home(service);
        if(service.launches!=2)throw new AssertionError("App-switch Home did not return");
        event(service,"com.amazon.tv.settings.v2","com.amazon.tv.settings.MainActivity");home(service);
        if(Handler.pending==null||service.launches!=2)throw new AssertionError("Native settings handoff lost its grace period");
        event(service,"com.amazon.tv.launcher","com.amazon.tv.launcher.ui.MainSettingsActivity");
        if(Handler.pending!=null)throw new AssertionError("Native settings was interrupted by pending redirect");
        System.out.println("Home routing own-settings and native-settings behavior passed");
    }
}
