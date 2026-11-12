package com.jeremykenedy.firetv.ui.nativeapp.appearance;

import android.app.*;
import android.content.*;
import android.graphics.Rect;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.jeremykenedy.firetv.ui.nativeapp.accessibility.AccessibilityPreferences;
import com.jeremykenedy.firetv.ui.nativeapp.setups.SavedSetupManager;
import com.jeremykenedy.firetv.ui.nativeapp.setups.SavedSetupsActivity;
import com.jeremykenedy.firetv.ui.nativeapp.stateguard.GuardStore;
import java.io.*;
import java.util.*;

/** Scrolling and return navigation without applying or changing any saved presentation. */
public final class AppearanceViewportInstrumentation extends Instrumentation {
    private Bundle args;
    private Activity theme, setups;
    private String step = "durable guard";
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); args = arguments; start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            check("com.jeremykenedy.firetv.ui.preview".equals(getTargetContext().getPackageName()), "Disposable preview only.");
            check(new GuardStore(getTargetContext().getDataDir()).verify(args.getString("state_guard_case_id"),
                    new LinkedHashSet<String>(Arrays.asList("shared_prefs", "files", "no_backup")),
                    args.getString("state_guard_manifest_sha256")).matches, "Matching durable guard required before fixture writes.");
            SavedSetupManager manager = new SavedSetupManager(getTargetContext());
            check(manager.list().isEmpty(), "Use a fresh owned setup fixture.");
            for (int i = 1; i <= 24; i++) manager.save(String.format(java.util.Locale.ROOT, "Saved appearance %02d", i), null);
            File saved = new File(getTargetContext().getFilesDir(), "saved-setups/setups.json");
            byte[] before = read(saved);
            Map<String, ?> appearance = AppearancePreferences.file(getTargetContext()).getAll();
            File appearanceFile = new File(getTargetContext().getDataDir(), "shared_prefs/source_appearance.xml");
            byte[] appearanceBytes = appearanceFile.isFile() ? read(appearanceFile) : null;
            boolean fixed = "true".equals(args.getString("expect_fixed"));
            int overflows = 0;
            for (int percent : new int[]{100, 120}) {
                step = "theme and saved setups at text " + percent;
                AccessibilityPreferences.save(getTargetContext(), percent, false);
                theme = startActivitySync(new Intent(getTargetContext(), WallpaperThemeActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                window(theme); focus(theme, "wallpaper:setups");
                overflows += inspect(theme, result, "theme" + percent, fixed);
                final int[] themeScroll = new int[1];
                onMain(new Runnable() { @Override public void run() { themeScroll[0] = scroll(theme.getWindow().getDecorView()).getScrollY(); } });
                ActivityMonitor monitor = addMonitor(SavedSetupsActivity.class.getName(), null, false);
                press(KeyEvent.KEYCODE_DPAD_CENTER);
                setups = waitForMonitorWithTimeout(monitor, 5000); removeMonitor(monitor);
                check(setups != null, "Actual Theme Saved setups action opens its screen."); window(setups);
                String last = "setup:" + manager.list().get(23).getString("id");
                focus(setups, last); press(KeyEvent.KEYCODE_DPAD_UP); press(KeyEvent.KEYCODE_DPAD_DOWN);
                check(last.equals(setups.getCurrentFocus().getTag()), "Actual remote Up/Down returns to last setup.");
                SystemClock.sleep(500); overflows += inspect(setups, result, "setups" + percent, fixed);
                press(KeyEvent.KEYCODE_DPAD_CENTER); waitForIdleSync();
                press(KeyEvent.KEYCODE_BACK); window(setups);
                check(last.equals(setups.getCurrentFocus().getTag()), "Dismissing setup actions keeps the focused saved setup.");
                capture("setups" + percent + "-returned.png");
                press(KeyEvent.KEYCODE_BACK); window(theme);
                boolean retained = "wallpaper:setups".equals(theme.getCurrentFocus().getTag());
                result.putBoolean("theme_return_focus_" + percent, retained);
                if (fixed) check(retained, "Back restores Theme Saved setups focus.");
                inspect(theme, result, "theme" + percent + "-returned", fixed);
                if (fixed) onMain(new Runnable() { @Override public void run() {
                    check(themeScroll[0] == scroll(theme.getWindow().getDecorView()).getScrollY(), "Back preserves Theme scroll position.");
                } });
                if (fixed && percent == 120) {
                    ActivityMonitor recreated = addMonitor(WallpaperThemeActivity.class.getName(), null, false);
                    onMain(new Runnable() { @Override public void run() { theme.recreate(); } });
                    Activity replacement = waitForMonitorWithTimeout(recreated, 5000); removeMonitor(recreated);
                    check(replacement != null, "Theme recreation completes."); theme = replacement; window(theme);
                    check("wallpaper:setups".equals(theme.getCurrentFocus().getTag()), "Recreated Theme retains captured Saved setups focus.");
                    inspect(theme, result, "theme120-recreated", true);
                }
                onMain(new Runnable() { @Override public void run() { theme.finish(); } });
                check(Arrays.equals(before, read(saved)), "Browsing and Back preserve exact setup file bytes.");
                check(appearance.equals(AppearancePreferences.file(getTargetContext()).getAll()),
                        "Browsing preserves appearance choices.");
                check(appearanceBytes == null ? !appearanceFile.exists() : appearanceFile.isFile()
                        && Arrays.equals(appearanceBytes, read(appearanceFile)), "Appearance file bytes and prior absence remain unchanged.");
            }
            result.putInt("unclipped_viewports", overflows);
            result.putString("status", fixed ? "PASS" : "BASELINE_OBSERVED");
            result.putBoolean("setup_bytes_and_appearance_unchanged", true);
        } catch (Throwable failure) {
            result.putString("status", "FAIL"); result.putString("step", step); result.putString("reason", failure.toString());
            try { capture("failure.png"); } catch (Exception ignored) { }
        }
        finish(-1, result);
    }
    private int inspect(final Activity activity, Bundle result, String name, final boolean fixed) throws Exception {
        final Bundle bounds = new Bundle();
        onMain(new Runnable() { @Override public void run() {
            ScrollView scroll = scroll(activity.getWindow().getDecorView());
            ViewGroup page = (ViewGroup)scroll.getParent(); View heading = page.getChildAt(0);
            View focused = activity.getCurrentFocus();
            check(scroll.getScrollY() > 0, "Bottom row must scroll the actual screen.");
            Rect viewport = new Rect(); scroll.getGlobalVisibleRect(viewport);
            int[] position = new int[2]; focused.getLocationOnScreen(position);
            float extraY = focused.getHeight() * (focused.getScaleY() - 1) / 2;
            boolean visible = position[1] - extraY >= viewport.top - 1
                    && position[1] + focused.getHeight() + extraY <= viewport.bottom + 1;
            Rect focusedBounds = new Rect(); focused.getGlobalVisibleRect(focusedBounds);
            boolean growthVisible = focusedBounds.width() >= Math.floor(focused.getWidth() * focused.getScaleX()) - 2
                    && focusedBounds.height() >= Math.floor(focused.getHeight() * focused.getScaleY()) - 2;
            bounds.putBoolean("focus_growth_fully_visible", growthVisible);
            bounds.putString("focus_visible_bounds", focusedBounds.toShortString());
            bounds.putBoolean("focus_fully_visible", visible); bounds.putInt("scroll_y", scroll.getScrollY());
            bounds.putString("viewport", viewport.toShortString()); bounds.putString("clip_bounds", String.valueOf(scroll.getClipBounds()));
            bounds.putBoolean("page_clips_children", page.getClipChildren());
            bounds.putInt("heading_bottom", heading.getBottom()); bounds.putInt("scroll_top", scroll.getTop());
            bounds.putFloat("focus_scale", focused.getScaleX());
            boolean clipped = page.getClipChildren() || scroll.getClipBounds() != null;
            bounds.putBoolean("viewport_clipped", clipped);
            if (fixed) { check(clipped, "Content must clip below the fixed header."); check(visible && growthVisible, "Focused row including horizontal and vertical growth must remain visible."); }
        }});
        result.putString(name, bounds.toString()); capture(name + ".png");
        return bounds.getBoolean("viewport_clipped") ? 0 : 1;
    }
    private void focus(final Activity activity, final String tag) {
        window(activity);
        onMain(new Runnable() { @Override public void run() {
            View target = activity.getWindow().getDecorView().findViewWithTag(tag);
            check(target != null && target.requestFocus(), "Actual row can receive remote focus: " + tag);
        }}); waitForIdleSync(); SystemClock.sleep(600);
    }
    private ScrollView scroll(View view) {
        if (view instanceof ScrollView) return (ScrollView)view;
        if (view instanceof ViewGroup) for (int i=0;i<((ViewGroup)view).getChildCount();i++) {
            ScrollView found=scroll(((ViewGroup)view).getChildAt(i)); if(found!=null)return found;
        }
        return null;
    }
    private void window(final Activity activity) {
        long until=SystemClock.uptimeMillis()+5000;
        while(SystemClock.uptimeMillis()<until) {
            final boolean[] ready={false};onMain(new Runnable(){@Override public void run(){ready[0]=activity.hasWindowFocus();}});
            if(ready[0]) {waitForIdleSync();return;} SystemClock.sleep(50);
        }
        throw new AssertionError("Expected foreground: " + activity.getClass().getSimpleName());
    }
    private void press(int key) { sendKeyDownUpSync(key); waitForIdleSync(); }
    private void onMain(final Runnable action) {
        final Throwable[] failure={null};runOnMainSync(new Runnable(){@Override public void run(){try{action.run();}catch(Throwable error){failure[0]=error;}}});
        if(failure[0]!=null)throw new AssertionError("Native action failed",failure[0]);
    }
    private void capture(String name) throws Exception {
        waitForIdleSync();SystemClock.sleep(500);
        try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),name))) {
            check(getUiAutomation().takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,out),"Capture succeeds.");
        }
    }
    private byte[] read(File file) throws Exception { try(FileInputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()) {
        byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);return out.toByteArray(); } }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
