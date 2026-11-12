package com.jeremykenedy.firetv.ui.nativeapp.appgrid;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.ScrollView;
import com.jeremykenedy.firetv.ui.nativeapp.stateguard.GuardStore;
import java.io.*;
import java.util.*;

public final class AppManagerFocusInstrumentation extends Instrumentation {
    private Bundle args;
    private Activity settings, manager;
    private String key;
    private int scrollBefore;
    @Override public void onCreate(Bundle value) { super.onCreate(value); args = value; start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            check(new GuardStore(getTargetContext().getDataDir()).verify(args.getString("state_guard_case_id"),
                    new LinkedHashSet<String>(Arrays.asList("shared_prefs", "files", "no_backup")),
                    args.getString("state_guard_manifest_sha256")).matches, "Durable guard required");
            settings = startActivitySync(new Intent().setClassName(getTargetContext(),
                    "com.jeremykenedy.firetv.ui.nativeapp.SettingsActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            ActivityMonitor monitor = addMonitor("com.jeremykenedy.firetv.ui.nativeapp.appgrid.AppGridSettingsActivity", null, false);
            focusId(settings, 0x2070); // UI settings entry
            press(KeyEvent.KEYCODE_DPAD_CENTER);
            focusId(settings, 0x2010); press(KeyEvent.KEYCODE_DPAD_CENTER);
            manager = waitForMonitorWithTimeout(monitor, 5000);
            check(manager != null, "Current Settings > UI settings > Apps route opens manager");
            for (int i=0; i<60; i++) {
                waitForIdleSync();
                final boolean[] ready = new boolean[1];
                onMain(new Runnable() { public void run() { ready[0] = manager.getCurrentFocus()!=null && String.valueOf(manager.getCurrentFocus().getTag()).startsWith("app-manager:hide:"); }});
                if (ready[0]) break;
                SystemClock.sleep(100);
            }
            for (int i=0; i<11; i++) press(KeyEvent.KEYCODE_DPAD_DOWN);
            for (int i=0; i<3; i++) press(KeyEvent.KEYCODE_DPAD_RIGHT);
            onMain(new Runnable() { public void run() {
                View selected=manager.getCurrentFocus(); key=String.valueOf(selected.getTag());
                check(key.startsWith("app-manager:settings:"), "D-pad reaches row app options");
                scrollBefore=scroll(selected).getScrollY(); check(scrollBefore>0, "Fixture list is scrolled");
            }});
            screenshot("before");
            action(1); continuity("favorite");
            check(saved("favorites"), "Favorite saved");
            action(0); continuity("hide");
            check(saved("hidden"), "Hidden app saved and remains in manager");
            action(0); continuity("show");
            check(!saved("hidden"), "Show restores app");
            action(1); continuity("remove favorite");
            check(!saved("favorites"), "Favorite removal saved");
            String beforeOrder = homeOrder();
            action(2); continuity("move earlier");
            String earlierOrder = homeOrder();
            check(!earlierOrder.equals(beforeOrder), "Move earlier saves Home order");
            action(3); continuity("move later");
            check(!homeOrder().equals(earlierOrder), "Move later saves Home order");
            screenshot("after");
            for(int i=0;i<24;i++)press(KeyEvent.KEYCODE_DPAD_UP);
            onMain(new Runnable() { public void run() { check(manager.getCurrentFocus() instanceof android.widget.Button && "Grid options".equals(((android.widget.Button)manager.getCurrentFocus()).getText().toString()), "Remote reaches Grid options"); }});
            press(KeyEvent.KEYCODE_DPAD_CENTER); press(KeyEvent.KEYCODE_BACK);
            SystemClock.sleep(200); waitForIdleSync();
            onMain(new Runnable() { public void run() {
                View focus=manager.getCurrentFocus();
                check(focus!=null && String.valueOf(focus.getTag()).startsWith("app-manager:hide:"), "Grid Back restores first app fallback");
                android.graphics.Rect visible=new android.graphics.Rect();
                check(focus.getGlobalVisibleRect(visible) && visible.height()==focus.getHeight(), "Fallback control remains fully visible");
                check(scroll(focus).getScrollY()==0, "Grid Back shows the first row rather than stale deep scroll");
            }});
            screenshot("grid-back");
            press(KeyEvent.KEYCODE_BACK);
            onMain(new Runnable() { public void run() { check(settings.getCurrentFocus()!=null && settings.getCurrentFocus().getId()==0x2010,"Back restores Apps settings route"); }});
            result.putString("status", "PASS: remote manager actions preserve app/control focus and scroll; Back restores Apps route");
        } catch(Throwable failure) { result.putString("failure", failure.toString()); try { screenshot("failure"); } catch(Exception ignored) {} }
        finally { onMain(new Runnable() { public void run() { if(manager!=null)manager.finish(); if(settings!=null)settings.finish(); }}); }
        finish(result.containsKey("failure")?1:-1,result);
    }
    private String homeOrder() { return getTargetContext().getSharedPreferences("native_app_grid", Context.MODE_PRIVATE).getString("home_order", ""); }
    private boolean saved(String key) { return Arrays.asList(getTargetContext().getSharedPreferences("native_app_grid", Context.MODE_PRIVATE).getString(key, "").split("\\n")).contains(appKey()); }
    private String appKey() { return key.substring("app-manager:settings:".length()); }
    private void action(int choice) {
        long now=SystemClock.uptimeMillis();
        sendKeySync(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER,0));
        SystemClock.sleep(800);
        sendKeySync(new KeyEvent(now,SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER,0));
        waitForIdleSync();
        for(int i=0;i<choice;i++)press(KeyEvent.KEYCODE_DPAD_DOWN);
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        SystemClock.sleep(200); waitForIdleSync();
    }
    private void continuity(final String action) {
        onMain(new Runnable() { public void run() {
            View focus=manager.getCurrentFocus();
            check(focus!=null && key.equals(focus.getTag()),action+" retains exact app/control focus; observed "+(focus==null?"none":focus.getTag()));
            check(scroll(focus).getScrollY()==scrollBefore,action+" retains scroll; expected "+scrollBefore+", observed "+scroll(focus).getScrollY());
        }});
    }
    private ScrollView scroll(View child) { for(ViewParent parent=child.getParent();parent!=null;parent=parent.getParent())if(parent instanceof ScrollView)return (ScrollView)parent;throw new AssertionError("Missing scroll"); }
    private void focusId(final Activity activity, final int id) { onMain(new Runnable() { public void run() { View view=activity.findViewById(id);check(view!=null,"Current route ID "+id);view.requestFocus(); }}); }
    private void press(int key) { sendKeyDownUpSync(key); waitForIdleSync(); }
    private void screenshot(String name)throws Exception { SystemClock.sleep(250); Bitmap bitmap=getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"app-manager-"+name+".png"))){check(bitmap!=null && bitmap.compress(Bitmap.CompressFormat.PNG,100,out),"Screenshot");} }
    private void onMain(final Runnable task) { final Throwable[] error=new Throwable[1];runOnMainSync(new Runnable(){public void run(){try{task.run();}catch(Throwable failure){error[0]=failure;}}});if(error[0]!=null)throw new AssertionError(error[0]); }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
