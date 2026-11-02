from pathlib import Path
import hashlib
import json
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = ROOT / 'native/src/com/jeremykenedy/firetv/ui/nativeapp/holidays'


class HolidaySceneChecks(unittest.TestCase):
    def test_selection_preserves_wallpaper_custom_effects_and_unselected_layer(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            stub = root / 'android/content/SharedPreferences.java'
            stub.parent.mkdir(parents=True)
            stub.write_text('''package android.content; import java.util.Map;
public interface SharedPreferences { Map<String,?> getAll(); Editor edit();
 interface Editor { Editor putString(String key,String value); Editor remove(String key); boolean commit(); }}''')
            test = root / 'HolidayTest.java'
            test.write_text('''import android.content.SharedPreferences; import java.util.*;
import com.jeremykenedy.firetv.ui.nativeapp.holidays.*;
public class HolidayTest {
 static class Preferences implements SharedPreferences {
  Map<String,Object> values=new HashMap<>(); boolean fail;
  public Map<String,?> getAll(){return new HashMap<>(values);}
  public Editor edit(){return new Editor(){Map<String,Object> pending=new HashMap<>();
   public Editor putString(String k,String v){pending.put(k,v);return this;}
   public Editor remove(String k){pending.put(k,null);return this;}
   public boolean commit(){for(String k:pending.keySet())if(pending.get(k)==null)values.remove(k);else values.put(k,pending.get(k));
    boolean result=!fail;fail=false;return result;}
  };}
 }
 static void check(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args){
  Preferences p=new Preferences(); Set<String> ids=new HashSet<>();
  for(HolidayScenes.Scene scene:HolidayScenes.ALL){check(ids.add(scene.id));check(HolidayScenes.find(scene.id)==scene);
   for(HolidaySelection.Mode mode:HolidaySelection.Mode.values()){
    p.values.clear();p.values.put("wallpaper_image_uri","content://owner/image");p.values.put("wallpaper_video_uri","content://owner/video");
    p.values.put("scene","previous");p.values.put("base","aurora");p.values.put("overlay","hearts_floating");
    p.values.put("overlay_opacity",12);p.values.put("overlay_speed",7);p.values.put("overlay_intensity",3);p.values.put("overlay_frozen",true);
    Map<String,Object> expected=new HashMap<>(p.values);
    if(mode!=HolidaySelection.Mode.OVERLAY)expected.put("scene",scene.id);
    if(mode!=HolidaySelection.Mode.SCENE)expected.put("overlay",scene.overlay);
    HolidaySelection.apply(p,scene.id,mode);check(expected.equals(p.values));
   }
  }
  check(HolidayScenes.find("unknown")==null);Map<String,Object> before=new HashMap<>(p.values);
  try{HolidaySelection.apply(p,"unknown",HolidaySelection.Mode.SCENE);throw new AssertionError();}catch(IllegalArgumentException expected){}
  check(before.equals(p.values));
  try{HolidaySelection.apply(p,"halloween",null);throw new AssertionError();}catch(IllegalArgumentException expected){}
  check(before.equals(p.values));
  p.fail=true;try{HolidaySelection.apply(p,"halloween",HolidaySelection.Mode.SCENE_AND_OVERLAY);throw new AssertionError();}catch(IllegalStateException expected){}
  check(before.equals(p.values));
  p.values.remove("scene");p.values.remove("overlay");before=new HashMap<>(p.values);
  p.fail=true;try{HolidaySelection.apply(p,"christmas",HolidaySelection.Mode.SCENE_AND_OVERLAY);throw new AssertionError();}catch(IllegalStateException expected){}
  check(before.equals(p.values));System.out.println("Holiday scene layer selection and preservation checks passed");
 }
}''')
            subprocess.run(['javac', '-d', folder, str(stub), str(test),
                            str(PACKAGE / 'HolidayScenes.java'), str(PACKAGE / 'HolidaySelection.java')], check=True)
            subprocess.run(['java', '-cp', folder, 'HolidayTest'], check=True)

    def test_animation_cycle_speed_and_delayed_frames_remain_bounded(self):
        with tempfile.TemporaryDirectory() as folder:
            test = Path(folder) / 'AnimationTest.java'
            test.write_text('''import com.jeremykenedy.firetv.ui.nativeapp.holidays.HolidayAnimation;
import java.util.*;
public class AnimationTest {
 static void check(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args){
  for(int speed:new int[]{0,33,50,100}){
   HolidayAnimation timeline=new HolidayAnimation();timeline.setSpeed(speed);
   check(timeline.opacity()==0);Set<Integer> frames=new HashSet<>();boolean visible=false,looped=false;
   float previous=0;
   for(int i=0;i<1700;i++){
    timeline.advance(33);float progress=timeline.progress();
    check(progress>=0 && progress<1);check(timeline.opacity()>=0 && timeline.opacity()<=255);
    check(timeline.flicker()>=.43f && timeline.flicker()<=1.01f);
    frames.add(timeline.frame());visible|=timeline.opacity()==255;looped|=progress<previous;previous=progress;
   }
   check(frames.size()==6 && visible && looped);
   float progress=timeline.progress();timeline.setSpeed(100-speed);check(Math.abs(progress-timeline.progress())<.0001f);
   progress=timeline.progress();timeline.advance(-1000);check(progress==timeline.progress());
   timeline.reset();timeline.advance(Long.MAX_VALUE);check(timeline.progress()<=100f/15000);
   timeline.reset();check(timeline.progress()==0 && timeline.frame()==0 && timeline.opacity()==0);
  }
  HolidayAnimation slow=new HolidayAnimation(),fast=new HolidayAnimation();slow.setSpeed(0);fast.setSpeed(100);
  for(int i=0;i<30;i++){slow.advance(33);fast.advance(33);}check(fast.progress()>slow.progress());
  System.out.println("Holiday animation frame cycle, speed, fades and delayed-frame bounds passed");
 }
}''')
            subprocess.run(['javac', '-d', folder, str(test), str(PACKAGE / 'HolidayAnimation.java')], check=True)
            subprocess.run(['java', '-cp', folder, 'AnimationTest'], check=True)

    def test_snowfall_depth_speed_reset_and_delayed_frames(self):
        with tempfile.TemporaryDirectory() as folder:
            test = Path(folder) / 'SnowfallTest.java'
            test.write_text('''import com.jeremykenedy.firetv.ui.nativeapp.holidays.SceneSnowfall;
public class SnowfallTest {
 static void check(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args){
  SceneSnowfall slow=new SceneSnowfall(),fast=new SceneSnowfall();
  check(SceneSnowfall.COUNT==64);int[] layers=new int[3];
  for(int i=0;i<SceneSnowfall.COUNT;i++){
   check(slow.x(i)==fast.x(i) && slow.y(i)==fast.y(i));layers[slow.depth(i)]++;
   check(slow.radius(i)>0 && slow.radius(i)<7);check(slow.angle(i)>=0 && slow.angle(i)<360);
  }
  check(layers[0]==28 && layers[1]==24 && layers[2]==12);
  float initial=slow.y(0);slow.advance(100,0);fast.advance(100,100);
  check(fast.y(0)-initial>slow.y(0)-initial);
  slow.reset();fast.reset();slow.advance(Long.MAX_VALUE,50);fast.advance(100,50);
  for(int i=0;i<SceneSnowfall.COUNT;i++)check(slow.x(i)==fast.x(i) && slow.y(i)==fast.y(i));
  for(int step=0;step<9000;step++)fast.advance(100,100);
  for(int i=0;i<SceneSnowfall.COUNT;i++){
   check(fast.x(i)>=0 && fast.x(i)<1);check(fast.y(i)>=0 && fast.y(i)<1);
   check(fast.angle(i)>=0 && fast.angle(i)<360);
  }
  fast.reset();slow.reset();slow.advance(-100,50);
  for(int i=0;i<SceneSnowfall.COUNT;i++)check(slow.x(i)==fast.x(i) && slow.y(i)==fast.y(i));
  System.out.println("Scene snowfall depth, speed, determinism and bounds passed");
 }
}''')
            subprocess.run(['javac', '-d', folder, str(test), str(PACKAGE / 'SceneSnowfall.java')], check=True)
            subprocess.run(['java', '-cp', folder, 'SnowfallTest'], check=True)

    def test_winter_mist_is_bounded_continuous_and_has_a_distinct_snow_field(self):
        with tempfile.TemporaryDirectory() as folder:
            test = Path(folder) / 'WinterTest.java'
            test.write_text('''import com.jeremykenedy.firetv.ui.nativeapp.holidays.*;
public class WinterTest {
 static void check(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args){
  SceneSnowfall christmas=new SceneSnowfall(),winter=new SceneSnowfall(0x57494e544552L);
  boolean different=false;for(int i=0;i<SceneSnowfall.COUNT;i++)different|=christmas.x(i)!=winter.x(i);
  check(different);WinterMist mist=new WinterMist(),other=new WinterMist();
  mist.advance(Long.MAX_VALUE,50);other.advance(100,50);
  for(int band=0;band<3;band++)check(mist.offsetX(band)==other.offsetX(band));
  mist.reset();float initial=mist.offsetX(0);mist.advance(-100,50);check(mist.offsetX(0)==initial);
  for(int step=0;step<20000;step++){
   float previous=mist.offsetX(0);mist.advance(100,100);
   check(Math.abs(previous-mist.offsetX(0))<.001f);
   for(int band=0;band<3;band++){
    check(Math.abs(mist.offsetX(band))<=.0141f);check(Math.abs(mist.offsetY(band))<=.0031f);
    check(mist.opacity(band)>=14 && mist.opacity(band)<=28);
   }
  }
  mist.reset();other.reset();mist.advance(100,0);other.advance(100,100);
  check(other.offsetX(0)>mist.offsetX(0));
  System.out.println("Winter atmosphere bounds, seamless motion and distinct snow initialization passed");
 }
}''')
            subprocess.run(['javac', '-d', folder, str(test), str(PACKAGE / 'WinterMist.java'),
                            str(PACKAGE / 'SceneSnowfall.java')], check=True)
            subprocess.run(['java', '-cp', folder, 'WinterTest'], check=True)

    def test_packaged_scene_bytes_match_provenance(self):
        manifest = json.loads((ROOT / 'native/holiday-art/assets.json').read_text())
        self.assertEqual({'halloween', 'christmas', 'winter', 'halloween_clean', 'halloween_walk', 'hanukkah', 'diwali', 'eid_fitr', 'lunar_new_year', 'valentine', 'st_patrick', 'easter', 'thanksgiving', 'passover', 'vesak', 'juneteenth', 'ramadan'}, {a['id'] for a in manifest['assets']})
        for asset in manifest['assets']:
            data = (ROOT / asset['path']).read_bytes()
            self.assertEqual(asset['bytes'], len(data))
            self.assertEqual(asset['sha256'], hashlib.sha256(data).hexdigest())
            self.assertEqual(b'\x89PNG\r\n\x1a\n', data[:8])
            self.assertEqual(asset['width'], int.from_bytes(data[16:20], 'big'))
            self.assertEqual(asset['height'], int.from_bytes(data[20:24], 'big'))
            if asset.get('alpha'):
                self.assertEqual(6, data[25])
                self.assertEqual(6, asset['columns'] * asset['rows'])
        coverage = (ROOT / 'native/holiday-art/COVERAGE.md').read_text()
        self.assertEqual(41, sum(line.startswith('| ') for line in coverage.splitlines()) - 2)


if __name__ == '__main__':
    unittest.main()
