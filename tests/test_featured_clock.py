from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class FeaturedClockChecks(unittest.TestCase):
    def test_manual_deadline_menu_pause_and_main_lifecycle(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            stubs = {
                'android/content/SharedPreferences.java': 'package android.content; public class SharedPreferences { public int value; public int getInt(String k,int d){return value;} }',
                'android/content/Context.java': 'package android.content; public class Context { public SharedPreferences prefs=new SharedPreferences(); public Context getApplicationContext(){return this;} }',
                'android/os/Looper.java': 'package android.os; public class Looper { public static Looper getMainLooper(){return new Looper();} }',
                'android/os/Handler.java': 'package android.os; public class Handler { public static Runnable pending; public static long delay; public Handler(Looper l){} public void removeCallbacks(Runnable r){if(pending==r)pending=null;} public boolean postDelayed(Runnable r,long d){pending=r;delay=d;return true;} }',
                'com/jeremykenedy/firetv/ui/DeviceSettings.java': 'package com.jeremykenedy.firetv.ui; class DeviceSettings {static android.content.SharedPreferences prefs(android.content.Context c){return c.prefs;}}',
                'com/jeremykenedy/firetv/ui/FeaturedGroups.java': 'package com.jeremykenedy.firetv.ui; class FeaturedGroups {static int ticks;static void advance(){ticks++;}}',
            }
            paths = []
            for name, contents in stubs.items():
                path = root / name; path.parent.mkdir(parents=True, exist_ok=True); path.write_text(contents); paths.append(str(path))
            subprocess.run(['javac', '-d', folder, *paths,
                            str(ROOT / 'app/src/com/jeremykenedy/firetv/ui/FeaturedCardTiming.java'),
                            str(ROOT / 'app/src/com/jeremykenedy/firetv/ui/FeaturedCardClock.java'),
                            str(ROOT / 'tests/FeaturedCardClockTest.java')], check=True)
            subprocess.run(['java', '-cp', folder, 'com.jeremykenedy.firetv.ui.FeaturedCardClockTest'], check=True)
