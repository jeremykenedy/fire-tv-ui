package com.jeremykenedy.firetv.ui.nativeapp.appearance;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.jeremykenedy.firetv.ui.nativeapp.stateguard.GuardStore;
import java.io.*;
import java.util.*;

public final class SettingsViewportInstrumentation extends Instrumentation {
    private Bundle args;
    private final StringBuilder observations = new StringBuilder();
    @Override public void onCreate(Bundle value) { super.onCreate(value); args=value;start(); }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            Context context=getTargetContext();
            check(new GuardStore(context.getDataDir()).verify(args.getString("state_guard_case_id"),new LinkedHashSet<String>(Arrays.asList("shared_prefs","files","no_backup")),args.getString("state_guard_manifest_sha256")).matches,"Matching durable guard required");
            boolean large="true".equals(args.getString("large_text"));
            check(Math.abs(context.getResources().getConfiguration().fontScale-(large?1.3f:1f))<.01f,"Expected owned system text scale");
            context.getSharedPreferences("source_accessibility",0).edit().putInt("text_percent",large?120:100).putBoolean("high_contrast",false).commit();
            final Activity settings=startActivitySync(new Intent().setClassName(context,"com.jeremykenedy.firetv.ui.nativeapp.SettingsActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();SystemClock.sleep(300);
            inspectScroll(settings,"Settings","settings");
            onMain(new Runnable(){public void run(){check(settings.findViewById(0x2070).requestFocus(),"UI settings focus");}});press(KeyEvent.KEYCODE_DPAD_CENTER);
            ActivityMonitor monitor=addMonitor("com.jeremykenedy.firetv.ui.nativeapp.focus.FocusSettingsActivity",null,false);
            onMain(new Runnable(){public void run(){check(settings.findViewById(0x2001).requestFocus(),"App Icons focus");}});press(KeyEvent.KEYCODE_DPAD_CENTER);
            Activity icons=waitForMonitorWithTimeout(monitor,5000);removeMonitor(monitor);check(icons!=null,"Actual UI settings App Icons route");waitForIdleSync();SystemClock.sleep(300);
            inspectScroll(icons,"Icons","icons");
            press(KeyEvent.KEYCODE_BACK);SystemClock.sleep(300);check(settings.hasWindowFocus(),"Back returns to Settings");
            onMain(new Runnable(){public void run(){View selected=settings.getCurrentFocus();observations.append("Settings child Back selected=").append(selected instanceof TextView?((TextView)selected).getText():"none").append(" id=").append(selected==null?0:selected.getId()).append('\n');if(!"false".equals(args.getString("expect_clip")))check(selected!=null&&selected.getId()==0x2001,"Back preserves selected App Icons in UI settings");}});screenshot("settings-icons-back.png");
            if (!"false".equals(args.getString("expect_clip"))) {
                ActivityMonitor childMonitor=addMonitor("com.jeremykenedy.firetv.ui.nativeapp.focus.FocusSettingsActivity",null,false);
                press(KeyEvent.KEYCODE_DPAD_CENTER);
                check(waitForMonitorWithTimeout(childMonitor,5000)!=null,"Selected App Icons reopens");removeMonitor(childMonitor);
                ActivityMonitor restoredMonitor=addMonitor("com.jeremykenedy.firetv.ui.nativeapp.SettingsActivity",null,false);
                onMain(new Runnable(){public void run(){settings.recreate();}});SystemClock.sleep(250);
                press(KeyEvent.KEYCODE_BACK);
                final Activity restored=waitForMonitorWithTimeout(restoredMonitor,5000);removeMonitor(restoredMonitor);check(restored!=null,"Paused Settings recreation returns");SystemClock.sleep(350);
                onMain(new Runnable(){public void run(){View selected=restored.getCurrentFocus();check(findText(restored.getWindow().getDecorView(),"UI settings")!=null,"Recreation preserves current submenu");check(selected!=null&&selected.getId()==0x2001,"Hidden-window state save retains selected App Icons");check(findScroll(restored.getWindow().getDecorView()).getScrollY()==0,"Recreation preserves current submenu scroll");}});
                screenshot("settings-recreated-back.png");
            }
            result.putString("status","PASS: actual Settings and App Icons routes, bottom scrolling and raw header/focus measurements");
        } catch(Throwable failure) { result.putString("failure",failure.toString());try{screenshot("settings-viewport-failure.png");}catch(Exception ignored){} }
        result.putString("observations",observations.toString());finish(result.containsKey("failure")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);
    }
    private void inspectScroll(final Activity activity,final String title,String prefix)throws Exception {
        final Rect heading=new Rect();final ScrollView[] scroll={null};
        onMain(new Runnable(){public void run(){View root=activity.getWindow().getDecorView();TextView text=findText(root,title);check(text!=null,"Fixed heading "+title);int[] p=new int[2];text.getLocationOnScreen(p);heading.set(p[0],p[1],p[0]+text.getWidth(),p[1]+text.getHeight());scroll[0]=findScroll(root);check(scroll[0]!=null,"Existing scroll viewport");}});
        Bitmap top=capture();store(top,prefix+"-top.png");
        for(int i=0;i<24;i++)press(KeyEvent.KEYCODE_DPAD_DOWN);SystemClock.sleep(350);waitForIdleSync();
        onMain(new Runnable(){public void run(){View focused=activity.getCurrentFocus();check(focused instanceof Button,"Focused selectable final row");check(scroll[0].getScrollY()>0,"Actual bottom scroll "+title);int[] p=new int[2];scroll[0].getLocationOnScreen(p);Rect visible=new Rect();check(focused.getGlobalVisibleRect(visible),"Final row visible");observations.append(title).append(" scrollY=").append(scroll[0].getScrollY()).append(" header=").append(heading).append(" viewportY=").append(p[1]).append(" clip=").append(scroll[0].getClipBounds()).append(" selected=").append(((Button)focused).getText()).append(" scaledWidth=").append(focused.getWidth()*focused.getScaleX()).append(" visible=").append(visible).append('\n');
            if(!"false".equals(args.getString("expect_clip"))){check(scroll[0].getClipBounds()!=null,"Bounded vertical scroll rendering");check(visible.width()+2>=focused.getWidth()*focused.getScaleX()&&visible.height()+2>=focused.getHeight()*focused.getScaleY(),"Focused row keeps horizontal and vertical growth");}
        }});
        Bitmap bottom=capture();store(bottom,prefix+"-bottom.png");int changed=0;for(int y=heading.top;y<heading.bottom;y++)for(int x=heading.left;x<heading.right;x++)if(top.getPixel(x,y)!=bottom.getPixel(x,y))changed++;
        observations.append(title).append(" headerChangedPixels=").append(changed).append('\n');top.recycle();bottom.recycle();
        if(!"false".equals(args.getString("expect_clip")))check(changed==0,"Fixed heading rendering stays unchanged after scrolling "+title+": "+changed);
        for(int i=0;i<24;i++)press(KeyEvent.KEYCODE_DPAD_UP);SystemClock.sleep(250);
        onMain(new Runnable(){public void run(){check(scroll[0].getScrollY()==0,"Remote returns to first row");}});
    }
    private static TextView findText(View view,String text){if(view instanceof TextView&&text.equals(((TextView)view).getText().toString()))return(TextView)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){TextView found=findText(((ViewGroup)view).getChildAt(i),text);if(found!=null)return found;}return null;}
    private static ScrollView findScroll(View view){if(view instanceof ScrollView)return(ScrollView)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){ScrollView found=findScroll(((ViewGroup)view).getChildAt(i));if(found!=null)return found;}return null;}
    private void onMain(final Runnable action){final Throwable[] failure={null};runOnMainSync(new Runnable(){public void run(){try{action.run();}catch(Throwable error){failure[0]=error;}}});if(failure[0]!=null)throw new AssertionError(failure[0]);}
    private void press(int key){sendKeyDownUpSync(key);waitForIdleSync();SystemClock.sleep(60);}
    private Bitmap capture(){waitForIdleSync();SystemClock.sleep(150);Bitmap bitmap=getUiAutomation().takeScreenshot();check(bitmap!=null,"Native screenshot");return bitmap;}
    private void store(Bitmap bitmap,String name)throws Exception{try(OutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),name))){check(bitmap.compress(Bitmap.CompressFormat.PNG,100,out),"Stored screenshot");}}
    private void screenshot(String name)throws Exception{Bitmap bitmap=capture();try{store(bitmap,name);}finally{bitmap.recycle();}}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
