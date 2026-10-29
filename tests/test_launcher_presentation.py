from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class LauncherPresentationChecks(unittest.TestCase):
    def test_home_keeps_visible_and_returning_scenes_but_closes_focused_popup(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp)
            activity = path / 'android/app/Activity.java'
            activity.parent.mkdir(parents=True)
            activity.write_text('package android.app; public class Activity { public boolean focused=true; public int resets; public boolean hasWindowFocus(){return focused;} public void recreate(){resets++;} }')
            check = path / 'HomePresentationTest.java'
            check.write_text('''import android.app.Activity;
import com.jeremykenedy.firetv.ui.HomePresentation;
public class HomePresentationTest {
 public static void main(String[] args) {
  Activity home = new Activity();
  HomePresentation.resumed(home);
  for(int i=0;i<10;i++) HomePresentation.closeTransientWindow(home);
  if(home.resets!=0) throw new AssertionError("Repeated Home rebuilt visible launcher");
  HomePresentation.paused(home); home.focused=false;
  HomePresentation.closeTransientWindow(home);
  if(home.resets!=0) throw new AssertionError("App return rebuilt launcher");
  HomePresentation.resumed(home);
  HomePresentation.paused(home);
  HomePresentation.closeTransientWindow(home);
  if(home.resets!=1) throw new AssertionError("Focused popup no longer dismisses");
  HomePresentation.closeTransientWindow(home);
  if(home.resets!=1) throw new AssertionError("Popup reset was repeated");
  home.focused=true; HomePresentation.resumed(home);
  HomePresentation.paused(home); HomePresentation.closeTransientWindow(home);
  if(home.resets!=1) throw new AssertionError("Ordinary paused Home rebuilt launcher");
  Activity replacement=new Activity(); HomePresentation.resumed(replacement);
  HomePresentation.paused(home);
  replacement.focused=false; HomePresentation.closeTransientWindow(replacement);
  if(replacement.resets!=1) throw new AssertionError("Old activity paused new activity");
 }
}''')
            subprocess.run(['javac', '-d', temp, str(activity), str(check), str(ROOT / 'app/src/com/jeremykenedy/firetv/ui/HomePresentation.java')], check=True)
            subprocess.run(['java', '-cp', temp, 'HomePresentationTest'], check=True)

    def test_custom_column_values_and_existing_preferences(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp)
            prefs = path / 'android/content/SharedPreferences.java'
            prefs.parent.mkdir(parents=True)
            prefs.write_text('package android.content; public class SharedPreferences { public int value; public int getInt(String key,int fallback){return value;} }')
            check = path / 'LauncherLayoutTest.java'
            check.write_text('''package com.jeremykenedy.firetv.ui;
import android.content.SharedPreferences;
public class LauncherLayoutTest {
 public static void main(String[] args) {
  for(int i=3;i<=12;i++) if(LauncherLayout.parseColumns(" "+i+" ")!=i) throw new AssertionError();
  String[] invalid={"", "0", "1", "2", "13", "-3", "2.5", "abc", "999999999999999"};
  for(String value:invalid){try{LauncherLayout.parseColumns(value);throw new AssertionError(value);}catch(IllegalArgumentException expected){}}
  SharedPreferences prefs=new SharedPreferences(); prefs.value=6;
  if(LauncherLayout.columns(prefs)!=6) throw new AssertionError("Existing choice changed");
  prefs.value=16;if(LauncherLayout.columns(prefs)!=16)throw new AssertionError("Legacy choice removed");
  prefs.value=0;if(LauncherLayout.columns(prefs)!=5)throw new AssertionError("Invalid grid fallback");
 }
}''')
            subprocess.run(['javac', '-d', temp, str(prefs), str(check), str(ROOT / 'app/src/com/jeremykenedy/firetv/ui/LauncherLayout.java')], check=True)
            subprocess.run(['java', '-cp', temp, 'com.jeremykenedy.firetv.ui.LauncherLayoutTest'], check=True)
