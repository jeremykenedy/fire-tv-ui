from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class FactoryHomeChecks(unittest.TestCase):
    def test_native_launch_bypasses_initial_transition_but_preserves_next_home(self):
        with tempfile.TemporaryDirectory() as folder:
            subprocess.run(['javac', '-d', folder,
                            str(ROOT / 'app/src/com/jeremykenedy/firetv/ui/FactoryHomeBypass.java'),
                            str(ROOT / 'tests/FactoryHomeBypassTest.java')], check=True)
            subprocess.run(['java', '-cp', folder,
                            'com.jeremykenedy.firetv.ui.FactoryHomeBypassTest'], check=True)

    def test_tile_resolves_only_the_exported_native_system_launcher(self):
        stubs = {
            'android/util/Log.java': 'package android.util; public class Log { public static int i(String tag,String value){return 0;} }',
            'android/net/Uri.java': 'package android.net; public class Uri { private String value; private Uri(String value){this.value=value;} public static Uri parse(String value){return new Uri(value);} public String toString(){return value;} }',
            'android/content/ComponentName.java': 'package android.content; public class ComponentName { public String pkg,name; public ComponentName(String p,String n){pkg=p;name=n;} public String getPackageName(){return pkg;} public String getClassName(){return name;} }',
            'android/content/Context.java': 'package android.content; public class Context { public Intent launched; public android.content.pm.PackageManager manager=new android.content.pm.PackageManager(); public android.content.pm.PackageManager getPackageManager(){return manager;} public void startActivity(Intent i){launched=i;} }',
            'android/content/Intent.java': 'package android.content; public class Intent { public static final String ACTION_MAIN="main",ACTION_VIEW="view",CATEGORY_HOME="home"; public static final int FLAG_ACTIVITY_NEW_TASK=1,FLAG_ACTIVITY_RESET_TASK_IF_NEEDED=2; public ComponentName component; public String pkg,action; public android.net.Uri data; public Intent(String action){this.action=action;} public Intent(String action,android.net.Uri data){this.action=action;this.data=data;} public ComponentName getComponent(){return component;} public Intent addCategory(String c){return this;} public Intent setPackage(String p){pkg=p;return this;} public Intent setComponent(ComponentName c){component=c;return this;} public Intent addFlags(int f){return this;} }',
            'android/content/pm/ApplicationInfo.java': 'package android.content.pm; public class ApplicationInfo { public static final int FLAG_SYSTEM=1; public int flags; }',
            'android/content/pm/ActivityInfo.java': 'package android.content.pm; public class ActivityInfo { public boolean exported,enabled; public String packageName,name,permission; public ApplicationInfo applicationInfo=new ApplicationInfo(); }',
            'android/content/pm/ResolveInfo.java': 'package android.content.pm; public class ResolveInfo { public ActivityInfo activityInfo; public CharSequence nonLocalizedLabel; }',
            'android/content/pm/PackageManager.java': 'package android.content.pm; public class PackageManager { public static final int COMPONENT_ENABLED_STATE_ENABLED=1; public int componentState; public int getComponentEnabledSetting(android.content.ComponentName c){return componentState;} public ResolveInfo home; public android.content.Intent resolved; public ResolveInfo resolveActivity(android.content.Intent i,int flags){resolved=i;return home;} }',
            'android/os/SystemClock.java': 'package android.os; public class SystemClock { public static long uptimeMillis(){return 1000;} }',
            'android/view/KeyEvent.java': 'package android.view; public class KeyEvent { public static final int KEYCODE_HOME=3; public int getKeyCode(){return 3;} }',
            'android/view/accessibility/AccessibilityEvent.java': 'package android.view.accessibility; public class AccessibilityEvent { public static final int TYPE_WINDOW_STATE_CHANGED=32; public int getEventType(){return 32;} public CharSequence getPackageName(){return "com.amazon.tv.launcher";} public CharSequence getClassName(){return "com.amazon.tv.launcher.ui.HomeActivity_vNext";} public long getEventTime(){return 1100;} }',
        }
        with tempfile.TemporaryDirectory() as folder:
            files = []
            for name, content in stubs.items():
                target = Path(folder) / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(content)
                files.append(str(target))
            source = ROOT / 'app/src/com/jeremykenedy/firetv/ui'
            subprocess.run(['javac', '-d', folder, *files, str(source / 'FactoryHome.java'),
                            str(source / 'FactoryHomeBypass.java'), str(source / 'NativeLiveTv.java'), str(ROOT / 'tests/FactoryHomeTest.java'), str(ROOT / 'tests/NativeLiveTvTest.java')], check=True)
            subprocess.run(['java', '-cp', folder,
                            'com.jeremykenedy.firetv.ui.FactoryHomeTest'], check=True)
            subprocess.run(['java', '-cp', folder,
                            'com.jeremykenedy.firetv.ui.NativeLiveTvTest'], check=True)
