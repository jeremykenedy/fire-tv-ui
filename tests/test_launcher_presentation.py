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
            prefs.write_text('package android.content; public class SharedPreferences { public int value=5, rows; public boolean allApps; public boolean getBoolean(String key,boolean fallback){return allApps;} public String direction="vertical"; public int getInt(String key,int fallback){return key.equals("apps_per_row")?value:rows;} public String getString(String key,String fallback){return direction;} }')
            activity = path / 'android/app/Activity.java'
            activity.parent.mkdir(parents=True)
            activity.write_text('package android.app; public class Activity { public int recreates; public void recreate(){recreates++;} }')
            settings = path / 'DeviceSettings.java'
            settings.write_text('package com.jeremykenedy.firetv.ui; public class DeviceSettings { public static android.content.SharedPreferences prefs=new android.content.SharedPreferences(); public static android.content.SharedPreferences prefs(android.app.Activity activity){return prefs;} }')
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
  for(int apps=0;apps<=300;apps++) for(int columns=3;columns<=12;columns++) for(int rows=1;rows<=6;rows++) {
    int width=LauncherLayout.horizontalColumns(apps,columns,rows);
    if(width<columns || (apps+width-1)/width>rows) throw new AssertionError("Grid exceeds row limit");
    boolean[] seen=new boolean[apps];
    for(int row=0;row<(apps+width-1)/width;row++) for(int column=0;column<width;column++) {
      int index=row*width+column;
      if(index<apps){if(seen[index])throw new AssertionError("Duplicate app");seen[index]=true;}
    }
    for(boolean reachable:seen)if(!reachable)throw new AssertionError("Hidden app");
  }
  if(LauncherLayout.horizontalColumns(11,5,2)!=6)throw new AssertionError("Incomplete last row");
  if(LauncherLayout.horizontalColumns(2,5,6)!=5)throw new AssertionError("Small list changed tile size");
  android.app.Activity home=new android.app.Activity();
  DeviceSettings.prefs=prefs; prefs.value=5;
  LauncherLayout.created(home);
  if(LauncherLayout.horizontal() || LauncherLayout.gridColumns(31,5)!=5)throw new AssertionError("Default layout changed");
  for(int i=0;i<10;i++)LauncherLayout.resumed(home);
  if(home.recreates!=0)throw new AssertionError("Ordinary Home rebuilt");
  prefs.direction="horizontal";LauncherLayout.resumed(home);
  if(home.recreates!=1 || LauncherLayout.gridColumns(31,5)!=31)throw new AssertionError("Horizontal default not applied");
  LauncherLayout.resumed(home);if(home.recreates!=1)throw new AssertionError("Repeated rebuild");
  if(!LauncherLayout.expandHomeApps())throw new AssertionError("Horizontal apps not expanded");
  prefs.allApps=true;if(LauncherLayout.expandHomeApps())throw new AssertionError("All apps button choice ignored");
  prefs.allApps=false;
  prefs.rows=2;LauncherLayout.resumed(home);
  if(home.recreates!=2 || LauncherLayout.gridColumns(31,5)!=16)throw new AssertionError("Row limit not applied");
  prefs.value=8;LauncherLayout.resumed(home);if(home.recreates!=2)throw new AssertionError("Column refresh changed");
  prefs.direction="vertical";LauncherLayout.resumed(home);
  if(home.recreates!=3 || prefs.rows!=2 || prefs.value!=8 || LauncherLayout.gridColumns(31,8)!=8)throw new AssertionError("Vertical restoration lost choices");
  prefs.direction="invalid";if(LauncherLayout.horizontal(prefs))throw new AssertionError("Invalid direction enabled");
  prefs.rows=-1;if(LauncherLayout.maximumRows(prefs)!=1)throw new AssertionError("Invalid row fallback");
  prefs.rows=7;if(LauncherLayout.maximumRows(prefs)!=1)throw new AssertionError("Excess row fallback");
 }
}''')
            subprocess.run(['javac', '-d', temp, str(prefs), str(activity), str(settings), str(check), str(ROOT / 'app/src/com/jeremykenedy/firetv/ui/LauncherLayout.java')], check=True)
            subprocess.run(['java', '-cp', temp, 'com.jeremykenedy.firetv.ui.LauncherLayoutTest'], check=True)
