#!/usr/bin/env python3
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
src = root / 'src/com/jeremykenedy/firetv/ui/nativeapp/statusbar'
with tempfile.TemporaryDirectory(prefix='statusbar-proof-') as temp:
    temp = Path(temp)
    context = temp / 'android/content/Context.java'
    context.parent.mkdir(parents=True)
    context.write_text('''package android.content; public class Context { public static final int MODE_PRIVATE=0; public final SharedPreferences values=new SharedPreferences(); public SharedPreferences getSharedPreferences(String name,int mode){return values;} }''')
    prefs = temp / 'android/content/SharedPreferences.java'
    prefs.write_text('''package android.content; public class SharedPreferences { public final java.util.Map<String,Object> values=new java.util.HashMap<>(); public boolean contains(String key){return values.containsKey(key);} public boolean getBoolean(String key,boolean fallback){return values.containsKey(key) ? (Boolean)values.get(key) : fallback;} public String getString(String key,String fallback){return values.containsKey(key) ? (String)values.get(key) : fallback;} }''')
    subprocess.run(['javac', '-d', temp, context, prefs, src/'StatusBarOptions.java', root/'tests/statusbar/StatusBarOptionsTest.java'], check=True)
    subprocess.run(['java', '-cp', temp, 'com.jeremykenedy.firetv.ui.nativeapp.statusbar.StatusBarOptionsTest'], check=True)
