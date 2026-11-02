#!/usr/bin/env python3
"""Run isolated radar policy, transport and cache checks without a network or device."""
from pathlib import Path
import subprocess
import tempfile
ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp/widget/radar'
STUBS = {
    'android/content/Context.java': '''package android.content; public class Context { public static final int MODE_PRIVATE=0; public final SharedPreferences prefs=new SharedPreferences(); public SharedPreferences getSharedPreferences(String n,int m){return prefs;} }''',
    'android/content/SharedPreferences.java': '''package android.content; import java.util.*; public class SharedPreferences {public final Map<String,Object> values=new HashMap<>();public boolean fail;public Map<String,?> getAll(){return new HashMap<>(values);}public int getInt(String k,int d){return values.containsKey(k)?(Integer)values.get(k):d;}public boolean getBoolean(String k,boolean d){return values.containsKey(k)?(Boolean)values.get(k):d;}public Editor edit(){return new Editor();}public class Editor {private final Map<String,Object> edits=new HashMap<>();public Editor putInt(String k,int v){edits.put(k,v);return this;}public Editor putBoolean(String k,boolean v){edits.put(k,v);return this;}public Editor putString(String k,String v){edits.put(k,v);return this;}public Editor putLong(String k,long v){edits.put(k,v);return this;}public Editor putFloat(String k,float v){edits.put(k,v);return this;}public Editor putStringSet(String k,java.util.Set<String> v){edits.put(k,v);return this;}public Editor remove(String k){edits.put(k,null);return this;}public boolean commit(){for(String k:edits.keySet()){if(edits.get(k)==null)values.remove(k);else values.put(k,edits.get(k));}return !fail;}}}''',
}
with tempfile.TemporaryDirectory(prefix='radar-model-') as work:
    root = Path(work)
    for name, content in STUBS.items():
        path = root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
    files = [SOURCE / (name + '.java') for name in ('RadarSource', 'RadarCache', 'RadarSnapshot', 'RadarRequest', 'RadarHttp', 'RadarRepository')]
    subprocess.run(['javac', '-d', work, *map(str, files), *[str(root / name) for name in STUBS], str(SOURCE / 'RadarPreferences.java'), str(ROOT / 'tests/radar/RadarTest.java'), str(ROOT / 'tests/radar/RadarPreferencesTest.java')], check=True)
    subprocess.run(['java', '-cp', work, 'com.jeremykenedy.firetv.ui.nativeapp.widget.radar.RadarTest'], check=True)
    subprocess.run(['java', '-cp', work, 'com.jeremykenedy.firetv.ui.nativeapp.widget.radar.RadarPreferencesTest'], check=True)
