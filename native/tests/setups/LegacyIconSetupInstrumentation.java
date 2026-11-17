package com.jeremykenedy.firetv.ui.nativeapp.setups;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.KeyEvent;
import android.view.View;
import com.jeremykenedy.firetv.ui.nativeapp.appgrid.RetainedHomeChoices;
import com.jeremykenedy.firetv.ui.nativeapp.migration.*;
import com.jeremykenedy.firetv.ui.nativeapp.stateguard.GuardStore;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.*;

/** Owned disposable preview only; durable restoration remains the operator's responsibility. */
public final class LegacyIconSetupInstrumentation extends Instrumentation {
    private static final String ID="11111111-1111-1111-1111-111111111111";
    private static final String MISSING="com.example.legacysetup", OTHER="com.example.otherpending";
    private Bundle args;
    private Activity screen;
    @Override public void onCreate(Bundle values){super.onCreate(values);args=new Bundle(values);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();int code=Activity.RESULT_CANCELED;
        try {
            Context context=getTargetContext();
            check(context.getPackageName().equals("com.jeremykenedy.firetv.ui.preview"),"Preview only");
            GuardStore.Result guard=new GuardStore(context.getDataDir()).verify(args.getString("state_guard_case_id"),
                    new LinkedHashSet<>(Arrays.asList("shared_prefs","files","no_backup")),args.getString("state_guard_manifest_sha256"));
            check(guard.matches,"Matching durable guard required");
            PresentationMappingContext inventory=AndroidPresentationInventory.read(context,new PreferenceSnapshot(Collections.emptyMap()));
            List<String> components=new ArrayList<>(new LinkedHashSet<>(inventory.components.values()));
            check(components.size()>1,"Two installed activities required");
            String included=components.get(0), unrelated=components.get(1), absent=MISSING+"/"+MISSING+".Main";
            File directory=new File(context.getFilesDir(),"saved-setups");check(directory.mkdirs()||directory.isDirectory(),"Fixture directory unavailable");
            File source=new File(directory,"77777777-7777-7777-7777-777777777777.image");
            boolean large="true".equals(args.getString("large_icon"));
            retainedImage(source,large);
            result.putLong("archived_image_bytes",source.length());
            result.putBoolean("large_icon",large);
            byte[] original=Files.readAllBytes(source.toPath());String uri=Uri.fromFile(source).toString();
            JSONObject apps=new JSONObject().put(included,new JSONObject().put("customIconUri",uri))
                    .put(MISSING,new JSONObject().put("customIconUri",uri));
            JSONObject settings=new JSONObject().put("keys",new JSONArray(Arrays.asList("app_visibility","apps_per_row","use_24h_clock")))
                    .put("values",new JSONObject().put("app_visibility",apps.toString()).put("apps_per_row",4).put("use_24h_clock",true));
            File index=new File(directory,"setups.json");check(!index.exists(),"Disposable setup index must initially be absent");
            try(OutputStream output=new FileOutputStream(index)){output.write(new JSONObject().put("version",1).put("setups",new JSONArray()
                    .put(new JSONObject().put("id",ID).put("name","Retained icon setup").put("savedAt",1).put("settings",settings))).toString().getBytes("UTF-8"));}
            byte[] indexBefore=Files.readAllBytes(index.toPath());
            SharedPreferences icons=context.getSharedPreferences("native_icons",0), grid=context.getSharedPreferences("native_app_grid",0);
            Map<String,Object> pending=new LinkedHashMap<>();pending.put("icon:"+MISSING,"factory");pending.put("icon:"+OTHER,"factory");
            check(icons.edit().putBoolean("edited:"+included,true).putBoolean("edited:"+absent,true)
                    .putString(unrelated,"catalog:sonos").putBoolean("edited:"+unrelated,true)
                    .putString(RetainedHomeChoices.KEY,RetainedHomeChoices.write("native_icons",pending)).commit(),"Current icon choices saved");
            check(grid.edit().putInt("columns",7).commit(),"Current grid saved");
            context.getSharedPreferences("launcher_prefs",0).edit().putBoolean("use_24h_clock",false).putString("setup_unrelated_fixture","keep").commit();
            Map<String,Map<String,Object>> before=capture(context);
            File managedDirectory=new File(context.getFilesDir(),"icons");
            check(!managedDirectory.exists()||managedDirectory.list()!=null&&managedDirectory.list().length==0,"No preexisting managed icon copy");
            open(context);apply();
            if("true".equals(args.getString("baseline"))){
                waitFor("Setup unavailable");
                check(before.equals(capture(context)),"Existing failure must preserve exact preferences");
                check(Arrays.equals(original,Files.readAllBytes(source.toPath()))&&Arrays.equals(indexBefore,Files.readAllBytes(index.toPath())),"Existing failure preserves archive/index bytes");
                screenshot(context,"legacy-setup-baseline.png");
                result.putString("result","PASS: reproduced existing version-1 retained-icon apply refusal from actual Saved setups UI");
                code=Activity.RESULT_OK;
            } else {
                awaitApplied(icons,included);waitFor("Retained icon setup");
                String selected=icons.getString(included,"");
                check(selected.startsWith("image:"),"Captured personal icon replaces current Theme");
                File managed=new File(new File(context.getFilesDir(),"icons"),selected.substring(6));
                scoped(icons,included,unrelated,absent,selected);
                check("keep".equals(context.getSharedPreferences("launcher_prefs",0).getString("setup_unrelated_fixture","")),"Unrelated local setting retained");
                focusRetained();
                check(Arrays.equals(original,Files.readAllBytes(managed.toPath())),"Prepared icon retains exact captured bytes");
                android.graphics.drawable.Drawable rendered=com.jeremykenedy.firetv.ui.nativeapp.icons.IconSelectionStore.resolve(context,included,new android.graphics.drawable.ColorDrawable(0xffff0000),null);
                check(rendered instanceof android.graphics.drawable.BitmapDrawable
                        && ((android.graphics.drawable.BitmapDrawable)rendered).getBitmap().getPixel(0,0)==0xff2477aa,"Actual icon consumer renders captured pixels instead of factory fallback");
                unchanged(source,index,original,indexBefore);screenshot(context,"legacy-setup-applied.png");
                close();open(context);waitFor("Retained icon setup");
                check(source.delete(),"Owned source removed for missing-image case");
                Map<String,Map<String,Object>> beforeMissing=capture(context);apply();waitFor("Setup unavailable");
                check(beforeMissing.equals(capture(context)),"Missing retained source refuses all preference changes");
                check(!source.exists()&&Arrays.equals(original,Files.readAllBytes(managed.toPath())),"Missing source is not recreated and managed icon is unchanged");
                check(Arrays.equals(indexBefore,Files.readAllBytes(index.toPath())),"Missing source never deletes or rewrites setup");
                screenshot(context,"legacy-setup-missing.png");click("OK");waitFor("Retained icon setup");focusRetained();
                check(icons.edit().putBoolean("edited:"+included,true).commit()&&grid.edit().putInt("columns",7).commit(),"Unchecked artwork fixture saved");
                Map<String,?> iconsBeforeUnchecked=icons.getAll();
                toggleArtwork();apply();
                long until=SystemClock.uptimeMillis()+10000;
                while(SystemClock.uptimeMillis()<until&&grid.getInt("columns",0)!=4)SystemClock.sleep(50);
                waitFor("Retained icon setup");
                check(grid.getInt("columns",0)==4&&iconsBeforeUnchecked.equals(icons.getAll()),"Unchecked artwork applies other choices while preserving exact icon/edit/pending values");
                check(!source.exists()&&Arrays.equals(indexBefore,Files.readAllBytes(index.toPath())),"Unchecked artwork never recreates missing archive or changes setup index");
                screenshot(context,"legacy-setup-no-artwork.png");toggleArtwork();
                try(OutputStream output=new FileOutputStream(source)){output.write(original);}
                check(managed.delete(),"Owned managed copy removed for explicit Apply preparation");
                apply();awaitApplied(icons,included);waitFor("Retained icon setup");
                check(managed.isFile()&&Arrays.equals(original,Files.readAllBytes(managed.toPath())),"Explicit Apply recreates only captured managed copy");
                scoped(icons,included,unrelated,absent,selected);
                check(grid.edit().putInt("columns",7).commit(),"Rollback fixture prepared");
                Map<String,Map<String,Object>> beforeFailure=capture(context);
                try{new SavedSetupManager(new FailingGridContext(context)).apply(ID,true);throw new AssertionError("Failed commit accepted");}
                catch(IOException expected){}
                check(beforeFailure.equals(capture(context))&&!PresentationState.hasRecovery(context),"Failed commit restores exact preferences and resolves verified recovery");
                unchanged(source,index,original,indexBefore);
                check(Arrays.equals(original,Files.readAllBytes(managed.toPath())),"Rollback preserves captured and managed image bytes");
                result.putString("result","PASS: actual legacy setup UI, captured Theme override, scoped pending art, missing-source refusal, explicit managed-copy preparation and rollback");
                code=Activity.RESULT_OK;
            }
        }catch(Throwable failure){try{screenshot(getTargetContext(),"legacy-setup-failure.png");}catch(Exception unavailable){}result.putString("result","FAIL: "+failure.toString());}
        finally{try{close();}catch(Throwable failure){result.putString("result","FAIL: close: "+failure);code=Activity.RESULT_CANCELED;}
            result.putString("restoration","Durable guard retained; operator must stop, restore, independently verify and resolve");}
        finish(code,result);
    }
    private static void retainedImage(File source,boolean large)throws Exception {
        int side=large?2048:2;
        Bitmap bitmap=Bitmap.createBitmap(side,side,Bitmap.Config.ARGB_8888);
        try {
            if (large) {
                int[] row=new int[side];int random=0x13572468;
                for(int y=0;y<side;y++) {
                    for(int x=0;x<side;x++) {random^=random<<13;random^=random>>>17;random^=random<<5;row[x]=x<32&&y<32?0xff2477aa:0xff000000|(random&0xffffff);}
                    bitmap.setPixels(row,0,side,0,y,side,1);
                }
            } else bitmap.eraseColor(0xff2477aa);
            try(OutputStream output=new FileOutputStream(source)){check(bitmap.compress(Bitmap.CompressFormat.PNG,100,output),"Retained image fixture saved");}
        } finally {bitmap.recycle();}
        if(large)check(source.length()>8L*1024*1024&&source.length()<=20L*1024*1024,"Large fixture lies inside original saved image size contract");
    }
    private void scoped(SharedPreferences icons,String included,String unrelated,String absent,String selected){
        Map<String,?> values=icons.getAll();
        check(!values.containsKey("edited:"+included)&&!values.containsKey("edited:"+absent),"Only included captured choices clear newer edit markers");
        check("catalog:sonos".equals(values.get(unrelated))&&Boolean.TRUE.equals(values.get("edited:"+unrelated)),"Unrelated installed icon and marker retained");
        Map<String,Object> pending=RetainedHomeChoices.read("native_icons",values.get(RetainedHomeChoices.KEY));
        check(selected.equals(pending.get("icon:"+MISSING))&&"factory".equals(pending.get("icon:"+OTHER)),"Captured unavailable art replaces its pending choice without losing unrelated art");
        Map<String,Object> promoted=RetainedHomeChoices.promoteIcons(values,Collections.singletonMap(MISSING,absent));
        check(selected.equals(promoted.get(absent)),"Restored unavailable artwork can promote after prior Theme reset");
    }
    private static void unchanged(File source,File index,byte[] image,byte[] manifest)throws Exception{
        check(Arrays.equals(image,Files.readAllBytes(source.toPath()))&&Arrays.equals(manifest,Files.readAllBytes(index.toPath())),"Retained source and version-1 index bytes preserved");
    }
    private static Map<String,Map<String,Object>> capture(Context context)throws Exception{
        Map<String,Map<String,Object>> result=new LinkedHashMap<>();for(Map.Entry<String,PreferenceSnapshot> entry:PresentationState.capture(context).entrySet())result.put(entry.getKey(),entry.getValue().values());return result;
    }
    private void open(Context context){screen=startActivitySync(new Intent().setClassName(context,SavedSetupsActivity.class.getName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();}
    private void close(){if(screen!=null){final Activity activity=screen;runOnMainSync(new Runnable(){@Override public void run(){activity.finish();}});screen=null;waitForIdleSync();}}
    private void apply(){
        runOnMainSync(new Runnable(){@Override public void run(){View row=screen.getWindow().getDecorView().findViewWithTag("setup:"+ID);check(row!=null&&row.requestFocus(),"Saved setup row accepts remote focus");}});
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);waitFor("Apply");waitForIdleSync();SystemClock.sleep(100);
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);waitForIdleSync();
    }
    private void toggleArtwork(){runOnMainSync(new Runnable(){@Override public void run(){View option=screen.getWindow().getDecorView().findViewWithTag("artwork");check(option!=null&&option.requestFocus(),"Artwork option accepts remote focus");}});sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);waitForIdleSync();}
    private void focusRetained(){runOnMainSync(new Runnable(){@Override public void run(){check(screen.getCurrentFocus()!=null&&("setup:"+ID).equals(screen.getCurrentFocus().getTag()),"Saved setup selection retains remote focus");}});}
    private void awaitApplied(SharedPreferences icons,String key){long end=SystemClock.uptimeMillis()+10000;while(SystemClock.uptimeMillis()<end){if(icons.getString(key,"").startsWith("image:")&&!PresentationState.hasRecovery(getTargetContext()))return;SystemClock.sleep(50);}throw new AssertionError("Captured icon was not applied");}
    private AccessibilityNodeInfo waitFor(String label){long end=SystemClock.uptimeMillis()+10000;while(SystemClock.uptimeMillis()<end){AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root!=null)for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(label))if(node.getText()!=null&&label.equalsIgnoreCase(node.getText().toString()))return node;SystemClock.sleep(50);}throw new AssertionError("Missing UI: "+label);}
    private void click(String label){AccessibilityNodeInfo node=waitFor(label);while(node!=null&&!node.isClickable())node=node.getParent();check(node!=null&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK),"Unable to select "+label);waitForIdleSync();}
    private void screenshot(Context context,String name)throws Exception{waitForIdleSync();SystemClock.sleep(200);Bitmap image=getUiAutomation().takeScreenshot();check(image!=null,"Screenshot available");try(OutputStream output=new FileOutputStream(new File(context.getExternalFilesDir(null),name))){check(image.compress(Bitmap.CompressFormat.PNG,100,output),"Screenshot saved");}finally{image.recycle();}}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static final class FailingGridContext extends ContextWrapper {
        private boolean failed;
        FailingGridContext(Context context){super(context);}
        @Override public SharedPreferences getSharedPreferences(String name,int mode){
            final SharedPreferences delegate=super.getSharedPreferences(name,mode);if(!name.equals("native_app_grid"))return delegate;
            return(SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},new InvocationHandler(){
                @Override public Object invoke(Object proxy,Method method,Object[] args)throws Throwable{
                    if(!method.getName().equals("edit"))return method.invoke(delegate,args);final SharedPreferences.Editor editor=delegate.edit();
                    return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},new InvocationHandler(){
                        @Override public Object invoke(Object proxy,Method method,Object[] args)throws Throwable{Object result=method.invoke(editor,args);if(method.getName().equals("commit")&&!failed){failed=true;return false;}return result instanceof SharedPreferences.Editor?proxy:result;}
                    });
                }
            });
        }
    }
}
