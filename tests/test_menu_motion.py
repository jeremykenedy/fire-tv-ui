from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class MenuMotionChecks(unittest.TestCase):
    def test_actual_motion_policy_preserves_defaults_and_handles_animation_fallbacks(self):
        stubs = {
            'android/animation/ValueAnimator.java': '''package android.animation;
public class ValueAnimator {
 public static boolean enabled=true;
 public static int queries;
 public static boolean areAnimatorsEnabled(){queries++;return enabled;}
}''',
            'android/content/SharedPreferences.java': '''package android.content;
public class SharedPreferences {
 public final java.util.Map<String,Object> values=new java.util.HashMap<>();
 public boolean commitResult=true;
 public int commits;
 public java.util.Map<String,?> getAll(){return values;}
 public Editor edit(){return new Editor();}
 public class Editor {
  private final java.util.Map<String,Object> changed=new java.util.HashMap<>(values);
  public Editor remove(String key){changed.remove(key);return this;}
  public Editor putString(String key,String value){changed.put(key,value);return this;}
  public boolean commit(){commits++;values.clear();values.putAll(changed);return commitResult;}
 }
}''',
            'android/content/Context.java': '''package android.content;
public class Context {
 public final SharedPreferences preferences=new SharedPreferences();
 public String getPackageName(){return "com.jeremykenedy.firetv.ui";}
}''',
            'android/content/DialogInterface.java': '''package android.content;
public interface DialogInterface {
 void dismiss();
 interface OnClickListener {void onClick(DialogInterface dialog,int index);}
}''',
            'android/content/res/Resources.java': '''package android.content.res;
public class Resources {
 public final java.util.Map<String,Integer> ids=new java.util.HashMap<>();
 public final java.util.List<String> queries=new java.util.ArrayList<>();
 public int getIdentifier(String name,String type,String pkg){
  queries.add(name+":"+type+":"+pkg);
  if(!"anim".equals(type)||!"com.jeremykenedy.firetv.ui".equals(pkg))return 0;
  Integer value=ids.get(name);return value==null?0:value;
 }
}''',
            'android/app/Activity.java': '''package android.app;
public class Activity extends android.content.Context {
 public final android.content.res.Resources resources=new android.content.res.Resources();
 public int overrides,enter=-1,exit=-1;
 public android.content.res.Resources getResources(){return resources;}
 public void overridePendingTransition(int enter,int exit){overrides++;this.enter=enter;this.exit=exit;}
}''',
            'android/app/AlertDialog.java': '''package android.app;
import android.content.DialogInterface;
public class AlertDialog implements DialogInterface {
 public boolean dismissed;
 public void dismiss(){dismissed=true;}
 public static class Builder {
  public static Builder last;
  public String title;
  public int selected;
  public DialogInterface.OnClickListener choice;
  public Builder(android.content.Context context){last=this;}
  public Builder setTitle(String value){title=value;return this;}
  public Builder setMessage(String value){return this;}
  public Builder setSingleChoiceItems(String[] labels,int selected,DialogInterface.OnClickListener listener){this.selected=selected;choice=listener;return this;}
  public Builder setNegativeButton(String label,DialogInterface.OnClickListener listener){return this;}
  public Builder setPositiveButton(String label,DialogInterface.OnClickListener listener){return this;}
  public AlertDialog show(){return new AlertDialog();}
 }
}''',
            'com/jeremykenedy/firetv/ui/DeviceSettings.java': '''package com.jeremykenedy.firetv.ui;
final class DeviceSettings {
 static android.content.SharedPreferences prefs(android.content.Context context){return context.preferences;}
}''',
        }
        check = '''package com.jeremykenedy.firetv.ui;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;

public final class MenuMotionTest {
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 private static Activity activity(Object style){
  Activity result=new Activity();
  if(style!=null)result.preferences.values.put(MenuMotionOptions.SETTING,style);
  String[] names={"menu_fade_in","menu_fade_out","menu_slide_in_right","menu_slide_out_left","menu_slide_in_left","menu_slide_out_right"};
  for(int i=0;i<names.length;i++)result.resources.ids.put(names[i],100+i);
  return result;
 }
 private static void expect(Activity activity,int enter,int exit){
  check(activity.overrides==1&&activity.enter==enter&&activity.exit==exit,"Wrong animation resources: "+activity.enter+"/"+activity.exit);
 }
 private static void resourcePair(Activity activity,String enter,String exit){
  check(activity.resources.queries.equals(java.util.Arrays.asList(enter+":anim:com.jeremykenedy.firetv.ui",exit+":anim:com.jeremykenedy.firetv.ui")),"Wrong resource lookup: "+activity.resources.queries);
 }
 public static void main(String[] args){
  for(boolean enabled:new boolean[]{true,false}){
   ValueAnimator.enabled=enabled;
   for(Object style:new Object[]{null,"default","unknown",17,true,java.util.Collections.emptyList()}){
    for(boolean opening:new boolean[]{true,false}){
     Activity target=activity(style);ValueAnimator.queries=0;
     MenuMotion.apply(target,opening);
     check(target.overrides==0&&target.resources.queries.isEmpty()&&ValueAnimator.queries==0,"Default or invalid preference overrode existing motion");
     check("Default".equals(MenuMotion.label(target)),"Invalid preference label is not safe");
     check(target.preferences.commits==0,"Reading motion wrote preferences");
    }
   }
   for(boolean opening:new boolean[]{true,false}){
    Activity target=activity("none");MenuMotion.apply(target,opening);expect(target,0,0);
    check(target.resources.queries.isEmpty(),"None loaded animation resources");
   }
  }
  ValueAnimator.enabled=true;
  for(boolean opening:new boolean[]{true,false}){
   Activity fade=activity("fade");MenuMotion.apply(fade,opening);expect(fade,100,101);resourcePair(fade,"menu_fade_in","menu_fade_out");
   Activity slide=activity("slide");MenuMotion.apply(slide,opening);
   expect(slide,opening?102:104,opening?103:105);
   resourcePair(slide,opening?"menu_slide_in_right":"menu_slide_in_left",opening?"menu_slide_out_left":"menu_slide_out_right");
  }
  ValueAnimator.enabled=false;
  for(String style:new String[]{"fade","slide"}){
   for(boolean opening:new boolean[]{true,false}){
    Activity target=activity(style);target.resources.ids.clear();MenuMotion.apply(target,opening);expect(target,0,0);
    check(target.resources.queries.isEmpty(),"Disabled system animators still requested resources");
   }
  }
  ValueAnimator.enabled=true;
  for(String style:new String[]{"fade","slide"}){
   for(boolean opening:new boolean[]{true,false}){
    String[] names=MenuMotionOptions.animations(style,opening);
    for(int missing=0;missing<3;missing++){
     Activity target=activity(style);
     if(missing!=1)target.resources.ids.remove(names[0]);
     if(missing!=0)target.resources.ids.remove(names[1]);
     MenuMotion.apply(target,opening);
     check(target.overrides==0,"Partially missing resources suppressed the existing transition");
     resourcePair(target,names[0],names[1]);
    }
   }
  }
  for(int i=0;i<MenuMotionOptions.VALUES.length;i++){
   check(MenuMotionOptions.valid(MenuMotionOptions.VALUES[i])&&MenuMotionOptions.index(MenuMotionOptions.VALUES[i])==i,"Option lookup mismatch");
   check(MenuMotionOptions.LABELS[i].equals(MenuMotion.label(activity(MenuMotionOptions.VALUES[i]))),"Option label mismatch");
  }
  for(Object value:new Object[]{null,2,true,"future"})check(!MenuMotionOptions.valid(value),"Invalid motion option accepted");
  for(boolean opening:new boolean[]{true,false})check(java.util.Arrays.equals(new String[]{"",""},MenuMotionOptions.animations("future",opening)),"Unknown animation lookup is unsafe");

  final int[] refreshes={0};
  Runnable refresh=new Runnable(){public void run(){refreshes[0]++;}};
  Activity chooser=activity("slide");chooser.preferences.values.put("unrelated","preserve");
  MenuMotion.choose(chooser,refresh);AlertDialog.Builder builder=AlertDialog.Builder.last;
  check(builder.selected==3,"Chooser did not select current option");
  AlertDialog dialog=new AlertDialog();builder.choice.onClick(dialog,0);
  check(!chooser.preferences.values.containsKey(MenuMotionOptions.SETTING)&&"preserve".equals(chooser.preferences.values.get("unrelated")),"Default choice did not remove only its own override");
  check(dialog.dismissed&&refreshes[0]==1,"Successful choice did not refresh once");
  for(int index=1;index<4;index++){
   MenuMotion.choose(chooser,refresh);dialog=new AlertDialog();AlertDialog.Builder.last.choice.onClick(dialog,index);
   check(MenuMotionOptions.VALUES[index].equals(chooser.preferences.values.get(MenuMotionOptions.SETTING))&&dialog.dismissed,"Choice did not persist");
  }
  chooser.preferences.commitResult=false;MenuMotion.choose(chooser,refresh);dialog=new AlertDialog();int before=refreshes[0];
  AlertDialog.Builder.last.choice.onClick(dialog,0);
  check(!dialog.dismissed&&refreshes[0]==before&&"Could not save transitions".equals(AlertDialog.Builder.last.title),"Failed commit was reported as saved");
  System.out.println("Menu motion defaults, direction, accessibility, resources and chooser checks passed");
 }
}'''
        with tempfile.TemporaryDirectory() as folder:
            paths = []
            for relative, source in stubs.items():
                path = Path(folder) / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(source)
                paths.append(str(path))
            harness = Path(folder) / 'MenuMotionTest.java'
            harness.write_text(check)
            sources = ROOT / 'app/src/com/jeremykenedy/firetv/ui'
            subprocess.run(['javac', '-encoding', 'UTF-8', '-d', folder, *paths,
                            str(sources / 'MenuMotion.java'),
                            str(sources / 'MenuMotionOptions.java'), str(harness)], check=True)
            subprocess.run(['java', '-cp', folder,
                            'com.jeremykenedy.firetv.ui.MenuMotionTest'], check=True)
