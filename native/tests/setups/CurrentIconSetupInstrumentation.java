package com.jeremykenedy.firetv.ui.nativeapp.setups;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import com.jeremykenedy.firetv.ui.nativeapp.appgrid.RetainedHomeChoices;
import com.jeremykenedy.firetv.ui.nativeapp.migration.*;
import com.jeremykenedy.firetv.ui.nativeapp.stateguard.GuardStore;
import org.json.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

/** Actual Save and Apply controls on an owned, durably guarded preview. */
public final class CurrentIconSetupInstrumentation extends Instrumentation {
    private Bundle args;
    private Activity screen;
    @Override public void onCreate(Bundle values){super.onCreate(values);args=new Bundle(values);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();int code=Activity.RESULT_CANCELED;
        try {
            Context context=getTargetContext();
            check(context.getPackageName().equals("com.jeremykenedy.firetv.ui.preview"),"Preview only");
            check(new GuardStore(context.getDataDir()).verify(args.getString("state_guard_case_id"),new LinkedHashSet<>(Arrays.asList("shared_prefs","files","no_backup")),args.getString("state_guard_manifest_sha256")).matches,"Matching durable guard required");
            SavedSetupManager manager=new SavedSetupManager(context);check(manager.list().isEmpty(),"Fresh setup index required");
            String component=AndroidPresentationInventory.read(context,new PreferenceSnapshot(Collections.emptyMap())).components.values().iterator().next();
            File source=new File(context.getFilesDir(),"old-original-icon.png");
            Bitmap bitmap=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888);bitmap.eraseColor(0xff2477aa);
            try(OutputStream out=new FileOutputStream(source)){check(bitmap.compress(Bitmap.CompressFormat.PNG,100,out),"Original source created");}finally{bitmap.recycle();}
            String uri=Uri.fromFile(source).toString();
            String selected=AndroidLegacyIconMigration.prepare(context,Collections.singleton(uri)).get(uri);
            File managed=new File(new File(context.getFilesDir(),"icons"),selected.substring(6));byte[] original=Files.readAllBytes(managed.toPath());
            SharedPreferences raw=context.getSharedPreferences("launcher_prefs",0),icons=context.getSharedPreferences("native_icons",0);
            String historical=new JSONObject().put(component,new JSONObject().put("customIconUri",uri)).toString(2);
            String groups="[]";
            check(raw.edit().putInt("source_ui_strict_migration_version",1).putString("app_visibility",historical).putString("widgets_config",groups).putBoolean("use_24h_clock",true).commit(),"Historical source recorded");
            Map<String,Object> pending=new LinkedHashMap<>();pending.put("icon:com.example.pendingcurrent",selected);
            check(icons.edit().putString(component,selected).putString(RetainedHomeChoices.KEY,RetainedHomeChoices.write("native_icons",pending)).commit(),"Current personal artwork saved");
            check(source.delete(),"Historical source removed while current managed icon remains");
            Map<String,?> rawBefore=raw.getAll(),iconsBefore=icons.getAll();
            screen=startActivitySync(new Intent().setClassName(context,SavedSetupsActivity.class.getName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
            save("Current personal artwork");
            if("true".equals(args.getString("baseline"))){
                waitFor("Setup unavailable");check(manager.list().isEmpty()&&rawBefore.equals(raw.getAll())&&iconsBefore.equals(icons.getAll()),"Baseline Save refusal preserves exact state");
                screenshot(context,"current-setup-baseline.png");result.putString("result","PASS: reproduced actual Save refusal with missing historical source and valid current managed artwork");
            } else {
                JSONObject personal=awaitSaved(manager,"Current personal artwork");check(rawBefore.equals(raw.getAll())&&iconsBefore.equals(icons.getAll()),"Save preserves current and historical preference values");
                check(!personal.getJSONObject("settings").getJSONObject("values").has("app_visibility")&&!SavedSetupManager.decodeNative(personal.getJSONObject("native")).get("launcher_prefs").values().containsKey("widgets_config"),"New snapshot retains current choices instead of redundant historical records");
                check(personal.getJSONObject("nativeImages").has(selected),"Current installed and pending artwork retained by native image manifest");
                byte[] indexBefore=Files.readAllBytes(new File(context.getFilesDir(),"saved-setups/setups.json").toPath());
                check(managed.delete()&&icons.edit().putString(component,"factory").putBoolean("edited:"+component,true).remove(RetainedHomeChoices.KEY).commit(),"Current choice changed and managed copy removed");
                apply(personal.getString("id"));awaitChoice(icons,component,selected);
                check(Arrays.equals(original,Files.readAllBytes(managed.toPath()))&&iconsBefore.equals(icons.getAll()),"Saved current setup restores exact artwork, pending choices and edit state from its archive");
                check(Arrays.equals(indexBefore,Files.readAllBytes(new File(context.getFilesDir(),"saved-setups/setups.json").toPath()))&&rawBefore.equals(raw.getAll())&&!source.exists(),"Apply preserves index, original raw values and missing source absence");
                android.graphics.drawable.Drawable rendered=com.jeremykenedy.firetv.ui.nativeapp.icons.IconSelectionStore.resolve(context,component,new android.graphics.drawable.ColorDrawable(0xffff0000),null);
                check(rendered instanceof android.graphics.drawable.BitmapDrawable&&((android.graphics.drawable.BitmapDrawable)rendered).getBitmap().getPixel(0,0)==0xff2477aa,"Current consumer renders restored captured pixels");
                screenshot(context,"current-setup-personal.png");
                check(icons.edit().putString(component,"factory").putBoolean("edited:"+component,true).commit(),"Unchecked-artwork fixture selected");
                SharedPreferences grid=context.getSharedPreferences("native_app_grid",0);check(grid.edit().putInt("columns",7).commit(),"Unchecked layout change selected");
                Map<String,?> unchecked=icons.getAll();toggleArtwork();apply(personal.getString("id"));
                long checkboxUntil=SystemClock.uptimeMillis()+10000;while(SystemClock.uptimeMillis()<checkboxUntil&&(grid.contains("columns")||PresentationState.hasRecovery(context)))SystemClock.sleep(50);
                waitFor("Current personal artwork");waitForIdleSync();
                check(!grid.contains("columns")&&unchecked.equals(icons.getAll())&&rawBefore.equals(raw.getAll()),"Actual unchecked Apply restores layout while preserving exact current icons and historical JSON text");toggleArtwork();

                check(icons.edit().remove(component).remove(RetainedHomeChoices.KEY).putBoolean("edited:"+component,true).commit(),"Theme default choice recorded");
                Map<String,?> theme=icons.getAll();save("Current Theme choice");JSONObject builtin=awaitSaved(manager,"Current Theme choice");
                check(builtin.getJSONObject("nativeImages").length()==0,"Theme choice does not retain inactive historical personal images");
                check(icons.edit().putString(component,selected).remove("edited:"+component).commit(),"Personal choice selected after Theme save");
                apply(builtin.getString("id"));long until=SystemClock.uptimeMillis()+10000;while(SystemClock.uptimeMillis()<until&&!theme.equals(icons.getAll()))SystemClock.sleep(50);
                waitFor("Current Theme choice");check(theme.equals(icons.getAll())&&rawBefore.equals(raw.getAll())&&!source.exists(),"Theme snapshot restores exact current built-in and edit choices without opening historical media");
                screenshot(context,"current-setup-theme.png");
                result.putString("result","PASS: actual Save and Apply preserve current personal/pending artwork and Theme choices after historical source deletion; retained copy restores absent managed file; historical originals and setup bytes preserved");
            }
            code=Activity.RESULT_OK;
        }catch(Throwable failure){try{screenshot(getTargetContext(),"current-setup-failure.png");}catch(Exception ignored){}result.putString("result","FAIL: "+failure);}
        finally{if(screen!=null){runOnMainSync(new Runnable(){@Override public void run(){screen.finish();}});waitForIdleSync();}result.putString("restoration","Operator must stop, restore, independently verify and resolve durable guard");}
        finish(code,result);
    }
    private void save(String name){
        runOnMainSync(new Runnable(){@Override public void run(){View row=screen.getWindow().getDecorView().findViewWithTag("save");check(row!=null&&row.requestFocus(),"Save row accepts remote focus");}});sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);waitFor("Save");
        AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();check(setName(root,name),"Setup name entered");click("Save");
    }
    private boolean setName(AccessibilityNodeInfo node,String name){if(node==null)return false;if("android.widget.EditText".contentEquals(node.getClassName())){Bundle value=new Bundle();value.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,name);return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,value);}for(int i=0;i<node.getChildCount();i++)if(setName(node.getChild(i),name))return true;return false;}
    private JSONObject awaitSaved(SavedSetupManager manager,String name)throws Exception{long until=SystemClock.uptimeMillis()+10000;while(SystemClock.uptimeMillis()<until){for(JSONObject item:manager.list())if(name.equals(item.getString("name"))){awaitRow(item.getString("id"));waitFor(name);waitForIdleSync();SystemClock.sleep(150);return item;}SystemClock.sleep(50);}throw new AssertionError("Setup not saved: "+name);}
    private void awaitRow(final String id){long until=SystemClock.uptimeMillis()+10000;while(SystemClock.uptimeMillis()<until){final boolean[] found={false};runOnMainSync(new Runnable(){@Override public void run(){found[0]=screen.getWindow().getDecorView().findViewWithTag("setup:"+id)!=null;}});if(found[0])return;SystemClock.sleep(50);}throw new AssertionError("Saved setup row not ready");}
    private void apply(final String id){runOnMainSync(new Runnable(){@Override public void run(){View row=screen.getWindow().getDecorView().findViewWithTag("setup:"+id);check(row!=null&&row.requestFocus(),"Captured setup accepts remote focus");}});waitForIdleSync();SystemClock.sleep(150);sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);waitFor("Apply");waitForIdleSync();SystemClock.sleep(100);sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);}
    private void toggleArtwork(){runOnMainSync(new Runnable(){@Override public void run(){View row=screen.getWindow().getDecorView().findViewWithTag("artwork");check(row!=null&&row.requestFocus(),"Artwork checkbox accepts remote focus");}});sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);waitForIdleSync();}
    private void awaitChoice(SharedPreferences prefs,String key,String value){long until=SystemClock.uptimeMillis()+10000;while(SystemClock.uptimeMillis()<until){if(value.equals(prefs.getString(key,""))&&!PresentationState.hasRecovery(getTargetContext())){waitFor("Current personal artwork");return;}SystemClock.sleep(50);}throw new AssertionError("Captured artwork not applied");}
    private AccessibilityNodeInfo waitFor(String label){long until=SystemClock.uptimeMillis()+10000;while(SystemClock.uptimeMillis()<until){AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root!=null)for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(label))if(node.getText()!=null&&label.equalsIgnoreCase(node.getText().toString()))return node;SystemClock.sleep(50);}throw new AssertionError("Missing UI: "+label);}
    private void click(String label){AccessibilityNodeInfo node=waitFor(label);while(node!=null&&!node.isClickable())node=node.getParent();check(node!=null&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK),"Select "+label);waitForIdleSync();}
    private void screenshot(Context context,String name)throws Exception{waitForIdleSync();SystemClock.sleep(200);Bitmap image=getUiAutomation().takeScreenshot();check(image!=null,"Screenshot available");try(OutputStream output=new FileOutputStream(new File(context.getExternalFilesDir(null),name))){check(image.compress(Bitmap.CompressFormat.PNG,100,output),"Screenshot saved");}finally{image.recycle();}}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
