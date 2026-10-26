from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class ScreensaverPreviewChecks(unittest.TestCase):
    def test_direct_preview_and_system_fallback_preserve_selection(self):
        stubs = {
            'android/content/ComponentName.java': '''package android.content; public class ComponentName { public String pkg,cls; public ComponentName(String p,String c){pkg=p;cls=c;} public String getPackageName(){return pkg;} public static ComponentName unflattenFromString(String s){if(s==null||!s.contains("/"))return null;String[] a=s.split("/",2);return new ComponentName(a[0],a[1]);} }''',
            'android/content/Intent.java': 'package android.content; public class Intent { public ComponentName component; public Intent setComponent(ComponentName c){component=c;return this;} public ComponentName getComponent(){return component;} }',
            'android/content/SharedPreferences.java': '''package android.content; public class SharedPreferences { public java.util.Map<String,Object> values=new java.util.HashMap<>(); public boolean commit=true; public boolean getBoolean(String k,boolean d){return (Boolean)values.getOrDefault(k,d);} public String getString(String k,String d){return (String)values.getOrDefault(k,d);} public Editor edit(){return new Editor();} public class Editor { java.util.Map<String,Object> put=new java.util.HashMap<>(); java.util.List<String> remove=new java.util.ArrayList<>(); public Editor putString(String k,String v){put.put(k,v);return this;} public Editor putBoolean(String k,boolean v){put.put(k,v);return this;} public Editor remove(String k){remove.add(k);return this;} public boolean commit(){if(!commit)return false;values.putAll(put);for(String k:remove)values.remove(k);return true;} } }''',
            'android/content/Context.java': '''package android.content; public class Context { public android.content.pm.PackageManager pm=new android.content.pm.PackageManager(); public Intent launched; public boolean fail; public int launches; public Object getContentResolver(){return this;} public android.content.pm.PackageManager getPackageManager(){return pm;} public int checkSelfPermission(String p){return -1;} public void startActivity(Intent i){launches++;if(fail)throw new IllegalStateException("removed");launched=i;} }''',
            'android/content/pm/ActivityInfo.java': 'package android.content.pm; public class ActivityInfo { public boolean enabled=true,exported=true; public String permission; public ApplicationInfo applicationInfo=new ApplicationInfo(); public static class ApplicationInfo { public boolean enabled=true; } }',
            'android/content/pm/PackageManager.java': '''package android.content.pm; public class PackageManager { public static final int PERMISSION_GRANTED=0,GET_META_DATA=128; public android.content.pm.ServiceInfo service=new android.content.pm.ServiceInfo(); public android.content.Intent launcher; public android.content.Intent getLaunchIntentForPackage(String p){return launcher;} public ServiceInfo getServiceInfo(android.content.ComponentName c,int flags){return service;} public java.util.Map<String,ActivityInfo> activities=new java.util.HashMap<>(); public ActivityInfo getActivityInfo(android.content.ComponentName c,int f)throws NameNotFoundException{ActivityInfo a=activities.get(c.pkg+"/"+c.cls);if(a==null)throw new NameNotFoundException();return a;} public static class NameNotFoundException extends Exception{} }''',
            'android/provider/Settings.java': '''package android.provider; public class Settings { public static class Secure { public static boolean putString(Object r,String k,String v){com.jeremykenedy.firetv.ui.DeviceSettings.selection=v;com.jeremykenedy.firetv.ui.DeviceSettings.writes++;return true;} } }''',
            'android/content/res/XmlResourceParser.java': 'package android.content.res; public class XmlResourceParser { public static final int END_DOCUMENT=1,START_TAG=2; int event; public String activity; public XmlResourceParser(String a){activity=a;} public int next(){return ++event==1?START_TAG:END_DOCUMENT;} public int getEventType(){return START_TAG;} public String getName(){return "dream";} public String getAttributeValue(String n,String k){return activity;} public void close(){} }',
            'android/content/pm/ServiceInfo.java': 'package android.content.pm; public class ServiceInfo { public String settings; public android.content.res.XmlResourceParser loadXmlMetaData(PackageManager p,String key){return settings==null?null:new android.content.res.XmlResourceParser(settings);} }',
            'com/jeremykenedy/firetv/ui/DeviceSettings.java': '''package com.jeremykenedy.firetv.ui; public class DeviceSettings { public static String COMPONENTS="dream",selection="old/.Dream"; public static boolean enabled=false,writable=true; public static int writes; public static android.content.SharedPreferences prefs=new android.content.SharedPreferences(); static android.content.SharedPreferences prefs(android.content.Context c){return prefs;} public static String active(Object r){return selection;} public static boolean isEnabled(Object r){return enabled;} static boolean canWrite(android.content.Context c){return writable;} public static void setEnabled(Object r,boolean b){enabled=b;writes++;} static void select(Object r,String c){selection=c;enabled=true;writes+=2;} }''',
            'com/jeremykenedy/firetv/ui/ComponentCheck.java': 'package com.jeremykenedy.firetv.ui; class ComponentCheck { static void requireInstalledDream(android.content.Context c,String s){} }',
            'com/jeremykenedy/firetv/ui/Screensavers.java': 'package com.jeremykenedy.firetv.ui; class Screensavers { static String fullComponent(String s){return s==null?"":s;} }',
            'com/jeremykenedy/firetv/ui/ScreensaverCatalog.java': '''package com.jeremykenedy.firetv.ui; class ScreensaverCatalog { static class Entry { String dream="app/.Dream",preview="app/.Preview",previewControls=""; } static Entry entry=new Entry(); static java.util.List<Entry> load(android.content.Context c){return java.util.Arrays.asList(entry);} }''',
        }
        check = '''package com.jeremykenedy.firetv.ui;
import android.content.*;import android.content.pm.*;
public class PreviewTest {
 static void ok(boolean b,String why){if(!b)throw new AssertionError(why);}
 static void fails(Context c){try{ScreensaverPreview.start(c,"app/.Dream");throw new AssertionError("Expected unavailable");}catch(IllegalStateException expected){}}
 public static void main(String[] x){
  Context c=new Context();ActivityInfo app=new ActivityInfo();c.pm.activities.put("app/.Preview",app);
  ScreensaverPreview.start(c,"app/.Dream");ok(c.launched.component.cls.equals(".Preview"),"Direct target");ok(DeviceSettings.writes==0&&!DeviceSettings.enabled,"Direct preview changed system settings");ok(DeviceSettings.prefs.values.isEmpty(),"Direct preview changed pending state");
  ok(ScreensaverPreview.actionLabel(c,"app/.Dream",false).equals("Preview it"),"Direct label");
  c.launched=null;app.exported=false;fails(c);ok(DeviceSettings.writes==0&&c.launched==null,"Private preview mutated state");
  ok(ScreensaverPreview.actionLabel(c,"app/.Dream",false).equals("Preview unavailable"),"Unavailable route label");
  c.pm.service.settings=".Settings";ActivityInfo settings=new ActivityInfo();c.pm.activities.put("app/app.Settings",settings);
  ok(ScreensaverPreview.actionLabel(c,"app/.Dream",true).equals("Preview controls"),"Controls label");ScreensaverPreview.start(c,"app/.Dream");ok(c.launched.component.cls.equals("app.Settings"),"Private preview did not reach own controls");ok(DeviceSettings.writes==0&&DeviceSettings.prefs.values.isEmpty(),"Controls changed selection/pending");
  ScreensaverCatalog.entry.previewControls="app/.Launcher";c.pm.activities.put("app/.Launcher",new ActivityInfo());ScreensaverPreview.start(c,"app/.Dream");ok(c.launched.component.cls.equals(".Launcher"),"Reviewed preview controls must bypass settings without a preview button");ok(ScreensaverControls.resolve(c,"app/.Dream").getComponent().cls.equals("app.Settings"),"Options must retain Dream settings");ok(DeviceSettings.writes==0,"Reviewed controls changed selection");
  ScreensaverCatalog.entry.previewControls="other/.Launcher";fails(c);ScreensaverCatalog.entry.previewControls="app/.Launcher";c.pm.activities.get("app/.Launcher").permission="privileged";fails(c);c.pm.activities.get("app/.Launcher").permission=null;ScreensaverCatalog.entry.previewControls="";
  c.pm.service.settings="other/.Settings";fails(c);c.pm.service.settings=".Settings";settings.permission="privileged";fails(c);settings.permission=null;settings.exported=false;fails(c);
  c.pm.launcher=new Intent().setComponent(new ComponentName("app",".Launcher"));ActivityInfo launcher=new ActivityInfo();c.pm.activities.put("app/.Launcher",launcher);ScreensaverPreview.start(c,"app/.Dream");ok(c.launched.component.cls.equals(".Launcher"),"Accessible launcher fallback");ok(DeviceSettings.writes==0,"Launcher controls changed system");
  c.pm.launcher=new Intent().setComponent(new ComponentName("other",".Launcher"));fails(c);c.pm.launcher=null;c.pm.service.settings=null;
  app.exported=true;app.permission="privileged";fails(c);ok(DeviceSettings.writes==0,"Protected preview mutated state");app.permission=null;
  app.enabled=false;fails(c);app.enabled=true;app.applicationInfo.enabled=false;fails(c);app.applicationInfo.enabled=true;
  ScreensaverCatalog.entry.preview="other/.Preview";fails(c);ScreensaverCatalog.entry.preview="app/.Preview";
  c.fail=true;fails(c);ok(DeviceSettings.writes==0,"Failed direct launch changed selection");c.fail=false;
  app.exported=false;c.pm.activities.put("com.android.systemui/com.android.systemui.Somnambulator",new ActivityInfo());
  DeviceSettings.writable=false;fails(c);ok(DeviceSettings.writes==0,"Unprivileged fallback changed settings");DeviceSettings.writable=true;
  DeviceSettings.prefs.commit=false;fails(c);ok(DeviceSettings.writes==0,"Failed save changed selection");DeviceSettings.prefs.commit=true;
  ScreensaverPreview.start(c,"app/.Dream");ok(DeviceSettings.selection.equals("app/.Dream")&&DeviceSettings.enabled,"Fallback did not select temporarily");ok(DeviceSettings.prefs.getBoolean(ScreensaverPreview.PENDING,false),"Pending missing");
  ScreensaverPreview.restore(c);ok(DeviceSettings.selection.equals("old/.Dream")&&!DeviceSettings.enabled,"Restoration lost original disabled setting");ok(DeviceSettings.prefs.values.isEmpty(),"Pending not cleared");
  DeviceSettings.selection=null;c.fail=true;fails(c);ok(DeviceSettings.selection==null&&!DeviceSettings.enabled,"Failed fallback did not restore absent original selection");ok(DeviceSettings.prefs.values.isEmpty(),"Failed fallback left pending");
  c.fail=false;ScreensaverPreview.start(c,"app/.Dream");DeviceSettings.prefs.commit=false;try{ScreensaverPreview.restore(c);throw new AssertionError("Ignored cleanup failure");}catch(IllegalStateException expected){}ok(DeviceSettings.prefs.getBoolean(ScreensaverPreview.PENDING,false),"Lost retry state");DeviceSettings.prefs.commit=true;ScreensaverPreview.restore(c);ok(DeviceSettings.prefs.values.isEmpty(),"Retry failed");
 }
}'''
        with tempfile.TemporaryDirectory() as folder:
            files = []
            for name, content in stubs.items():
                target = Path(folder) / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(content)
                files.append(str(target))
            harness = Path(folder) / 'PreviewTest.java'
            harness.write_text(check)
            source = ROOT / 'app/src/com/jeremykenedy/firetv/ui/ScreensaverPreview.java'
            subprocess.run(['javac', '-d', folder, *files, str(source), str(ROOT / 'app/src/com/jeremykenedy/firetv/ui/ScreensaverControls.java'), str(harness)], check=True)
            subprocess.run(['java', '-cp', folder, 'com.jeremykenedy.firetv.ui.PreviewTest'], check=True)
