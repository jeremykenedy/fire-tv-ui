#!/usr/bin/env python3
"""Run account-free layout and persistence behavior checks."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp/layout'
STUBS = {
    'com/jeremykenedy/firetv/ui/nativeapp/providers/TvRecommendations.java': '''package com.jeremykenedy.firetv.ui.nativeapp.providers;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutItem;
public final class TvRecommendations {
 public static final class Feed {
  public final LayoutItem item;
  public final String title;
  public Feed(String id,String title){item=new LayoutItem(id,LayoutItem.Kind.CHANNEL_CARD);this.title=title;}
 }
}''',
    'android/content/Context.java': '''package android.content;
public class Context {
 public static final int MODE_PRIVATE=0;
 public final SharedPreferences prefs=new SharedPreferences();
 public SharedPreferences getSharedPreferences(String name,int mode){return prefs;}
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
with tempfile.TemporaryDirectory() as folder:
    work = Path(folder)
    for name, source in STUBS.items():
        path = work / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source)
    subprocess.run(['javac', '-d', work, *[work / p for p in STUBS],
                    SOURCE / 'LayoutItem.java', SOURCE / 'LayoutOptions.java', SOURCE / 'HomeSlots.java',
                    SOURCE / 'LayoutPreferences.java', ROOT / 'tests/layout/LayoutTest.java'], check=True)
    subprocess.run(['java', '-cp', work,
                    'com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutTest'], check=True)
