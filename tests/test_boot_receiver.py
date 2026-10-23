from pathlib import Path
import os
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class BootReceiverChecks(unittest.TestCase):
    def test_package_update_repairs_controls_without_interrupting_current_app(self):
        stubs = {
            'android/content/BroadcastReceiver.java': 'package android.content; public abstract class BroadcastReceiver { public abstract void onReceive(Context c,Intent i); }',
            'android/content/Context.java': 'package android.content; public class Context { public int launches; public String getPackageName(){return "test.ui";} public android.content.pm.PackageManager getPackageManager(){return new android.content.pm.PackageManager();} public void startActivity(Intent i){launches++;} }',
            'android/content/Intent.java': 'package android.content; public class Intent { public static final String ACTION_BOOT_COMPLETED="boot",ACTION_MY_PACKAGE_REPLACED="updated"; public static final int FLAG_ACTIVITY_NEW_TASK=1,FLAG_ACTIVITY_RESET_TASK_IF_NEEDED=2; private String action; public Intent(String a){action=a;} public String getAction(){return action;} public Intent addFlags(int f){return this;} }',
            'android/content/pm/PackageManager.java': 'package android.content.pm; public class PackageManager { public android.content.Intent getLaunchIntentForPackage(String p){return new android.content.Intent("launch");} }',
            'com/jeremykenedy/firetv/ui/DeviceSettings.java': 'package com.jeremykenedy.firetv.ui; class DeviceSettings { static int repairs; static boolean writable=true; static boolean canWrite(android.content.Context c){return writable;} static void repair(android.content.Context c){repairs++;} }',
            'com/jeremykenedy/firetv/ui/ProtectionService.java': 'package com.jeremykenedy.firetv.ui; class ProtectionService { static int starts; static void start(android.content.Context c){starts++;} }',
            'com/jeremykenedy/firetv/ui/HomeControl.java': 'package com.jeremykenedy.firetv.ui; class HomeControl { static int repairs; static boolean wanted=true; static boolean wanted(android.content.Context c){return wanted;} static void setEnabled(android.content.Context c,boolean enabled,boolean rebind){if(!enabled||!rebind)throw new AssertionError();repairs++;} }',
        }
        check = '''package com.jeremykenedy.firetv.ui;
import android.content.*;
public final class BootReceiverTest {
 public static void main(String[] args){
  DeviceBootReceiver receiver=new DeviceBootReceiver();Context current=new Context();
  receiver.onReceive(current,new Intent(Intent.ACTION_MY_PACKAGE_REPLACED));
  if(current.launches!=0)throw new AssertionError("Updating interrupted the current app");
  if(DeviceSettings.repairs!=1||ProtectionService.starts!=1||HomeControl.repairs!=1)throw new AssertionError("Update must repair controls");
  receiver.onReceive(current,new Intent(Intent.ACTION_BOOT_COMPLETED));
  if(current.launches!=1)throw new AssertionError("Enabled startup must still open Home after boot");
  HomeControl.wanted=false;receiver.onReceive(current,new Intent(Intent.ACTION_BOOT_COMPLETED));
  if(current.launches!=1)throw new AssertionError("Disabled startup opened Home");
  int repairs=DeviceSettings.repairs;receiver.onReceive(current,new Intent("unrelated"));
  if(DeviceSettings.repairs!=repairs)throw new AssertionError("Unrelated broadcast repaired settings");
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
            source = Path(os.environ.get('BOOT_RECEIVER_SOURCE', ROOT / 'app/src/com/jeremykenedy/firetv/ui/DeviceBootReceiver.java'))
            subprocess.run(['javac', '-d', folder, *files, str(source), str(harness)], check=True)
            subprocess.run(['java', '-cp', folder, 'com.jeremykenedy.firetv.ui.BootReceiverTest'], check=True)
