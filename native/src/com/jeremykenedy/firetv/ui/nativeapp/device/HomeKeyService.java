package com.jeremykenedy.firetv.ui.nativeapp.device;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/** Original Home routing service; preview builds are intentionally unable to redirect Home. */
public class HomeKeyService extends AccessibilityService {
    public static final String PRODUCTION_PACKAGE = HomeRoutingPolicy.PRODUCTION_PACKAGE;
    public static final String HOME_KEYS = "source_home_keys_enabled";
    public static final String HOME_WINDOWS = "source_home_window_fallback_enabled";
    private static final String MAIN = "com.jeremykenedy.firetv.ui.nativeapp.MainActivity";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean consumedPress;
    private ComponentName pendingHome;
    private final Runnable redirect = new Runnable() {
        @Override public void run() {
            if (pendingHome != null && enabled(HOME_WINDOWS)) openHome();
            pendingHome = null;
        }
    };

    private boolean enabled(String key) {
        return HomeRoutingPolicy.allowed(getPackageName(),
                getSharedPreferences("launcher_prefs", MODE_PRIVATE).getAll().get(key));
    }

    @Override protected void onServiceConnected() {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) return;
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        // XML must also declare canRequestFilterKeyEvents; the OS may refuse this capability.
        info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        setServiceInfo(info);
    }

    @Override protected boolean onKeyEvent(KeyEvent event) {
        if (event == null || event.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        if (event.getAction() == KeyEvent.ACTION_UP) {
            boolean handled = consumedPress;
            consumedPress = false;
            return handled;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        if (event.getRepeatCount() > 0) return consumedPress;
        consumedPress = false;
        cancelPending();
        DeviceRoutes.ROUTING.homePressed();
        if (!enabled(HOME_KEYS)) return false;
        // Consume a complete key pair only after a real launch, or when Home is already visible.
        consumedPress = DeviceRoutes.ROUTING.isHomeVisible()
                || DeviceRoutes.ROUTING.launchPending(SystemClock.uptimeMillis()) || openHome();
        return consumedPress;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || event.getPackageName() == null || event.getClassName() == null) return;
        cancelPending();
        String pkg = event.getPackageName().toString();
        String cls = event.getClassName().toString();
        DeviceRoutes.ROUTING.launcherVisibility(getPackageName().equals(pkg) && MAIN.equals(cls));
        if (!enabled(HOME_WINDOWS)) return;
        Intent factory = DeviceRoutes.resolve(this, DeviceRoutes.Destination.FACTORY_UI);
        if (factory == null || factory.getComponent() == null
                || !pkg.equals(factory.getComponent().getPackageName())
                || !cls.equals(factory.getComponent().getClassName())
                || !DeviceRoutes.ROUTING.shouldOpen(true, SystemClock.uptimeMillis())) return;
        pendingHome = factory.getComponent();
        // Let Settings and other follow-on windows cancel the fallback before it fires.
        handler.postDelayed(redirect, 600);
    }

    private boolean openHome() {
        long now = SystemClock.uptimeMillis();
        if (!DeviceRoutes.ROUTING.shouldOpen(PRODUCTION_PACKAGE.equals(getPackageName()), now)) return false;
        Intent launch = new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName(getPackageName(), MAIN))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            startActivity(launch);
            DeviceRoutes.ROUTING.opened(now);
            return true;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private void cancelPending() { handler.removeCallbacks(redirect); pendingHome = null; }
    @Override public void onInterrupt() { cancelPending(); consumedPress = false; }
    @Override public void onDestroy() { cancelPending(); consumedPress = false; super.onDestroy(); }
}
