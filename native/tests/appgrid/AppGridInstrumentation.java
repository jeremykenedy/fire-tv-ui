package com.jeremykenedy.firetv.ui.nativeapp.appgrid;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import com.jeremykenedy.firetv.ui.nativeapp.focus.FocusTileView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AppGridInstrumentation extends Instrumentation {
    private Activity activity;
    private final List<FocusTileView> tiles = new ArrayList<>();
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        SharedPreferences prefs = getTargetContext().getSharedPreferences("native_app_grid", Context.MODE_PRIVATE);
        Map<String, ?> original = prefs.getAll();
        try {
            prefs.edit().clear().commit();
            home();
            check(tiles.size() >= 2, "Two real installed TV apps required");
            final String key = String.valueOf(tiles.get(1).getTag());
            final String label = tiles.get(1).getContentDescription().toString();
            select(key); longPress();
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN); sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            waitTiles();
            check(AppGridPreferences.load(getTargetContext()).favorites.contains(key), "Longpress favorite persists");
            check(String.valueOf(tiles.get(0).getTag()).equals(key), "Favorite appears first in actual Home");
            select(key); longPress();
            for (int i = 0; i < 4; i++) sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER); waitTiles();
            check(AppGridPreferences.load(getTargetContext()).hidden.contains(key), "Longpress hide persists");
            check(!keys().contains(key), "Hidden tile removed from actual Home");
            open("AppGridSettingsActivity", "appgrid");
            final Button[] appButton = new Button[1];
            for (int attempt = 0; attempt < 40 && appButton[0] == null; attempt++) {
                SystemClock.sleep(100);
                onMain(new Runnable() { @Override public void run() { appButton[0] = button(activity.getWindow().getDecorView(), label + " · Hidden"); } });
            }
            check(appButton[0] != null, "Hidden app is reachable in manager");
            onMain(new Runnable() { @Override public void run() { appButton[0].performClick(); } });
            SystemClock.sleep(200); sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER); waitForIdleSync();
            check(!AppGridPreferences.load(getTargetContext()).hidden.contains(key), "Manager Show restores app without deleting favorite");
            AppGridOptions changed = AppGridPreferences.load(getTargetContext()).grid(7, 2, true);
            AppGridPreferences.save(getTargetContext(), changed);
            home();
            check(keys().contains(key), "Shown app returns after activity restart");
            final int[] grid = new int[2];
            onMain(new Runnable() { @Override public void run() {
                GridLayout view = (GridLayout) tiles.get(0).getParent();
                grid[0] = view.getOrientation(); grid[1] = view.getRowCount();
            } });
            check(grid[0] == GridLayout.VERTICAL && grid[1] == 2, "Horizontal grid renders saved row limit");
            check(AppGridPreferences.load(getTargetContext()).columns == 7, "Custom columns survive activity restart");
            result.putString("status", "PASS");
            result.putBoolean("real_longpress_favorite_hide", true);
            result.putBoolean("hidden_manager_recovery", true);
            result.putBoolean("custom_horizontal_grid", true);
        } catch (Throwable error) { result.putString("status", "FAIL"); result.putString("reason", error.toString()); }
        finally {
            if (activity != null) onMain(new Runnable() { @Override public void run() { activity.finish(); } });
            SharedPreferences.Editor restore = prefs.edit().clear();
            for (Map.Entry<String, ?> entry : original.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Integer) restore.putInt(entry.getKey(), (Integer) value);
                else if (value instanceof Boolean) restore.putBoolean(entry.getKey(), (Boolean) value);
                else if (value instanceof String) restore.putString(entry.getKey(), (String) value);
            }
            result.putBoolean("app_preferences_restored", restore.commit() && original.equals(prefs.getAll()));
        }
        finish(0, result);
    }
    private void home() { open("MainActivity", ""); waitTiles(); }
    private void open(String name, String subpackage) {
        if (activity != null) onMain(new Runnable() { @Override public void run() { activity.finish(); } });
        activity = startActivitySync(new Intent().setClassName(getTargetContext(), "com.jeremykenedy.firetv.ui.nativeapp." + (subpackage.isEmpty() ? "" : subpackage + ".") + name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        waitForIdleSync();
    }
    private void waitTiles() {
        SystemClock.sleep(500);
        for (int i = 0; i < 40; i++) {
            onMain(new Runnable() { @Override public void run() { tiles.clear(); collect(activity.getWindow().getDecorView()); } });
            if (!tiles.isEmpty()) return;
            SystemClock.sleep(100);
        }
    }
    private void select(final String key) {
        onMain(new Runnable() { @Override public void run() {
            for (FocusTileView tile : tiles) if (key.equals(tile.getTag())) { tile.requestFocus(); return; }
            throw new AssertionError("Missing app to focus");
        } });
    }
    private void longPress() {
        onMain(new Runnable() { @Override public void run() { activity.getCurrentFocus().performLongClick(); } });
        SystemClock.sleep(200); waitForIdleSync();
    }
    private List<String> keys() { List<String> result = new ArrayList<>(); for (FocusTileView tile : tiles) result.add(String.valueOf(tile.getTag())); return result; }
    private void collect(View view) {
        if (view instanceof FocusTileView) tiles.add((FocusTileView) view);
        if (view instanceof ViewGroup) for (int i=0;i<((ViewGroup)view).getChildCount();i++) collect(((ViewGroup)view).getChildAt(i));
    }
    private Button button(View view, String prefix) {
        if (view instanceof Button && ((Button)view).getText().toString().startsWith(prefix)) return (Button)view;
        if (view instanceof ViewGroup) for (int i=0;i<((ViewGroup)view).getChildCount();i++) { Button found = button(((ViewGroup)view).getChildAt(i),prefix); if(found != null)return found; }
        return null;
    }
    private void onMain(final Runnable run) {
        final Throwable[] error = new Throwable[1];
        runOnMainSync(new Runnable() { @Override public void run() { try { run.run(); } catch(Throwable failure) { error[0]=failure; } } });
        if(error[0]!=null)throw new AssertionError(error[0]);
    }
    private static void check(boolean condition, String message) { if(!condition)throw new AssertionError(message); }
}
