from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class CardControlChecks(unittest.TestCase):
    def test_select_menu_open_exact_hide_and_source_write_recovery(self):
        sources = {
            'android/net/Uri.java': 'package android.net; public class Uri {}',
            'android/view/KeyEvent.java': '''package android.view; public class KeyEvent {
 public static final int KEYCODE_MENU=82,KEYCODE_DPAD_CENTER=23,KEYCODE_ENTER=66,KEYCODE_NUMPAD_ENTER=160,ACTION_UP=1;
 private final int code,action; private final boolean canceled;
 public KeyEvent(int a,int c,boolean x){action=a;code=c;canceled=x;} public int getKeyCode(){return code;}
 public int getAction(){return action;} public boolean isCanceled(){return canceled;}
}''',
            'android/content/DialogInterface.java': '''package android.content; public interface DialogInterface {
 interface OnDismissListener {void onDismiss(DialogInterface d);} interface OnClickListener {void onClick(DialogInterface d,int i);}
}''',
            'android/app/AlertDialog.java': '''package android.app;
import android.content.*;
public class AlertDialog implements DialogInterface {
 public static Builder last; public static int shown;
 public static class Builder {
  public String title,message;public String[] options;public DialogInterface.OnClickListener click;
  public DialogInterface.OnDismissListener dismiss;
  public Builder(Context c){} public Builder setTitle(String t){title=t;return this;}
  public Builder setMessage(String t){message=t;return this;} public Builder setPositiveButton(String t,Object c){return this;}
  public Builder setOnDismissListener(DialogInterface.OnDismissListener d){dismiss=d;return this;}
  public Builder setItems(String[] a,DialogInterface.OnClickListener l){options=a;click=l;return this;}
  public AlertDialog show(){last=this;shown++;return new AlertDialog();}
  public void choose(String label){for(int i=0;i<options.length;i++)if(options[i].equals(label)){click.onClick(null,i);if(dismiss!=null)dismiss.onDismiss(null);return;}throw new AssertionError(label);}
 }
}''',
            'android/content/SharedPreferences.java': '''package android.content; import java.util.*;
public class SharedPreferences {
 public final Map<String,Object> values=new HashMap<>();public boolean failNext;
 public Map<String,?> getAll(){return new HashMap<>(values);} public Editor edit(){return new Editor();}
 public class Editor { final Map<String,Object> pending=new HashMap<>();
 public Editor putString(String k,String v){pending.put(k,v);return this;} public Editor remove(String k){pending.put(k,null);return this;}
 public boolean commit(){for(String k:pending.keySet())if(pending.get(k)==null)values.remove(k);else values.put(k,pending.get(k));
 boolean success=!failNext;failNext=false;return success;}
 }
}''',
            'android/content/Context.java': '''package android.content;public class Context {
 public final SharedPreferences prefs=new SharedPreferences();public Context getPackageManager(){return this;}
 public Intent getLaunchIntentForPackage(String s){return new Intent(this,Object.class);}public String getPackageName(){return "com.example.home";}
 public void startActivity(Intent i){}
}''',
            'android/content/Intent.java': '''package android.content;public class Intent {
 public static final String CATEGORY_HOME="home";public static final int FLAG_ACTIVITY_CLEAR_TOP=1,FLAG_ACTIVITY_SINGLE_TOP=2;
 public Intent(Context c,Class<?> t){}public Intent addCategory(String c){return this;} public Intent addFlags(int f){return this;}
}''',
            'org/json/JSONObject.java': '''package org.json;import java.util.*;
public class JSONObject {
 private final Map<String,Object> values=new LinkedHashMap<>();private static final Map<String,JSONObject> saved=new HashMap<>();
 public JSONObject(){} public JSONObject(String s){if(s.equals("{}"))return;JSONObject other=saved.get(s);if(other==null)throw new IllegalArgumentException();copy(other);}
 private void copy(JSONObject other){for(String k:other.values.keySet()){Object v=other.values.get(k);if(v instanceof JSONObject){JSONObject child=new JSONObject();child.copy((JSONObject)v);v=child;}values.put(k,v);}}
 public JSONObject put(String k,Object v){values.put(k,v);return this;}public boolean has(String k){return values.containsKey(k);}
 public JSONObject optJSONObject(String k){Object v=values.get(k);return v instanceof JSONObject?(JSONObject)v:null;}
 public Object get(String k){return values.get(k);} public String toString(){String k="fixture:"+saved.size();JSONObject copy=new JSONObject();copy.copy(this);saved.put(k,copy);return k;}
}''',
            'com/jeremykenedy/firetv/ui/DeviceSettings.java': '''package com.jeremykenedy.firetv.ui;public class DeviceSettings {
 public static android.content.SharedPreferences prefs(android.content.Context c){return c.prefs;}
}''',
            'com/jeremykenedy/firetv/ui/HiddenCards.java': '''package com.jeremykenedy.firetv.ui;
public class HiddenCards {
 public static String hidden;
 static Card selected(String k,String s,String t,long f){return k!=null&&k.matches("[a-f0-9]{64}")?new Card(k,s,t):null;}
 static void hide(android.content.Context c,String k){hidden=k;}
 static class Card{final String key,source,title;Card(String k,String s,String t){key=k;source=s;title=t;}}
}''',
            'com/jeremykenedy/firetv/ui/FeaturedCardClock.java': '''package com.jeremykenedy.firetv.ui;public class FeaturedCardClock {
 public static int paused;public static void pause(){paused++;}public static void resume(){paused--;}public static void changed(){}
}''',
            'com/jeremykenedy/firetv/ui/FeedOrder.java': '''package com.jeremykenedy.firetv.ui;public class FeedOrder {
 static String appName(android.content.Context c,String s){return "Player";}static String key(android.content.Context c,String s,long f){return s+f;}
}''',
            'com/jeremykenedy/firetv/ui/FeaturedGroups.java': '''package com.jeremykenedy.firetv.ui;public class FeaturedGroups {static void move(String f,int d){}}''',
            'com/jeremykenedy/firetv/ui/ChannelCardsActivity.java': '''package com.jeremykenedy.firetv.ui;public class ChannelCardsActivity {
 static void open(android.content.Context c,String t,String s,java.util.List<Long> f){}
}''',
            'com/jeremykenedy/firetv/ui/FeaturedCardsActivity.java': 'package com.jeremykenedy.firetv.ui;public class FeaturedCardsActivity {}',
            'com/jeremykenedy/firetv/ui/FeedOrderActivity.java': 'package com.jeremykenedy.firetv.ui;public class FeedOrderActivity {}',
        }
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            files = []
            for name, content in sources.items():
                target = folder / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(content)
                files.append(target)
            subprocess.run(['javac', '-d', folder, *files,
                            ROOT / 'app/src/com/jeremykenedy/firetv/ui/FeaturedCardMenu.java',
                            ROOT / 'tests/CardControlsTest.java'], check=True)
            subprocess.run(['java', '-cp', folder,
                            'com.jeremykenedy.firetv.ui.CardControlsTest'], check=True)
