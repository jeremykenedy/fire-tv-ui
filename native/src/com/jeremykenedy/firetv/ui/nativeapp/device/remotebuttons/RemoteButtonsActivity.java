package com.jeremykenedy.firetv.ui.nativeapp.device.remotebuttons;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RemoteButtonsActivity extends com.jeremykenedy.firetv.ui.nativeapp.MenuActivity {
    private boolean startupBlocked;
    private RemoteButtonPreferences preferences;
    private RemoteButtonDispatcher dispatcher;
    private LinearLayout body;
    private TextView status;
    private boolean learning;
    private boolean testing;
    private int swallowed = -1;
    private final Handler handler = new Handler();
    private final Runnable timeout = new Runnable() {
        @Override public void run() {
            learning = false; testing = false;
            status.setText("No supported event received. The remote or OS may reserve this button. No mapping was changed.");
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.redirectIfBlocked(this)) { startupBlocked = true; return; }
        preferences = new RemoteButtonPreferences(this);
        dispatcher = new RemoteButtonDispatcher(this);
        show();
    }
    @Override protected void onResume() {
        if (startupBlocked) { super.onResume(); return; } super.onResume(); RemoteButtonDispatcher.learning(true); }
    @Override protected void onPause() {
        if (startupBlocked) { super.onPause(); return; }
        handler.removeCallbacks(timeout); learning = false; testing = false;
        dispatcher.clear(); swallowed = -1;
        RemoteButtonDispatcher.learning(false);
        super.onPause();
    }
    private void show() {
        ScrollView scroll = new ScrollView(this);
        body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(48, 24, 48, 24); body.setBackgroundColor(0xff17151f);
        scroll.addView(body); setContentView(scroll);
        text("Remote buttons", 28);
        text("Learn an exposed branded app key, such as Netflix, Sling, Disney+ or Amazon Music. Only dedicated Android video/featured app keys are supported. Other keys stay unchanged.", 18);
        text("Launcher only works while Home has focus. System scope requires the production launcher, its enabled key-filter accessibility service and OS support. A received event does not prove the vendor launch is cancelled; test from another app on this remote. Some remotes expose no usable key.", 17);
        status = text("No hardware capability has been verified on this device.", 17);
        Button learn = button("Learn a branded button", new Runnable() { @Override public void run() {
            learning = true; testing = false;
            status.setText("Press the branded app button within 20 seconds. Back cancels. If its app opens, return here; that key may be reserved.");
            armTimeout();
        }});
        button("Test a physical button", new Runnable() { @Override public void run() {
            learning = false; testing = true;
            status.setText("Press a learned button within 20 seconds. Check the selected destination appears and the original app does not also open. This screen tests launcher interception only.");
            armTimeout();
        }});
        for (final int key : preferences.learned()) {
            String target = preferences.target(key);
            button(preferences.label(key) + "  |  " + (target.isEmpty() ? "Factory action" : targetLabel(target) + (preferences.system(key) ? " / System requested" : " / Launcher only")),
                    new Runnable() { @Override public void run() { edit(key); }});
        }
        button("Accessibility settings", new Runnable() { @Override public void run() {
            try { startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
            catch (RuntimeException failure) { message("Accessibility settings are unavailable on this device."); }
        }});
        button("Restore all factory actions", new Runnable() { @Override public void run() {
            new AlertDialog.Builder(RemoteButtonsActivity.this).setTitle("Restore all factory actions?")
                    .setMessage("Remove only our remote mappings. Original apps and other settings are preserved. Then test each physical button from Home and another app to verify its factory action returns.")
                    .setNegativeButton("Cancel", null).setPositiveButton("Restore all", new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface dialog, int which) {
                        try { preferences.restoreAll(); show(); status.setText("Mappings removed. Test the physical buttons to verify factory actions."); }
                        catch (Exception failure) { message(failure.getMessage()); }
                    }}).show();
        }});
        learn.requestFocus();
    }
    private void armTimeout() { handler.removeCallbacks(timeout); handler.postDelayed(timeout, 20000); }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (startupBlocked) return super.dispatchKeyEvent(event);
        int key = event.getKeyCode();
        if (key == swallowed) {
            if (event.getAction() == KeyEvent.ACTION_UP) swallowed = -1;
            return true;
        }
        if (key == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN && (learning || testing)) {
            learning = false; testing = false; handler.removeCallbacks(timeout);
            status.setText("Cancelled. No mapping changed.");
            return super.dispatchKeyEvent(event);
        }
        if ((learning || testing) && RemoteButtonPolicy.supported(key) && event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            handler.removeCallbacks(timeout);
            if (learning) {
                learning = false; swallowed = key;
                final String[] labels = {"Netflix", "Sling", "Disney+", "Amazon Music", "Other branded app"};
                new AlertDialog.Builder(this).setTitle("Exposed key " + key + ": choose its label")
                        .setItems(labels, new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface dialog, int which) {
                            try { preferences.learn(key, labels[which]); show(); status.setText("Key received in this screen. Physical cancellation still needs testing."); edit(key); }
                            catch (Exception failure) { message(failure.getMessage()); }
                        }}).setNegativeButton("Cancel", null).show();
                return true;
            }
            testing = false;
            RemoteButtonDispatcher.learning(false);
            boolean handled = dispatcher.dispatch(event, false);
            RemoteButtonDispatcher.learning(true);
            if (handled) swallowed = key;
            status.setText(handled ? "Mapping launched. Check the destination and absence of the original launch. System scope remains unverified." : "No mapping launched. Factory handling was allowed; check the physical result.");
            if (handled) return true;
        }
        return super.dispatchKeyEvent(event);
    }
    private void edit(final int key) {
        new AlertDialog.Builder(this).setTitle(preferences.label(key))
                .setItems(new String[]{"Select app or action", "Restore factory action"}, new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface dialog, int which) {
                    if (which == 0) chooseTarget(key);
                    else {
                        try { preferences.restore(key); show(); status.setText("Mapping removed. Press this button from Home and another app to verify the original action returns."); }
                        catch (Exception failure) { message(failure.getMessage()); }
                    }
                }}).setNegativeButton("Cancel", null).show();
    }
    private void chooseTarget(final int key) {
        final List<String> names = new ArrayList<>();
        final List<String> targets = new ArrayList<>();
        names.add("Launcher settings"); targets.add("action:settings");
        names.add("All apps"); targets.add("action:apps");
        Map<String, ResolveInfo> apps = new LinkedHashMap<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo app : getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                if (app.activityInfo != null && app.activityInfo.exported && app.activityInfo.enabled && app.activityInfo.applicationInfo.enabled) {
                    String component = new ComponentName(app.activityInfo.packageName, app.activityInfo.name).flattenToString();
                    apps.put(component, app);
                }
            }
        }
        List<String> components = new ArrayList<>(apps.keySet());
        Collections.sort(components, new Comparator<String>() {
            @Override public int compare(String a, String b) { return apps.get(a).loadLabel(getPackageManager()).toString().compareToIgnoreCase(apps.get(b).loadLabel(getPackageManager()).toString()); }
        });
        for (String component : components) {
            names.add(apps.get(component).loadLabel(getPackageManager()) + " (" + apps.get(component).activityInfo.packageName + ")");
            targets.add("app:" + component);
        }
        new AlertDialog.Builder(this).setTitle("Select app or action").setItems(names.toArray(new String[0]), new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface dialog, int which) { chooseScope(key, targets.get(which)); } })
                .setNegativeButton("Cancel", null).show();
    }
    private void chooseScope(final int key, final String target) {
        String[] scopes = "com.jeremykenedy.firetv.ui".equals(getPackageName())
                ? new String[]{"Launcher only", "Request system scope (requires accessibility; test on this remote)"}
                : new String[]{"Launcher only (preview cannot filter system keys)"};
        new AlertDialog.Builder(this).setTitle("Mapping scope")
                .setItems(scopes, new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface dialog, int which) {
                    try { preferences.map(key, target, which == 1); show(); status.setText("Mapping saved. Use Test, then verify from another app if requesting system scope."); }
                    catch (Exception failure) { message(failure.getMessage()); }
                }}).setNegativeButton("Cancel", null).show();
    }
    private String targetLabel(String target) {
        if ("action:settings".equals(target)) return "Launcher settings";
        if ("action:apps".equals(target)) return "All apps";
        ComponentName app = target.startsWith("app:") ? ComponentName.unflattenFromString(target.substring(4)) : null;
        if (app != null) {
            try { return getPackageManager().getActivityInfo(app, 0).loadLabel(getPackageManager()).toString(); }
            catch (Exception missing) { return app.getPackageName() + " (unavailable)"; }
        }
        return "Unavailable action";
    }
    private TextView text(String value, int size) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size);
        view.setTextColor(0xfff4f1fa); view.setPadding(0, 0, 0, 14); body.addView(view); return view;
    }
    private Button button(String label, final Runnable action) {
        Button button = new Button(this); button.setText(label);
        button.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View view) { action.run(); }});
        body.addView(button, new LinearLayout.LayoutParams(-1, -2)); return button;
    }
    private void message(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
