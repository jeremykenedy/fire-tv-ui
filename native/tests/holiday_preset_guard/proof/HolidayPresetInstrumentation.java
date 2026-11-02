package com.jeremykenedy.firetv.ui.nativeapp.migration;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import com.jeremykenedy.firetv.ui.nativeapp.SourceStartup;
import com.jeremykenedy.firetv.ui.nativeapp.appearance.AppearancePreferences;
import com.jeremykenedy.firetv.ui.nativeapp.setups.HolidayPresetTransaction;
import com.jeremykenedy.firetv.ui.nativeapp.setups.PresentationState;
import com.jeremykenedy.firetv.ui.nativeapp.stateguard.GuardStore;
import java.io.*;
import java.util.*;

/** Disposable synthetic production-package lab only. */
public final class HolidayPresetInstrumentation extends Instrumentation {
    private Bundle args;
    @Override public void onCreate(Bundle args){super.onCreate(args);this.args=args;start();}
    @Override public void onStart(){Bundle result=new Bundle();Activity home=null,settings=null;int code=Activity.RESULT_CANCELED;try {
        final Context context=getTargetContext();check("com.jeremykenedy.firetv.ui".equals(context.getPackageName()),"Production lab only");
        String id=args.getString("state_guard_case_id"),hash=args.getString("state_guard_manifest_sha256"),receipt=args.getString("preupgrade_receipt_sha256");
        check(id!=null&&hash!=null&&receipt!=null&&receipt.matches("[a-f0-9]{64}"),"Guard and pre-upgrade receipt required");
        for(int n=0;n<300&&!SourceStartup.ready(context)&&SourceStartup.error(context)==null;n++)SystemClock.sleep(100);
        check(SourceStartup.ready(context),"Startup recovery failed: "+SourceStartup.error(context));
        GuardStore.Result guard=new GuardStore(context.getDataDir()).verify(id,new LinkedHashSet<String>(Arrays.asList("shared_prefs","files","no_backup")),hash);
        check(!guard.matches,"Startup must have restored synthetic interrupted values");
        verifyRestored(context);
        home=startActivitySync(new Intent().setClassName(context,"com.jeremykenedy.firetv.ui.nativeapp.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        settings=startActivitySync(new Intent().setClassName(context,"com.jeremykenedy.firetv.ui.nativeapp.appearance.BackgroundSettingsActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        synchronized(PresentationState.class) {
            partial(context);
            try(FileOutputStream output=new FileOutputStream(new File(context.getNoBackupFilesDir(),"holiday-preset-v1.snapshot"))){output.write(new byte[]{1,2,3});output.getFD().sync();}
        }
        check(!SourceStartup.ready(context),"Existing ready process must block on a pending journal");
        try{AppearancePreferences.load(context);throw new AssertionError("Partial appearance read accepted");}catch(IllegalStateException expected){}
        try{com.jeremykenedy.firetv.ui.nativeapp.focus.FocusPreferences.load(context);throw new AssertionError("Partial focus read accepted");}catch(IllegalStateException expected){}
        try{com.jeremykenedy.firetv.ui.nativeapp.icons.IconPackStore.selected(context);throw new AssertionError("Partial icon pack read accepted");}catch(IllegalStateException expected){}
        final Activity covering=settings;
        onMain(new Runnable(){public void run(){View action=covering.getWindow().getDecorView().findViewWithTag("appearance:base_frozen");check(action!=null,"Background motion control missing");action.performClick();}});waitForIdleSync();
        final Activity launcher=home;Button retry=waitForButton(launcher,"Retry");check(retry!=null,"Home must show recovery Retry");
        click(retry);SystemClock.sleep(700);check(!SourceStartup.ready(context)&&HolidayPresetTransaction.hasRecovery(context),"Corrupt recovery must stay blocked and retained");
        check("stars".equals(context.getSharedPreferences("source_appearance",0).getAll().get("base")),"Failed recovery must not write partial appearance");
        synchronized(PresentationState.class){JournalFixture.seed(context);}
        retry=waitForButton(launcher,"Retry");check(retry!=null,"Retry must remain available after failed recovery");click(retry);
        for(int n=0;n<100&&!SourceStartup.ready(context);n++)SystemClock.sleep(100);
        check(SourceStartup.ready(context),"Same-process retry did not restore journal: "+SourceStartup.error(context));verifyRestored(context);
        check(waitForButton(launcher,"Settings")!=null,"Home did not rebuild after recovery");
        result.putString("guard_manifest_sha256",hash);result.putString("preupgrade_receipt_sha256",receipt);
        result.putString("result","PASS: seeded startup recovery, same-process pending read gate, failed Retry and successful Retry");
        result.putString("limits","Synthetic same-signer lab only; guard restoration and installed APK replacement are operator steps.");
        code=Activity.RESULT_OK;
    }catch(Throwable failure){result.putString("error",failure.toString());}
    finally{final Activity s=settings,h=home;try{onMain(new Runnable(){public void run(){if(s!=null)s.finish();if(h!=null)h.finish();}});}catch(Throwable failure){result.putString("cleanup_error",failure.toString());code=Activity.RESULT_CANCELED;}finish(code,result);}}
    private static void partial(Context context){
        check(context.getSharedPreferences("source_appearance",0).edit().putString("base","stars").putString("base_palette","christmas").putBoolean("base_frozen",true).putString("scene","").commit(),"Partial appearance");
        check(context.getSharedPreferences("native_focus",0).edit().putInt("border_rgb",0xffffff).putBoolean("border_enabled",true).putInt("border_opacity",100).putBoolean("fill_enabled",false).remove("legacy_app_default").commit(),"Partial focus");
        check(context.getSharedPreferences("native_icon_packs",0).edit().putString("package","builtin:holiday:christmas").commit(),"Partial pack");
    }
    private static void verifyRestored(Context context){
        Map<String,?> a=context.getSharedPreferences("source_appearance",0).getAll(),f=context.getSharedPreferences("native_focus",0).getAll(),p=context.getSharedPreferences("native_icon_packs",0).getAll();
        check("aurora".equals(a.get("base"))&&!a.containsKey("base_palette")&&Boolean.FALSE.equals(a.get("base_frozen"))&&"halloween".equals(a.get("scene"))&&"keep".equals(a.get("fixture_unrelated")),"Appearance recovery mismatch");
        check(Boolean.TRUE.equals(f.get("legacy_app_default"))&&Integer.valueOf(0x112233).equals(f.get("border_rgb"))&&!f.containsKey("border_enabled")&&!f.containsKey("border_opacity")&&!f.containsKey("fill_enabled"),"Focus recovery mismatch");
        check("original".equals(p.get("package"))&&!HolidayPresetTransaction.hasRecovery(context),"Pack or journal recovery mismatch");
    }
    private Button waitForButton(final Activity activity,final String label){final Button[] result=new Button[1];for(int n=0;n<100;n++){onMain(new Runnable(){public void run(){result[0]=find(activity.getWindow().getDecorView(),label);}});if(result[0]!=null)return result[0];SystemClock.sleep(100);}return null;}
    private void click(final Button button){onMain(new Runnable(){public void run(){button.performClick();}});waitForIdleSync();}
    private static Button find(View view,String label){if(view instanceof Button&&label.contentEquals(((Button)view).getText()))return (Button)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){Button found=find(group.getChildAt(i),label);if(found!=null)return found;}}return null;}
    private void onMain(final Runnable action){
        final Throwable[] failure=new Throwable[1];
        runOnMainSync(new Runnable(){public void run(){try{action.run();}catch(Throwable error){failure[0]=error;}}});
        if(failure[0]!=null)throw new AssertionError("UI action failed",failure[0]);
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
