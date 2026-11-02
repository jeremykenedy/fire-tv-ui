package com.jeremykenedy.firetv.ui.nativeapp.device.remotebuttons;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

public final class RemoteButtonDispatcher {
    private final Context context;
    private final RemoteButtonPreferences preferences;
    private final RemoteButtonPolicy policy = new RemoteButtonPolicy();
    private static boolean learning;
    public RemoteButtonDispatcher(Context context) {
        this.context = context;
        preferences = new RemoteButtonPreferences(context);
    }
    public static void learning(boolean active) { learning = active; }
    public boolean hasSystemMappings() { return preferences.hasSystemMappings(); }
    public void clear() { policy.clear(); }
    public boolean dispatch(final KeyEvent event, final boolean systemWide) {
        if (event == null || learning) return false;
        final int key = event.getKeyCode();
        return policy.dispatch(key, event.getAction(), event.getRepeatCount(), event.isCanceled(), new RemoteButtonPolicy.Launch() {
            @Override public boolean open() {
                if (!preferences.learned().contains(key) || (systemWide && !preferences.system(key))) return false;
                return launch(preferences.target(key));
            }
        });
    }
    public static boolean validTarget(String target) {
        if ("action:settings".equals(target) || "action:apps".equals(target)) return true;
        if (target == null || !target.startsWith("app:")) return false;
        ComponentName component = ComponentName.unflattenFromString(target.substring(4));
        return component != null && !component.getPackageName().isEmpty() && !component.getClassName().isEmpty();
    }
    public boolean launch(String target) {
        if (!validTarget(target)) return false;
        Intent intent;
        if (target.startsWith("app:")) intent = new Intent(Intent.ACTION_MAIN).setComponent(ComponentName.unflattenFromString(target.substring(4)));
        else intent = new Intent().setComponent(new ComponentName(context.getPackageName(),
                "action:settings".equals(target) ? "com.jeremykenedy.firetv.ui.nativeapp.SettingsActivity"
                        : "com.jeremykenedy.firetv.ui.nativeapp.allapps.AllAppsActivity"));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            return true;
        } catch (RuntimeException unavailable) { return false; }
    }
}
