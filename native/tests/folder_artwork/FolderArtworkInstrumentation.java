package com.jeremykenedy.firetv.ui.nativeapp.folders;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import com.jeremykenedy.firetv.ui.nativeapp.appgrid.AppInventory;
import com.jeremykenedy.firetv.ui.nativeapp.focus.*;
import com.jeremykenedy.firetv.ui.nativeapp.stateguard.GuardStore;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

public final class FolderArtworkInstrumentation extends Instrumentation {
    private Bundle args;
    private Activity home, folder;
    private final String id="folder-artwork-proof", missing="missing.folder/missing.folder.Main";
    private final String fixture="com.jeremykenedy.firetv.ui.folderartproof/com.jeremykenedy.firetv.ui.folderartproof.App00";
    private final CountDownLatch release=new CountDownLatch(1);
    private boolean delay=true;
    @Override public void onCreate(Bundle value){super.onCreate(value);args=value;start();}
    @Override public Activity newActivity(ClassLoader loader,String name,Intent intent)throws InstantiationException,IllegalAccessException,ClassNotFoundException{
        Activity activity=super.newActivity(loader,name,intent);
        if(delay&&name.equals(FolderActivity.class.getName())){
            delay=false;
            try{Field field=activity.getClass().getDeclaredField("loader");field.setAccessible(true);((ExecutorService)field.get(activity)).execute(new Runnable(){public void run(){try{check(release.await(20,TimeUnit.SECONDS),"Release initial folder discovery");}catch(InterruptedException stopped){Thread.currentThread().interrupt();}}});}
            catch(Exception failure){throw new AssertionError(failure);}
        }
        return activity;
    }
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            final Context context=getTargetContext();
            check(new GuardStore(context.getDataDir()).verify(args.getString("state_guard_case_id"),new LinkedHashSet<String>(Arrays.asList("shared_prefs","files","no_backup")),args.getString("state_guard_manifest_sha256")).matches,"Matching durable guard required");
            List<AppInventory.App> apps=AppInventory.load(context);AppInventory.App play=null;
            for(AppInventory.App app:apps)if("Play Store".equals(app.label))play=app;
            check(play!=null,"Installed Play Store banner for actual stable visual comparison");final String playKey=play.key();
            final boolean large = "true".equals(args.getString("large_text"));
            if (large) {
                check(Math.abs(context.getResources().getConfiguration().fontScale-1.3f)<0.01f,"Owned emulator system font scale1.3 is active");
                com.jeremykenedy.firetv.ui.nativeapp.accessibility.AccessibilityPreferences.save(context,120,false);
            }
            context.getSharedPreferences("native_focus",0).edit().clear().commit();
            context.getSharedPreferences("launcher_prefs",0).edit().remove(FocusPreferences.APP_GROWTH).remove(FocusPreferences.APP_SHADOW_SIZE).remove("icon_shadow").commit();
            FolderPreferences.save(context,FolderOptions.empty().create(id,"Media").addApp(id,playKey));
            home=startActivitySync(new Intent().setClassName(context,"com.jeremykenedy.firetv.ui.nativeapp.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
            awaitHomeTile();openFolder();
            final int[] initialBounds=new int[2];
            onMain(new Runnable(){public void run(){FocusTileView tile=member(playKey);check(tile.isFocused(),"Direct Home folder initially focuses first app, not footer");check(String.valueOf(tile.getContentDescription()).endsWith(" · Loading"),"Loading member retains its stable position");initialBounds[0]=tile.getWidth();initialBounds[1]=tile.getHeight();}});
            screenshot("folder-loading.png");release.countDown();awaitLabel(playKey,"Play Store");
            final int[] resting=new int[2];
            onMain(new Runnable(){public void run(){FocusTileView tile=member(playKey);check(tile.isFocused(),"Discovery keeps selected app");check(tile.getWidth()==initialBounds[0]&&tile.getHeight()==initialBounds[1],"Loading artwork does not move the focus target");View surface=tile.getChildAt(0);resting[0]=surface.getWidth();resting[1]=surface.getHeight();inspect(tile,1.18f,18);}});
            screenshot("folder-default18-shadow18.png");
            if (large) {
                onMain(new Runnable(){public void run(){
                    TextView title=(TextView)member(playKey).getChildAt(1);
                    android.graphics.Paint.FontMetricsInt metrics=title.getPaint().getFontMetricsInt();
                    check(title.getHeight()>=metrics.bottom-metrics.top+title.getCompoundPaddingTop()+title.getCompoundPaddingBottom(),"Large saved/system text fully fits its external label");
                    Bitmap captured=folder.getIntent().getParcelableExtra("folder_backdrop");
                    check(captured!=null&&!captured.isRecycled()&&captured.getWidth()<=192&&captured.getHeight()<=108,"Bounded transient Home backdrop remains in the folder Intent");
                    ViewGroup root=(ViewGroup)((ViewGroup)folder.findViewById(android.R.id.content)).getChildAt(0);
                    android.graphics.drawable.BitmapDrawable blurred=(android.graphics.drawable.BitmapDrawable)((ImageView)root.getChildAt(0)).getDrawable();
                    check(blurred.getPaint().isFilterBitmap()&&!blurred.getBitmap().isRecycled(),"Owned Home backdrop uses filtered live bitmap copy");
                }});
                screenshot("folder-large-text.png");
                result.putString("status","PASS: saved120% and system font scale1.3 external label fits with bounded blurred Home backdrop");
            } else {
            FocusPreferences.saveAppSize(context,FocusPreferences.APP_GROWTH,30);FocusPreferences.saveAppShadow(context,38);refresh(playKey);
            onMain(new Runnable(){public void run(){FocusTileView tile=member(playKey);check(tile.getChildAt(0).getWidth()==resting[0]&&tile.getChildAt(0).getHeight()==resting[1],"Thirty percent keeps original resting artwork");inspect(tile,1.3f,38);labelClear(tile);}});
            screenshot("folder-growth30-shadow38.png");
            for(int shadow:new int[]{0,18,28,38}) {
                FocusPreferences.saveAppShadow(context,shadow);refresh(playKey);
                screenshot("folder-fixed30-shadow"+shadow+".png");
            }
            for(String style:new String[]{"border","color"}){
                FocusPreferences.setAppStyle(context,style);refresh(playKey);
                onMain(new Runnable(){public void run(){FocusTileView tile=member(playKey);View surface=tile.getChildAt(0);check(surface.getWidth()==resting[0]&&surface.getHeight()==resting[1],"Explicit style keeps fixed16:9 artwork");check(((ViewGroup)surface).getChildAt(1).getVisibility()==View.GONE,"No duplicated internal label");inspect(tile,1.3f,38);labelClear(tile);}});
                screenshot("folder-"+style+"-growth30-shadow38.png");
            }
            FocusPreferences.setAppStyle(context,"default");FocusPreferences.saveAppSize(context,FocusPreferences.APP_GROWTH,0);FocusPreferences.saveAppShadow(context,0);refresh(playKey);
            onMain(new Runnable(){public void run(){inspect(member(playKey),1f,0);}});screenshot("folder-growth-off-shadow-off.png");
            FocusPreferences.saveAppSize(context,FocusPreferences.APP_GROWTH,30);FocusPreferences.saveAppShadow(context,38);refresh(playKey);
            press(KeyEvent.KEYCODE_DPAD_DOWN);checkFocus("add");press(KeyEvent.KEYCODE_DPAD_CENTER);
            int selected=-1;for(AppInventory.App app:apps)if(!playKey.equals(app.key())){selected++;if(fixture.equals(app.key()))break;}
            check(selected>=0,"Fixture available in native add-app chooser");for(int i=0;i<selected;i++)press(KeyEvent.KEYCODE_DPAD_DOWN);press(KeyEvent.KEYCODE_DPAD_CENTER);
            check(FolderPreferences.load(context).find(id).components.contains(fixture),"Actual Add apps chooser saves membership");checkFocus("add");
            focusMember(playKey);press(KeyEvent.KEYCODE_DPAD_RIGHT);checkFocus("app:"+fixture);press(KeyEvent.KEYCODE_DPAD_CENTER);awaitText("Ordinary launcher fixture opened");screenshot("folder-app-open.png");press(KeyEvent.KEYCODE_BACK);awaitFolder();checkFocus("app:"+fixture);
            longPress();press(KeyEvent.KEYCODE_DPAD_CENTER);check(FolderPreferences.load(context).find(id).components.get(0).equals(fixture),"Move earlier saves actual member order");checkFocus("app:"+fixture);
            longPress();press(KeyEvent.KEYCODE_DPAD_DOWN);press(KeyEvent.KEYCODE_DPAD_CENTER);check(FolderPreferences.load(context).find(id).components.get(1).equals(fixture),"Move later saves actual member order");checkFocus("app:"+fixture);
            longPress();press(KeyEvent.KEYCODE_DPAD_DOWN);press(KeyEvent.KEYCODE_DPAD_DOWN);press(KeyEvent.KEYCODE_DPAD_CENTER);check(!FolderPreferences.load(context).find(id).components.contains(fixture),"Remove member keeps app installed");checkFocus("add");
            press(KeyEvent.KEYCODE_DPAD_RIGHT);checkFocus("controls");press(KeyEvent.KEYCODE_DPAD_CENTER);awaitText("Rename");press(KeyEvent.KEYCODE_BACK);checkFocus("controls");
            press(KeyEvent.KEYCODE_BACK);awaitHome();onMain(new Runnable(){public void run(){check(home.getCurrentFocus()!=null&&("folder:"+id).equals(home.getCurrentFocus().getTag()),"Back restores the selected Home folder");}});screenshot("folder-home-back.png");
            FolderOptions expanded=FolderPreferences.load(context).addApp(id,missing);for(AppInventory.App app:apps)if(app.key().startsWith("com.jeremykenedy.firetv.ui.folderartproof/"))expanded=expanded.addApp(id,app.key());FolderPreferences.save(context,expanded);openFolder();awaitLabel(playKey,"Play Store");
            onMain(new Runnable(){public void run(){check(member(missing)!=null,"Missing member keeps its ordered tile");}});
            for(int i=0;i<3;i++){press(KeyEvent.KEYCODE_DPAD_DOWN);SystemClock.sleep(150);}
            SystemClock.sleep(350);waitForIdleSync();
            onMain(new Runnable(){public void run(){scroll().scrollBy(0,dp(6));}});SystemClock.sleep(200);
            final String[] selectedKey={null};final int[] scrollY={0};
            onMain(new Runnable(){public void run(){check(folder.getCurrentFocus() instanceof FocusTileView,"Remote reaches a lower app row");FocusTileView tile=(FocusTileView)folder.getCurrentFocus();selectedKey[0]=String.valueOf(tile.getTag());scrollY[0]=scroll().getScrollY();check(scrollY[0]>0,"Multirow folder scrolls");inspect(tile,1.3f,38);labelClear(tile);}});screenshot("folder-scrolled-growth30-shadow38.png");
            ActivityMonitor recreated=addMonitor(FolderActivity.class.getName(),null,false);onMain(new Runnable(){public void run(){folder.recreate();}});Activity replacement=waitForMonitorWithTimeout(recreated,5000);removeMonitor(recreated);check(replacement!=null,"Folder recreation returns");folder=replacement;awaitFolder();SystemClock.sleep(400);
            onMain(new Runnable(){public void run(){check(folder.getCurrentFocus()!=null&&selectedKey[0].equals(folder.getCurrentFocus().getTag()),"Recreation keeps selected app");check(Math.abs(scroll().getScrollY()-scrollY[0])<=1,"Recreation keeps folder scroll");}});screenshot("folder-recreated-scroll.png");
            longPress();press(KeyEvent.KEYCODE_BACK);checkFocus(selectedKey[0]);
            press(KeyEvent.KEYCODE_BACK);awaitHome();
            folder=startActivitySync(new Intent(context,FolderActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));awaitFolder();
            onMain(new Runnable(){public void run(){View row=folder.getWindow().getDecorView().findViewWithTag("folder:"+id);check(row!=null&&row.requestFocus(),"Manager folder route remains reachable");}});press(KeyEvent.KEYCODE_DPAD_CENTER);awaitLabel(playKey,"Play Store");
            onMain(new Runnable(){public void run(){
                check(folder.getIntent().getParcelableExtra("folder_backdrop")==null,"Manager fallback has no captured Home Intent");
                View root=((ViewGroup)folder.findViewById(android.R.id.content)).getChildAt(0);
                check(root.getBackground() instanceof android.graphics.drawable.ColorDrawable&&((android.graphics.drawable.ColorDrawable)root.getBackground()).getColor()==0xff101523,"Manager route retains its prior page background fallback");
            }});screenshot("folder-manager-fallback.png");press(KeyEvent.KEYCODE_BACK);checkFocus("folder:"+id);
            result.putString("status","PASS: actual Home folder artwork, loading positions, growth18/30/off and shadow0/18/28/38, explicit border/color, native Add/Open/move/remove/controls/Back, exact manually adjusted scroll recreation and menu focus");
            }
        }catch(Throwable failure){result.putString("failure",failure.toString()+(failure.getCause()==null?"":"; "+failure.getCause()));try{screenshot("folder-failure.png");}catch(Exception ignored){}}
        finally{release.countDown();onMain(new Runnable(){public void run(){if(folder!=null)folder.finish();if(home!=null)home.finish();}});}
        finish(result.containsKey("failure")?1:-1,result);
    }
    private void awaitHomeTile(){long end=SystemClock.uptimeMillis()+12000;while(SystemClock.uptimeMillis()<end){final boolean[] ready={false};onMain(new Runnable(){public void run(){View view=home.getWindow().getDecorView().findViewWithTag("folder:"+id);ready[0]=view instanceof FocusTileView&&view.getWidth()>0;}});if(ready[0])return;SystemClock.sleep(50);}throw new AssertionError("Home folder tile missing");}
    private void openFolder(){ActivityMonitor monitor=addMonitor(FolderActivity.class.getName(),null,false);onMain(new Runnable(){public void run(){home.getWindow().getDecorView().findViewWithTag("folder:"+id).requestFocus();}});press(KeyEvent.KEYCODE_DPAD_CENTER);folder=waitForMonitorWithTimeout(monitor,5000);removeMonitor(monitor);check(folder!=null,"Remote Home Select opens folder");awaitFolder();SystemClock.sleep(200);}
    private FocusTileView member(String key){View view=folder.getWindow().getDecorView().findViewWithTag("app:"+key);check(view instanceof FocusTileView,"Artwork focus target for "+key);return(FocusTileView)view;}
    private void focusMember(final String key){onMain(new Runnable(){public void run(){member(key).requestFocus();}});waitForIdleSync();SystemClock.sleep(200);}
    private void refresh(final String key){onMain(new Runnable(){public void run(){member(key).refreshPreferences();}});waitForIdleSync();SystemClock.sleep(200);}
    private ScrollView scroll(){return(ScrollView)folder.getWindow().getDecorView().findViewWithTag("folder:scroll");}
    private void inspect(FocusTileView tile,float scale,int shadow){View surface=tile.getChildAt(0);check(Math.abs(surface.getScaleX()-scale)<0.005f&&Math.abs(surface.getScaleY()-scale)<0.005f,"Actual selected app growth "+scale+" observed "+surface.getScaleX());check(Math.abs(surface.getElevation()-dp(shadow))<1,"Actual selected shadow");int[] position=new int[2];surface.getLocationOnScreen(position);Rect frame=new Rect();tile.getWindowVisibleDisplayFrame(frame);check(position[0]>=frame.left-1&&position[1]>=frame.top-1&&position[0]+surface.getWidth()*scale<=frame.right+1&&position[1]+surface.getHeight()*scale<=frame.bottom+1,"Actual artwork bounds stay within window");}
    private void labelClear(FocusTileView tile){View surface=tile.getChildAt(0);TextView label=(TextView)tile.getChildAt(1);int[] art=new int[2],text=new int[2];surface.getLocationOnScreen(art);label.getLocationOnScreen(text);Rect glyphs=new Rect();String value=label.getText().toString();label.getPaint().getTextBounds(value,0,value.length(),glyphs);float inkTop=text[1]+label.getBaseline()+glyphs.top;check(art[1]+surface.getHeight()*surface.getScaleY()<=inkTop+1,"Focused artwork does not obscure external label: bottom="+(art[1]+surface.getHeight()*surface.getScaleY())+", label="+inkTop);}
    private void checkFocus(final String tag){waitForIdleSync();SystemClock.sleep(200);onMain(new Runnable(){public void run(){View current=folder.getCurrentFocus();check(current!=null&&tag.equals(current.getTag()),"Selected folder control "+tag+", observed "+(current==null?"none":current.getTag()));}});}
    private void longPress(){long now=SystemClock.uptimeMillis();sendKeySync(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER,0));SystemClock.sleep(800);sendKeySync(new KeyEvent(now,SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER,0));waitForIdleSync();}
    private void press(int key){sendKeyDownUpSync(key);waitForIdleSync();}
    private void awaitFolder(){long end=SystemClock.uptimeMillis()+10000;while(SystemClock.uptimeMillis()<end){final boolean[] ready={false};onMain(new Runnable(){public void run(){ready[0]=folder.hasWindowFocus()&&folder.findViewById(android.R.id.content).getWidth()>0;}});if(ready[0])return;SystemClock.sleep(50);}throw new AssertionError("Folder did not resume");}
    private void awaitHome(){long end=SystemClock.uptimeMillis()+10000;while(SystemClock.uptimeMillis()<end&&!home.hasWindowFocus())SystemClock.sleep(50);check(home.hasWindowFocus(),"Home resumes");SystemClock.sleep(250);}
    private void awaitLabel(final String key,final String label){long end=SystemClock.uptimeMillis()+10000;while(SystemClock.uptimeMillis()<end){final boolean[] ready={false};onMain(new Runnable(){public void run(){ready[0]=label.equals(String.valueOf(member(key).getContentDescription()));}});if(ready[0]){SystemClock.sleep(200);return;}SystemClock.sleep(50);}throw new AssertionError("App artwork not loaded");}
    private void awaitText(String label){long end=SystemClock.uptimeMillis()+10000;while(SystemClock.uptimeMillis()<end){AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root!=null)for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(label))if(label.equals(String.valueOf(node.getText())))return;SystemClock.sleep(50);}throw new AssertionError("Visible native text "+label);}
    private void screenshot(String name)throws Exception{waitForIdleSync();SystemClock.sleep(200);Bitmap bitmap=getUiAutomation().takeScreenshot();check(bitmap!=null,"Native screenshot");try(OutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),name))){check(bitmap.compress(Bitmap.CompressFormat.PNG,100,out),"Screenshot stored");}finally{bitmap.recycle();}}
    private int dp(int value){return Math.round(value*getTargetContext().getResources().getDisplayMetrics().density);}
    private void onMain(final Runnable action){final Throwable[] failure={null};runOnMainSync(new Runnable(){public void run(){try{action.run();}catch(Throwable error){failure[0]=error;}}});if(failure[0]!=null)throw new AssertionError("Native folder assertion",failure[0]);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
