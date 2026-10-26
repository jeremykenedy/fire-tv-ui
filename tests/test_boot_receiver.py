from pathlib import Path
import os
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class BootReceiverChecks(unittest.TestCase):
    def test_update_rebind_preserves_home_choices_and_boot_keeps_its_policy(self):
        stubs = {
            'android/content/BroadcastReceiver.java': '''package android.content; public abstract class BroadcastReceiver {
 public int code; public String data; public abstract void onReceive(Context c,Intent i);
 public void setResultCode(int value){code=value;} public void setResultData(String value){data=value;}
 public PendingResult goAsync(){return new PendingResult();}
 public static class PendingResult { public void setResultCode(int value){} public void setResultData(String value){} public void finish(){} }
}''',
            'android/content/ContentResolver.java': 'package android.content; public class ContentResolver {}',
            'android/content/SharedPreferences.java': '''package android.content; public interface SharedPreferences {
 boolean getBoolean(String key,boolean fallback); boolean contains(String key); Editor edit();
 interface Editor { Editor putBoolean(String key,boolean value); Editor remove(String key); boolean commit(); }
}''',
            'android/content/Context.java': '''package android.content;
import java.util.*;
public class Context {
 public int launches; public final Prefs prefs=new Prefs();
 public Context getApplicationContext(){return this;}
 public String getPackageName(){return "com.jeremykenedy.firetv.ui";}
 public ContentResolver getContentResolver(){return new ContentResolver();}
 public android.content.pm.PackageManager getPackageManager(){return new android.content.pm.PackageManager();}
 public void startActivity(Intent intent){launches++;}
 public static class Prefs implements SharedPreferences {
  public final Map<String,Boolean> values=new HashMap<>(); public int commits; public boolean failCommit;
  public boolean getBoolean(String key,boolean fallback){return values.containsKey(key)?values.get(key):fallback;}
  public boolean contains(String key){return values.containsKey(key);}
  public Editor edit(){return new Editor(){
   final Map<String,Boolean> pending=new HashMap<>();final Set<String> removed=new HashSet<>();
   public Editor putBoolean(String key,boolean value){pending.put(key,value);return this;}
   public Editor remove(String key){removed.add(key);return this;}
   public boolean commit(){if(failCommit){failCommit=false;return false;}for(String key:removed)values.remove(key);values.putAll(pending);commits++;return true;}
  };}
 }
}''',
            'android/content/Intent.java': 'package android.content; public class Intent { public static final String ACTION_BOOT_COMPLETED="boot",ACTION_MY_PACKAGE_REPLACED="updated"; public static final int FLAG_ACTIVITY_NEW_TASK=1,FLAG_ACTIVITY_RESET_TASK_IF_NEEDED=2; private String action; public Intent(String a){action=a;} public String getAction(){return action;} public String getStringExtra(String key){return "operation".equals(key)?action:null;} public Intent addFlags(int f){return this;} }',
            'android/content/pm/PackageManager.java': 'package android.content.pm; public class PackageManager { public android.content.Intent getLaunchIntentForPackage(String p){return new android.content.Intent("launch");} }',
            'android/provider/Settings.java': '''package android.provider;
import java.util.*; import android.content.ContentResolver;
public class Settings { public static class Secure {
 public static final Map<String,String> values=new HashMap<>(); public static int writes,failAt;
 public static String getString(ContentResolver c,String key){return values.get(key);}
 public static int getInt(ContentResolver c,String key,int fallback){String v=values.get(key);return v==null?fallback:Integer.parseInt(v);}
 private static boolean put(String key,String value){writes++;if(writes==failAt)return false;values.put(key,value);return true;}
 public static boolean putString(ContentResolver c,String key,String value){return put(key,value);}
 public static boolean putInt(ContentResolver c,String key,int value){return put(key,Integer.toString(value));}
} }''',
            'android/text/TextUtils.java': 'package android.text; public class TextUtils { public static String join(CharSequence delimiter,Iterable<?> values){java.util.StringJoiner j=new java.util.StringJoiner(delimiter.toString());for(Object v:values)j.add(v.toString());return j.toString();} }',
            'com/jeremykenedy/firetv/ui/DeviceSettings.java': '''package com.jeremykenedy.firetv.ui;
class DeviceSettings {
 static int repairs; static boolean writable=true;
 static final String DREAM_UNLOCKED="dream",PROTECT="protect",SLEEP="sleep",IDLE="idle",AUTO_WAKE="wake",COMPONENTS="components",ENABLED="enabled";
 static void keepAdb(android.content.Context c,boolean v){} static void capture(android.content.Context c){}
 static void sleep(android.content.Context c,int v){} static void idle(android.content.Context c,int v){}
 static void autoWake(android.content.Context c,boolean v){} static void component(android.content.Context c,String v){}
 static void enableDream(android.content.Context c,boolean v){}
 static boolean canWrite(android.content.Context c){return writable;}
 static android.content.SharedPreferences prefs(android.content.Context c){return c.prefs;}
 static void repair(android.content.Context c){repairs++;}
}''',
            'com/jeremykenedy/firetv/ui/ProtectionService.java': 'package com.jeremykenedy.firetv.ui; class ProtectionService { static int starts; static void start(android.content.Context c){starts++;} }',
            'com/jeremykenedy/firetv/ui/StagedUiUpdate.java': 'package com.jeremykenedy.firetv.ui; class StagedUiUpdate { static void stage(android.content.Context c){} }',
            'com/jeremykenedy/firetv/ui/BackupStore.java': 'package com.jeremykenedy.firetv.ui; class BackupStore { static void save(android.content.Context c){} static void restore(android.content.Context c,boolean imported){} static void simpleSetup(android.content.Context c){} }',
            'com/jeremykenedy/firetv/ui/RecommendationService.java': 'package com.jeremykenedy.firetv.ui; class RecommendationService { static void setEnabled(android.content.Context c,boolean enabled){} }',
            'com/jeremykenedy/firetv/ui/BundledIcons.java': 'package com.jeremykenedy.firetv.ui; class BundledIcons { static int attempts; static void afterUpdate(android.content.BroadcastReceiver r,android.content.Context c){attempts++;} }',
            'com/jeremykenedy/firetv/ui/HomeRedirectService.java': 'package com.jeremykenedy.firetv.ui; class HomeRedirectService {}',
        }
        check = '''package com.jeremykenedy.firetv.ui;
import android.content.*; import android.provider.Settings; import java.util.*;
public final class BootReceiverTest {
 static final String OTHER="third.party/Reader";
 static final String OWN="com.jeremykenedy.firetv.ui/com.jeremykenedy.firetv.ui.Hra:com.jeremykenedy.firetv.ui/com.jeremykenedy.firetv.ui.HomeRedirectService";
 static Context mixed(boolean wanted){
  Context c=new Context();c.prefs.values.put(HomeControl.PREFERENCE,wanted);
  c.prefs.values.put("start_on_boot",false);c.prefs.values.put("override_home_button",false);
  c.prefs.values.put("better_home_detection",false);return c;
 }
 static void settings(String services){
  Settings.Secure.values.clear();Settings.Secure.values.put("enabled_accessibility_services",services);
  Settings.Secure.values.put("accessibility_enabled","1");Settings.Secure.writes=0;Settings.Secure.failAt=0;
  DeviceSettings.writable=true;DeviceSettings.repairs=0;ProtectionService.starts=0;
 }
 static void same(Object a,Object b,String message){if(!a.equals(b))throw new AssertionError(message+": "+a+" != "+b);}
 public static void main(String[] args){
  DeviceBootReceiver receiver=new DeviceBootReceiver();
  settings(OTHER+":"+OWN);Context c=mixed(true);Map<String,Boolean> before=new HashMap<>(c.prefs.values);
  receiver.onReceive(c,new Intent(Intent.ACTION_MY_PACKAGE_REPLACED));
  same(c.prefs.values,before,"Update rewrote saved Home choices");same(c.prefs.commits,0,"Update wrote preferences");
  if(c.launches!=0||!HomeControl.isEnabled(c)||DeviceSettings.repairs!=1||ProtectionService.starts!=1)throw new AssertionError("Update repair/foreground");
  same(HomeControl.services(Settings.Secure.getString(c.getContentResolver(),"enabled_accessibility_services")).size(),3,"Rebind lost or duplicated services");
  receiver.onReceive(c,new Intent(Intent.ACTION_MY_PACKAGE_REPLACED));same(c.prefs.values,before,"Repeated update changed choices");

  settings(OTHER);c=new Context();receiver.onReceive(c,new Intent(Intent.ACTION_MY_PACKAGE_REPLACED));
  if(!c.prefs.values.isEmpty()||c.prefs.commits!=0||!HomeControl.isEnabled(c))throw new AssertionError("Missing preferences must stay absent");

  settings(OTHER);c=mixed(false);before=new HashMap<>(c.prefs.values);
  receiver.onReceive(c,new Intent(Intent.ACTION_MY_PACKAGE_REPLACED));HomeControl.rebind(c);
  same(c.prefs.values,before,"Disabled Home choices changed");same(Settings.Secure.writes,0,"Disabled Home was enabled");same(c.launches,0,"Disabled Home launched");

  settings(OTHER);c=mixed(true);before=new HashMap<>(c.prefs.values);HomeControl.rebind(c);
  same(c.prefs.values,before,"Repair normalized choices");if(!HomeControl.isEnabled(c))throw new AssertionError("Repair did not rebind");

  for(int fail=1;fail<=4;fail++){
   settings(OTHER+":"+OWN);c=mixed(true);before=new HashMap<>(c.prefs.values);
   Map<String,String> prior=new HashMap<>(Settings.Secure.values);Settings.Secure.failAt=fail;
   receiver.onReceive(c,new Intent(Intent.ACTION_MY_PACKAGE_REPLACED));
   same(c.prefs.values,before,"Failed update changed choices");same(c.prefs.commits,0,"Failed rebind wrote preferences");
   same(Settings.Secure.values,prior,"Failed rebind did not restore accessibility");same(c.launches,0,"Failed update launched Home");
  }

  settings(OTHER);c=mixed(true);before=new HashMap<>(c.prefs.values);DeviceSettings.writable=false;
  int priorIcons=BundledIcons.attempts;
  receiver.onReceive(c,new Intent(Intent.ACTION_MY_PACKAGE_REPLACED));same(c.prefs.values,before,"Missing permission changed choices");
  same(BundledIcons.attempts,priorIcons+1,"Icon attempt must not require secure-settings access");
  same(DeviceSettings.repairs,0,"Missing permission repaired");same(Settings.Secure.writes,0,"Missing permission wrote settings");

  settings(OTHER);c=new Context();same(HomeControl.enabledPreferenceState(c),"home-preference=unset","Missing state");
  c.prefs.values.put("start_on_boot",false);c.prefs.values.put("better_home_detection",false);
  HomeControl.rememberEnabledPreference(c,true);same(HomeControl.enabledPreferenceState(c),"home-preference=on","On state");
  if(c.prefs.values.containsKey("override_home_button")||c.prefs.getBoolean("start_on_boot",true)||c.prefs.getBoolean("better_home_detection",true))throw new AssertionError("Narrow preference normalized other keys");
  HomeControl.rememberEnabledPreference(c,false);same(HomeControl.enabledPreferenceState(c),"home-preference=off","Off state");
  HomeControl.clearEnabledPreference(c);same(HomeControl.enabledPreferenceState(c),"home-preference=unset","Clear lost absence");
  same(c.prefs.values.size(),2,"Narrow preference lost unrelated keys");

  settings(OTHER);c=new Context();c.prefs.values.put("start_on_boot",false);c.prefs.values.put("better_home_detection",false);
  BackupReceiver backup=new BackupReceiver();before=new HashMap<>(c.prefs.values);
  backup.onReceive(c,new Intent("home-preference-state"));same(backup.code,0,"Missing state failed");same(backup.data,"home-preference=unset","Receiver missing state");
  same(c.prefs.values,before,"State query wrote preferences");same(c.prefs.commits,0,"State query committed");
  for(boolean enabled:new boolean[]{true,false}){
   backup.onReceive(c,new Intent(enabled?"home-preference-on":"home-preference-off"));same(backup.code,0,"Narrow receiver write failed");same(backup.data,"ok","Narrow receiver reply");
   before.put(HomeControl.PREFERENCE,enabled);same(c.prefs.values,before,"Receiver normalized other Home keys");
   backup.onReceive(c,new Intent("home-preference-state"));same(backup.data,enabled?"home-preference=on":"home-preference=off","Receiver state readback");
  }
  backup.onReceive(c,new Intent("home-preference-clear"));before.remove(HomeControl.PREFERENCE);
  same(backup.code,0,"Clear failed");same(backup.data,"ok","Clear reply");same(c.prefs.values,before,"Clear changed other keys");
  backup.onReceive(c,new Intent("home-preference-state"));same(backup.data,"home-preference=unset","Clear did not restore absence");
  same(Settings.Secure.writes,0,"Preference operation wrote OS routing");
  c.prefs.failCommit=true;backup.onReceive(c,new Intent("home-preference-on"));
  same(backup.code,1,"Failed preference commit reported success");same(c.prefs.values,before,"Failed preference commit changed values");

  for(boolean populated:new boolean[]{true,false})for(boolean enabled:new boolean[]{true,false})for(int fail=1;fail<=(enabled?4:2);fail++){
   settings(OTHER+":"+OWN);c=populated?mixed(true):new Context();before=new HashMap<>(c.prefs.values);
   Map<String,String> prior=new HashMap<>(Settings.Secure.values);Settings.Secure.failAt=fail;
   try{HomeControl.setEnabled(c,enabled,true);throw new AssertionError("Expected routing failure");}catch(IllegalStateException expected){}
   same(c.prefs.values,before,"Full Home failure lost four-key values/presence");same(Settings.Secure.values,prior,"Full Home failure lost accessibility");
  }

  settings(OTHER);c=mixed(true);HomeControl.setEnabled(c,true,true);
  if(!c.prefs.getBoolean("start_on_boot",false)||!c.prefs.getBoolean("override_home_button",false)||!c.prefs.getBoolean("better_home_detection",false))throw new AssertionError("Explicit Home-on behavior changed");
  HomeControl.setEnabled(c,false,false);if(HomeControl.isEnabled(c)||c.prefs.getBoolean(HomeControl.PREFERENCE,true))throw new AssertionError("Explicit Home-off failed");

  settings(OTHER);c=mixed(true);receiver.onReceive(c,new Intent(Intent.ACTION_BOOT_COMPLETED));
  if(c.launches!=1||!c.prefs.getBoolean("start_on_boot",false))throw new AssertionError("Existing boot policy changed");
  c=mixed(false);receiver.onReceive(c,new Intent(Intent.ACTION_BOOT_COMPLETED));same(c.launches,0,"Disabled boot launched Home");
  int repairs=DeviceSettings.repairs;receiver.onReceive(c,new Intent("unrelated"));receiver.onReceive(c,new Intent(null));
  same(DeviceSettings.repairs,repairs,"Unrelated broadcast repaired");
  System.out.println("Home update preservation, rollback, explicit controls and boot checks passed");
 }
}'''
        with tempfile.TemporaryDirectory() as folder:
            files = []
            for name, content in stubs.items():
                target = Path(folder) / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(content)
                files.append(str(target))
            harness = Path(folder) / 'BootReceiverTest.java'
            harness.write_text(check)
            receiver = Path(os.environ.get('BOOT_RECEIVER_SOURCE', ROOT / 'app/src/com/jeremykenedy/firetv/ui/DeviceBootReceiver.java'))
            home = ROOT / 'app/src/com/jeremykenedy/firetv/ui/HomeControl.java'
            subprocess.run(['javac', '-d', folder, *files, str(receiver), str(home), str(ROOT / 'app/src/com/jeremykenedy/firetv/ui/BackupReceiver.java'), str(harness)], check=True)
            subprocess.run(['java', '-cp', folder, 'com.jeremykenedy.firetv.ui.BootReceiverTest'], check=True)
