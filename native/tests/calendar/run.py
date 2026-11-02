#!/usr/bin/env python3
"""Verify calendar selection, temporal rules, privacy and strict local settings."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp'
STUBS = {
    'android/content/Context.java': '''package android.content;
public class Context { public static final int MODE_PRIVATE=0;
public final SharedPreferences prefs=new SharedPreferences();
public SharedPreferences getSharedPreferences(String name,int mode){return prefs;} }''',
    'android/content/SharedPreferences.java': '''package android.content;
import java.util.*;
public class SharedPreferences {
public final Map<String,Object> values=new HashMap<>(); public boolean failNext;
public Map<String,?> getAll(){return new HashMap<>(values);} public Editor edit(){return new Editor();}
public class Editor { private boolean clear; private final Map<String,Object> edits=new HashMap<>();
public Editor clear(){clear=true;return this;} public Editor putString(String key,String value){edits.put(key,value);return this;}
public boolean commit(){if(clear)values.clear();values.putAll(edits); if(failNext){failNext=false;return false;}return true;} }
}''',
}
with tempfile.TemporaryDirectory() as folder:
    work = Path(folder)
    for name, contents in STUBS.items():
        path = work / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(contents)
    subprocess.run(['javac', '-d', work, *[work / name for name in STUBS],
                    SOURCE / 'migration/LegacyJson.java', SOURCE / 'widget/calendar/CalendarOptions.java',
                    SOURCE / 'widget/calendar/CalendarPreferences.java', SOURCE / 'widget/calendar/CalendarData.java',
                    ROOT / 'tests/calendar/CalendarTest.java'], check=True)
    subprocess.run(['java', '-cp', work, 'com.jeremykenedy.firetv.ui.nativeapp.widget.calendar.CalendarTest'], check=True)
