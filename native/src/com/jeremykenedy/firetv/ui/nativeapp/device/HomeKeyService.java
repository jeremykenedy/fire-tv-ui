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
import com.jeremykenedy.firetv.ui.nativeapp.SourceStartup;
import com.jeremykenedy.firetv.ui.nativeapp.device.remotebuttons.RemoteButtonDispatcher;
import com.jeremykenedy.firetv.ui.nativeapp.device.remotebuttons.RemoteButtonPreferences;

/** Original Home routing service; preview builds are intentionally unable to redirect Home. */
public class HomeKeyService extends AccessibilityService {
    public static final String PRODUCTION_PACKAGE = HomeRoutingPolicy.PRODUCTION_PACKAGE;
    public static final String HOME_KEYS = HomeSetupPolicy.HOME_KEYS;
    public static final String HOME_WINDOWS = HomeSetupPolicy.HOME_WINDOWS;
    private static final String MAIN = "com.jeremykenedy.firetv.ui.nativeapp.MainActivity";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean consumedPress;
    private boolean destroyed;
    private ComponentName pendingHome;
    private SharedPreferences preferences;
    private SharedPreferences remotePreferences;
    private RemoteButtonDispatcher remoteButtons;
    private final SharedPreferences.OnSharedPreferenceChangeListener remoteListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override public void onSharedPreferenceChanged(SharedPreferences changed, String key) {
                    if (remoteButtons != null) remoteButtons.clear();
                    updateServiceInfo();
                }
            };
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
            trace("Window redirect pending=" + (pendingHome != null));
            if (pendingHome != null && enabled(HOME_WINDOWS)) openHome();
            pendingHome = null;
        }
    };

    private void trace(String message) {
        if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            android.util.Log.i("FireTvHome", message);
        }
    }

    private boolean enabled(String key) {
        if (!PRODUCTION_PACKAGE.equals(getPackageName()) || !HomeCommandHandler.recover(this)) return false;
        HomeSetupPolicy choices = HomeSetupPolicy.read(getSharedPreferences("launcher_prefs", MODE_PRIVATE).getAll());
        return HomeRoutingPolicy.allowed(getPackageName(), HOME_KEYS.equals(key) ? choices.keys : choices.windows);
    }

    /** The legacy window-only compatibility component must not compete for key filtering. */
    protected boolean filtersHomeKeys() { return true; }

    @Override protected void onServiceConnected() {
        if (!SourceStartup.ready(this)) {
            SourceStartup.start(this, new Runnable() {
                @Override public void run() { if (!destroyed && SourceStartup.ready(HomeKeyService.this)) connectReady(); }
            });
            return;
        }
        connectReady();
    }

    private void connectReady() {
        if (preferences != null) preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener);
        preferences = getSharedPreferences("launcher_prefs", MODE_PRIVATE);
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
        if (remotePreferences != null) remotePreferences.unregisterOnSharedPreferenceChangeListener(remoteListener);
        if (filtersHomeKeys() && PRODUCTION_PACKAGE.equals(getPackageName())) {
            remoteButtons = new RemoteButtonDispatcher(this);
            remotePreferences = getSharedPreferences(RemoteButtonPreferences.FILE, MODE_PRIVATE);
            remotePreferences.registerOnSharedPreferenceChangeListener(remoteListener);
        }
        updateServiceInfo();
    }

    private void updateServiceInfo() {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) return;
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        // XML must also declare canRequestFilterKeyEvents; the OS may refuse this capability.
        if (filtersHomeKeys() && (enabled(HOME_KEYS) || enabled(HOME_WINDOWS)
                || (remoteButtons != null && remoteButtons.hasSystemMappings()))) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        } else {
            info.flags &= ~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        }
        setServiceInfo(info);
    }

    @Override protected boolean onKeyEvent(KeyEvent event) {
        if (!SourceStartup.ready(this)) return false;
        if (remoteButtons != null && remoteButtons.dispatch(event, true)) return true;
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
        if (!enabled(HOME_KEYS)) {
            consumedPress = enabled(HOME_WINDOWS) && DeviceRoutes.policy(this).isHomeVisible();
            return consumedPress;
        }
        // Consume a complete key pair only after a real launch, or when Home is already visible.
        consumedPress = DeviceRoutes.policy(this).isHomeVisible()
                || DeviceRoutes.policy(this).launchPending(SystemClock.uptimeMillis()) || openHome();
        return consumedPress;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!SourceStartup.ready(this)) return;
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || event.getPackageName() == null || event.getClassName() == null) return;
        String pkg = event.getPackageName().toString();
        String cls = event.getClassName().toString();
        int changes = event.getContentChangeTypes();
        trace("Window " + pkg + "/" + cls + " windowId=" + event.getWindowId() + " changes=" + changes + " pending=" + (pendingHome != null));
        // A removed pane can report after the next foreground window is already visible.
        if ((changes & AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED) != 0
                && (changes & AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_APPEARED) == 0) return;
        DeviceRoutes.policy(this).launcherVisibility(getPackageName().equals(pkg) && MAIN.equals(cls));
        if (!enabled(HOME_WINDOWS)) { cancelPending(); trace("Window routing off"); return; }
        Intent factory = DeviceRoutes.resolve(this, DeviceRoutes.Destination.FACTORY_UI);
        trace("Factory " + (factory == null ? "unavailable" : factory.getComponent())
                + " ready=" + DeviceRoutes.policy(this).shouldOpen(true, SystemClock.uptimeMillis()));
        if (factory == null || factory.getComponent() == null
                || !pkg.equals(factory.getComponent().getPackageName())
                || !cls.equals(factory.getComponent().getClassName())
                || !DeviceRoutes.policy(this).shouldOpen(true, SystemClock.uptimeMillis())) {
            cancelPending();
            return;
        }
        // Repeated reports for one Home window must not postpone routing indefinitely.
        if (factory.getComponent().equals(pendingHome)) return;
        cancelPending();
        pendingHome = factory.getComponent();
        // Let Settings and other follow-on windows cancel the fallback before it fires.
        handler.postDelayed(redirect, 900);
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
    @Override public void onInterrupt() {
        trace("Feedback interrupt pending=" + (pendingHome != null));
        cancelPending(); consumedPress = false;
        if (remoteButtons != null) remoteButtons.clear();
    }
    @Override public void onDestroy() {
        destroyed = true;
        cancelPending(); consumedPress = false;
        if (preferences != null) preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener);
        if (remotePreferences != null) remotePreferences.unregisterOnSharedPreferenceChangeListener(remoteListener);
        if (remoteButtons != null) remoteButtons.clear();
        super.onDestroy();
    }
}
