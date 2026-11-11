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
 java.util.Map<String,?> getAll(); String getString(String key,String fallback); int getInt(String key,int fallback); Editor edit();
 interface Editor { Editor putString(String key,String value); Editor putInt(String key,int value); Editor putBoolean(String key,boolean value); Editor remove(String key); boolean commit(); }
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
 public Map<String,?> getAll(){return new HashMap<>(values);}
 public String getString(String k,String d){return (String)values.getOrDefault(k,d);}
 public int getInt(String k,int d){return (Integer)values.getOrDefault(k,d);}
 public Editor edit(){return new Editor(){
 Map<String,Object> pending=new HashMap<>();
 public Editor putString(String k,String v){pending.put(k,v);return this;}
 public Editor putInt(String k,int v){pending.put(k,v);return this;}
 public Editor putBoolean(String k,boolean v){pending.put(k,v);return this;}
 public Editor remove(String k){pending.put(k,null);return this;}
 public boolean commit(){for(Map.Entry<String,Object> e:pending.entrySet()){if(e.getValue()==null)values.remove(e.getKey());else values.put(e.getKey(),e.getValue());}return commit;}
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
 DeviceSettings.commit=true;
 check(Math.abs(IconFocus.scale(1f)-1.18f)<0.00001f,"Existing18percent growth preserved");
 check(IconFocus.shadowSize(18f)==18f,"Existing18dp shadow preserved");
 DeviceSettings.values.put("icon_radius",12);DeviceSettings.values.put("white_border",false);
 java.util.Map<String,Object> original=new java.util.HashMap<>(DeviceSettings.values);
 for(int percent:new int[]{0,6,12,24,30}){
  IconFocus.setGrowth(context,percent);
  check(IconFocus.growth(context)==percent,"Selected growth saved");
  check(Math.abs(IconFocus.scale(1f)-(1f+percent/100f))<0.00001f,"Selected growth reaches renderer");
  check(IconFocus.scale(0f)==1f,"Resting artwork size remains fixed");
 }
 check(IconFocus.consumeSizeRefresh(),"Successful saves request immediate redraw");
 check(!IconFocus.consumeSizeRefresh(),"Refresh consumed once");
 IconFocus.setGrowth(context,null);
 check(!DeviceSettings.values.containsKey(IconFocus.GROWTH)&&Math.abs(IconFocus.scale(1f)-1.18f)<0.00001f,"Default removes override");
 for(int size:new int[]{18,28,38}){
  IconFocus.setShadow(context,size);
  check(IconFocus.shadow(context)==size&&IconFocus.shadowSize(18f)==size,"Selected shadow reaches renderer");
 }
 IconFocus.setShadow(context,0);
 check(IconFocus.shadow(context)==0&&IconFocus.shadowSize(18f)==0,"Off disables shadow");
 check(DeviceSettings.values.get(IconFocus.SHADOW_SIZE).equals(38),"Off retains previous size");
 DeviceSettings.values.put("icon_shadow",true);
 check(IconFocus.shadowSize(18f)==38,"Original toggle restores selected size");
 DeviceSettings.values.put("icon_shadow",false);
 check(IconFocus.shadowSize(18f)==0,"Original Off remains authoritative");
 IconFocus.setShadow(context,18);
 check(!DeviceSettings.values.containsKey(IconFocus.SHADOW_SIZE),"Default removes size override");
 for(String k:original.keySet()) if(!k.equals("icon_shadow"))check(original.get(k).equals(DeviceSettings.values.get(k)),"Other choices unchanged:"+k);
 for(int bad:new int[]{-1,31})try{IconFocus.setGrowth(context,bad);throw new AssertionError("Unbounded growth");}catch(IllegalArgumentException expected){}
 try{IconFocus.setShadow(context,39);throw new AssertionError("Unbounded shadow");}catch(IllegalArgumentException expected){}
 java.util.Map<String,Object> before=new java.util.HashMap<>(DeviceSettings.values);
 DeviceSettings.commit=false;
 try{IconFocus.setGrowth(context,30);throw new AssertionError("Failed growth save accepted");}catch(IllegalStateException expected){}
 check(before.equals(DeviceSettings.values),"Failed growth save restored in-memory values");
 try{IconFocus.setShadow(context,38);throw new AssertionError("Failed shadow save accepted");}catch(IllegalStateException expected){}
 check(before.equals(DeviceSettings.values),"Failed shadow save restored both values");
 DeviceSettings.commit=true;DeviceSettings.values.put(IconFocus.GROWTH,"broken");
 before=new java.util.HashMap<>(DeviceSettings.values);
 try{IconFocus.setGrowth(context,30);throw new AssertionError("Malformed original overwritten");}catch(IllegalStateException expected){}
 check(before.equals(DeviceSettings.values),"Malformed saved value preserved");
 System.out.println("Icon focus colors, growth, shadow, defaults and failed saves passed");
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
