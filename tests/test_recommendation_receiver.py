from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class RecommendationReceiverChecks(unittest.TestCase):
    def test_managed_toggle_never_opens_an_activity_and_reports_save_errors(self):
        stubs = {
            'android/content/Context.java': 'package android.content; public class Context { public int launches; public Context getApplicationContext(){return this;} }',
            'android/content/Intent.java': 'package android.content; public class Intent { private final String operation; public Intent(String value){operation=value;} public String getStringExtra(String key){return "operation".equals(key)?operation:null;} }',
            'android/content/BroadcastReceiver.java': 'package android.content; public abstract class BroadcastReceiver { public int code; public String data; public abstract void onReceive(Context c,Intent i); public void setResultCode(int value){code=value;} public void setResultData(String value){data=value;} public PendingResult goAsync(){return new PendingResult();} public static class PendingResult { public void setResultCode(int i){} public void setResultData(String s){} public void finish(){} } }',
            'com/jeremykenedy/firetv/ui/StagedUiUpdate.java': 'package com.jeremykenedy.firetv.ui; class StagedUiUpdate { static void stage(android.content.Context c){} }',
            'com/jeremykenedy/firetv/ui/BackupStore.java': 'package com.jeremykenedy.firetv.ui; class BackupStore { static void save(android.content.Context c){} static void restore(android.content.Context c,boolean b){} static void simpleSetup(android.content.Context c){} }',
            'com/jeremykenedy/firetv/ui/HomeControl.java': 'package com.jeremykenedy.firetv.ui; class HomeControl { static void setEnabled(android.content.Context c,boolean b,boolean r){} static void remember(android.content.Context c,boolean b){} static void rememberEnabledPreference(android.content.Context c,boolean b){} static void clearEnabledPreference(android.content.Context c){} static String enabledPreferenceState(android.content.Context c){return "home-preference=unset";} }',
            'com/jeremykenedy/firetv/ui/ProtectionService.java': 'package com.jeremykenedy.firetv.ui; class ProtectionService { static void start(android.content.Context c){} }',
            'com/jeremykenedy/firetv/ui/DeviceSettings.java': 'package com.jeremykenedy.firetv.ui; class DeviceSettings { static final String DREAM_UNLOCKED="dream",PROTECT="protect",SLEEP="sleep",IDLE="idle",AUTO_WAKE="wake",COMPONENTS="component",ENABLED="enabled"; static Prefs prefs(android.content.Context c){return new Prefs();} static void keepAdb(android.content.Context c,boolean b){} static void capture(android.content.Context c){} static void sleep(android.content.Context c,int i){} static void idle(android.content.Context c,int i){} static void autoWake(android.content.Context c,boolean b){} static void component(android.content.Context c,String s){} static void enableDream(android.content.Context c,boolean b){} static class Prefs { Prefs edit(){return this;} Prefs putBoolean(String k,boolean b){return this;} boolean commit(){return true;} } }',
            'com/jeremykenedy/firetv/ui/RecommendationService.java': 'package com.jeremykenedy.firetv.ui; class RecommendationService { static boolean enabled,fail; static int changes; static void setEnabled(android.content.Context context,boolean value){if(fail)throw new IllegalStateException("Save failed");enabled=value;changes++;} }',
        }
        check = '''package com.jeremykenedy.firetv.ui; import android.content.*;
public final class RecommendationReceiverTest { public static void main(String[] args){
 Context current=new Context();BackupReceiver receiver=new BackupReceiver();
 receiver.onReceive(current,new Intent("recommendations-on"));
 if(!RecommendationService.enabled||RecommendationService.changes!=1||receiver.code!=0||!"ok".equals(receiver.data))throw new AssertionError("Enable failed");
 receiver.onReceive(current,new Intent("recommendations-off"));
 if(RecommendationService.enabled||RecommendationService.changes!=2||receiver.code!=0)throw new AssertionError("Disable failed");
 RecommendationService.fail=true;receiver.onReceive(current,new Intent("recommendations-on"));
 if(receiver.code!=1||RecommendationService.enabled||current.launches!=0)throw new AssertionError("Failed save reported success");
 }}'''
        with tempfile.TemporaryDirectory() as folder:
            files = []
            for name, content in stubs.items():
                target = Path(folder) / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(content)
                files.append(str(target))
            harness = Path(folder) / 'RecommendationReceiverTest.java'
            harness.write_text(check)
            subprocess.run(['javac', '-d', folder,
                            *files, str(ROOT / 'app/src/com/jeremykenedy/firetv/ui/BackupReceiver.java'),
                            str(harness)], check=True)
            subprocess.run(['java', '-cp', folder,
                            'com.jeremykenedy.firetv.ui.RecommendationReceiverTest'], check=True)
