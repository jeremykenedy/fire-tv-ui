#!/usr/bin/env python3
"""Check independent effects and storage preservation on the host."""
from pathlib import Path
import subprocess
import tempfile
ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/'src/com/jeremykenedy/firetv/ui/nativeapp/appearance'
STUBS = {
    'com/jeremykenedy/firetv/ui/nativeapp/setups/PresentationState.java': 'package com.jeremykenedy.firetv.ui.nativeapp.setups; public class PresentationState {}',
    'com/jeremykenedy/firetv/ui/nativeapp/setups/HolidayPresetTransaction.java': 'package com.jeremykenedy.firetv.ui.nativeapp.setups; public class HolidayPresetTransaction { public static void assertWritable(android.content.Context context) {} public static void assertReadable(android.content.Context context) {} }',
    'android/content/Context.java': '''package android.content; public class Context { public static final int MODE_PRIVATE=0; public final SharedPreferences prefs=new SharedPreferences(); public SharedPreferences getSharedPreferences(String n,int m){return prefs;} }''',
    'android/content/SharedPreferences.java': '''package android.content; import java.util.*; public class SharedPreferences {public final Map<String,Object> values=new HashMap<>();public boolean fail;public Map<String,?> getAll(){return new HashMap<>(values);}public int getInt(String k,int d){return values.containsKey(k)?(Integer)values.get(k):d;}public boolean getBoolean(String k,boolean d){return values.containsKey(k)?(Boolean)values.get(k):d;}public Editor edit(){return new Editor();}public class Editor {private final Map<String,Object> edits=new HashMap<>();public Editor putInt(String k,int v){edits.put(k,v);return this;}public Editor putBoolean(String k,boolean v){edits.put(k,v);return this;}public Editor putString(String k,String v){edits.put(k,v);return this;}public Editor remove(String k){edits.put(k,null);return this;}public boolean commit(){for(String k:edits.keySet()){if(edits.get(k)==null)values.remove(k);else values.put(k,edits.get(k));}return !fail;}}}''',
}
with tempfile.TemporaryDirectory(prefix='appearance-test-') as folder:
    work=Path(folder)
    for name,source in STUBS.items():
        path=work/name
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(source)
    subprocess.run(['javac','-d',str(work),*[str(work/name) for name in STUBS],str(ROOT/'src/com/jeremykenedy/firetv/ui/nativeapp/holidays/HolidayScenes.java'),str(SOURCE/'LegacyGradients.java'),str(SOURCE/'AppearanceOptions.java'),str(SOURCE/'AppearancePreferences.java'),str(SOURCE/'PaletteRecipes.java'),str(SOURCE/'AppearanceClock.java'),str(ROOT/'tests/appearance/AppearanceTest.java'),str(ROOT/'tests/appearance/AppearanceClockTest.java')],check=True)
    subprocess.run(['java','-cp',str(work),'com.jeremykenedy.firetv.ui.nativeapp.appearance.AppearanceTest'],check=True)
    subprocess.run(['java','-cp',str(work),'com.jeremykenedy.firetv.ui.nativeapp.appearance.AppearanceClockTest'],check=True)
