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
            PresentationState.apply(context,fixture,true);
            screen=startActivitySync(new Intent().setClassName(context,BackupActivity.class.getName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            check(text("Source preview presentation only."),"Native preview scope is visible.");
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
            NotesPreferences.save(context,NotesOptions.defaults());
            ActivityMonitor householdImport=selected(Intent.ACTION_OPEN_DOCUMENT);
            click("Import presentation backup");confirm("Import");waitStatus("Presentation backup imported.");removeMonitor(householdImport);
            check(NotesPreferences.load(context).items.size()==1&&NotesPreferences.load(context).summaryOnly&&!NotesPreferences.load(context).allowExport,"SAF household import restores content without enabling detail display or export consent.");
            String privateSnapshot=NotesPreferences.encode(NotesPreferences.load(context));
            try { BackupTransaction.apply(new FailingNotesContext(context),privatePayload);throw new AssertionError("Failed commit accepted"); }
            catch(java.io.IOException expected) { }
            same(fixture,PresentationState.capture(context),"Failure after private memory mutation rolls presentation back.");
            check(privateSnapshot.equals(NotesPreferences.encode(NotesPreferences.load(context))),"Private document rollback is exact.");
            check(!BackupTransaction.hasRecovery(context)&&!PresentationState.hasRecovery(context),"Verified rollback resolves both private journals.");
            result.putString("result","PASS: real controlled provider export/import, cancellation, malformed input, privacy consent reset and rollback after commit mutation.");
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
    private void confirm(String label) throws Exception {
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
                AccessibilityNodeInfo title=matching(root,"Import presentation backup?",false,0,new int[]{0});
                if(title!=null) {
                    titlePresent=true;title.recycle();
                    AccessibilityNodeInfo action=matching(root,label,true,0,new int[]{0});
                    if(action!=null) {
                        same(beforeConfirmation,PresentationState.capture(getTargetContext()),"Presentation cannot change before explicit confirmation.");
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
    private static final class FailingNotesContext extends ContextWrapper {
        private boolean failed;
        FailingNotesContext(Context context){super(context);}
        @Override public SharedPreferences getSharedPreferences(String name,int mode){
            final SharedPreferences delegate=super.getSharedPreferences(name,mode);
            if(!name.equals(NotesPreferences.STORE))return delegate;
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
