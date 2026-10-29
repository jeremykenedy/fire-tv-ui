from pathlib import Path
import os
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class HomeRedirectChecks(unittest.TestCase):
    def test_home_preserves_settings_routes_and_recovers_after_launch_failures(self):
        stubs = {
            'android/content/Context.java': 'package android.content; public class Context { public int launches, attempts; public boolean failLaunch; public Intent lastLaunch; public android.content.pm.PackageManager getPackageManager(){return new android.content.pm.PackageManager();} public void startActivity(Intent i){attempts++;if(failLaunch)throw new IllegalStateException("synthetic private detail");launches++;lastLaunch=i;} }',
            'android/content/Intent.java': 'package android.content; public class Intent { public static final String CATEGORY_HOME="home"; public static final int FLAG_ACTIVITY_NEW_TASK=1,FLAG_ACTIVITY_RESET_TASK_IF_NEEDED=2,FLAG_ACTIVITY_NO_ANIMATION=4; public String category; public int flags; public Intent addCategory(String c){category=c;return this;} public Intent addFlags(int f){flags|=f;return this;} }',
            'android/content/pm/PackageManager.java': 'package android.content.pm; public class PackageManager { public static boolean missing,failResolution; public android.content.Intent getLaunchIntentForPackage(String p){if(failResolution)throw new IllegalStateException("synthetic resolver detail");return missing?null:new android.content.Intent();} }',
            'android/accessibilityservice/AccessibilityService.java': 'package android.accessibilityservice; public class AccessibilityService extends android.content.Context { protected void onServiceConnected(){} public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent e){} public void onInterrupt(){} public void onDestroy(){} }',
            'android/os/Looper.java': 'package android.os; public class Looper { public static Looper getMainLooper(){return new Looper();} }',
            'android/os/Handler.java': 'package android.os; public class Handler { public static Runnable pending; public Handler(Looper l){} public void postDelayed(Runnable r,long delay){pending=r;} public void removeCallbacks(Runnable r){if(pending==r)pending=null;} }',
            'android/util/Log.java': 'package android.util; public class Log { public static final java.util.List<String> entries=new java.util.ArrayList<>(); public static void i(String tag,String message){entries.add(message);} public static void w(String tag,String message){entries.add(message);} }',
            'android/view/accessibility/AccessibilityEvent.java': 'package android.view.accessibility; public class AccessibilityEvent { public static final int TYPE_WINDOW_STATE_CHANGED=32; final String pkg,cls; public AccessibilityEvent(String p,String c){pkg=p;cls=c;} public CharSequence getPackageName(){return pkg;} public CharSequence getClassName(){return cls;} public int getEventType(){return 32;} }',
            'com/jeremykenedy/firetv/ui/EquipmentDialogRecovery.java': 'package com.jeremykenedy.firetv.ui; class EquipmentDialogRecovery { EquipmentDialogRecovery(android.content.Context c){} void check(){} void stop(){} }',
            'com/jeremykenedy/firetv/ui/FactoryHome.java': 'package com.jeremykenedy.firetv.ui; class FactoryHome { static boolean bypass(android.view.accessibility.AccessibilityEvent e){return false;} }',
        }
        with tempfile.TemporaryDirectory() as folder:
            files = []
            for name, content in stubs.items():
                target = Path(folder) / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(content)
                files.append(str(target))
            source = Path(os.environ.get('HOME_REDIRECT_SOURCE', ROOT / 'app/src/com/jeremykenedy/firetv/ui/HomeRedirectService.java'))
            subprocess.run(['javac', '-d', folder, *files, str(source),
                            str(ROOT / 'tests/HomeRedirectTest.java')], check=True)
            subprocess.run(['java', '-cp', folder,
                            'com.jeremykenedy.firetv.ui.HomeRedirectTest'], check=True)
