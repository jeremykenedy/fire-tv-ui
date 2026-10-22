from pathlib import Path
import os
import subprocess
import tempfile
import unittest

from java_runtime import json_runtime

ROOT = Path(__file__).resolve().parents[1]


class BackupMigrationChecks(unittest.TestCase):
    def test_default_template_omits_local_state_and_keeps_accepted_standby(self):
        import json
        data = json.loads((ROOT / 'app/simple-setup.json').read_text())
        for key in ('fire_tv_ui_equipment_remote', 'fire_tv_ui_adb_trusted_network',
                    'status_bar_weather_loc', 'accessibility_consent_shown',
                    'fire_tv_ui_dream', 'fire_tv_ui_dream_enabled'):
            self.assertNotIn(key, data)
        self.assertEqual(data['apps_per_row'], 5)
        self.assertEqual(data['fire_tv_ui_sleep'], 1200000)
        self.assertEqual(data['fire_tv_ui_sleep_saved'], 1200000)
        self.assertFalse(data['fire_tv_ui_deep_sleep_enabled'])
        self.assertEqual(data['fire_tv_ui_auto_wake'], 1)
        apps = json.loads(data['app_visibility'])
        for name in ('HDMI 1', 'HDMI 2'):
            self.assertTrue(apps['tvinput_name:' + name]['customIconUri'].startswith('asset:input-icons/'))
        for row in json.loads(data['widgets_config']):
            for widget in row:
                self.assertEqual(widget.get('data', {}).get('location', ''), '')

    def test_device_identity_stays_local_while_portable_settings_restore(self):
        jar = json_runtime()
        sources = {
            'android/content/pm/PackageInfo.java': '''package android.content.pm; public class PackageInfo { public long firstInstallTime=1,lastUpdateTime=1; }''',
            'android/content/pm/PackageManager.java': '''package android.content.pm; public class PackageManager { public static PackageInfo info=new PackageInfo(); public PackageInfo getPackageInfo(String p,int flags)throws NameNotFoundException{return info;} public static class NameNotFoundException extends Exception{} }''',
            'android/content/SharedPreferences.java': '''package android.content;
import java.util.*;
public interface SharedPreferences {
 Map<String,?> getAll(); String getString(String key,String fallback); Editor edit();
 interface Editor { Editor clear(); Editor putBoolean(String k,boolean v); Editor putInt(String k,int v);
 Editor putLong(String k,long v); Editor putFloat(String k,float v); Editor putString(String k,String v);
 Editor putStringSet(String k,Set<String> v); boolean commit(); }
}''',
            'android/content/Context.java': '''package android.content;
import java.util.*;
public class Context {
 public String getPackageName(){return "ui.test";} public android.content.pm.PackageManager getPackageManager(){return new android.content.pm.PackageManager();} public static final int MODE_PRIVATE=0; public static java.io.File files; public final Prefs prefs=new Prefs(); public java.io.File getFilesDir(){return files;}
 public SharedPreferences getSharedPreferences(String name,int mode) { return prefs; }
 public android.content.res.AssetManager getAssets() { return new android.content.res.AssetManager(); }
 public static class Prefs implements SharedPreferences {
  public final Map<String,Object> values=new HashMap<>();
  public Map<String,?> getAll() { return values; }
  public String getString(String k,String fallback) { return (String)values.getOrDefault(k,fallback); }
  public Editor edit() { return new Editor() {
   final Map<String,Object> pending=new HashMap<>(); boolean clear;
   public Editor clear(){clear=true;return this;}
   public Editor putBoolean(String k,boolean v){pending.put(k,v);return this;}
   public Editor putInt(String k,int v){pending.put(k,v);return this;}
   public Editor putLong(String k,long v){pending.put(k,v);return this;}
   public Editor putFloat(String k,float v){pending.put(k,v);return this;}
   public Editor putString(String k,String v){pending.put(k,v);return this;}
   public Editor putStringSet(String k,Set<String> v){pending.put(k,v);return this;}
   public boolean commit(){if(clear)values.clear();values.putAll(pending);return true;}
  }; }
 }
}''',
            'android/content/res/AssetManager.java': '''package android.content.res;
public class AssetManager { public static final java.util.Map<String,String> contents=new java.util.HashMap<>(); public java.io.InputStream open(String path) {
 return new java.io.ByteArrayInputStream(contents.getOrDefault(path,"{\\"theme\\":\\"String\\"}").getBytes()); } }''',
            'android/net/Uri.java': '''package android.net; public class Uri { private java.io.File file; private Uri(java.io.File f){file=f;} public static Uri fromFile(java.io.File f){return new Uri(f);} public String toString(){return file.toURI().toString();} }''',
            'android/os/Build.java': '''package android.os;
public class Build { public static class VERSION { public static int SDK_INT=34; } }''',
            'android/os/Environment.java': '''package android.os;
public class Environment {
 public static final String DIRECTORY_DOWNLOADS="Downloads"; public static java.io.File directory;
 public static java.io.File getExternalStoragePublicDirectory(String name){return directory;}
 public static boolean isExternalStorageManager(){return true;}
}''',
            'android/util/AtomicFile.java': '''package android.util;
public class AtomicFile {
 final java.io.File file; public AtomicFile(java.io.File f){file=f;}
 public java.io.FileInputStream openRead() throws java.io.IOException{return new java.io.FileInputStream(file);}
 public java.io.FileOutputStream startWrite() throws java.io.IOException{return new java.io.FileOutputStream(file);}
 public void finishWrite(java.io.FileOutputStream out) throws java.io.IOException{out.close();}
 public void failWrite(java.io.FileOutputStream out) throws java.io.IOException{out.close();}
}''',
            'com/jeremykenedy/firetv/ui/DeviceSettings.java': '''package com.jeremykenedy.firetv.ui;
public class DeviceSettings { public static boolean repaired; public static void sleep(android.content.Context c,int value){throw new AssertionError("Never sleep must not be applied by setup");} public static boolean canWrite(android.content.Context c){return true;} public static void repair(android.content.Context c){repaired=true;} }''',
            'com/jeremykenedy/firetv/ui/EquipmentControl.java': '''package com.jeremykenedy.firetv.ui;
final class EquipmentControl { static final String REMOTE="fire_tv_ui_equipment_remote"; }''',
        }
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp)
            files = []
            for name, content in sources.items():
                target = folder / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(content)
                files.append(str(target))
            subprocess.run(['javac', '-cp', str(jar), '-d', temp, *files,
                            os.environ.get('BACKUP_STORE_SOURCE', str(ROOT / 'app/src/com/jeremykenedy/firetv/ui/BackupStore.java')),
                            str(ROOT / 'tests/BackupMigrationTest.java')], check=True)
            subprocess.run(['java', '-cp', temp + os.pathsep + str(jar),
                            'com.jeremykenedy.firetv.ui.BackupMigrationTest', temp], check=True)
