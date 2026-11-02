package com.jeremykenedy.firetv.ui.nativeapp.device;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/** Original Home routing service; preview builds are intentionally unable to redirect Home. */
public class HomeKeyService extends AccessibilityService {
    public static final String PRODUCTION_PACKAGE = HomeRoutingPolicy.PRODUCTION_PACKAGE;
    public static final String HOME_KEYS = HomeSetupPolicy.HOME_KEYS;
    public static final String HOME_WINDOWS = HomeSetupPolicy.HOME_WINDOWS;
    private static final String MAIN = "com.jeremykenedy.firetv.ui.nativeapp.MainActivity";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean consumedPress;
    private ComponentName pendingHome;
    private SharedPreferences preferences;
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override public void onSharedPreferenceChanged(SharedPreferences changed, String key) {
                    if (key == null || HOME_KEYS.equals(key) || HOME_WINDOWS.equals(key)
                            || HomeSetupPolicy.LEGACY_HOME.equals(key) || "override_home_button".equals(key)
                            || "better_home_detection".equals(key)) {
                        updateServiceInfo();
                        if (!enabled(HOME_WINDOWS)) cancelPending();
                    }
                }
            };
    private final Runnable redirect = new Runnable() {
        @Override public void run() {
            if (pendingHome != null && enabled(HOME_WINDOWS)) openHome();
            pendingHome = null;
        }
    };

    private boolean enabled(String key) {
        if (!PRODUCTION_PACKAGE.equals(getPackageName()) || !HomeCommandHandler.recover(this)) return false;
        HomeSetupPolicy choices = HomeSetupPolicy.read(getSharedPreferences("launcher_prefs", MODE_PRIVATE).getAll());
        return HomeRoutingPolicy.allowed(getPackageName(), HOME_KEYS.equals(key) ? choices.keys : choices.windows);
    }

    /** The legacy window-only compatibility component must not compete for key filtering. */
    protected boolean filtersHomeKeys() { return true; }

    @Override protected void onServiceConnected() {
        if (preferences != null) preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener);
        preferences = getSharedPreferences("launcher_prefs", MODE_PRIVATE);
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
        updateServiceInfo();
    }

    private void updateServiceInfo() {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) return;
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        // XML must also declare canRequestFilterKeyEvents; the OS may refuse this capability.
        if (filtersHomeKeys() && (enabled(HOME_KEYS) || enabled(HOME_WINDOWS))) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        } else {
            info.flags &= ~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        }
        setServiceInfo(info);
    }

    @Override protected boolean onKeyEvent(KeyEvent event) {
        if (!filtersHomeKeys() || event == null || event.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        if (event.getAction() == KeyEvent.ACTION_UP) {
            boolean handled = consumedPress;
            consumedPress = false;
            return handled;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        if (event.getRepeatCount() > 0) return consumedPress;
        consumedPress = false;
        cancelPending();
        if (!DeviceRoutes.policy(this).homePressed()) return false;
        if (!enabled(HOME_KEYS)) return false;
        // Consume a complete key pair only after a real launch, or when Home is already visible.
        consumedPress = DeviceRoutes.policy(this).isHomeVisible()
                || DeviceRoutes.policy(this).launchPending(SystemClock.uptimeMillis()) || openHome();
        return consumedPress;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || event.getPackageName() == null || event.getClassName() == null) return;
        cancelPending();
        String pkg = event.getPackageName().toString();
        String cls = event.getClassName().toString();
        DeviceRoutes.policy(this).launcherVisibility(getPackageName().equals(pkg) && MAIN.equals(cls));
        if (!enabled(HOME_WINDOWS)) return;
        Intent factory = DeviceRoutes.resolve(this, DeviceRoutes.Destination.FACTORY_UI);
        if (factory == null || factory.getComponent() == null
                || !pkg.equals(factory.getComponent().getPackageName())
                || !cls.equals(factory.getComponent().getClassName())
                || !DeviceRoutes.policy(this).shouldOpen(true, SystemClock.uptimeMillis())) return;
        pendingHome = factory.getComponent();
        // Let Settings and other follow-on windows cancel the fallback before it fires.
        handler.postDelayed(redirect, 600);
    }

    private boolean openHome() {
        long now = SystemClock.uptimeMillis();
        if (!DeviceRoutes.policy(this).shouldOpen(PRODUCTION_PACKAGE.equals(getPackageName()), now)) return false;
        Intent launch = new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName(getPackageName(), MAIN))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            startActivity(launch);
            DeviceRoutes.policy(this).opened(now);
            return true;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private void cancelPending() { handler.removeCallbacks(redirect); pendingHome = null; }
    @Override public void onInterrupt() { cancelPending(); consumedPress = false; }
    @Override public void onDestroy() {
        cancelPending(); consumedPress = false;
        if (preferences != null) preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener);
        super.onDestroy();
    }
}
