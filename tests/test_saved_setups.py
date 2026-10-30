from pathlib import Path
import os
import subprocess
import tempfile
import unittest
from java_runtime import json_runtime

ROOT = Path(__file__).resolve().parents[1]


class SavedSetupChecks(unittest.TestCase):
    def test_roundtrip_images_validation_and_failed_writes(self):
        jar = json_runtime()
        sources = {
            'android/content/SharedPreferences.java': '''package android.content;
import java.util.*;
public interface SharedPreferences { Map<String,?> getAll(); Editor edit();
 default int getInt(String k,int d){Object v=getAll().get(k);return v instanceof Integer?(Integer)v:d;}
 default Set<String> getStringSet(String k,Set<String> d){Object v=getAll().get(k);return v instanceof Set?(Set<String>)v:d;}
 interface Editor { Editor remove(String k); Editor putString(String k,String v); Editor putBoolean(String k,boolean v); Editor putInt(String k,int v); Editor putStringSet(String k,Set<String> v); boolean commit(); }
}''',
            'android/content/ContentResolver.java': '''package android.content;
public class ContentResolver { public java.io.InputStream openInputStream(android.net.Uri uri) throws java.io.IOException { return new java.io.FileInputStream(uri.getPath()); } }''',
            'android/content/Context.java': '''package android.content;
import java.util.*;
public class Context {
 public final java.io.File files; public final Prefs prefs=new Prefs(); public Context(java.io.File f){files=f;files.mkdirs();}
 public java.io.File getFilesDir(){return files;} public ContentResolver getContentResolver(){return new ContentResolver();}
 public static class Prefs implements SharedPreferences {
  public final Map<String,Object> values=new LinkedHashMap<>(); public int failures;
  public Map<String,?> getAll(){return new LinkedHashMap<>(values);}
  public Editor edit(){return new Editor(){final Map<String,Object> edits=new LinkedHashMap<>();
   public Editor remove(String k){edits.put(k,null);return this;}
   public Editor putString(String k,String v){edits.put(k,v);return this;}
   public Editor putBoolean(String k,boolean v){edits.put(k,v);return this;}
   public Editor putInt(String k,int v){edits.put(k,v);return this;}
   public Editor putStringSet(String k,Set<String> v){edits.put(k,new LinkedHashSet<>(v));return this;}
   public boolean commit(){for(Map.Entry<String,Object> e:edits.entrySet()){if(e.getValue()==null)values.remove(e.getKey());else values.put(e.getKey(),e.getValue());}return failures--<=0;}
  };}
 }
}''',
            'android/net/Uri.java': '''package android.net;
public class Uri { final java.net.URI value; private Uri(String s){value=java.net.URI.create(s);} public static Uri parse(String s){return new Uri(s);} public static Uri fromFile(java.io.File f){return new Uri(f.toURI().toString());} public String getPath(){return value.getPath();} public String getScheme(){return value.getScheme();} public String toString(){return value.toString();} }''',
            'android/util/AtomicFile.java': '''package android.util;
public class AtomicFile {
 final java.io.File file,temp; public static boolean failNext;
 public AtomicFile(java.io.File f){file=f;temp=new java.io.File(f+".new");}
 public java.io.FileInputStream openRead() throws java.io.IOException{return new java.io.FileInputStream(file);}
 public java.io.FileOutputStream startWrite() throws java.io.IOException{if(failNext){failNext=false;throw new java.io.IOException("Simulated storage failure");}return new java.io.FileOutputStream(temp);}
 public void finishWrite(java.io.FileOutputStream out) throws java.io.IOException{out.close();java.nio.file.Files.move(temp.toPath(),file.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
 public void failWrite(java.io.FileOutputStream out) throws java.io.IOException{out.close();temp.delete();}
}''',
            'com/jeremykenedy/firetv/ui/DeviceSettings.java': '''package com.jeremykenedy.firetv.ui;
final class DeviceSettings {static android.content.SharedPreferences prefs(android.content.Context c){return c.prefs;}}''',
        }
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp)
            files = []
            for name, content in sources.items():
                target = folder / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(content)
                files.append(str(target))
            subprocess.run(['javac', '-cp', str(jar), '-d', temp, *files,
                            str(ROOT / 'app/src/com/jeremykenedy/firetv/ui/SavedSetupSettings.java'),
                            str(ROOT / 'app/src/com/jeremykenedy/firetv/ui/SavedSetupStore.java'),
                            str(ROOT / 'app/src/com/jeremykenedy/firetv/ui/FeaturedCardTiming.java'),
                            str(ROOT / 'tests/SavedSetupsTest.java')], check=True)
            subprocess.run(['java', '-cp', temp + os.pathsep + str(jar),
                            'com.jeremykenedy.firetv.ui.SavedSetupsTest', temp], check=True)
