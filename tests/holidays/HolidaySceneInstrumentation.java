package com.jeremykenedy.firetv.ui.nativeapp.holidays;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.FrameLayout;
import java.util.HashMap;
import java.util.Map;

/** Isolated native acceptance fixture; it never writes appearance preferences. */
public final class HolidaySceneInstrumentation extends Instrumentation {
    private Activity activity;
    private Dialog dialog, focusBlocker;
    private HolidaySceneView view;
    private FrameLayout surface;

    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        SharedPreferences preferences = getTargetContext().getSharedPreferences("source_appearance", Context.MODE_PRIVATE);
        final Map<String, ?> before = new HashMap<String, Object>(preferences.getAll());
        try {
            activity = startActivitySync(new Intent().setClassName(getTargetContext(),
                    "com.jeremykenedy.firetv.ui.nativeapp.appearance.BackgroundPreviewActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            onMain(new Runnable() {
                @Override public void run() {
                    dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
                    surface = new FrameLayout(activity);
                    view = new HolidaySceneView(activity);
                    surface.addView(view, new FrameLayout.LayoutParams(-1, -1));
                    dialog.setContentView(surface); dialog.show();
                    view.setScene(HolidayScenes.find("christmas"), true, 50); view.setResumed(true);
                }
            });
            waitForFocus(true);
            waitForScene();
            Bitmap first = capture(); SystemClock.sleep(1600); Bitmap second = capture();
            int snowChanges = changes(first, second, true);
            first.recycle(); second.recycle();
            check(snowChanges > 10, "Christmas snowfall must move in the sky away from the tree lights.");
            result.putInt("christmas_sky_changed_samples", snowChanges);

            onMain(new Runnable() { @Override public void run() { view.setResumed(false); } });
            stable("Paused scene must stop advancing.");
            onMain(new Runnable() { @Override public void run() { view.setResumed(true); } });
            first = capture(); SystemClock.sleep(1200); second = capture();
            check(changes(first, second, true) > 10, "Christmas snowfall must resume.");
            first.recycle(); second.recycle();

            onMain(new Runnable() { @Override public void run() { view.setVisibility(View.INVISIBLE); } });
            stable("Hidden scene must stop advancing.");
            onMain(new Runnable() { @Override public void run() { view.setVisibility(View.VISIBLE); } });
            onMain(new Runnable() {
                @Override public void run() {
                    focusBlocker = new Dialog(activity);
                    focusBlocker.setTitle("Holiday acceptance focus check");
                    focusBlocker.show();
                }
            });
            waitForFocus(false);
            stable("Scene behind a focused dialog must stop advancing.");
            onMain(new Runnable() { @Override public void run() { focusBlocker.dismiss(); } });
            waitForFocus(true);
            onMain(new Runnable() { @Override public void run() { view.setScene(HolidayScenes.find("christmas"), false, 50); } });
            stable("Motion off must retain an unchanged complete Christmas still.");
            onMain(new Runnable() { @Override public void run() { view.setScene(HolidayScenes.find("halloween"), true, 50); } });
            waitForScene(); SystemClock.sleep(2200);
            first = capture(); SystemClock.sleep(1600); second = capture();
            int halloweenChanges = changes(first, second, false);
            first.recycle(); second.recycle();
            check(halloweenChanges > 20, "Halloween character animation must change the native scene.");
            result.putInt("halloween_changed_samples", halloweenChanges);

            onMain(new Runnable() {
                @Override public void run() {
                    view.setScene(HolidayScenes.find("christmas"), true, 100);
                    view.setScene(HolidayScenes.find("halloween"), true, 0);
                    view.setScene(HolidayScenes.find("winter"), false, 50);
                }
            });
            waitForScene(); stable("Winter still must remain static after rapid scene changes.");
            onMain(new Runnable() { @Override public void run() { view.setScene(HolidayScenes.find("winter"), true, 50); } });
            first = capture(); SystemClock.sleep(1600); second = capture();
            int winterChanges = changes(first, second, true);
            first.recycle(); second.recycle();
            check(winterChanges > 10, "Winter must have moving snow over its separate landscape.");
            result.putInt("winter_sky_changed_samples", winterChanges);
            onMain(new Runnable() { @Override public void run() { view.setResumed(false); } });
            stable("Paused winter mist and snow must stop advancing.");
            onMain(new Runnable() { @Override public void run() {
                view.setResumed(true); view.setScene(HolidayScenes.find("winter"), false, 50);
            } });
            stable("Motion off must restore the complete original winter still.");
            for(final String id:new String[]{"hanukkah","diwali","eid_fitr","lunar_new_year","valentine","st_patrick","easter","thanksgiving","passover","vesak","juneteenth","ramadan","new_year","epiphany","eid_adha","rosh_hashanah","yom_kippur","sukkot","purim","holi","navratri","janmashtami","ganesh_chaturthi","vaisakhi","gurpurab","losar","obon","mahavir_jayanti","paryushan","naw_ruz","ridvan","nowruz","mehrgan","kwanzaa","independence","spring","summer","autumn"}) {
                onMain(new Runnable() { @Override public void run() { view.setScene(HolidayScenes.find(id),true,50); } });
                waitForScene();
                first=capture(); SystemClock.sleep(1400); second=capture();
                int lightChanges=changes(first,second,false); first.recycle(); second.recycle();
                check(lightChanges>10,"Scene-matched lights must visibly animate for "+id);
                result.putInt(id+"_light_changed_samples",lightChanges);
                onMain(new Runnable() { @Override public void run() { view.setScene(HolidayScenes.find(id),false,50); } });
                stable("Motion off must retain complete "+id+" artwork.");
            }
            onMain(new Runnable() { @Override public void run() { view.setScene(null, true, 50); } });
            SystemClock.sleep(400);
            first = capture();
            check(transparent(first), "Clearing a scene must leave the underlying wallpaper visible, including after pending loads.");
            first.recycle();
            onMain(new Runnable() { @Override public void run() { surface.removeView(view); } });
            check(before.equals(preferences.getAll()), "Native fixture must preserve all appearance preferences.");
            result.putString("status", "passed");
            result.putString("visual_acceptance", "pending owner and physical TV review");
        } catch (Throwable failure) {
            result.putString("status", "failed"); result.putString("error", failure.toString());
        } finally {
            onMain(new Runnable() {
                @Override public void run() {
                    if (view != null) view.setResumed(false);
                    if (focusBlocker != null) focusBlocker.dismiss();
                    if (dialog != null) dialog.dismiss();
                    if (activity != null) activity.finish();
                }
            });
            if (!before.equals(preferences.getAll())) {
                result.putString("status", "failed");
                result.putString("preservation_error", "Appearance preferences changed; fixture does not overwrite owner values.");
            }
        }
        finish("passed".equals(result.getString("status")) ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }

