package com.jeremykenedy.firetv.ui.nativeapp.appearance;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import java.util.Map;

public final class AppearanceInstrumentation extends Instrumentation {
    private static final String PREFIX="com.jeremykenedy.firetv.ui.nativeapp.";
    private Activity main,settings,editor,preview;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result=new Bundle();
        final SharedPreferences prefs=AppearancePreferences.file(getTargetContext());
        final Map<String,?> before=prefs.getAll();
        try {
            check(prefs.edit().clear().putString("base","off").putString("overlay","snowflakes")
                    .putInt("overlay_opacity",80).putInt("overlay_intensity",70)
                    .putString("wallpaper_image_uri","content://owner/preserved-image")
                    .putString("wallpaper_video_uri","content://owner/preserved-video").commit(),"Set isolated fixture");
            onMain(new Runnable() { @Override public void run() { verifyRender(); } });
            main=startActivitySync(new Intent().setClassName(getTargetContext(),PREFIX+"MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            settings=open(main,"Settings",PREFIX+"SettingsActivity");
            editor=open(settings,"Backgrounds and overlays",PREFIX+"appearance.BackgroundSettingsActivity");
            SystemClock.sleep(200);
            final long[] time=new long[1];
            onMain(new Runnable() { @Override public void run() {
                AppearanceView overlay=(AppearanceView)editor.getWindow().getDecorView().findViewWithTag("appearance:overlay");
                check(overlay!=null&&overlay.isFrameScheduled(),"Overlay callbacks run with base off through actual settings route"); time[0]=overlay.animationMillis();
            } });
            SystemClock.sleep(300);
            onMain(new Runnable() { @Override public void run() {
                AppearanceView overlay=(AppearanceView)editor.getWindow().getDecorView().findViewWithTag("appearance:overlay");
                check(overlay.animationMillis()>time[0],"Overlay time advances visibly independent of base");
            } });
            preview=open(editor,"Full screen preview",PREFIX+"appearance.BackgroundPreviewActivity");
            onMain(new Runnable() { @Override public void run() {
                AppearanceView overlay=(AppearanceView)editor.getWindow().getDecorView().findViewWithTag("appearance:overlay");
                check(!overlay.isFrameScheduled(),"Covered settings pause frame callback");
            } });
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); waitForIdleSync();
            onMain(new Runnable() { @Override public void run() {
                check(editor.hasWindowFocus(),"Back restores settings window focus");
                View action=editor.getWindow().getDecorView().findViewWithTag("appearance:overlay_frozen"); action.performClick();
                AppearanceView overlay=(AppearanceView)editor.getWindow().getDecorView().findViewWithTag("appearance:overlay");
                check(!overlay.isFrameScheduled(),"Still selection removes animation callback");
            } });
            AppearanceOptions saved=AppearancePreferences.load(getTargetContext());
            check(saved.overlayFrozen&&saved.overlayOpacity==80&&saved.base.equals("off"),"Selected state persists without unrelated changes");
            check(saved.imageUri.equals("content://owner/preserved-image")&&saved.videoUri.equals("content://owner/preserved-video"),"Wallpaper references retained");
            onMain(new Runnable() { @Override public void run() {
                editor.getWindow().getDecorView().findViewWithTag("appearance:overlay_frozen").performClick();
                AppearanceView overlay=(AppearanceView)editor.getWindow().getDecorView().findViewWithTag("appearance:overlay");
                check(overlay.isFrameScheduled(),"Individual animated choice resumes");
                editor.getWindow().getDecorView().findViewWithTag("appearance:reduced_motion").performClick();
                check(!overlay.isFrameScheduled(),"Reduced motion cancels overlay callback");
                check(!AppearancePreferences.load(getTargetContext()).overlayFrozen,"Reduced motion keeps individual choice");
                editor.getWindow().getDecorView().findViewWithTag("appearance:reduced_motion").performClick();
                check(overlay.isFrameScheduled(),"Disabling reduced motion restores individual animated choice");
            } });
            result.putString("result","PASS: native overlay transparency, opacity, independent motion, actual settings and preview routes, pause and Back");
        } catch(Throwable failure) { result.putString("failure",failure.toString()); }
        finally {
            onMain(new Runnable() { @Override public void run() { if(preview!=null)preview.finish(); if(editor!=null)editor.finish(); if(settings!=null)settings.finish(); if(main!=null)main.finish(); } });
            SharedPreferences.Editor restore=prefs.edit().clear();
            for(Map.Entry<String,?> entry:before.entrySet()) {
                Object value=entry.getValue(); String key=entry.getKey();
                if(value instanceof String)restore.putString(key,(String)value); else if(value instanceof Integer)restore.putInt(key,(Integer)value);
                else if(value instanceof Boolean)restore.putBoolean(key,(Boolean)value); else if(value instanceof Long)restore.putLong(key,(Long)value);
                else if(value instanceof Float)restore.putFloat(key,(Float)value);
                else if(value instanceof java.util.Set) { @SuppressWarnings("unchecked") java.util.Set<String> set=(java.util.Set<String>)value; restore.putStringSet(key,set); }
            }
            check(restore.commit()&&prefs.getAll().equals(before),"Restore all appearance preferences exactly");
        }
        finish(result.containsKey("failure") ? 1 : -1,result);
    }
    private void verifyRender() {
        Context context=getTargetContext(); AppearanceView view=new AppearanceView(context,true); view.layout(0,0,960,540);
        Bitmap pixels=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888);
        view.setOptions(AppearancePreferences.load(context)); view.draw(new Canvas(pixels));
        long high=alpha(pixels); check(high>0&&high<960L*540*255,"Overlay contains transparent backing and visible flakes");
        int transparent=0; for(int y=0;y<540;y+=8) for(int x=0;x<960;x+=8) if((pixels.getPixel(x,y)>>>24)==0) transparent++;
        check(transparent>6000,"Most of the overlay remains completely transparent");
        AppearancePreferences.number(context,"overlay_opacity",20); pixels.eraseColor(0);
        view.setOptions(AppearancePreferences.load(context)); view.draw(new Canvas(pixels));
        check(alpha(pixels)<high/2,"Opacity visibly changes rendered alpha");
        AppearancePreferences.number(context,"overlay_opacity",80);
        int[] rendered=new int[960*540];
        for(String style:AppearanceOptions.OVERLAYS) {
            pixels.eraseColor(0);
            view.setOptions(new AppearanceOptions("off",false,50,50,50,style,true,50,70,80,"","",""));
            view.draw(new Canvas(pixels)); pixels.getPixels(rendered,0,960,0,0,960,540);
            int visible=0,clear=0;
            for(int pixel:rendered) { if((pixel>>>24)==0)clear++; else visible++; }
            check(clear>0,"Transparent backing retained for "+style);
            check(style.equals("off")?visible==0:visible>0,"Selected overlay actually renders: "+style);
        }
        pixels.recycle();
        Bitmap source=Bitmap.createBitmap(3,2,Bitmap.Config.ARGB_8888);
        source.setPixel(0,0,0xffff0000); source.setPixel(2,0,0xff00ff00); source.setPixel(0,1,0xff0000ff);
        Bitmap rotated=WallpaperImages.orient(source,android.media.ExifInterface.ORIENTATION_ROTATE_90);
        check(rotated.getWidth()==2&&rotated.getHeight()==3,"Portrait EXIF rotates dimensions");
        check(rotated.getPixel(1,0)==0xffff0000&&rotated.getPixel(1,2)==0xff00ff00&&rotated.getPixel(0,0)==0xff0000ff,"Portrait EXIF preserves pixel orientation");
        rotated.recycle();
    }
    private long alpha(Bitmap pixels) { long sum=0; for(int y=0;y<pixels.getHeight();y++)for(int x=0;x<pixels.getWidth();x++)sum+=pixels.getPixel(x,y)>>>24; return sum; }
    private Activity open(final Activity source,final String label,String target) {
        final ActivityMonitor monitor=addMonitor(target,null,false);
        onMain(new Runnable() { @Override public void run() { Button button=button(source.getWindow().getDecorView(),label); check(button!=null,"Missing native route "+label); button.performClick(); } });
        Activity opened=waitForMonitorWithTimeout(monitor,5000); removeMonitor(monitor); check(opened!=null,"Route opened "+label); waitForIdleSync(); return opened;
    }
    private Button button(View view,String label) {
        if(view instanceof Button && label.contentEquals(((Button)view).getText()))return (Button)view;
        if(view instanceof ViewGroup) { ViewGroup group=(ViewGroup)view; for(int i=0;i<group.getChildCount();i++) { Button match=button(group.getChildAt(i),label); if(match!=null)return match; } } return null;
    }
    private void onMain(final Runnable action) { final Throwable[] error=new Throwable[1]; runOnMainSync(new Runnable() { @Override public void run() { try{action.run();}catch(Throwable failure){error[0]=failure;} } }); if(error[0]!=null)throw new AssertionError(error[0]); }
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
}
