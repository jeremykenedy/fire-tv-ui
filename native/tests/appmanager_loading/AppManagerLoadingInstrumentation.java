package com.jeremykenedy.firetv.ui.nativeapp.appgrid;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.TextView;
import android.widget.ScrollView;
import com.jeremykenedy.firetv.ui.nativeapp.stateguard.GuardStore;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

public final class AppManagerLoadingInstrumentation extends Instrumentation {
    private Bundle args;
    private Activity settings, manager;
    private CountDownLatch initial = new CountDownLatch(1), refresh;
    private final String activityName = "com.jeremykenedy.firetv.ui.nativeapp.appgrid.AppGridSettingsActivity";
    @Override public void onCreate(Bundle value) { super.onCreate(value); args=value; start(); }
    @Override public Activity newActivity(ClassLoader loader, String name, Intent intent)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        Activity activity=super.newActivity(loader,name,intent);
        if(activityName.equals(name)) hold(activity,initial);
        return activity;
    }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            check(new GuardStore(getTargetContext().getDataDir()).verify(args.getString("state_guard_case_id"),
                    new LinkedHashSet<String>(Arrays.asList("shared_prefs","files","no_backup")),
                    args.getString("state_guard_manifest_sha256")).matches,"Durable guard required");
            settings=startActivitySync(new Intent().setClassName(getTargetContext(),"com.jeremykenedy.firetv.ui.nativeapp.SettingsActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            ActivityMonitor monitor=addMonitor(activityName,null,false);
            focusId(settings,0x2070); press(KeyEvent.KEYCODE_DPAD_CENTER);
            focusId(settings,0x2010); press(KeyEvent.KEYCODE_DPAD_CENTER);
            manager=waitForMonitorWithTimeout(monitor,5000); check(manager!=null,"Current Apps route opens manager");
            onMain(new Runnable(){public void run(){
                check(text("Loading TV apps...")!=null,"Initial discovery explains loading");
                check(text("No TV apps available.")==null,"Pending discovery does not claim an empty inventory");
            }});
            screenshot("loading"); initial.countDown(); awaitApps();
            for(int i=0;i<8;i++)press(KeyEvent.KEYCODE_DPAD_DOWN);
            for(int i=0;i<3;i++)press(KeyEvent.KEYCODE_DPAD_RIGHT);
            final View[] retained=new View[1]; final int[] scrollY=new int[1]; final String[] tag=new String[1];
            onMain(new Runnable(){public void run(){
                retained[0]=manager.getCurrentFocus();tag[0]=String.valueOf(retained[0].getTag());
                check(tag[0].startsWith("app-manager:settings:"),"Remote selects a scrolled app control");
                scrollY[0]=scroll(retained[0]).getScrollY();check(scrollY[0]>0,"Scrollable fixture list");
                check(text("Loading TV apps...")==null,"Real inventory replaces loading");
            }});
            screenshot("loaded");
            refresh=new CountDownLatch(1);hold(manager,refresh);
            onMain(new Runnable(){public void run(){invoke(manager,"reloadApps");}});
            SystemClock.sleep(350);
            onMain(new Runnable(){public void run(){
                check(manager.getCurrentFocus()==retained[0],"Pending refresh retains the actual focused view");
                check(manager.getWindow().getDecorView().findViewWithTag(tag[0])==retained[0],"Pending refresh retains visible content");
                check(scroll(retained[0]).getScrollY()==scrollY[0],"Pending refresh retains scroll");
                check(text("Loading TV apps...")==null && text("No TV apps available.")==null,"Refresh does not blank the current list");
            }});
            screenshot("refreshing");refresh.countDown();executor(manager).submit(new Runnable(){public void run(){}}).get(5,TimeUnit.SECONDS);
            SystemClock.sleep(300);waitForIdleSync();
            onMain(new Runnable(){public void run(){
                check(text("Loading TV apps...")==null && text("No TV apps available.")==null,"Completed refresh shows the discovered inventory");
                check(manager.getCurrentFocus()!=null,"Completed refresh retains reachable controls");
            }});
            screenshot("refreshed");
            onMain(new Runnable(){public void run(){
                set(manager,"apps",new ArrayList<Object>());invoke(manager,"render");
                check(text("No TV apps available.")!=null,"A completed empty inventory keeps its genuine empty explanation");
            }});
            screenshot("empty-presentation");
            result.putString("status","PASS: delayed native discovery loading, real installed list, retained content/focus/scroll while refresh is pending, synthetic completed-empty presentation");
        }catch(Throwable failure){result.putString("failure",failure.toString());try{screenshot("loading-failure");}catch(Exception ignored){}}
        finally{initial.countDown();if(refresh!=null)refresh.countDown();onMain(new Runnable(){public void run(){if(manager!=null)manager.finish();if(settings!=null)settings.finish();}});}
        finish(result.containsKey("failure")?1:-1,result);
    }
    private void hold(Activity activity,final CountDownLatch latch){executor(activity).execute(new Runnable(){public void run(){try{check(latch.await(30,TimeUnit.SECONDS),"Discovery delay released");}catch(InterruptedException stopped){Thread.currentThread().interrupt();}}});}
    private ExecutorService executor(Activity activity){return (ExecutorService)get(activity,"loader");}
    private Object get(Object owner,String name){try{Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);}catch(Exception failed){throw new AssertionError(failed);}}
    private void set(Object owner,String name,Object value){try{Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);field.set(owner,value);}catch(Exception failed){throw new AssertionError(failed);}}
    private void invoke(Object owner,String name){try{Method method=owner.getClass().getDeclaredMethod(name);method.setAccessible(true);method.invoke(owner);}catch(Exception failed){throw new AssertionError(failed);}}
    private void awaitApps(){for(int i=0;i<60;i++){final boolean[] ready=new boolean[1];onMain(new Runnable(){public void run(){View focus=manager.getCurrentFocus();ready[0]=focus!=null&&String.valueOf(focus.getTag()).startsWith("app-manager:hide:");}});if(ready[0])return;SystemClock.sleep(100);}throw new AssertionError("Real inventory did not appear");}
    private TextView text(String value){return findText(manager.getWindow().getDecorView(),value);}
    private TextView findText(View view,String value){if(view instanceof TextView&&value.equals(((TextView)view).getText().toString()))return(TextView)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){TextView found=findText(group.getChildAt(i),value);if(found!=null)return found;}}return null;}
    private ScrollView scroll(View child){for(ViewParent parent=child.getParent();parent!=null;parent=parent.getParent())if(parent instanceof ScrollView)return(ScrollView)parent;throw new AssertionError("Missing scroll");}
    private void focusId(final Activity owner,final int id){onMain(new Runnable(){public void run(){View view=owner.findViewById(id);check(view!=null,"Current route ID "+id);view.requestFocus();}});}
    private void press(int code){sendKeyDownUpSync(code);waitForIdleSync();}
    private void screenshot(String name)throws Exception{SystemClock.sleep(250);Bitmap bitmap=getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"app-manager-"+name+".png"))){check(bitmap!=null&&bitmap.compress(Bitmap.CompressFormat.PNG,100,out),"Screenshot");}}
    private void onMain(final Runnable task){final Throwable[] failure=new Throwable[1];runOnMainSync(new Runnable(){public void run(){try{task.run();}catch(Throwable error){failure[0]=error;}}});if(failure[0]!=null)throw new AssertionError(failure[0]);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