    private void waitForFocus(final boolean expected) {
        final boolean[] focused = new boolean[1];
        for (int attempt = 0; attempt < 100; attempt++) {
            onMain(new Runnable() { @Override public void run() { focused[0] = view.hasWindowFocus(); } });
            if (focused[0] == expected) return;
            SystemClock.sleep(50);
        }
        throw new AssertionError("Native focus state did not settle.");
    }

    private void waitForScene() {
        for (int attempt = 0; attempt < 100; attempt++) {
            SystemClock.sleep(100);
            Bitmap frame = capture(); boolean ready = !transparent(frame); frame.recycle();
            if (ready) return;
        }
        throw new AssertionError("Scene artwork did not decode within ten seconds.");
    }

    private Bitmap capture() {
        final Bitmap[] captured = new Bitmap[1];
        onMain(new Runnable() {
            @Override public void run() {
                check(view.getWidth() > 0 && view.getHeight() > 0, "Native fixture needs a laid out scene view.");
                int width = Math.min(1280, view.getWidth());
                int height = Math.max(1, Math.round(width * view.getHeight() / (float) view.getWidth()));
                captured[0] = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(captured[0]);
                canvas.scale(width / (float) view.getWidth(), height / (float) view.getHeight());
                view.draw(canvas);
            }
        });
        return captured[0];
    }

    private void stable(String message) {
        waitForIdleSync(); Bitmap first = capture(); SystemClock.sleep(1300); Bitmap second = capture();
        boolean same = first.sameAs(second); first.recycle(); second.recycle(); check(same, message);
    }

    private static int changes(Bitmap first, Bitmap second, boolean skyOnly) {
        int changed = 0;
        int left = skyOnly ? first.getWidth() / 4 : 0;
        int right = skyOnly ? first.getWidth() * 2 / 3 : first.getWidth();
        int bottom = skyOnly ? first.getHeight() / 2 : first.getHeight();
        for (int y = 0; y < bottom; y += 2)
            for (int x = left; x < right; x += 2)
                if (first.getPixel(x, y) != second.getPixel(x, y)) changed++;
        return changed;
    }

    private static boolean transparent(Bitmap frame) {
        for (int y = 0; y < frame.getHeight(); y += 16)
            for (int x = 0; x < frame.getWidth(); x += 16)
                if ((frame.getPixel(x, y) >>> 24) != 0) return false;
        return true;
    }

    private void onMain(Runnable action) { runOnMainSync(action); waitForIdleSync(); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
