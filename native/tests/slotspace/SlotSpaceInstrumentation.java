package com.jeremykenedy.firetv.ui.nativeapp.providers;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.LinearLayout;
import com.jeremykenedy.firetv.ui.nativeapp.appgrid.*;
import com.jeremykenedy.firetv.ui.nativeapp.cards.HomeContentView;
import com.jeremykenedy.firetv.ui.nativeapp.focus.FocusTileView;
import com.jeremykenedy.firetv.ui.nativeapp.layout.*;
import com.jeremykenedy.firetv.ui.nativeapp.providers.TvRecommendations;
import com.jeremykenedy.firetv.ui.nativeapp.stateguard.GuardStore;
import java.io.*;
import java.util.*;

/** Actual Settings/Back transitions on an exclusively owned guarded preview. */
public final class SlotSpaceInstrumentation extends Instrumentation {
    private Bundle args;
    private Activity home,settings,cards;
    private String app;
    private final String channel="channel:slot-space-proof";
    @Override public void onCreate(Bundle values){super.onCreate(values);args=values;start();}
    @Override public void onStart(){Bundle result=new Bundle();int code=Activity.RESULT_CANCELED;
        try{
            Context context=getTargetContext();check(context.getPackageName().equals("com.jeremykenedy.firetv.ui.preview"),"Preview only");
            check(new GuardStore(context.getDataDir()).verify(args.getString("state_guard_case_id"),new LinkedHashSet<>(Arrays.asList("shared_prefs","files","no_backup")),args.getString("state_guard_manifest_sha256")).matches,"Matching durable guard required");
            List<AppInventory.App> apps=AppInventory.load(context);check(!apps.isEmpty(),"Installed TV app required");app=apps.get(0).key();StringBuilder bounds=new StringBuilder();
            for(String scenario:new String[]{"default","clock","dock","mini"}){
                boolean widget=scenario.equals("clock"),dock=scenario.equals("dock"),mini=scenario.equals("mini");
                android.content.SharedPreferences.Editor raw=context.getSharedPreferences("launcher_prefs",0).edit().putBoolean("mini_mode",mini);
                if(scenario.equals("default"))raw.remove("keep_dock_bottom");else raw.putBoolean("keep_dock_bottom",dock);
                check(raw.commit(),"Owned Home presentation fixture stored");
                LayoutOptions layout=new LayoutOptions(320,180,Arrays.asList(channel,HomeSlots.CLOCK.key()),widget?Collections.<String>emptyList():Collections.singletonList(HomeSlots.CLOCK.key())).withMode(widget?LayoutOptions.Mode.BOTH:LayoutOptions.Mode.CARDS_ONLY);
                LayoutPreferences.save(context,layout);
                home=startActivitySync(new Intent().setClassName(context,"com.jeremykenedy.firetv.ui.nativeapp.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();awaitTile();SystemClock.sleep(400);
                final HomeContentView content=content();
                final TvRecommendations.Feed fixture=new TvRecommendations.Feed("slot-space-proof","Space fixture",context.getPackageName(),Collections.singletonList(new TvRecommendations.Program("fixture-program",context.getPackageName(),"Space fixture",null,"")));
                onMain(new Runnable(){public void run(){check(content.applyResult(content.beginRefresh(),new TvRecommendations.Result(TvRecommendations.Status.READY,Collections.singletonList(fixture))),"Reviewed synthetic feed reconciled through existing Home host");}});
                Rect before=measure();screenshot(context,scenario+"-before.png");
                toggleCards();awaitSpace(widget,dock);
                Rect off=measure();screenshot(context,scenario+"-off-back.png");
                if(!widget&&!dock)check(off.top<before.top-80,"Empty content removes the band above apps");else check(Math.abs(off.top-before.top)<=2,"Visible widget or explicit docking retains existing app position");
                if(widget)onMain(new Runnable(){public void run(){View clock=content.findViewWithTag("slot:"+HomeSlots.CLOCK.key());check(clock!=null&&clock.isShown(),"Enabled clock remains visible after cards are hidden");}});
                navigateEmptyOrWidget(widget);
                toggleCards();awaitSpace(true,dock);
                Rect restored=measure();screenshot(context,scenario+"-on-back.png");check(Math.abs(restored.top-before.top)<=2&&restored.width()==before.width()&&restored.height()==before.height(),"On and Back restore card-on app geometry without restarting Home");
                Map<String,?> actual=context.getSharedPreferences("launcher_prefs",0).getAll();check(scenario.equals("default")?!actual.containsKey("keep_dock_bottom"):Boolean.valueOf(dock).equals(actual.get("keep_dock_bottom")),"Saved dock presence and value remain exact");
                check(Boolean.valueOf(mini).equals(actual.get("mini_mode")),"Mini choice is unchanged by card controls");
                bounds.append(scenario).append(" before=").append(before).append(" off=").append(off).append(" on=").append(restored).append("\n");closeHome();
            }
            result.putString("bounds",bounds.toString());result.putString("result","PASS: actual Home Settings/UI/Cards Off and Back collapse empty space; On and Back restore geometry on same Home; visible clock retained, explicit dock preserved and unfocusable, mini preserved; remote navigation works");
            result.putString("limits","Reviewed synthetic feed/retained card slots and local Clock only; no real provider, permission, production upgrade or physical TV qualification");code=Activity.RESULT_OK;
        }catch(Throwable failure){try{screenshot(getTargetContext(),"failure.png");}catch(Exception ignored){}result.putString("result","FAIL: "+failure+(failure.getCause()==null?"":"; "+failure.getCause()));}
        finally{if(cards!=null){final Activity old=cards;onMain(new Runnable(){public void run(){old.finish();}});}if(settings!=null){final Activity old=settings;onMain(new Runnable(){public void run(){old.finish();}});}closeHome();result.putString("restoration","Operator must restore, independently verify and resolve durable guard");}finish(code,result);
    }
    private void toggleCards(){
        ActivityMonitor monitor=addMonitor("com.jeremykenedy.firetv.ui.nativeapp.SettingsActivity",null,false);
        onMain(new Runnable(){public void run(){View row=home.getWindow().getDecorView().findViewWithTag("statusbar:preferences");check(row!=null&&row.requestFocus(),"Actual Home Settings control takes remote focus");}});sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);settings=waitForMonitorWithTimeout(monitor,10000);removeMonitor(monitor);check(settings!=null,"Settings route opened");awaitWindow(settings);
        pressId(settings,0x2070);SystemClock.sleep(200);
        monitor=addMonitor("com.jeremykenedy.firetv.ui.nativeapp.cards.CardsSettingsActivity",null,false);pressId(settings,0x2020);cards=waitForMonitorWithTimeout(monitor,10000);removeMonitor(monitor);check(cards!=null,"Actual UI settings Cards route opened");awaitWindow(cards);
        onMain(new Runnable(){public void run(){View row=cards.getWindow().getDecorView().findViewWithTag("hide-cards");check(row!=null&&row.requestFocus(),"Actual Hide featured cards control takes remote focus");}});sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);waitForIdleSync();SystemClock.sleep(200);
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);awaitWindow(settings);cards=null;
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitForIdleSync();SystemClock.sleep(200);
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);awaitWindow(home);settings=null;check(!home.isDestroyed()&&!home.isFinishing(),"Back returns to SAME original Home");
    }
    private void pressId(final Activity activity,final int id){onMain(new Runnable(){public void run(){View row=activity.findViewById(id);check(row!=null&&row.requestFocus(),"Settings row takes remote focus");}});sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);waitForIdleSync();}
    private void awaitWindow(Activity activity){long end=SystemClock.uptimeMillis()+10000;while(SystemClock.uptimeMillis()<end){waitForIdleSync();if(activity.hasWindowFocus()){SystemClock.sleep(200);return;}SystemClock.sleep(50);}throw new AssertionError("Expected window "+activity.getClass().getName());}
    private FocusTileView awaitTile(){long end=SystemClock.uptimeMillis()+10000;while(SystemClock.uptimeMillis()<end){final View[] found={null};onMain(new Runnable(){public void run(){found[0]=home.getWindow().getDecorView().findViewWithTag(app);}});if(found[0] instanceof FocusTileView&&found[0].getWidth()>0)return(FocusTileView)found[0];SystemClock.sleep(50);}throw new AssertionError("App tile unavailable");}
    private HomeContentView content(){final View[] found={null};onMain(new Runnable(){public void run(){found[0]=home.getWindow().getDecorView().findViewWithTag("home:content");}});check(found[0] instanceof HomeContentView,"Actual mixed Home host exists");return(HomeContentView)found[0];}
    private void awaitSpace(final boolean visible,final boolean dock){long end=SystemClock.uptimeMillis()+10000;while(SystemClock.uptimeMillis()<end){final boolean[] match={false};onMain(new Runnable(){public void run(){View viewport=(View)content().getParent();LinearLayout.LayoutParams params=(LinearLayout.LayoutParams)viewport.getLayoutParams();match[0]=viewport.getVisibility()==(visible?View.VISIBLE:dock?View.INVISIBLE:View.GONE)&&params.weight==(visible||dock?1f:0f);}});if(match[0]){waitForIdleSync();SystemClock.sleep(200);return;}SystemClock.sleep(50);}throw new AssertionError("Actual slot viewport state not updated");}
    private Rect measure(){final FocusTileView tile=awaitTile();onMain(new Runnable(){public void run(){check(tile.requestFocus(),"App focus retained");}});waitForIdleSync();SystemClock.sleep(250);final Rect bounds=new Rect();onMain(new Runnable(){public void run(){View surface=tile.getChildAt(0);int[] xy=new int[2];surface.getLocationOnScreen(xy);bounds.set(xy[0],xy[1],xy[0]+Math.round(surface.getWidth()*surface.getScaleX()),xy[1]+Math.round(surface.getHeight()*surface.getScaleY()));}});return bounds;}
    private void navigateEmptyOrWidget(final boolean widget){final FocusTileView tile=awaitTile();onMain(new Runnable(){public void run(){tile.requestFocus();}});sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);waitForIdleSync();onMain(new Runnable(){public void run(){View focus=home.getCurrentFocus();check(focus!=null&&focus!=tile,"Remote Up leaves apps");if(!widget){for(android.view.ViewParent parent=focus.getParent();parent!=null;parent=parent.getParent())check(parent!=content(),"Empty spacer cannot capture focus");}}});sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);waitForIdleSync();onMain(new Runnable(){public void run(){check(home.getCurrentFocus() instanceof FocusTileView,"Remote Down returns to apps");}});}
    private void closeHome(){if(home!=null){final Activity old=home;onMain(new Runnable(){public void run(){old.finish();}});home=null;waitForIdleSync();}}
    private void screenshot(Context context,String name)throws Exception{waitForIdleSync();Bitmap image=getUiAutomation().takeScreenshot();check(image!=null,"Raw screenshot exists");try(OutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),name))){check(image.compress(Bitmap.CompressFormat.PNG,100,out),"Screenshot saved");}finally{image.recycle();}}
    private void onMain(final Runnable action){if(Looper.myLooper()==Looper.getMainLooper()){action.run();return;}final Throwable[] error={null};runOnMainSync(new Runnable(){public void run(){try{action.run();}catch(Throwable failure){error[0]=failure;}}});if(error[0]!=null)throw new AssertionError("Native UI assertion",error[0]);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
