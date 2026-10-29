from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class IconFocusChecks(unittest.TestCase):
    def test_pack_contrast_styles_and_saved_choices(self):
        stubs = {
            'android/content/SharedPreferences.java': '''package android.content;
public interface SharedPreferences {
 String getString(String key,String fallback); int getInt(String key,int fallback); Editor edit();
 interface Editor { Editor putString(String key,String value); Editor putInt(String key,int value); boolean commit(); }
}''',
            'android/content/Context.java': '''package android.content;
public class Context { public Context getApplicationContext(){return this;} }''',
            'android/graphics/Bitmap.java': '''package android.graphics;
public class Bitmap {
 public int color=0xffffffff,generation=1,reads; public boolean clear=true,recycled;
 public boolean isRecycled(){return recycled;} public int getGenerationId(){return generation;}
 public int getWidth(){return 16;} public int getHeight(){return 16;}
 public int getPixel(int x,int y){reads++;return clear&&(x<4||x>11||y<4||y>11)?0:color;}
}''',
            'com/jeremykenedy/firetv/ui/DeviceSettings.java': '''package com.jeremykenedy.firetv.ui;
import android.content.*; import java.util.*;
public class DeviceSettings {
 static final Map<String,Object> values=new HashMap<>();
 static boolean commit=true;
 public static SharedPreferences prefs(Context context){return new SharedPreferences(){
 public String getString(String k,String d){return (String)values.getOrDefault(k,d);}
 public int getInt(String k,int d){return (Integer)values.getOrDefault(k,d);}
 public Editor edit(){return new Editor(){
 Map<String,Object> pending=new HashMap<>();
 public Editor putString(String k,String v){pending.put(k,v);return this;}
 public Editor putInt(String k,int v){pending.put(k,v);return this;}
 public boolean commit(){if(commit)values.putAll(pending);return commit;}
 };}
 };}
}''',
            'com/jeremykenedy/firetv/ui/IconFocusTest.java': '''package com.jeremykenedy.firetv.ui;
import android.content.Context; import android.graphics.Bitmap;
public class IconFocusTest {
 static long pack(int c){return ((long)c)<<32;}
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 public static void main(String[] args){
 Context context=new Context(); IconFocus.configure(context);
 long resting=pack(0xff101820),white=pack(0xffffffff),blue=pack(0xff144071);
 Bitmap glyph=new Bitmap();
 check(IconFocus.background(resting,white,glyph)==white,"No pack keeps the original fill");
 check(!IconFocus.borderEnabled(false),"Default retains the saved outline toggle");
 DeviceSettings.values.put("icon_pack_package","example.pack");
 check(IconFocus.background(resting,white,glyph)==pack(0xff17202f),"White glyph remains visible on focus");
 int reads=glyph.reads;
 IconFocus.background(resting,white,glyph);
 check(glyph.reads==reads,"Unchanged artwork is sampled once");
 check(IconFocus.background(resting,blue,glyph)==blue,"A contrasting saved focus color remains unchanged");
 glyph.clear=false;glyph.generation++;
 check(IconFocus.background(resting,white,glyph)==white,"Full artwork keeps its original fill");
 glyph.clear=true;glyph.color=0xff000000;glyph.generation++;
 check(IconFocus.background(resting,resting,glyph)==pack(0xfff4f4f4),"Dark glyph keeps contrast too");
 check(glyph.color==0xff000000,"Artwork is never recolored");
 IconFocus.setStyle(context,"border");
 check(IconFocus.style(context).equals("border"),"Style saved");
 check(IconFocus.background(resting,white,glyph)==resting,"Border only retains the resting background");
 check(IconFocus.borderEnabled(false),"Border style remains visible with original border off");
 IconFocus.setColor(context,IconFocus.BORDER,0x00aadeff);
 check(IconFocus.border(white)==pack(0xffaadeff),"Saved border color reaches renderer");
 IconFocus.setColor(context,IconFocus.BACKGROUND,0xff275d78);
 IconFocus.setStyle(context,"fill");
 check(IconFocus.background(resting,white,null)==pack(0xff275d78),"Saved fill color reaches renderer");
 IconFocus.setStyle(context,"default");
 check(IconFocus.border(white)==white,"Default restores the original border");
 check(IconFocus.background(resting,blue,null)==blue,"Default restores the original fill");
 check(!DeviceSettings.values.containsKey("white_border"),"Original settings are preserved");
 try{IconFocus.setStyle(context,"invisible");throw new AssertionError("Invalid style saved");}catch(IllegalArgumentException expected){}
 try{IconFocus.setColor(context,"mode",0);throw new AssertionError("Unrelated preference changed");}catch(IllegalArgumentException expected){}
 DeviceSettings.commit=false;
 try{IconFocus.setStyle(context,"border");throw new AssertionError("Failed save reported success");}catch(IllegalStateException expected){}
 System.out.println("Icon focus choices and artwork contrast passed");
 }
}''',
        }
        with tempfile.TemporaryDirectory() as folder:
            files = []
            for name, content in stubs.items():
                path = Path(folder) / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content)
                files.append(str(path))
            subprocess.run(['javac', '-d', folder, *files,
                            str(ROOT / 'app/src/com/jeremykenedy/firetv/ui/IconFocus.java')], check=True)
            subprocess.run(['java', '-cp', folder,
                            'com.jeremykenedy.firetv.ui.IconFocusTest'], check=True)
