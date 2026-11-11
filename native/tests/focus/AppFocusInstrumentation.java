package com.jeremykenedy.firetv.ui.nativeapp.focus;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import com.jeremykenedy.firetv.ui.nativeapp.appgrid.*;
import com.jeremykenedy.firetv.ui.nativeapp.backup.*;
import com.jeremykenedy.firetv.ui.nativeapp.folders.*;
import com.jeremykenedy.firetv.ui.nativeapp.setups.*;
import com.jeremykenedy.firetv.ui.nativeapp.stateguard.GuardStore;
import java.io.*;
import java.util.*;

/** Preview-only actual controls and Home geometry, with an operator-owned durable guard. */
public final class AppFocusInstrumentation extends Instrumentation {
    private Bundle args;
    private Activity home,settings;
    private final String folder="app-focus-proof-folder";
    @Override public void onCreate(Bundle values){super.onCreate(values);args=values;start();}
    @Override public void onStart(){
        Bundle result=new Bundle();int code=Activity.RESULT_CANCELED;
        try {
            final Context context=getTargetContext();
            check(context.getPackageName().equals("com.jeremykenedy.firetv.ui.preview"),"Preview only");
            check(new GuardStore(context.getDataDir()).verify(args.getString("state_guard_case_id"),new LinkedHashSet<>(Arrays.asList("shared_prefs","files","no_backup")),args.getString("state_guard_manifest_sha256")).matches,"Matching durable guard required");
            check(FocusPreferences.appGrowth(context)==18&&FocusPreferences.appShadowSize(context)==18,"Fresh app defaults preserve original growth and shadow");
            List<AppInventory.App> apps=AppInventory.load(context);check(apps.size()>=2,"Two installed app activities required");
            final String first=apps.get(1).key();
            check(context.getSharedPreferences("native_focus",0).edit().putInt("growth_percent",7).putInt("duration_millis",0).putString("app_focus_style","default").commit(),"Independent current source focus choices seeded");
            final Map<String,?> generic=context.getSharedPreferences("native_focus",0).getAll();
            FocusPreferences.saveAppSize(context,FocusPreferences.APP_GROWTH,18);
            configure(context,apps,6,false);
            openHome(context);FocusTileView initial=tile(first);focus(initial);
            final float[] dimensions=new float[3];
            final FocusTileView originalTile=initial;
            onMain(new Runnable(){public void run(){View surface=originalTile.getChildAt(0);dimensions[0]=surface.getWidth();dimensions[1]=surface.getHeight();dimensions[2]=surface.getWidth()*surface.getScaleX();check(Math.abs(surface.getScaleX()-1.18f)<0.002f,"Default Home delivers eighteen percent focus");check(Math.abs(surface.getElevation()-dp(18))<1,"Default Home shadow is eighteen dp");}});
            screenshot(context,"app-focus-default.png");
            settings=startActivitySync(new Intent().setClassName(context,FocusSettingsActivity.class.getName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
            choose("icons:app-growth","30%");choose("icons:app-shadow","Extra large (38 dp)");
            check(FocusPreferences.appGrowth(context)==30&&FocusPreferences.appShadowSize(context)==38&&generic.equals(context.getSharedPreferences("native_focus",0).getAll()),"Actual app controls preserve generic choices");
            choose("icons:app-shadow","Off");waitFor("App focus shadow: Off");
            check(FocusPreferences.appShadowSize(context)==0,"Actual shadow control turns the authoritative flag Off");
            choose("icons:app-shadow","Extra large (38 dp)");
            screenshot(context,"app-focus-controls.png");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitForIdleSync();settings=null;
            long resumedUntil=SystemClock.uptimeMillis()+10000;while(SystemClock.uptimeMillis()<resumedUntil&&!home.hasWindowFocus())SystemClock.sleep(50);
            check(!home.isDestroyed()&&home.hasWindowFocus(),"Actual Back resumes the same existing Home Activity");
            final FocusTileView larger=tile(first);focus(larger);
            onMain(new Runnable(){public void run(){View surface=larger.getChildAt(0);check(surface.getWidth()==dimensions[0]&&surface.getHeight()==dimensions[1],"Resting artwork is unchanged after selecting thirty percent");check(surface.getWidth()*surface.getScaleX()>dimensions[2]+1,"Thirty percent visibly enlarges focused artwork beyond eighteen percent");}});
            inspect(larger,true,true);screenshot(context,"app-focus-max.png");navigation(false);
            final FocusTileView folderTile=tile("folder:"+folder);focus(folderTile);inspect(folderTile,true,true);
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);waitFor("Add apps");sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitForIdleSync();tile("folder:"+folder);
            final SavedSetupManager setups=new SavedSetupManager(context);final String saved=setups.save("App focus proof",null);
            PortableBackup.Validator validator=new PortableBackup.Validator(){public void validate(Map<String,com.jeremykenedy.firetv.ui.nativeapp.migration.PreferenceSnapshot> state)throws Exception{PresentationState.validatePortable(state);}};
            byte[] backup=PortableBackup.encode(PresentationState.capturePortable(context),validator);
            FocusPreferences.saveAppSize(context,FocusPreferences.APP_GROWTH,0);FocusPreferences.saveAppShadow(context,0);
            setups.apply(saved,true);check(FocusPreferences.appGrowth(context)==30&&FocusPreferences.appShadowSize(context)==38,"Named setup restores exact selected app sizes");
            FocusPreferences.saveAppSize(context,FocusPreferences.APP_GROWTH,0);PresentationState.applyPortable(context,PortableBackup.decode(backup,validator));
            check(FocusPreferences.appGrowth(context)==30&&FocusPreferences.appShadowSize(context)==38&&generic.equals(context.getSharedPreferences("native_focus",0).getAll()),"Portable app focus roundtrip preserves independent source focus");
            OriginalBackup imported=OriginalBackup.decode("{\"icon_focus_growth\":30,\"icon_focus_shadow_size\":38,\"icon_shadow\":false}".getBytes("UTF-8"));
            check(imported.unsupported.isEmpty(),"Stable app focus backup schema recognized");
            PresentationState.apply(context,setups.prepareOriginalBackup(imported.settings),true);
            check(FocusPreferences.appGrowth(context)==30&&FocusPreferences.appShadowSize(context)==0,"Imported stable shadow Off is authoritative");
            closeHome();openHome(context);FocusTileView off=tile(first);focus(off);inspect(off,true,false);screenshot(context,"app-focus-shadow-off.png");
            FocusPreferences.saveAppShadow(context,38);
            for(final int columns:new int[]{7,20,1}) {
                closeHome();configure(context,apps,columns,false);openHome(context);FocusTileView active=tile(first);focus(active);inspect(active,columns!=1,true);navigation(columns==1);screenshot(context,"app-focus-"+columns+".png");
            }
            closeHome();configure(context,apps,1,false);check(context.getSharedPreferences("native_app_grid",0).edit().putBoolean("horizontal",true).commit(),"Scroll-return layout selected");
            openHome(context);final FocusTileView returned=tile(first);focus(returned);
            final android.widget.HorizontalScrollView[] parent={null};final float[] originalOffset={0};
            onMain(new Runnable(){public void run(){android.view.ViewParent node=returned.getParent();while(node!=null){if(node instanceof android.widget.HorizontalScrollView){parent[0]=(android.widget.HorizontalScrollView)node;break;}node=node.getParent();}check(parent[0]!=null,"Actual Home horizontal scroll container available");originalOffset[0]=returned.getChildAt(0).getTranslationX();parent[0].scrollTo(150,0);}});
            waitForIdleSync();SystemClock.sleep(350);onMain(new Runnable(){public void run(){visible(returned);check(Math.abs(returned.getChildAt(0).getTranslationX()-originalOffset[0])>5,"Scrolling to viewport edge constrains the focused surface");parent[0].scrollTo(0,0);}});
            waitForIdleSync();SystemClock.sleep(350);onMain(new Runnable(){public void run(){visible(returned);check(Math.abs(returned.getChildAt(0).getTranslationX()-originalOffset[0])<=1.1f,"Returning scroll restores natural centered artwork position");}});
            screenshot(context,"app-focus-scroll-return.png");
            closeHome();configure(context,apps,6,true);openHome(context);FocusTileView mini=tile(first);focus(mini);inspect(mini,true,true);navigation(false);screenshot(context,"app-focus-mini.png");
            result.putString("result","PASS: actual app growth/shadow controls; unchanged resting artwork and visibly larger30%; Home and Home-folder tiles; normal6/7,20,1 viewport cap and mini navigation; shadowOff; native setup/portable/original import roundtrips");
            result.putString("limits","FolderActivity retains functional text rows; app artwork focus parity inside folders remains required. Original import exercised native reviewed parser/prepare/apply path, not SAF picker. Synthetic preview only; no production upgrade or physical TV acceptance.");code=Activity.RESULT_OK;
        }catch(Throwable failure){try{screenshot(getTargetContext(),"app-focus-failure.png");}catch(Exception ignored){}result.putString("result","FAIL: "+failure+(failure.getCause()==null?"":"; "+failure.getCause()));}
        finally{try{closeSettings();closeHome();}catch(Throwable failure){result.putString("result","FAIL: close: "+failure);code=Activity.RESULT_CANCELED;}result.putString("restoration","Operator must restore, independently verify and resolve durable guard");}
        finish(code,result);
    }
    private void configure(Context context,List<AppInventory.App> apps,int columns,boolean mini){
        AppGridPreferences.save(context,new AppGridOptions(columns,1,false,AppInventory.keys(apps),Collections.<String>emptySet(),Collections.<String>emptySet()));
        FolderPreferences.save(context,FolderOptions.empty().create(folder,"Focus proof folder").addApp(folder,apps.get(0).key()));
        HomeVisualOptions.setMini(context,mini);
    }
    private void openHome(Context context){home=startActivitySync(new Intent().setClassName(context,"com.jeremykenedy.firetv.ui.nativeapp.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();}
    private void closeHome(){if(home!=null){final Activity old=home;onMain(new Runnable(){public void run(){old.finish();}});home=null;waitForIdleSync();}}
    private void closeSettings(){if(settings!=null){final Activity old=settings;onMain(new Runnable(){public void run(){old.finish();}});settings=null;waitForIdleSync();}}
    private FocusTileView tile(final String key){long end=SystemClock.uptimeMillis()+12000;while(SystemClock.uptimeMillis()<end){final View[] found={null};onMain(new Runnable(){public void run(){found[0]=home.getWindow().getDecorView().findViewWithTag(key);}});if(found[0] instanceof FocusTileView&&found[0].getWidth()>0){waitForIdleSync();return(FocusTileView)found[0];}SystemClock.sleep(50);}throw new AssertionError("Missing Home tile: "+key);}
    private void focus(final FocusTileView tile){onMain(new Runnable(){public void run(){check(tile.requestFocus(),"Home tile accepts remote focus");}});waitForIdleSync();SystemClock.sleep(100);}
    private void inspect(final FocusTileView tile,final boolean full,final boolean shadow){onMain(new Runnable(){public void run(){View surface=tile.getChildAt(0);float scale=surface.getScaleX();check(Math.abs(scale-surface.getScaleY())<0.001f,"Focus preserves aspect ratio");check(full?Math.abs(scale-1.3f)<0.002f:scale>=1f&&scale<1.3f,"Requested growth is delivered or uniformly capped only for extreme viewport");check(Math.abs(surface.getElevation()-(shadow?dp(38):0))<1,"Actual shadow size respects selected value and Off");int[] xy=new int[2];surface.getLocationOnScreen(xy);Rect frame=new Rect();tile.getWindowVisibleDisplayFrame(frame);float edge=surface.getElevation();check(xy[0]>=frame.left+edge-2&&xy[1]>=frame.top+edge-2&&xy[0]+surface.getWidth()*scale<=frame.right-edge+2&&xy[1]+surface.getHeight()*scale<=frame.bottom-edge+2,"Focused artwork and shadow allowance fit the actual viewport");}});}
    private void visible(FocusTileView tile){View surface=tile.getChildAt(0);int[] xy=new int[2];surface.getLocationOnScreen(xy);Rect frame=new Rect();tile.getWindowVisibleDisplayFrame(frame);float edge=surface.getElevation();check(xy[0]>=frame.left+edge-2&&xy[1]>=frame.top+edge-2&&xy[0]+surface.getWidth()*surface.getScaleX()<=frame.right-edge+2&&xy[1]+surface.getHeight()*surface.getScaleY()<=frame.bottom-edge+2,"Focused artwork remains inside viewport after parent scrolling: "+Arrays.toString(xy)+" scale="+surface.getScaleX()+" extent="+surface.getWidth()+"x"+surface.getHeight()+" edge="+edge+" frame="+frame);}
    private void navigation(boolean vertical){final View[] before={null};onMain(new Runnable(){public void run(){before[0]=home.getCurrentFocus();}});for(int n=0;n<(vertical?10:1);n++){sendKeyDownUpSync(vertical?KeyEvent.KEYCODE_DPAD_DOWN:KeyEvent.KEYCODE_DPAD_RIGHT);waitForIdleSync();SystemClock.sleep(vertical?350:250);final boolean[] changed={false};onMain(new Runnable(){public void run(){View active=home.getCurrentFocus();if(active instanceof FocusTileView)visible((FocusTileView)active);changed[0]=active instanceof FocusTileView&&active!=before[0];}});if(changed[0])break;}onMain(new Runnable(){public void run(){check(home.getCurrentFocus() instanceof FocusTileView&&home.getCurrentFocus()!=before[0],"Remote navigation or bounded scrolling reaches the next app focus target");}});}
    private void choose(final String tag,String label){waitForIdleSync();SystemClock.sleep(250);onMain(new Runnable(){public void run(){View row=settings.getWindow().getDecorView().findViewWithTag(tag);check(row!=null&&row.requestFocus(),"App control accepts remote focus");}});waitForIdleSync();sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);click(label);waitForIdleSync();SystemClock.sleep(250);}
    private AccessibilityNodeInfo waitFor(String label){long end=SystemClock.uptimeMillis()+10000;while(SystemClock.uptimeMillis()<end){AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root!=null)for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(label))if(node.getText()!=null&&label.equals(node.getText().toString()))return node;SystemClock.sleep(50);}throw new AssertionError("Missing UI: "+label);}
    private void click(String label){AccessibilityNodeInfo node=waitFor(label);while(node!=null&&!node.isClickable())node=node.getParent();check(node!=null&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK),"Choose "+label);}
    private void screenshot(Context context,String name)throws Exception{waitForIdleSync();SystemClock.sleep(200);Bitmap bitmap=getUiAutomation().takeScreenshot();check(bitmap!=null,"Screenshot available");try(OutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),name))){check(bitmap.compress(Bitmap.CompressFormat.PNG,100,out),"Screenshot saved");}finally{bitmap.recycle();}}
    private int dp(int size){return Math.round(size*getTargetContext().getResources().getDisplayMetrics().density);}
    private void onMain(final Runnable action){final Throwable[] failure={null};runOnMainSync(new Runnable(){public void run(){try{action.run();}catch(Throwable error){failure[0]=error;}}});if(failure[0]!=null)throw new AssertionError("Native UI assertion",failure[0]);}
    private static void check(boolean valid,String message){if(!valid)throw new AssertionError(message);}
}
