package com.jeremykenedy.firetv.ui.nativeapp.focus;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import com.jeremykenedy.firetv.ui.nativeapp.appgrid.*;
import com.jeremykenedy.firetv.ui.nativeapp.stateguard.GuardStore;
import java.io.*;
import java.util.*;

public final class AppFocusDeltaInstrumentation extends Instrumentation {
    private Bundle args;private Activity home,settings;
    @Override public void onCreate(Bundle values){super.onCreate(values);args=values;start();}
    @Override public void onStart(){Bundle result=new Bundle();int code=Activity.RESULT_CANCELED;Bitmap off=null;
        try{
            Context context=getTargetContext();check(context.getPackageName().equals("com.jeremykenedy.firetv.ui.preview"),"Preview only");
            check(new GuardStore(context.getDataDir()).verify(args.getString("state_guard_case_id"),new LinkedHashSet<>(Arrays.asList("shared_prefs","files","no_backup")),args.getString("state_guard_manifest_sha256")).matches,"Durable guard required");
            List<AppInventory.App> apps=AppInventory.load(context);check(apps.size()>=3,"Three installed app activities required");String key=apps.get(1).key();
            context.getSharedPreferences("native_focus",0).edit().putString("app_focus_style","default").putInt("duration_millis",0).commit();FocusPreferences.saveAppSize(context,FocusPreferences.APP_GROWTH,30);
            AppGridPreferences.save(context,new AppGridOptions(3,1,false,AppInventory.keys(apps),Collections.<String>emptySet(),Collections.<String>emptySet()));
            Rect original=null;int count18=0,count38=0;StringBuilder outlineData=new StringBuilder();
            for(int size:new int[]{0,18,38}){
                choose(size);final FocusTileView tile=tile(key);onMain(new Runnable(){public void run(){check(tile.requestFocus(),"Centered app focus");}});waitForIdleSync();SystemClock.sleep(350);
                final Rect bounds=new Rect();final String[] diagnostic={""};onMain(new Runnable(){public void run(){View surface=tile.getChildAt(0);int[] xy=new int[2];surface.getLocationOnScreen(xy);bounds.set(xy[0],xy[1],xy[0]+Math.round(surface.getWidth()*surface.getScaleX()),xy[1]+Math.round(surface.getHeight()*surface.getScaleY()));Outline outline=new Outline();surface.getOutlineProvider().getOutline(surface,outline);diagnostic[0]="size="+size+" hw="+surface.isHardwareAccelerated()+" outlineAlpha="+outline.getAlpha()+" elevation="+surface.getElevation()+" bounds="+bounds;}});outlineData.append(diagnostic[0]).append("\n");
                if(original==null)original=new Rect(bounds);else check(original.equals(bounds),"Shadow captures preserve same centered bounds and thirty-percent size");
                Bitmap image=getUiAutomation().takeScreenshot();save(context,image,"app-shadow-"+size+".png");
                if(size==0)off=image;else{int darker=darker(off,image,bounds,Math.round(55*context.getResources().getDisplayMetrics().density));if(size==18)count18=darker;else count38=darker;image.recycle();}
            }
            result.putString("outline",outlineData.toString());result.putInt("darker_pixels_18",count18);result.putInt("darker_pixels_38",count38);
            if(!"true".equals(args.getString("baseline"))){check(count18>100&&count38>count18+100,"Eighteen and thirty-eight dp shadows render visibly different spread from Off");
                final Activity old=home;onMain(new Runnable(){public void run(){old.finish();}});home=null;waitForIdleSync();
                AppGridPreferences.save(context,new AppGridOptions(1,1,false,AppInventory.keys(apps),Collections.<String>emptySet(),Collections.<String>emptySet()));
                home=startActivitySync(new Intent().setClassName(context,"com.jeremykenedy.firetv.ui.nativeapp.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));final FocusTileView last=tile(apps.get(apps.size()-1).key());onMain(new Runnable(){public void run(){check(last.requestFocus(),"Extreme focused last row");}});waitForIdleSync();SystemClock.sleep(400);
                Bitmap image=getUiAutomation().takeScreenshot();int white=0;for(int y=0;y<65;y++)for(int x=200;x<1300;x++){int pixel=image.getPixel(x,y);if(Color.red(pixel)>220&&Color.green(pixel)>220&&Color.blue(pixel)>220)white++;}save(context,image,"app-focus-clipped-1.png");image.recycle();result.putInt("header_white_pixels",white);check(white<10,"Nonfocused offscreen app rows never bleed over header");
                boolean moved=false;for(int attempt=0;attempt<10&&!moved;attempt++){sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);waitForIdleSync();SystemClock.sleep(350);final boolean[] changed={false};onMain(new Runnable(){public void run(){changed[0]=home.getCurrentFocus() instanceof FocusTileView&&home.getCurrentFocus()!=last;}});moved=changed[0];}check(moved,"Remote scrolling can focus a previously clipped row");
            }
            result.putString("result","PASS: centered same-position Off/18/38 native shadow diagnostics and declared delta checks");code=Activity.RESULT_OK;
        }catch(Throwable failure){result.putString("result","FAIL: "+failure+(failure.getCause()==null?"":"; "+failure.getCause()));}
        finally{if(off!=null)off.recycle();if(settings!=null){final Activity old=settings;onMain(new Runnable(){public void run(){old.finish();}});}if(home!=null){final Activity old=home;onMain(new Runnable(){public void run(){old.finish();}});}result.putString("restoration","Operator must restore, independently verify and resolve durable guard");}finish(code,result);
    }
    private int darker(Bitmap off,Bitmap current,Rect bounds,int margin){int total=0;for(int y=Math.max(0,bounds.top-margin);y<Math.min(off.getHeight(),bounds.bottom+margin);y++)for(int x=Math.max(0,bounds.left-margin);x<Math.min(off.getWidth(),bounds.right+margin);x++){if(x>=bounds.left-1&&x<bounds.right+1&&y>=bounds.top-1&&y<bounds.bottom+1)continue;int a=off.getPixel(x,y),b=current.getPixel(x,y);if(Color.red(a)+Color.green(a)+Color.blue(a)>Color.red(b)+Color.green(b)+Color.blue(b)+6)total++;}return total;}
    private void choose(int size){if(home!=null){final Activity old=home;onMain(new Runnable(){public void run(){old.finish();}});home=null;waitForIdleSync();}FocusPreferences.saveAppShadow(getTargetContext(),size);home=startActivitySync(new Intent().setClassName(getTargetContext(),"com.jeremykenedy.firetv.ui.nativeapp.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();}
    private FocusTileView tile(final String key){long end=SystemClock.uptimeMillis()+10000;while(SystemClock.uptimeMillis()<end){final View[] found={null};onMain(new Runnable(){public void run(){found[0]=home.getWindow().getDecorView().findViewWithTag(key);}});if(found[0] instanceof FocusTileView&&found[0].getWidth()>0)return(FocusTileView)found[0];SystemClock.sleep(50);}throw new AssertionError("Missing app "+key);}
    private void save(Context context,Bitmap bitmap,String name)throws Exception{try(OutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),name))){check(bitmap.compress(Bitmap.CompressFormat.PNG,100,out),"Saved raw screenshot");}}
    private void onMain(final Runnable action){final Throwable[] error={null};runOnMainSync(new Runnable(){public void run(){try{action.run();}catch(Throwable failure){error[0]=failure;}}});if(error[0]!=null)throw new AssertionError("Native UI assertion",error[0]);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
