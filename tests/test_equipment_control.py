from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class EquipmentControlChecks(unittest.TestCase):
    def test_only_the_same_previously_supported_connected_remote_is_restored(self):
        sources = {
            'android/content/Context.java': '''package android.content;
public class Context { public Object getContentResolver() { return this; } }''',
            'android/provider/Settings.java': '''package android.provider;
public class Settings { public static class Global {
    public static int value, writes; public static boolean accept = true;
    public static int getInt(Object resolver, String key, int fallback) { return value; }
    public static boolean putInt(Object resolver, String key, int requested) {
        if (!"show_dc_settings".equals(key)) throw new AssertionError(key);
        writes++; if (accept) value = requested; return accept;
    }
} }''',
            'android/util/Log.java': '''package android.util;
public class Log { public static int i(String tag, String text) { return 0; } }''',
            'android/view/InputDevice.java': '''package android.view;
public class InputDevice {
    public static InputDevice[] connected = new InputDevice[0];
    public boolean external = true, virtual; public int vendor = 1, product = 1;
    public String name = "Amazon Fire TV Remote", descriptor = "known";
    public static int[] getDeviceIds() {
        int[] ids = new int[connected.length]; for (int i=0;i<ids.length;i++) ids[i]=i; return ids;
    }
    public static InputDevice getDevice(int id) { return connected[id]; }
    public boolean isExternal() { return external; } public boolean isVirtual() { return virtual; }
    public int getVendorId() { return vendor; } public int getProductId() { return product; }
    public String getName() { return name; } public String getDescriptor() { return descriptor; }
}''',
            'com/jeremykenedy/firetv/ui/DeviceSettings.java': '''package com.jeremykenedy.firetv.ui;
import android.content.Context;
public class DeviceSettings {
    static boolean fireTv = true; static final Prefs prefs = new Prefs();
    public static boolean hasWifiAdb(Context context) { return fireTv; }
    public static Prefs prefs(Context context) { return prefs; }
    public static class Prefs {
        String saved = "";
        public String getString(String key, String fallback) { return saved; }
        public Prefs edit() { return this; }
        public Prefs putString(String key, String value) { saved = value; return this; }
        public boolean commit() { return true; }
    }
}''',
            'com/jeremykenedy/firetv/ui/EquipmentControlTest.java': '''package com.jeremykenedy.firetv.ui;
import android.content.Context;
import android.provider.Settings.Global;
import android.view.InputDevice;
public class EquipmentControlTest {
    static final Context context = new Context();
    static void check(boolean condition) { if (!condition) throw new AssertionError(); }
    static InputDevice reset() {
        Global.value=0; Global.writes=0; Global.accept=true; DeviceSettings.fireTv=true;
        DeviceSettings.prefs.saved="known";
        InputDevice remote=new InputDevice(); InputDevice.connected=new InputDevice[]{remote}; return remote;
    }
    static void untouched() { EquipmentControl.repair(context); check(Global.value==0 && Global.writes==0); }
    public static void main(String[] args) {
        reset(); DeviceSettings.prefs.saved=""; untouched();
        Global.value=1; EquipmentControl.repair(context);
        check(DeviceSettings.prefs.saved.equals("known") && Global.writes==0);
        Global.value=0; EquipmentControl.repair(context); check(Global.value==1 && Global.writes==1);
        reset().descriptor="different"; untouched();
        reset(); InputDevice.connected=new InputDevice[0]; untouched();
        reset(); InputDevice.connected=new InputDevice[]{new InputDevice(),new InputDevice()}; untouched();
        reset().external=false; untouched(); reset().virtual=true; untouched();
        reset().vendor=0; untouched(); reset().product=0; untouched();
        reset().name="Unknown Remote"; untouched(); reset().descriptor=""; untouched();
        reset().descriptor=null; untouched(); reset(); DeviceSettings.fireTv=false; untouched();
        reset(); InputDevice.connected=new InputDevice[]{null}; untouched();
        reset(); Global.accept=false;
        try { EquipmentControl.repair(context); throw new AssertionError("Rejected write was hidden"); }
        catch (IllegalStateException expected) { check(Global.value==0); }
    }
}''',
        }
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp)
            files = []
            for name, content in sources.items():
                target = folder / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(content)
                files.append(str(target))
            real = ROOT / 'app/src/com/jeremykenedy/firetv/ui/EquipmentControl.java'
            subprocess.run(['javac', '-d', temp, *files, str(real)], check=True)
            subprocess.run(['java', '-cp', temp,
                            'com.jeremykenedy.firetv.ui.EquipmentControlTest'], check=True)

    def test_dialog_recovery_cannot_approve_system_or_wireless_debugging_dialogs(self):
        source = (ROOT / 'app/src/com/jeremykenedy/firetv/ui/EquipmentDialogRecovery.java').read_text()
        for forbidden in ('com.android.systemui', 'allowDebugging', 'alwaysUse',
                          'trusted_network', 'android:id/button1'):
            self.assertNotIn(forbidden, source)
