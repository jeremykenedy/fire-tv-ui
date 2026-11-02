#!/usr/bin/env python3
"""Host checks for preserved transition choices and animation fallbacks."""
from pathlib import Path
import subprocess
import tempfile
root = Path(__file__).resolve().parents[2]
stubs = {
'android/animation/ValueAnimator.java': 'package android.animation; public class ValueAnimator { public static boolean enabled=true; public static boolean areAnimatorsEnabled(){return enabled;} }',
'android/content/SharedPreferences.java': '''package android.content; public class SharedPreferences {
 public java.util.Map<String,Object> values=new java.util.HashMap<>(); public boolean success=true; public java.util.Map<String,?> getAll(){return values;}
 public Editor edit(){return new Editor();} public class Editor { String k,v; boolean remove; public Editor remove(String k){this.k=k;remove=true;return this;} public Editor putString(String k,String v){this.k=k;this.v=v;return this;} public Editor putBoolean(String k,boolean v){return this;} public Editor putInt(String k,int v){return this;} public Editor putLong(String k,long v){return this;} public Editor putFloat(String k,float v){return this;} public Editor putStringSet(String k,java.util.Set<String> v){return this;} public boolean commit(){if(remove)values.remove(k);else values.put(k,v);return success;} }
}''',
'android/content/DialogInterface.java': 'package android.content; public interface DialogInterface { void dismiss(); interface OnClickListener { void onClick(DialogInterface dialog,int index); } }',
'android/content/res/Resources.java': '''package android.content.res; public class Resources { public java.util.Map<String,Integer> ids=new java.util.HashMap<>(); public int getIdentifier(String name,String type,String pkg){return ids.containsKey(name)?ids.get(name):0;} }''',
'android/app/Activity.java': '''package android.app; public class Activity { public android.content.SharedPreferences prefs=new android.content.SharedPreferences(); public android.content.res.Resources resources=new android.content.res.Resources(); public int count,enter,exit; public android.content.SharedPreferences getSharedPreferences(String name,int mode){if(!name.equals("launcher_prefs"))throw new AssertionError();return prefs;} public android.content.res.Resources getResources(){return resources;} public String getPackageName(){return "fixture";} public void overridePendingTransition(int a,int b){count++;enter=a;exit=b;} }''',
'android/app/AlertDialog.java': '''package android.app; import android.content.DialogInterface; public class AlertDialog implements DialogInterface { public boolean dismissed; public void dismiss(){dismissed=true;} public static class Builder {public static Builder last; public int selected; public String title; public DialogInterface.OnClickListener choice; public Builder(Activity a){last=this;} public Builder setTitle(String t){title=t;return this;} public Builder setMessage(String t){return this;} public Builder setSingleChoiceItems(String[] labels,int selected,DialogInterface.OnClickListener c){this.selected=selected;choice=c;return this;} public Builder setNegativeButton(String t,DialogInterface.OnClickListener c){return this;} public Builder setPositiveButton(String t,DialogInterface.OnClickListener c){return this;} public AlertDialog show(){return new AlertDialog();}} }''',
'com/jeremykenedy/firetv/ui/nativeapp/MenuTransitionTest.java': '''package com.jeremykenedy.firetv.ui.nativeapp;
import android.app.*; import android.animation.ValueAnimator;
public final class MenuTransitionTest {
 static void check(boolean x){if(!x)throw new AssertionError();}
 static Activity activity(Object value){Activity a=new Activity();if(value!=null)a.prefs.values.put("menu_transition_style",value);String[] ids={"settings_fade_in","settings_fade_out","settings_slide_in_right","settings_slide_out_left","settings_slide_in_left","settings_slide_out_right"};for(int i=0;i<ids.length;i++)a.resources.ids.put(ids[i],100+i);return a;}
 public static void main(String[] args){
  for(Object value:new Object[]{null,"default","unknown",4,true}){Activity a=activity(value);MenuTransition.apply(a,true);check(a.count==0&&MenuTransition.label(a).equals("Default"));}
  for(boolean opening:new boolean[]{true,false}){Activity a=activity("none");MenuTransition.apply(a,opening);check(a.count==1&&a.enter==0&&a.exit==0);a=activity("fade");MenuTransition.apply(a,opening);check(a.enter==100&&a.exit==101);a=activity("slide");MenuTransition.apply(a,opening);check(a.enter==(opening?102:104)&&a.exit==(opening?103:105));}
  ValueAnimator.enabled=false;for(String style:new String[]{"fade","slide"}){Activity a=activity(style);MenuTransition.apply(a,true);check(a.count==1&&a.enter==0&&a.exit==0);}ValueAnimator.enabled=true;
  Activity a=activity("fade");a.resources.ids.remove("settings_fade_out");MenuTransition.apply(a,true);check(a.count==0);
  final int[] refresh={0};a=activity("slide");a.prefs.values.put("other","kept");final Activity target=a;Runnable done=()->refresh[0]++;MenuTransition.choose(a,done);check(AlertDialog.Builder.last.selected==3);AlertDialog dialog=new AlertDialog();AlertDialog.Builder.last.choice.onClick(dialog,0);check(dialog.dismissed&&refresh[0]==1&&!a.prefs.values.containsKey("menu_transition_style")&&"kept".equals(a.prefs.values.get("other")));
  for(int i=1;i<4;i++){MenuTransition.choose(a,done);dialog=new AlertDialog();AlertDialog.Builder.last.choice.onClick(dialog,i);check(dialog.dismissed&&MenuTransition.index(a.prefs.values.get("menu_transition_style"))==i);}
  a.prefs.success=false;MenuTransition.choose(a,done);dialog=new AlertDialog();int before=refresh[0];AlertDialog.Builder.last.choice.onClick(dialog,0);check(!dialog.dismissed&&refresh[0]==before&&"slide".equals(a.prefs.values.get("menu_transition_style"))&&AlertDialog.Builder.last.title.equals("Could not save transitions"));
  System.out.println("Menu transition saved choices, direction, system animation and save failures passed.");
 }
}'''
}
with tempfile.TemporaryDirectory() as d:
 out=Path(d); files=[]
 for name,source in stubs.items():
  path=out/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(source);files.append(str(path))
 subprocess.run(['javac','-d',d,*files,str(root/'src/com/jeremykenedy/firetv/ui/nativeapp/MenuTransition.java')],check=True)
 subprocess.run(['java','-cp',d,'com.jeremykenedy.firetv.ui.nativeapp.MenuTransitionTest'],check=True)
