#!/usr/bin/env python3
"""Exercise focus values and storage behavior without a device or vendor code."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp/focus'
STUBS = {
    'android/content/Context.java': '''package android.content; public class Context { public static final int MODE_PRIVATE=0; public final SharedPreferences prefs=new SharedPreferences(); public SharedPreferences getSharedPreferences(String n,int m){return prefs;} }''',
    'android/content/SharedPreferences.java': '''package android.content; import java.util.*; public class SharedPreferences {public final Map<String,Object> values=new HashMap<>();public boolean fail;public Map<String,?> getAll(){return new HashMap<>(values);}public int getInt(String k,int d){return values.containsKey(k)?(Integer)values.get(k):d;}public boolean getBoolean(String k,boolean d){return values.containsKey(k)?(Boolean)values.get(k):d;}public Editor edit(){return new Editor();}public class Editor {private final Map<String,Object> edits=new HashMap<>();public Editor putInt(String k,int v){edits.put(k,v);return this;}public Editor putBoolean(String k,boolean v){edits.put(k,v);return this;}public Editor putString(String k,String v){edits.put(k,v);return this;}public Editor putLong(String k,long v){edits.put(k,v);return this;}public Editor putFloat(String k,float v){edits.put(k,v);return this;}public Editor putStringSet(String k,Set<String> v){edits.put(k,v);return this;}public Editor remove(String k){edits.put(k,null);return this;}public boolean commit(){for(String k:edits.keySet()){if(edits.get(k)==null)values.remove(k);else values.put(k,edits.get(k));}return !fail;}}}''',
}
with tempfile.TemporaryDirectory() as folder:
    work = Path(folder)
    for name, source in STUBS.items():
        path = work / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source)
    subprocess.run(['javac', '-d', work, *[work / p for p in STUBS],
                    SOURCE / 'FocusOptions.java', SOURCE / 'FocusPreferences.java',
                    ROOT / 'tests/focus/FocusOptionsTest.java',
                    ROOT / 'tests/focus/FocusPreferencesTest.java'], check=True)
    for test in ('FocusOptionsTest', 'FocusPreferencesTest'):
        subprocess.run(['java', '-cp', work,
                        'com.jeremykenedy.firetv.ui.nativeapp.focus.' + test], check=True)
