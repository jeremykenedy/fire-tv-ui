from pathlib import Path
import re
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class HomeKeyDispatchChecks(unittest.TestCase):
    def test_success_failure_and_missing_target_preserve_system_fallback(self):
        stubs = {
            'android/content/Context.java': '''package android.content;
public class Context { public Intent launched; public int calls; public boolean fail;
public void startActivity(Intent intent) { calls++; if (fail) throw new SecurityException("private-intent-token"); launched=intent; } }''',
            'android/content/Intent.java': 'package android.content; public class Intent { public String toString(){throw new AssertionError("Do not inspect the cached intent");} }',
            'android/view/KeyEvent.java': '''package android.view;
public class KeyEvent { public static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MULTIPLE=2,KEYCODE_HOME=3;
private final int action,key; public KeyEvent(int action,int key){this.action=action;this.key=key;}
public int getAction(){return action;} public int getKeyCode(){return key;} }''',
            'android/util/Log.java': '''package android.util;
public class Log { public static java.util.List<String> entries=new java.util.ArrayList<>();
public static int i(String tag,String message){entries.add(message);return 0;}
public static int w(String tag,String message){entries.add(message);return 0;} }''',
        }
        with tempfile.TemporaryDirectory() as folder:
            files = []
            for name, content in stubs.items():
                target = Path(folder) / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(content)
                files.append(str(target))
            subprocess.run(['javac', '-d', folder, *files,
                            str(ROOT / 'app/src/com/jeremykenedy/firetv/ui/HomeKeyDispatch.java'),
                            str(ROOT / 'tests/HomeKeyDispatchTest.java')], check=True)
            subprocess.run(['java', '-cp', folder,
                            'com.jeremykenedy.firetv.ui.HomeKeyDispatchTest'], check=True)

    def test_built_hardware_home_returns_the_dispatch_result(self):
        path = ROOT / '.build/decoded/smali/com/overdevs/at4k/Hra.smali'
        if not path.exists():
            self.skipTest('Build the launcher before verifying its actual Home key hook.')
        source = path.read_text()
        method = re.search(r'\.method public final onKeyEvent\(Landroid/view/KeyEvent;\)Z.*?\.end method', source, re.S).group(0)
        self.assertIn('FactoryHome;->homeKey(Landroid/view/KeyEvent;)V', method)
        self.assertIn('Hra;->h:Z', method)
        self.assertIn('Hra;->j:Landroid/content/Intent;', method)
        self.assertIn('HomeKeyDispatch;->dispatch(Landroid/content/Context;Landroid/view/KeyEvent;ZLandroid/content/Intent;)Z\n    move-result v0\n    return v0', method)
        self.assertNotIn('.catch', method)
        self.assertNotIn('startActivity', method)
        connected = re.search(r'\.method public final onServiceConnected\(\)V.*?\.end method', source, re.S).group(0)
        self.assertIn('getLaunchIntentForPackage', connected)
        self.assertIn('"com.jeremykenedy.firetv.ui"', connected)
        self.assertIn('"android.intent.category.HOME"', connected)
        self.assertIn('const/high16 v1, 0x34030000', connected)
