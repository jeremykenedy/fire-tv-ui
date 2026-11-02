package com.jeremykenedy.firetv.ui.nativeapp.migration;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.jeremykenedy.firetv.ui.nativeapp.SourceStartup;
import com.jeremykenedy.firetv.ui.nativeapp.icons.IconPackStore;
import com.jeremykenedy.firetv.ui.nativeapp.setups.PresentationState;
import com.jeremykenedy.firetv.ui.nativeapp.stateguard.GuardStore;
import java.io.File;
import java.io.FileOutputStream;
import java.util.*;

/** Run only against the guarded disposable production-package lab. */
public final class HolidayThemeUiInstrumentation extends Instrumentation {
    private Bundle args;
    @Override public void onCreate(Bundle args){super.onCreate(args);this.args=args;start();}
    @Override public void onStart(){Bundle result=new Bundle();Activity editor=null;int code=Activity.RESULT_CANCELED;try {
        final Context context=getTargetContext();
        check("com.jeremykenedy.firetv.ui".equals(context.getPackageName()),"Production lab only");
        String id=args.getString("state_guard_case_id"),hash=args.getString("state_guard_manifest_sha256"),receipt=args.getString("preupgrade_receipt_sha256");
        check(id!=null&&hash!=null&&receipt!=null&&receipt.matches("[a-f0-9]{64}"),"Guard and receipt required");
        for(int n=0;n<300&&!SourceStartup.ready(context)&&SourceStartup.error(context)==null;n++)SystemClock.sleep(100);
        check(SourceStartup.ready(context),"Startup not ready");
        new GuardStore(context.getDataDir()).verify(id,new LinkedHashSet<String>(Arrays.asList("shared_prefs","files","no_backup")),hash);
        synchronized(PresentationState.class){
            check(context.getSharedPreferences("source_appearance",0).edit().putString("base","off").putString("scene","halloween").putString("overlay","rain").putString("wallpaper_image_uri","content://fixture/owner").commit(),"Appearance fixture");
            check(context.getSharedPreferences("native_icon_packs",0).edit().putString("package","original").commit(),"Pack fixture");
        }
        editor=startActivitySync(new Intent().setClassName(context,"com.jeremykenedy.firetv.ui.nativeapp.appearance.BackgroundSettingsActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        final Activity page=editor;onMain(new Runnable(){public void run(){View button=page.getWindow().getDecorView().findViewWithTag("appearance:holiday_theme");check(button!=null,"Holiday theme route missing");button.performClick();}});
        waitText("New Year (Seasonal and cultural)");
        pressScrollable("Hanukkah (Jewish)");press("Transparent overlay over my current background");press("Use coordinated icon colors");pressButton("Apply");
        for(int n=0;n<100;n++){if("builtin:holiday:hanukkah".equals(context.getSharedPreferences("native_icon_packs",0).getAll().get("package")))break;SystemClock.sleep(100);}
        Map<String,?> a=context.getSharedPreferences("source_appearance",0).getAll(),p=context.getSharedPreferences("native_icon_packs",0).getAll(),f=context.getSharedPreferences("native_focus",0).getAll();
        check("lanterns".equals(a.get("base"))&&"hanukkah".equals(a.get("base_palette"))&&Boolean.FALSE.equals(a.get("base_frozen"))&&"".equals(a.get("scene")),"Theme Apply did not select visible base");
        check("rain".equals(a.get("overlay"))&&"content://fixture/owner".equals(a.get("wallpaper_image_uri")),"Unselected overlay or wallpaper changed");
        check("builtin:holiday:hanukkah".equals(p.get("package"))&&Boolean.TRUE.equals(f.get("border_enabled"))&&Integer.valueOf(0xacbfdc).equals(f.get("border_rgb")),"Optional coordinated icon and focus missing");
        Drawable styled=IconPackStore.resolve(context,"fixture.app/.Main",new ColorDrawable(Color.RED));check(styled instanceof BitmapDrawable,"Holiday icon did not render");
        Bitmap pixels=((BitmapDrawable)styled).getBitmap();check(pixels.getPixel(256,30)==0xff101b2e&&pixels.getPixel(256,20)==0xffacbfdc,"Holiday plate and accent pixels differ from recipe");
        final Activity closing=editor;onMain(new Runnable(){public void run(){closing.finish();}});editor=null;
        context.startActivity(new Intent().setClassName(context,"com.jeremykenedy.firetv.ui.nativeapp.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));
        waitText("Settings");
        result.putString("result","PASS: actual Hanukkah Apply, preserved overlay/wallpaper, coordinated focus and rendered icon pixels; Home left visible for screenshot");
        result.putString("guard_manifest_sha256",hash);result.putString("preupgrade_receipt_sha256",receipt);code=Activity.RESULT_OK;
    }catch(Throwable failure){result.putString("error",failure.toString());captureFailure(result);}
    finally{try{if(editor!=null){final Activity closing=editor;onMain(new Runnable(){public void run(){closing.finish();}});}}catch(Throwable failure){result.putString("cleanup_error",failure.toString());code=Activity.RESULT_CANCELED;}finish(code,result);}}
    private AccessibilityNodeInfo findVisible(AccessibilityNodeInfo root,String text){
        if(root==null)return null;
        for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(text))
            if(node.isVisibleToUser()&&text.contentEquals(node.getText()))return node;
        return null;
    }
    private AccessibilityNodeInfo waitText(String text){for(int n=0;n<100;n++){AccessibilityNodeInfo node=findVisible(getUiAutomation().getRootInActiveWindow(),text);if(node!=null)return node;SystemClock.sleep(100);}throw new AssertionError("Missing UI: "+text);}
    private void pressScrollable(String text){
        for(int n=0;n<30;n++) {
            AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
            check(root!=null&&"com.jeremykenedy.firetv.ui".contentEquals(root.getPackageName()),"Holiday dialog lost window focus");
            AccessibilityNodeInfo node=findVisible(root,text);
            if(node!=null){click(node,text);return;}
            AccessibilityNodeInfo list=scrollable(root);
            if(list==null||!list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
            waitForIdleSync();SystemClock.sleep(150);
        }
        throw new AssertionError("Missing UI after scrolling: "+text);
    }
    private AccessibilityNodeInfo scrollable(AccessibilityNodeInfo node){
        if(node.isScrollable())return node;
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);if(child!=null){AccessibilityNodeInfo found=scrollable(child);if(found!=null)return found;}}
        return null;
    }
    private void click(AccessibilityNodeInfo node,String text){while(node!=null&&!node.isClickable())node=node.getParent();check(node!=null&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK),"Cannot select "+text);waitForIdleSync();}
    private void press(String text){click(waitText(text),text);}
    private void pressButton(String text){
        for(int n=0;n<100;n++){
            AccessibilityNodeInfo node=findButton(getUiAutomation().getRootInActiveWindow(),text);
            if(node!=null){click(node,text);return;}
            SystemClock.sleep(100);
        }
        throw new AssertionError("Missing button: "+text);
    }
    private AccessibilityNodeInfo findButton(AccessibilityNodeInfo node,String text){
        if(node==null)return null;
        CharSequence label=node.getText();
        if(node.isVisibleToUser()&&node.isClickable()&&label!=null&&text.equalsIgnoreCase(label.toString()))return node;
        for(int i=0;i<node.getChildCount();i++){
            AccessibilityNodeInfo found=findButton(node.getChild(i),text);
            if(found!=null)return found;
        }
        return null;
    }
    private void captureFailure(Bundle result){
        Bitmap screenshot=null;
        try {
            screenshot=getUiAutomation().takeScreenshot();
            if(screenshot==null)throw new IllegalStateException("Screenshot unavailable");
            File file=File.createTempFile("holiday-ui-failure-",".png",getTargetContext().getFilesDir());
            try(FileOutputStream output=new FileOutputStream(file)){if(!screenshot.compress(Bitmap.CompressFormat.PNG,100,output))throw new IllegalStateException("Screenshot encoding failed");output.getFD().sync();}
            result.putString("failure_screenshot",file.getAbsolutePath());
        } catch(Throwable failure){result.putString("failure_screenshot_error",failure.toString());}
        finally{if(screenshot!=null)screenshot.recycle();}
    }
    private void onMain(final Runnable action){
        final Throwable[] failure=new Throwable[1];
        runOnMainSync(new Runnable(){public void run(){try{action.run();}catch(Throwable error){failure[0]=error;}}});
        if(failure[0]!=null)throw new AssertionError("UI action failed",failure[0]);
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
