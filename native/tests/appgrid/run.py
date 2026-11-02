#!/usr/bin/env python3
"""Exercise app ordering and nondestructive discovery persistence without a device."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
stubs = {
    'android/content/Context.java': '''package android.content; public class Context { public static final int MODE_PRIVATE=0; public final SharedPreferences prefs=new SharedPreferences(); public SharedPreferences getSharedPreferences(String n,int m){return prefs;} }''',
    'android/content/SharedPreferences.java': '''package android.content; import java.util.*; public class SharedPreferences {public final Map<String,Object> values=new HashMap<>();public boolean fail;public int commits;public Map<String,?> getAll(){return new HashMap<>(values);}public int getInt(String k,int d){return values.containsKey(k)?(Integer)values.get(k):d;}public boolean getBoolean(String k,boolean d){return values.containsKey(k)?(Boolean)values.get(k):d;}public String getString(String k,String d){return values.containsKey(k)?(String)values.get(k):d;}public Editor edit(){return new Editor();}public class Editor {private final Map<String,Object> edits=new HashMap<>();public Editor putInt(String k,int v){edits.put(k,v);return this;}public Editor putBoolean(String k,boolean v){edits.put(k,v);return this;}public Editor putString(String k,String v){edits.put(k,v);return this;}public Editor remove(String k){edits.put(k,null);return this;}public void apply(){commit();}public boolean commit(){commits++;for(String k:edits.keySet()){if(edits.get(k)==null)values.remove(k);else values.put(k,edits.get(k));}return !fail;}}}''',
}
with tempfile.TemporaryDirectory(prefix='fire-tv-appgrid-') as folder:
    work = Path(folder)
    for name, source in stubs.items():
        path = work / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source)
    subprocess.run(['javac', '-d', work, *[work / name for name in stubs],
                    root / 'src/com/jeremykenedy/firetv/ui/nativeapp/appgrid/AppGridOptions.java',
                    root / 'src/com/jeremykenedy/firetv/ui/nativeapp/appgrid/AppGridPreferences.java',
                    root / 'src/com/jeremykenedy/firetv/ui/nativeapp/appgrid/HomeTileOrder.java',
                    root / 'src/com/jeremykenedy/firetv/ui/nativeapp/appgrid/HomeTilePreferences.java',
                    root / 'src/com/jeremykenedy/firetv/ui/nativeapp/folders/FolderOptions.java',
                    root / 'src/com/jeremykenedy/firetv/ui/nativeapp/inputs/InputEntry.java',
                    root / 'tests/appgrid/HomeTileOrderTest.java',
                    root / 'tests/appgrid/AppGridTest.java', root / 'tests/appgrid/AppGridPreferencesTest.java'], check=True)
    for name in ('AppGridTest', 'AppGridPreferencesTest', 'HomeTileOrderTest'):
        subprocess.run(['java', '-cp', work, 'com.jeremykenedy.firetv.ui.nativeapp.appgrid.' + name], check=True)
