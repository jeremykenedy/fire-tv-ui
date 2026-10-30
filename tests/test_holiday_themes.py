from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class HolidayThemeChecks(unittest.TestCase):
    def test_presets_apply_only_the_selected_layer_and_optional_icons(self):
        sources = {
            'android/content/Context.java': 'package android.content; public class Context {}',
            'android/content/SharedPreferences.java': '''package android.content;
public interface SharedPreferences { Editor edit(); interface Editor {
 Editor putString(String k,String v); Editor putBoolean(String k,boolean v);
 Editor putInt(String k,int v); boolean commit(); } }''',
            'com/jeremykenedy/firetv/ui/DeviceSettings.java': '''package com.jeremykenedy.firetv.ui;
import java.util.*; import android.content.*;
class DeviceSettings {
 static final Map<String,Object> values=new HashMap<>(); static boolean fail;
 static SharedPreferences prefs(Context c) { return () -> new SharedPreferences.Editor() {
  final Map<String,Object> pending=new HashMap<>();
  public SharedPreferences.Editor putString(String k,String v){pending.put(k,v);return this;}
  public SharedPreferences.Editor putBoolean(String k,boolean v){pending.put(k,v);return this;}
  public SharedPreferences.Editor putInt(String k,int v){pending.put(k,v);return this;}
  public boolean commit(){if(fail)return false;values.putAll(pending);return true;}
 }; }
}''',
            'com/jeremykenedy/firetv/ui/IconFocus.java': '''package com.jeremykenedy.firetv.ui;
class IconFocus {static final String STYLE="icon_focus_style",BORDER="icon_focus_border";}''',
            'com/jeremykenedy/firetv/ui/HolidayTest.java': '''package com.jeremykenedy.firetv.ui;
import java.util.*; import android.content.*;
public class HolidayTest {
 static void check(boolean v,String message){if(!v)throw new AssertionError(message);}
 public static void main(String[] args){
  Set<String> ids=new HashSet<>(); Context c=new Context();
  for(HolidayThemes.Theme t:HolidayThemes.ALL){
   check(ids.add(t.id),"duplicate recipe");check(HolidayThemes.find(t.id)==t,"identity");
   check(MenuBackgroundOptions.animated(MenuBackgroundOptions.style(t.motif)),"unsupported motif");
   check((t.dark>>>24)==255 && (t.light>>>24)==255 && (t.accent>>>24)==255,"opaque palette colors");
   for(boolean overlay:new boolean[]{false,true})for(boolean animated:new boolean[]{false,true}){
    DeviceSettings.values.clear();DeviceSettings.values.put("custom_wallpaper_uri","content://owner/image");
    DeviceSettings.values.put("display_order","one,two");DeviceSettings.values.put("channel_visibility","owner");
    DeviceSettings.values.put("icon_pack_package","owner.pack");
    DeviceSettings.values.put("menu_animation","aurora");DeviceSettings.values.put("menu_overlay","rain");
    Map<String,Object> before=new HashMap<>(DeviceSettings.values);
    HolidayThemeSettings.apply(c,t.id,overlay,animated,false);
    String style=overlay?MenuBackgroundOptions.OVERLAY:MenuBackgroundOptions.STYLE;
    String palette=overlay?MenuBackgroundOptions.OVERLAY_PALETTE:MenuBackgroundOptions.PALETTE;
    String frozen=overlay?MenuBackgroundOptions.OVERLAY_FROZEN:MenuBackgroundOptions.FROZEN;
    before.put(style,t.motif);before.put(palette,t.id);before.put(frozen,!animated);
    check(before.equals(DeviceSettings.values),"Preset altered unselected settings: "+t.id);
   }
  }
  for(String id:new String[]{"christmas","hanukkah","ramadan","diwali","vaisakhi","vesak","paryushan","naw_ruz","nowruz"})
   check(ids.contains(id),"Missing community preset");
  for(String value:new String[]{"snow","snowflakes","leaves","hearts_falling","hearts_floating"})
   check(MenuBackgroundOptions.animated(MenuBackgroundOptions.style(value)),"Missing requested motif");
  Map<String,Object> before=new HashMap<>(DeviceSettings.values);DeviceSettings.fail=true;
  try{HolidayThemeSettings.apply(c,"christmas",true,true,true);throw new AssertionError("Failure hidden");}
  catch(IllegalStateException expected){}check(before.equals(DeviceSettings.values),"Failed commit changed state");
  DeviceSettings.fail=false;
  try{HolidayThemeSettings.apply(c,"unknown",true,true,true);throw new AssertionError("Invalid preset accepted");}
  catch(IllegalArgumentException expected){}check(before.equals(DeviceSettings.values),"Unknown preset changed state");
  HolidayThemeSettings.apply(c,"hanukkah",true,false,true);
  check(DeviceSettings.values.get("icon_pack_package").equals("builtin:holiday:hanukkah"),"Optional icon palette absent");
  check(DeviceSettings.values.get("icon_focus_style").equals("border"),"Brand artwork focus visibility");
  check(DeviceSettings.values.get("custom_wallpaper_uri").equals("content://owner/image"),"Wallpaper lost");
  System.out.println("All holiday modes preserve other layers, owner content and optional icons; failures leave state intact.");
 }
}'''
        }
        with tempfile.TemporaryDirectory() as folder:
            files = []
            for name, content in sources.items():
                p = Path(folder) / name
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_text(content)
                files.append(str(p))
            package = ROOT / 'app/src/com/jeremykenedy/firetv/ui'
            subprocess.run(['javac', '-d', folder, *files,
                            str(package / 'MenuBackgroundOptions.java'),
                            str(package / 'HolidayThemes.java'),
                            str(package / 'HolidayThemeSettings.java')], check=True)
            subprocess.run(['java', '-cp', folder, 'com.jeremykenedy.firetv.ui.HolidayTest'], check=True)
