package com.jeremykenedy.firetv.ui.nativeapp.backup;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.*;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.migration.PreferenceSnapshot;
import com.jeremykenedy.firetv.ui.nativeapp.setups.PresentationState;
import com.jeremykenedy.firetv.ui.nativeapp.widget.notes.*;
import com.jeremykenedy.firetv.ui.nativeapp.widget.reminders.*;
import java.lang.reflect.*;
import java.util.*;

/** Native activity/document-provider fixtures. No production data or physical devices. */
public final class BackupInstrumentation extends Instrumentation {
    private Activity screen;
    private Map<String,PreferenceSnapshot> beforeConfirmation;
    private final Map<String,Object> privateBeforeConfirmation=new LinkedHashMap<>();
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result=new Bundle(); Context context=getTargetContext();
        Map<String,PreferenceSnapshot> before=null;
        Map<String,Object> privateBefore=new LinkedHashMap<>();
        try {
            check(!BackupTransaction.hasRecovery(context)&&!PresentationState.hasRecovery(context),"Pending recovery must be resolved before fixtures.");
            before=PresentationState.capture(context);
            for(String name:Arrays.asList(HouseholdContent.NOTES,HouseholdContent.REMINDERS))
                privateBefore.put(name,context.getSharedPreferences(HouseholdContent.store(name),0).getAll().get("document"));
            Map<String,PreferenceSnapshot> fixture=new LinkedHashMap<>();
            for(String file:PresentationState.files())fixture.put(file,new PreferenceSnapshot(Collections.emptyMap()));
            fixture.put("native_focus",new PreferenceSnapshot(Collections.singletonMap("fill_enabled",false)));
            String groups="[[{\"id\":\"11111111-1111-1111-1111-111111111111\",\"type\":\"DigitalClock\",\"size\":\"Small\",\"data\":{}},{\"id\":\"22222222-2222-2222-2222-222222222222\",\"type\":\"YearProgress\",\"size\":\"Small\",\"data\":{}}]]";
            fixture.put("native_layout",new PreferenceSnapshot(Collections.singletonMap("widget_groups",groups)));
            fixture.put("launcher_prefs",new PreferenceSnapshot(Collections.singletonMap("widgets_config",groups)));
            fixture.put("native_cards",new PreferenceSnapshot(com.jeremykenedy.firetv.ui.nativeapp.cards.CardOptions.defaults()
                    .withGlobalSeconds(12).withFeedSeconds("fixture-feed",4)
                    .hide("com.example.fixture","fixture-item","Fixture title")
                    .hideSeries("com.example.fixture","fixture-series","Fixture series")
                    .withItemOrder("com.example.fixture","fixture-feed",Arrays.asList("second","first")).backupValues()));
            PresentationState.apply(context,fixture,true);
            screen=startActivitySync(new Intent().setClassName(context,BackupActivity.class.getName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            check(text("Export saves launcher layout"),"Native backup scope is visible.");
            check(text("Original launcher backups can restore"),"Original backup and local data boundaries are visible.");
            captureScreenshot("backup-ui.png");
            ActivityMonitor canceled=addMonitor(pickerFilter(Intent.ACTION_OPEN_DOCUMENT),new ActivityResult(Activity.RESULT_CANCELED,null),true);
            click("Import presentation backup"); waitStatus("Canceled.");removeMonitor(canceled);
            check(canceled.getHits()==1,"Native document picker was requested.");
            same(fixture,PresentationState.capture(context),"Canceled picker preserves exact settings.");

            context.getContentResolver().call(BackupFixtureProvider.DOCUMENT,"reset",null,null);
            ActivityMonitor exported=selected(Intent.ACTION_CREATE_DOCUMENT);
            click("Export presentation backup");waitStatus("Presentation backup exported");removeMonitor(exported);
            byte[] bytes=context.getContentResolver().call(BackupFixtureProvider.DOCUMENT,"read",null,null).getByteArray("bytes");
            BackupPayload roundtrip=BackupPayload.decode(bytes,BackupActivity.VALIDATOR);
            same(fixture,roundtrip.stores,"Actual provider output preserves typed values and absence.");
            check(roundtrip.household.isEmpty(),"Ordinary export contains no household documents.");
            context.getSharedPreferences("native_focus",0).edit().putBoolean("fill_enabled",true).commit();
            context.getSharedPreferences("native_layout",0).edit().putString("widget_groups","[]").commit();
            context.getSharedPreferences("native_cards",0).edit().clear().commit();
            ActivityMonitor imported=selected(Intent.ACTION_OPEN_DOCUMENT);
            click("Import presentation backup");confirm("Import");waitStatus("Presentation backup imported.");removeMonitor(imported);
            same(fixture,PresentationState.capture(context),"Native provider import applies the exact exported presentation.");

            ActivityMonitor canceledConfirm=selected(Intent.ACTION_OPEN_DOCUMENT);
            click("Import presentation backup");confirm("Cancel");waitStatus("Canceled.");removeMonitor(canceledConfirm);
            same(fixture,PresentationState.capture(context),"Canceled confirmation leaves all settings intact.");
            context.getContentResolver().call(BackupFixtureProvider.DOCUMENT,"malformed",null,null);
            ActivityMonitor malformed=selected(Intent.ACTION_OPEN_DOCUMENT);
            click("Import presentation backup");waitStatus("Malformed");removeMonitor(malformed);
            same(fixture,PresentationState.capture(context),"Malformed document changes nothing.");

            context.getContentResolver().call(BackupFixtureProvider.DOCUMENT,"original",null,null);
            ActivityMonitor original=selected(Intent.ACTION_OPEN_DOCUMENT);
            click("Import presentation backup");confirm("Import","Import original launcher backup?");
            waitStatus("Original launcher presentation imported.");removeMonitor(original);
            check(context.getSharedPreferences("native_app_grid",0).getInt("columns",0)==4,"Original flat backup maps grid presentation.");
            check(context.getSharedPreferences("launcher_prefs",0).getBoolean("use_24h_clock",false),"Original direct presentation choice applied.");
            check(groups.equals(context.getSharedPreferences("native_layout",0).getString("widget_groups",null)),"Original import preserves omitted local widget grouping.");
            check(fixture.get("native_cards").values().equals(PresentationState.capture(context).get("native_cards").values()),"Original import preserves omitted card choices.");
            context.getContentResolver().call(BackupFixtureProvider.DOCUMENT,"original-groups",null,null);
            ActivityMonitor originalGroups=selected(Intent.ACTION_OPEN_DOCUMENT);
            click("Import presentation backup");confirm("Import","Import original launcher backup?");
            waitStatus("Original launcher presentation imported.");removeMonitor(originalGroups);
            check("WIDGETS_ONLY".equals(context.getSharedPreferences("native_layout",0).getString("mode",null)),"Original enabled widget mode restored through SAF.");
            check("none".equals(context.getSharedPreferences("launcher_prefs",0).getString("overlay_mode",null)),"Original inactive overlay retained through SAF.");
            context.getContentResolver().call(BackupFixtureProvider.DOCUMENT,"original-weather",null,null);
            ActivityMonitor originalWeather=selected(Intent.ACTION_OPEN_DOCUMENT);
            click("Import presentation backup");confirm("Import","Import original launcher backup?");
            waitStatus("Original launcher presentation imported.");removeMonitor(originalWeather);
            String weather=context.getSharedPreferences("native_layout",0).getString("widget_groups",null);
            com.jeremykenedy.firetv.ui.nativeapp.widgetgroups.WidgetGroups.Entry weatherEntry=com.jeremykenedy.firetv.ui.nativeapp.widgetgroups.WidgetGroups.decode(weather).groups.get(0).get(0);
            check("Portland".equals(weatherEntry.data.get("location"))&&"F".equals(weatherEntry.data.get("unit")),"Original inactive weather city/unit restored exactly through SAF.");
            check(weather.equals(context.getSharedPreferences("launcher_prefs",0).getString("widgets_config",null)),"Original weather raw source preserved alongside native grouping.");
            check("CARDS_ONLY".equals(context.getSharedPreferences("native_layout",0).getString("mode",null)),"Original inactive weather remains inactive.");
            java.io.File sourceIcon=new java.io.File(context.getFilesDir(),"original-backup-fixture.png");
            android.graphics.Bitmap iconBitmap=android.graphics.Bitmap.createBitmap(2,2,android.graphics.Bitmap.Config.ARGB_8888);
            iconBitmap.eraseColor(android.graphics.Color.BLUE);
            try(java.io.OutputStream output=new java.io.FileOutputStream(sourceIcon)) {check(iconBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output),"Synthetic original icon saved.");}
            finally {iconBitmap.recycle();}
            String iconUri=android.net.Uri.fromFile(sourceIcon).toString();
            Map<String,String> preparedIcons=com.jeremykenedy.firetv.ui.nativeapp.migration.AndroidLegacyIconMigration.prepare(context,Collections.singleton(iconUri));
            String expectedIcon=preparedIcons.get(iconUri);
            org.json.JSONObject iconChoice=new org.json.JSONObject().put("customIconUri",iconUri).put("hidden",true).put("hideWatchNext",true);
            String iconVisibility=new org.json.JSONObject().put("com.example.originalicon",iconChoice).toString();
            context.getSharedPreferences("launcher_prefs",0).edit().putString("app_visibility",iconVisibility).commit();
            context.getContentResolver().call(BackupFixtureProvider.DOCUMENT,"original-icon",iconUri,null);
            ActivityMonitor originalIcon=selected(Intent.ACTION_OPEN_DOCUMENT);
            click("Import presentation backup");confirm("Import","Import original launcher backup?");
            waitStatus("Original launcher presentation imported.");removeMonitor(originalIcon);
            Map<String,Object> retainedIcons=com.jeremykenedy.firetv.ui.nativeapp.appgrid.RetainedHomeChoices.read("native_icons",context.getSharedPreferences("native_icons",0).getAll().get("retained_choices"));
            check(expectedIcon.equals(retainedIcons.get("icon:com.example.originalicon")),"Original same-TV personal icon retained for unavailable app through SAF.");
            java.io.File copiedIcon=new java.io.File(new java.io.File(context.getFilesDir(),"icons"),expectedIcon.substring(6));
            byte[] iconBefore=java.nio.file.Files.readAllBytes(sourceIcon.toPath());
            check(Arrays.equals(iconBefore,java.nio.file.Files.readAllBytes(copiedIcon.toPath())),"Original and prepared personal icon bytes unchanged.");
            check(copiedIcon.delete(),"Owned prepared icon removed for unavailable-copy fixture.");
            Map<String,PreferenceSnapshot> beforeMissing=PresentationState.capture(context);
            Map<String,Object> originalIconSettings=Collections.<String,Object>singletonMap("app_visibility",iconVisibility);
            try {new com.jeremykenedy.firetv.ui.nativeapp.setups.SavedSetupManager(context).prepareOriginalBackup(new PreferenceSnapshot(originalIconSettings));throw new AssertionError("Missing original copy accepted");}
            catch(java.io.IOException expected) { }
            same(beforeMissing,PresentationState.capture(context),"Missing personal image review preserves exact preferences.");
            check(!copiedIcon.exists(),"Original review never recreates missing personal image.");
            try(java.io.OutputStream output=new java.io.FileOutputStream(copiedIcon)) {output.write(iconBefore);}
            PresentationState.apply(context,fixture,true);
            OriginalBackup originalPayload=OriginalBackup.decode("{\"apps_per_row\":4,\"use_24h_clock\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            try { BackupTransaction.applyOriginal(new FailingStoreContext(context,"native_app_grid"),originalPayload);throw new AssertionError("Original failed commit accepted"); }
            catch(java.io.IOException expected) { }
            same(fixture,PresentationState.capture(context),"Original failed commit restores the exact prior presentation.");
            check(!PresentationState.hasRecovery(context),"Original verified rollback resolves journal.");
            context.getContentResolver().call(BackupFixtureProvider.DOCUMENT,"original-unsupported",null,null);
            ActivityMonitor unsupported=selected(Intent.ACTION_OPEN_DOCUMENT);
            click("Import presentation backup");confirm("Close","Original backup cannot be imported");
            waitStatus("Original backup review closed.");removeMonitor(unsupported);
            same(fixture,PresentationState.capture(context),"Original file with device settings is wholly refused.");

            runOnMainSync(new Runnable(){@Override public void run(){screen.finish();}});
            Method saveRecovery=PresentationState.class.getDeclaredMethod("saveRecovery",Context.class,Map.class);
            saveRecovery.setAccessible(true); saveRecovery.invoke(null,context,fixture);
            context.getSharedPreferences("native_focus",0).edit().putBoolean("fill_enabled",true).commit();
            screen=startActivitySync(new Intent().setClassName(context,BackupActivity.class.getName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            runOnMainSync(new Runnable(){@Override public void run(){
                check(!button(screen.getWindow().getDecorView(),"Export presentation backup").isEnabled(),"Pending recovery blocks export.");
                check(!button(screen.getWindow().getDecorView(),"Import presentation backup").isEnabled(),"Pending recovery blocks import.");
            }});
            click("Restore interrupted presentation change");
            confirm("Restore","Restore previous presentation?"); waitStatus("Previous presentation restored.");
            same(fixture,PresentationState.capture(context),"Explicit native recovery restores exact presentation.");
            check(!PresentationState.hasRecovery(context),"Verified explicit recovery resolves journal.");

            NotesOptions notes=new NotesOptions(true,false,true,Collections.singletonList(new NotesOptions.Item("fixture-note","Sanitized household fixture",false,false)));
            NotesPreferences.save(context,notes);
            Map<String,String> household=HouseholdContent.capture(context,true,false);
            NotesOptions safe=NotesPreferences.decode(household.get(HouseholdContent.NOTES));
            check(safe.summaryOnly&&!safe.allowExport,"Exported household flags cannot carry consent or detail visibility.");
            click("Include notes in this export (requires local notes export permission)");
            ActivityMonitor householdExport=selected(Intent.ACTION_CREATE_DOCUMENT);
            click("Export presentation backup");waitStatus("Presentation backup exported");removeMonitor(householdExport);
            byte[] privateBytes=context.getContentResolver().call(BackupFixtureProvider.DOCUMENT,"read",null,null).getByteArray("bytes");
            BackupPayload privatePayload=BackupPayload.decode(privateBytes,BackupActivity.VALIDATOR);
            check(privatePayload.household.keySet().equals(Collections.singleton(HouseholdContent.NOTES)),"Only explicitly selected household content is in the provider document.");
            runOnMainSync(new Runnable(){@Override public void run(){
                check(!((android.widget.CheckBox)button(screen.getWindow().getDecorView(),"Include notes in this export (requires local notes export permission)")).isChecked(),"Household selection resets after each export.");
            }});
            NotesPreferences.save(context,NotesOptions.defaults());
            ActivityMonitor householdImport=selected(Intent.ACTION_OPEN_DOCUMENT);
            click("Import presentation backup");confirm("Import");waitStatus("Presentation backup imported.");removeMonitor(householdImport);
            check(NotesPreferences.load(context).items.size()==1&&NotesPreferences.load(context).summaryOnly&&!NotesPreferences.load(context).allowExport,"SAF household import restores content without enabling detail display or export consent.");
            String privateSnapshot=NotesPreferences.encode(NotesPreferences.load(context));
            try { BackupTransaction.apply(new FailingStoreContext(context,NotesPreferences.STORE),privatePayload);throw new AssertionError("Failed commit accepted"); }
            catch(java.io.IOException expected) { }
            same(fixture,PresentationState.capture(context),"Failure after private memory mutation rolls presentation back.");
            check(privateSnapshot.equals(NotesPreferences.encode(NotesPreferences.load(context))),"Private document rollback is exact.");
            check(!BackupTransaction.hasRecovery(context)&&!PresentationState.hasRecovery(context),"Verified rollback resolves both private journals.");
            result.putString("result","PASS: real controlled provider export/import, cancellation, malformed input, original flat file mapping and whole-file refusal, explicit recovery, privacy consent reset and rollback after commit mutation.");
        } catch(Throwable failure) {
            result.putString("result","FAIL: "+failure.getClass().getSimpleName()+": "+failure.getMessage());
        } finally {
            try {
                if(BackupTransaction.hasRecovery(context)||PresentationState.hasRecovery(context))BackupTransaction.recover(context);
                if(before!=null)PresentationState.apply(context,before,true);
                for(Map.Entry<String,Object> entry:privateBefore.entrySet()){
                    SharedPreferences.Editor editor=context.getSharedPreferences(HouseholdContent.store(entry.getKey()),0).edit();
                    if(entry.getValue()==null)editor.remove("document");else editor.putString("document",(String)entry.getValue());
                    check(editor.commit(),"Private fixture restoration committed.");
                    check(Objects.equals(entry.getValue(),context.getSharedPreferences(HouseholdContent.store(entry.getKey()),0).getAll().get("document")),"Private fixture restoration verified.");
                }
                if(before!=null)same(before,PresentationState.capture(context),"Original presentation restored.");
                context.getContentResolver().call(BackupFixtureProvider.DOCUMENT,"reset",null,null);
                result.putString("restoration","verified: exact original presentation and household document values");
            } catch(Throwable restore) {result.putString("result","FAIL: fixture restoration could not be verified: "+restore.getClass().getSimpleName());}
            if(screen!=null)runOnMainSync(new Runnable(){@Override public void run(){screen.finish();}});
        }
        finish(result.getString("result").startsWith("PASS")?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private void captureScreenshot(String name) throws Exception {
        android.graphics.Bitmap screenshot=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot();
        check(screenshot!=null,"Native backup screen capture is available.");
        try(java.io.OutputStream output=new java.io.FileOutputStream(new java.io.File(getTargetContext().getExternalFilesDir(null),name))) {
            check(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output),"Native backup capture saved.");
        } finally { screenshot.recycle(); }
    }
    private static IntentFilter pickerFilter(String action) throws IntentFilter.MalformedMimeTypeException {
        IntentFilter filter=new IntentFilter(action);
        filter.addCategory(Intent.CATEGORY_OPENABLE);
        filter.addDataType(Intent.ACTION_CREATE_DOCUMENT.equals(action)?"text/plain":"*/*");
        return filter;
    }
    private ActivityMonitor selected(String action) throws IntentFilter.MalformedMimeTypeException {return addMonitor(pickerFilter(action),new ActivityResult(Activity.RESULT_OK,new Intent().setData(BackupFixtureProvider.DOCUMENT)),true);}
    private void click(final String label) throws Exception {
        if(label.equals("Import presentation backup")) {
            beforeConfirmation=PresentationState.capture(getTargetContext());
            privateBeforeConfirmation.clear();
            for(String name:Arrays.asList(HouseholdContent.NOTES,HouseholdContent.REMINDERS))
                privateBeforeConfirmation.put(name,getTargetContext().getSharedPreferences(HouseholdContent.store(name),0).getAll().get("document"));
        }
        runOnMainSync(new Runnable(){@Override public void run(){Button found=button(screen.getWindow().getDecorView(),label);check(found!=null,"Native action exists: "+label);found.performClick();}});
    }
    private void confirm(String label) throws Exception { confirm(label,"Import presentation backup?"); }
    private void confirm(String label,String dialogTitle) throws Exception {
        UiAutomation automation=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        AccessibilityServiceInfo info=automation.getServiceInfo();
        info.flags|=AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        automation.setServiceInfo(info);
        long deadline=SystemClock.uptimeMillis()+10000;
        int targetWindows=0; boolean titlePresent=false,imePresent=false;
        while(SystemClock.uptimeMillis()<deadline) {
            List<AccessibilityNodeInfo> roots=new ArrayList<>();
            for(AccessibilityWindowInfo window:automation.getWindows()) {
                if(window.getType()==AccessibilityWindowInfo.TYPE_INPUT_METHOD)imePresent=true;
                AccessibilityNodeInfo root=window.getRoot();
                if(root!=null) {
                    if(window.getType()==AccessibilityWindowInfo.TYPE_APPLICATION&&getTargetContext().getPackageName().equals(String.valueOf(root.getPackageName())))roots.add(root);
                    else root.recycle();
                }
            }
            if(roots.isEmpty()) {
                AccessibilityNodeInfo root=automation.getRootInActiveWindow();
                if(root!=null) {
                    if(getTargetContext().getPackageName().equals(String.valueOf(root.getPackageName())))roots.add(root);else root.recycle();
                }
            }
            targetWindows=roots.size();
            for(AccessibilityNodeInfo root:roots) {
                AccessibilityNodeInfo title=matching(root,dialogTitle,false,0,new int[]{0});
                if(title!=null) {
                    titlePresent=true;title.recycle();
                    if (dialogTitle.equals("Import original launcher backup?")) captureScreenshot("original-review-ui.png");
                    if (dialogTitle.equals("Original backup cannot be imported")) captureScreenshot("original-refusal-ui.png");
                    if (dialogTitle.equals("Original backup cannot be imported")) {
                        AccessibilityNodeInfo forbidden=matching(root,"Import",true,0,new int[]{0});
                        check(forbidden==null,"Unsupported original file never offers partial import.");
                        List<AccessibilityNodeInfo> reasons=root.findAccessibilityNodeInfosByText("fire tv ui sleep");
                        check(!reasons.isEmpty(),"Unsupported original section identifies concrete device setting.");
                        for(AccessibilityNodeInfo reason:reasons)reason.recycle();
                    }
                    AccessibilityNodeInfo action=matching(root,label,true,0,new int[]{0});
                    if(action!=null) {
                        if (dialogTitle.startsWith("Import ")) same(beforeConfirmation,PresentationState.capture(getTargetContext()),"Presentation cannot change before explicit confirmation.");
                        for(Map.Entry<String,Object> entry:privateBeforeConfirmation.entrySet())check(Objects.equals(entry.getValue(),getTargetContext().getSharedPreferences(HouseholdContent.store(entry.getKey()),0).getAll().get("document")),"Household content cannot change before explicit confirmation.");
                        boolean clicked=action.performAction(AccessibilityNodeInfo.ACTION_CLICK);action.recycle();
                        for(AccessibilityNodeInfo owned:roots)owned.recycle();
                        check(clicked,"Explicit native confirmation must be clickable.");return;
                    }
                }
            }
            for(AccessibilityNodeInfo root:roots)root.recycle();
            SystemClock.sleep(50);
        }
        throw new AssertionError("Native confirmation unavailable: "+label+" (targetWindows="+targetWindows+",title="+titlePresent+",ime="+imePresent+")");
    }
    private AccessibilityNodeInfo matching(AccessibilityNodeInfo node,String label,boolean action,int depth,int[] nodes) {
        if(depth>32||++nodes[0]>2048)return null;
        if(node.isVisibleToUser()&&label.equalsIgnoreCase(String.valueOf(node.getText()))
                &&(!action||node.isEnabled()&&node.isClickable()&&"android.widget.Button".equals(String.valueOf(node.getClassName()))))return AccessibilityNodeInfo.obtain(node);
        for(int i=0;i<Math.min(node.getChildCount(),128);i++) {
            AccessibilityNodeInfo child=node.getChild(i);if(child==null)continue;
            AccessibilityNodeInfo found=matching(child,label,action,depth+1,nodes);child.recycle();if(found!=null)return found;
        }
        return null;
    }
    private void waitStatus(String prefix){long deadline=SystemClock.uptimeMillis()+10000;while(SystemClock.uptimeMillis()<deadline){if(text(prefix))return;SystemClock.sleep(25);}throw new AssertionError("Status unavailable: "+prefix);}
    private boolean text(final String prefix){final boolean[] found={false};runOnMainSync(new Runnable(){@Override public void run(){found[0]=text(screen.getWindow().getDecorView(),prefix);}});return found[0];}
    private static boolean text(View view,String prefix){if(view instanceof TextView&&((TextView)view).getText().toString().startsWith(prefix))return true;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(text(((ViewGroup)view).getChildAt(i),prefix))return true;return false;}
    private static Button button(View view,String label){if(view instanceof Button&&label.contentEquals(((Button)view).getText()))return(Button)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){Button found=button(((ViewGroup)view).getChildAt(i),label);if(found!=null)return found;}return null;}
    private static void same(Map<String,PreferenceSnapshot> first,Map<String,PreferenceSnapshot> second,String message){check(first.keySet().equals(second.keySet()),message);for(String file:first.keySet())check(first.get(file).values().equals(second.get(file).values()),message);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static final class FailingStoreContext extends ContextWrapper {
        private boolean failed;
        private final String store;
        FailingStoreContext(Context context,String store){super(context);this.store=store;}
        @Override public SharedPreferences getSharedPreferences(String name,int mode){
            final SharedPreferences delegate=super.getSharedPreferences(name,mode);
            if(!name.equals(store))return delegate;
            return(SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},new InvocationHandler(){
                @Override public Object invoke(Object proxy,Method method,Object[] args)throws Throwable{
                    if(!method.getName().equals("edit"))return method.invoke(delegate,args);
                    final SharedPreferences.Editor editor=delegate.edit();
                    return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},new InvocationHandler(){
                        @Override public Object invoke(Object proxy,Method method,Object[] args)throws Throwable{
                            Object result=method.invoke(editor,args);
                            if(method.getName().equals("commit")&&!failed){failed=true;return false;}
                            return result instanceof SharedPreferences.Editor?proxy:result;
                        }
                    });
                }
            });
        }
    }
}
