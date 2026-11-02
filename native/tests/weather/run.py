#!/usr/bin/env python3
"""Check NWS behavior against local fixtures; make no live weather requests."""
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT.parent / 'tests'))
from java_runtime import json_runtime
SOURCE = ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp/widget/weather'
ANDROID = Path.home() / 'Library/Android/sdk/platforms/android-36/android.jar'
STUBS = {
    'android/content/Context.java': '''package android.content;
public class Context {
 public static final int MODE_PRIVATE=0;
 public final SharedPreferences prefs=new SharedPreferences();
 public final SharedPreferences legacy=new SharedPreferences();
 public SharedPreferences getSharedPreferences(String name,int mode){return name.equals("launcher_prefs") ? legacy : prefs;}
}''',
    'android/content/SharedPreferences.java': '''package android.content;
import java.util.*;
public class SharedPreferences {
 public final Map<String,Object> values=new HashMap<>();
 public boolean fail;
 public Map<String,?> getAll(){return new HashMap<>(values);}
 public Editor edit(){return new Editor();}
 public class Editor {
  private final Map<String,Object> edits=new HashMap<>();
  public Editor putInt(String key,int value){edits.put(key,value);return this;}
  public Editor putString(String key,String value){edits.put(key,value);return this;}
  public Editor putBoolean(String key,boolean value){edits.put(key,value);return this;}
  public Editor putLong(String key,long value){edits.put(key,value);return this;}
  public Editor putFloat(String key,float value){edits.put(key,value);return this;}
  public Editor putStringSet(String key,Set<String> value){edits.put(key,new HashSet<>(value));return this;}
  public Editor remove(String key){edits.put(key,null);return this;}
  public boolean commit(){
   for(String key:edits.keySet()){
    if(edits.get(key)==null)values.remove(key);else values.put(key,edits.get(key));
   }
   return !fail;
  }
 }
}''',
}
PURE = ['WeatherOptions', 'WeatherRequest', 'WeatherCachePolicy', 'WeatherCache', 'WeatherHttp', 'WeatherForecast', 'WeatherRepository', 'WeatherPlaces']
with tempfile.TemporaryDirectory(prefix='weather-tests-') as directory:
    work = Path(directory)
    runtime = str(json_runtime())
    for name, contents in STUBS.items():
        target = work / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(contents)
    subprocess.run(['javac', '-cp', runtime, '-d', str(work),
                    *[str(SOURCE / (name + '.java')) for name in PURE],
                    str(Path(__file__).parent / 'WeatherFixture.java'),
                    str(Path(__file__).parent / 'WeatherTest.java'),
                    str(Path(__file__).parent / 'WeatherPlacesTest.java')], check=True)
    subprocess.run(['java', '-cp', str(work) + ':' + runtime,
                    'com.jeremykenedy.firetv.ui.nativeapp.widget.weather.WeatherTest'], check=True)
    subprocess.run(['java', '-cp', str(work) + ':' + runtime,
                    'com.jeremykenedy.firetv.ui.nativeapp.widget.weather.WeatherPlacesTest'], check=True)
    subprocess.run(['javac', '-cp', str(work), '-d', str(work), *[str(work / name) for name in STUBS],
                    str(SOURCE / 'WeatherPreferences.java'), str(Path(__file__).parent / 'WeatherPreferencesTest.java')], check=True)
    subprocess.run(['java', '-cp', str(work), 'com.jeremykenedy.firetv.ui.nativeapp.widget.weather.WeatherPreferencesTest'], check=True)
    subprocess.run(['javac', '-source', '8', '-target', '8', '-Xlint:-options',
                    '-bootclasspath', str(ANDROID), '-classpath', str(ROOT / '.build/classes'), '-sourcepath', str(ROOT / 'src') + ':' + str(ROOT / '.build/gen'),
                    '-d', str(work / 'integration'), *map(str, SOURCE.glob('*.java')),
                    str(Path(__file__).parent / 'WeatherFixture.java')], check=True)
    print('Weather widget/settings isolated API36 compilation passed.')
