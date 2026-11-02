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
            waitForHomeStyle(actualHome, apps, 1.06f, false);
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
            waitForHomeStyle(restoredHome, apps, 1.18f, true);
            result.putString("result","PASS: synthetic preview marker, Settings edit and async native Home scale/clipping, saved setup Home style/clipping restore");
            result.putString("limits","Synthetic marker only; production migration, visual pixel parity and failed disk save require separate proof.");
            finish(Activity.RESULT_OK,result);
        } catch (Throwable failure) { result.putString("error",failure.toString()); finish(Activity.RESULT_CANCELED,result); }
    }
    private void waitForHomeStyle(final Activity home, final List<AppInventory.App> apps, final float expected, final boolean clipped) {
        final boolean[] matched={false};
        for(int n=0;n<80 && !matched[0];n++) {
            onMain(new Runnable(){public void run(){
                for(AppInventory.App app:apps) {
                    View candidate=home.getWindow().getDecorView().findViewWithTag(app.key());
                    if(!(candidate instanceof FocusTileView)) continue;
                    if(!candidate.isFocused()) candidate.requestFocus();
                    android.view.ViewParent parent=candidate.getParent();
                    while(parent!=null && !(parent instanceof android.widget.ScrollView)) parent=parent.getParent();
                    if(!(parent instanceof android.widget.ScrollView)) return;
                    android.widget.ScrollView viewport=(android.widget.ScrollView)parent;
                    View surface=((ViewGroup)candidate).getChildAt(0);
                    matched[0]=candidate.isFocused() && candidate.hasWindowFocus()
                            && Math.abs(surface.getScaleX()-expected)<0.002f
                            && viewport.getClipChildren()==clipped && viewport.getClipToPadding()==clipped;
                    return;
                }
            }});
            if(!matched[0])SystemClock.sleep(100);
        }
        check(matched[0], "Home mode, visual scale and viewport clipping did not settle");
    }
    private void onMain(final Runnable action) {
        final Throwable[] failure = new Throwable[1];
        runOnMainSync(new Runnable() { public void run() { try { action.run(); } catch (Throwable error) { failure[0] = error; } } });
        if (failure[0] != null) throw new AssertionError("UI assertion failed", failure[0]);
    }
    private static View findText(View view,String needle){if(view instanceof TextView && ((TextView)view).getText().toString().contains(needle) && view.getVisibility()==View.VISIBLE)return view; if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){View found=findText(g.getChildAt(i),needle);if(found!=null)return found;}}return null;}
    private static Switch findSwitch(View view){if(view instanceof Switch && "Show a border".contentEquals(((Switch)view).getText()))return (Switch)view;if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){Switch found=findSwitch(g.getChildAt(i));if(found!=null)return found;}}return null;}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
