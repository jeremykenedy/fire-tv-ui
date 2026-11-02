package com.jeremykenedy.firetv.ui.nativeapp.focus;

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
import android.widget.Switch;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.appgrid.AppInventory;
import com.jeremykenedy.firetv.ui.nativeapp.setups.SavedSetupManager;
import com.jeremykenedy.firetv.ui.nativeapp.stateguard.GuardStore;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/** Synthetic preview state only. The operator must restore the durable guard. */
public final class FocusHomeInstrumentation extends Instrumentation {
    private Bundle args;
    @Override public void onCreate(Bundle args) { super.onCreate(args); this.args = args; start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        Activity home = null, settings = null;
        try {
            final Context context = getTargetContext();
            check("com.jeremykenedy.firetv.ui.preview".equals(context.getPackageName()), "Preview only");
            String id=args.getString("state_guard_case_id"), hash=args.getString("state_guard_manifest_sha256");
            check(id != null && hash != null && new GuardStore(context.getDataDir()).verify(id,
                new LinkedHashSet<String>(Arrays.asList("shared_prefs", "files", "no_backup")), hash).matches, "Durable guard required");
            if ("true".equals(args.getString("wide_grid"))) {
                wideGrid(context, result);
                finish(Activity.RESULT_OK, result);
                return;
            }
            SharedPreferences focus=context.getSharedPreferences("native_focus", 0);
            check(focus.edit().clear().putBoolean(FocusOptions.LEGACY_APP_DEFAULT,true).commit(), "Seed synthetic marker");
            final SavedSetupManager manager=new SavedSetupManager(context);
            final String legacySetup=manager.save("Focus fixture legacy", null);
            home=startActivitySync(new Intent().setClassName(context,"com.jeremykenedy.firetv.ui.nativeapp.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            List<AppInventory.App> apps=AppInventory.load(context); check(!apps.isEmpty(), "Installed visible app required");
            final Activity actualHome=home; final View[] tile=new View[1];
            for (int n=0;n<60 && tile[0]==null;n++) {
                onMain(new Runnable(){public void run(){for(AppInventory.App app:apps){View candidate=actualHome.getWindow().getDecorView().findViewWithTag(app.key()); if(candidate instanceof FocusTileView){tile[0]=candidate; break;}}}});
                if(tile[0]==null)SystemClock.sleep(100);
            }
            check(tile[0]!=null,"Visible app tile required");
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT); waitForIdleSync();
            onMain(new Runnable(){public void run(){check(tile[0].requestFocus(),"App tile focus");}}); waitForIdleSync();SystemClock.sleep(250);
            onMain(new Runnable(){public void run(){View surface=((ViewGroup)tile[0]).getChildAt(0);check(Math.abs(surface.getScaleX()-1.18f)<0.002f,"Actual Home app uses legacy style");}});
            settings=startActivitySync(new Intent().setClassName(context,"com.jeremykenedy.firetv.ui.nativeapp.focus.FocusSettingsActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
            final Activity actualSettings=settings;final Switch[] control=new Switch[1];
            onMain(new Runnable(){public void run(){check(findText(actualSettings.getWindow().getDecorView(),"Apps use an imported focus style.")!=null,"Imported style notice visible");control[0]=findSwitch(actualSettings.getWindow().getDecorView());check(control[0]!=null && control[0].requestFocus(),"Focus settings switch");}});
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);waitForIdleSync();
            check(!focus.contains(FocusOptions.LEGACY_APP_DEFAULT) && FocusPreferences.hasExplicitSettings(context),"Settings edit exits imported mode");
            onMain(new Runnable(){public void run(){View notice=findText(actualSettings.getWindow().getDecorView(),"Apps use an imported focus style.");check(notice==null || notice.getVisibility()!=View.VISIBLE,"Notice hides after save");actualSettings.finish();}});waitForIdleSync();
            manager.apply(legacySetup,true);check(FocusPreferences.legacyDefaultAppMode(context),"Saved setup restores marker");
            onMain(new Runnable(){public void run(){actualHome.finish();}});waitForIdleSync();
            final Activity restoredHome=startActivitySync(new Intent().setClassName(context,"com.jeremykenedy.firetv.ui.nativeapp.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
            final View[] restoredTile=new View[1];
            for(int n=0;n<60 && restoredTile[0]==null;n++) {
                onMain(new Runnable(){public void run(){for(AppInventory.App app:apps){View candidate=restoredHome.getWindow().getDecorView().findViewWithTag(app.key());if(candidate instanceof FocusTileView){restoredTile[0]=candidate;break;}}}});
                if(restoredTile[0]==null)SystemClock.sleep(100);
            }
            check(restoredTile[0]!=null,"Restored Home app tile required");
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);waitForIdleSync();
            onMain(new Runnable(){public void run(){check(restoredTile[0].requestFocus(),"Restored app tile focus");}});waitForIdleSync();SystemClock.sleep(250);
            onMain(new Runnable(){public void run(){check(Math.abs(((ViewGroup)restoredTile[0]).getChildAt(0).getScaleX()-1.18f)<0.002f,"Saved setup restores actual Home app style");}});
            result.putString("result","PASS: synthetic preview marker, actual Home app focus, Settings notice/edit and saved setup Home style restore");
            result.putString("limits","Synthetic marker only; production migration, visual pixel parity and failed disk save require separate proof.");
            finish(Activity.RESULT_OK,result);
        } catch (Throwable failure) { result.putString("error",failure.toString());
            if (failure.getCause() != null) result.putString("error_cause", failure.getCause().toString());
            finish(Activity.RESULT_CANCELED,result); }
    }
    private void onMain(final Runnable action) {
        final Throwable[] failure = new Throwable[1];
        runOnMainSync(new Runnable() { public void run() { try { action.run(); } catch (Throwable error) { failure[0] = error; } } });
        if (failure[0] != null) throw new AssertionError("UI assertion failed", failure[0]);
    }
    private void wideGrid(final Context context, Bundle result) throws Exception {
        List<AppInventory.App> apps = AppInventory.load(context);
        final List<String> appKeys = AppInventory.keys(apps);
        check(apps.size() >= 3, "Three installed app tiles are required");
        com.jeremykenedy.firetv.ui.nativeapp.folders.FolderPreferences.save(context,
                com.jeremykenedy.firetv.ui.nativeapp.folders.FolderOptions.empty());
        check(context.getSharedPreferences("native_focus", 0).edit().clear()
                .putBoolean(FocusOptions.LEGACY_APP_DEFAULT, true).commit(), "Seed imported app focus");
        SharedPreferences.Editor inputs = context.getSharedPreferences("native_inputs", 0).edit().clear();
        for (int n = 0; n < 160; n++) inputs.putBoolean("known:grid-width-proof:" + n, true)
                .putString("factory:grid-width-proof:" + n, "Grid fixture " + n);
        check(inputs.commit(), "Seed retained input tiles");
        for (final int columns : new int[] {5, 16, 20}) {
            final java.util.ArrayList<String> order = new java.util.ArrayList<>();
            order.add(apps.get(0).key());
            for (int n = 0; n < columns - 2; n++) order.add("input:grid-width-proof:" + n);
            order.add(apps.get(1).key());
            for (int n = columns - 2; n < 160; n++) {
                if (order.size() == columns * 6 - 1) order.add(apps.get(2).key());
                order.add("input:grid-width-proof:" + n);
            }
            check(order.size() > columns * 6 - 1 && apps.get(2).key().equals(order.get(columns * 6 - 1)), "Scrolled edge app fixture placement");
            com.jeremykenedy.firetv.ui.nativeapp.appgrid.AppGridPreferences.save(context,
                    com.jeremykenedy.firetv.ui.nativeapp.appgrid.AppGridOptions.defaults().grid(columns, 0, false));
            check(context.getSharedPreferences("native_app_grid", 0).edit()
                    .putString("home_order", String.join("\n", order)).commit(), "Seed wide Home order");
            final Activity home = startActivitySync(new Intent().setClassName(context,
                    "com.jeremykenedy.firetv.ui.nativeapp.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            try {
                final View[] first = new View[1], last = new View[1];
                for (int n = 0; n < 60 && last[0] == null; n++) {
                    onMain(new Runnable() { public void run() {
                        first[0] = home.getWindow().getDecorView().findViewWithTag(order.get(0));
                        last[0] = home.getWindow().getDecorView().findViewWithTag(order.get(columns - 1));
                    }});
                    if (last[0] == null) SystemClock.sleep(100);
                }
                check(first[0] instanceof FocusTileView && last[0] instanceof FocusTileView,
                        "Actual Home row must contain two real app tiles");
                waitForIdleSync();
                sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN); waitForIdleSync();
                onMain(new Runnable() { public void run() {
                    check(com.jeremykenedy.firetv.ui.nativeapp.appgrid.AppGridPreferences.load(context).columns == columns,
                            "Saved column count must remain exact");
                    check(first[0].requestFocus(), "First app accepts focus");
                }});
                waitForIdleSync(); SystemClock.sleep(250);
                onMain(new Runnable() { public void run() {
                    check(first[0].isFocused() && first[0].hasWindowFocus(), "First app remains focused");
                    check(Math.abs(((ViewGroup) first[0]).getChildAt(0).getScaleX() - 1.18f) < 0.002f, "First app focus is settled");
                    checkSurfaceInViewport(first[0], "First focused app");
                    View next = home.getWindow().getDecorView().findViewWithTag(order.get(1));
                    check(surfaceBounds(first[0]).right <= surfaceBounds(next).left + 1f, "First focused app must not overlap neighbor");
                }});
                screenshot(context, "grid-" + columns + "-first.png");
                for (int n = 1; n < columns; n++) {
                    sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT); waitForIdleSync();
                    final String expected = order.get(n);
                    onMain(new Runnable() { public void run() {
                        check(home.getCurrentFocus() != null && expected.equals(home.getCurrentFocus().getTag()),
                                "Dpad must reach every first row tile in order: " + expected);
                    }});
                }
                SystemClock.sleep(250);
                final int[] dimensions = new int[4];
                onMain(new Runnable() { public void run() {
                    android.widget.GridLayout grid = (android.widget.GridLayout) last[0].getParent();
                    android.widget.ScrollView viewport = viewport(last[0]);
                    check(grid.getColumnCount() == columns, "Home must render the saved vertical grid");
                    android.graphics.Rect window = new android.graphics.Rect();
                    check(viewport.getGlobalVisibleRect(window), "App viewport must be visible");
                    int[] location = new int[2]; last[0].getLocationOnScreen(location);
                    check(location[0] >= window.left && location[0] + last[0].getWidth() <= window.right + 1,
                            "Final column must fit the visible app viewport");
                    View surface = ((ViewGroup) last[0]).getChildAt(0);
                    check(Math.abs(surface.getScaleX() - 1.18f) < 0.002f, "Final app uses imported focus growth");
                    View previous = home.getWindow().getDecorView().findViewWithTag(order.get(columns - 2));
                    checkSurfaceInViewport(last[0], "Last focused app");
                    android.graphics.RectF grown = surfaceBounds(last[0]);
                    android.graphics.RectF neighbor = surfaceBounds(previous);
                    check(grown.left >= neighbor.right - 1, "Focused last app artwork must not overlap its neighbor");
                    check(grown.right <= home.getResources().getDisplayMetrics().widthPixels,
                            "Grown final app surface must stay on screen");
                    dimensions[0] = viewport.getWidth(); dimensions[1] = last[0].getWidth();
                    dimensions[2] = grid.getWidth(); dimensions[3] = grid.getChildCount();
                }});
                screenshot(context, "grid-" + columns + "-edge.png");
                sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN); waitForIdleSync();
                onMain(new Runnable() { public void run() {
                    check(order.get(columns * 2 - 1).equals(home.getCurrentFocus().getTag()),
                            "Dpad reaches the next row in the final column");
                }});
                for (int n = 0; n < 4; n++) { sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN); waitForIdleSync(); }
                final String[] expectedTag = new String[1], focusDetails = new String[1];
                onMain(new Runnable() { public void run() {
                    View focused = home.getCurrentFocus();
                    focusDetails[0] = focusDetails(focused);
                    check(focused instanceof FocusTileView && focused.isFocused() && focused.hasWindowFocus(),
                            "Final Dpad selection must be a focused tile in the active window: " + focusDetails[0]);
                    expectedTag[0] = String.valueOf(focused.getTag());
                    check(apps.get(2).key().equals(focused.getTag()), "Scrolled edge must be the real app tile");
                    check(((android.widget.GridLayout) focused.getParent()).indexOfChild(focused) >= columns,
                            "Final Dpad selection must be in a lower Home row");
                    focusDetails[0] = focusDetails(focused);
                }});
                result.putString("focus_before_wait_" + columns, focusDetails[0]);
                final float expectedScale = appKeys.contains(expectedTag[0]) ? 1.18f : 1.06f;
                final boolean[] scrolled = {false};
                for (int attempt = 0; attempt < 60 && !scrolled[0]; attempt++) {
                    onMain(new Runnable() { public void run() {
                        View focused = home.getCurrentFocus();
                        if (!(focused instanceof FocusTileView)) return;
                        android.widget.ScrollView viewport = viewport(focused);
                        android.graphics.Rect visible = new android.graphics.Rect();
                        View surface = ((ViewGroup) focused).getChildAt(0);
                        scrolled[0] = focused.isFocused() && focused.hasWindowFocus()
                                && expectedTag[0].equals(focused.getTag())
                                && viewport.getScrollY() > 0 && focused.getGlobalVisibleRect(visible)
                                && visible.width() == focused.getWidth() && visible.height() == focused.getHeight()
                                && Math.abs(surface.getScaleX() - expectedScale) < 0.002f
                                && Math.abs(surface.getScaleY() - expectedScale) < 0.002f;
                        focusDetails[0] = focusDetails(focused);
                    }});
                    if (!scrolled[0]) SystemClock.sleep(50);
                }
                check(scrolled[0], "Scrolled selection must be fully visible and visually focused at scale "
                        + expectedScale + ": " + focusDetails[0]);
                waitForIdleSync(); SystemClock.sleep(40);
                onMain(new Runnable() { public void run() {
                    View focused = home.getCurrentFocus();
                    check(focused instanceof FocusTileView && focused.isFocused() && focused.hasWindowFocus()
                            && expectedTag[0].equals(focused.getTag()), "Visual selection must remain active for screenshot");
                    checkSurfaceInViewport(focused, "Scrolled focused app");
                    focusDetails[0] = focusDetails(focused);
                }});
                result.putString("focus_at_capture_" + columns, focusDetails[0]);
                result.putFloat("expected_scroll_scale_" + columns, expectedScale);
                screenshot(context, "grid-" + columns + "-scrolled.png");
                result.putString("columns_" + columns, "PASS: viewport=" + dimensions[0] + ", tile=" + dimensions[1]
                        + ", grid=" + dimensions[2] + ", tiles=" + dimensions[3]);
            } finally {
                onMain(new Runnable() { public void run() { home.finish(); }}); waitForIdleSync();
                long deadline = SystemClock.uptimeMillis() + 5000;
                while (!home.isDestroyed() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(40);
                check(home.isDestroyed(), "Wide Home must finish before the next fixture");
            }
        }
        result.putInt("screen_width_px", context.getResources().getDisplayMetrics().widthPixels);
        result.putFloat("density", context.getResources().getDisplayMetrics().density);
        result.putString("result", "PASS: actual Home five/sixteen/twenty columns, first/last app focus, viewport bounds and scrolling");
        result.putString("limits", "Synthetic retained inputs and three real app tiles; operator must restore durable guard");
    }
    private static android.widget.ScrollView viewport(View view) {
        android.view.ViewParent parent = view.getParent();
        while (parent != null && !(parent instanceof android.widget.ScrollView)) parent = parent.getParent();
        check(parent instanceof android.widget.ScrollView, "Vertical app viewport required");
        return (android.widget.ScrollView) parent;
    }
    private static void checkSurfaceInViewport(View tile, String label) {
        android.graphics.Rect viewportBounds = new android.graphics.Rect();
        check(viewport(tile).getClipChildren() && viewport(tile).getClipToPadding(), label + " viewport must clip scrolling content");
        check(viewport(tile).getGlobalVisibleRect(viewportBounds), label + " viewport visible");
        android.graphics.RectF grown = surfaceBounds(tile);
        check(grown.left >= viewportBounds.left - 1f && grown.right <= viewportBounds.right + 1f
                && grown.top >= viewportBounds.top - 1f && grown.bottom <= viewportBounds.bottom + 1f,
                label + " surface clipped: " + grown + " viewport " + viewportBounds);
    }
    private static android.graphics.RectF surfaceBounds(View tile) {
        View surface = ((ViewGroup) tile).getChildAt(0);
        android.graphics.RectF bounds = new android.graphics.RectF(0, 0, surface.getWidth(), surface.getHeight());
        surface.getMatrix().mapRect(bounds);
        int[] location = new int[2]; tile.getLocationOnScreen(location);
        bounds.offset(location[0] + surface.getLeft(), location[1] + surface.getTop());
        return bounds;
    }
    private static String focusDetails(View focused) {
        if (focused == null) return "focused=null";
        if (!(focused instanceof FocusTileView)) return "tag=" + focused.getTag() + ", class="
                + focused.getClass().getName() + ", focused=" + focused.isFocused() + ", window=" + focused.hasWindowFocus();
        int[] location = new int[2]; focused.getLocationOnScreen(location);
        android.graphics.Rect tile = new android.graphics.Rect(location[0], location[1],
                location[0] + focused.getWidth(), location[1] + focused.getHeight());
        View surface = ((ViewGroup) focused).getChildAt(0);
        return "tag=" + focused.getTag() + ", class=" + focused.getClass().getName()
                + ", focused=" + focused.isFocused() + ", window=" + focused.hasWindowFocus()
                + ", tile=" + tile.toShortString() + ", surface=" + surfaceBounds(focused).toShortString()
                + ", scale=" + surface.getScaleX() + "/" + surface.getScaleY()
                + ", scrollY=" + viewport(focused).getScrollY();
    }
    private void screenshot(Context context, String name) throws Exception {
        android.graphics.Bitmap image = getUiAutomation().takeScreenshot();
        check(image != null, "Native screenshot required");
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(new java.io.File(context.getFilesDir(), name))) {
            check(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output), "Screenshot save failed");
        } finally { image.recycle(); }
    }
    private static View findText(View view,String needle){if(view instanceof TextView && ((TextView)view).getText().toString().contains(needle) && view.getVisibility()==View.VISIBLE)return view; if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){View found=findText(g.getChildAt(i),needle);if(found!=null)return found;}}return null;}
    private static Switch findSwitch(View view){if(view instanceof Switch && "Show a border".contentEquals(((Switch)view).getText()))return (Switch)view;if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){Switch found=findSwitch(g.getChildAt(i));if(found!=null)return found;}}return null;}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
