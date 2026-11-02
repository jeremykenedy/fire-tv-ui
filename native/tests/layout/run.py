#!/usr/bin/env python3
"""Run account-free layout and persistence behavior checks."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp/layout'
STUBS = {
    'android/app/Activity.java': 'package android.app; public class Activity extends android.content.Context {}',
    'android/view/View.java': 'package android.view; public class View {}',
    'com/jeremykenedy/firetv/ui/nativeapp/layout/ClockWidgetProvider.java': '''package com.jeremykenedy.firetv.ui.nativeapp.layout;
import android.content.Context;
import android.app.Activity;
import android.view.View;
public class ClockWidgetProvider implements WidgetProvider {
 public LayoutItem item(){return HomeSlots.CLOCK;}
 public String title(){return "Clock";}
 public boolean defaultVisible(){return true;}
 public State state(Context context){return State.READY;}
 public String message(Context context){return "Local time";}
 public int widthDp(){return 320;}
 public int heightDp(){return 160;}
 public View createView(Context context){return new View();}
 public void configure(Activity activity){}
 public void onStart(View view){}
 public void onStop(View view){}
}''',
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
# These host doubles isolate registry/order tests from Android views. Native tests use real providers.
WIDGETS = [
    ('worldclock', 'WorldClockWidgetProvider', 'world-clocks', 'World clocks'),
    ('yearprogress', 'YearProgressWidgetProvider', 'year-progress', 'Year progress'),
    ('notes', 'NotesWidgetProvider', 'household-notes', 'Household notes and lists'),
    ('breathing', 'BreathingWidgetProvider', 'breathing-break', 'Breathing break'),
    ('photos', 'PhotoWidgetProvider', 'photo-frame', 'Photo frame'),
    ('astronomy', 'AstronomyWidgetProvider', 'sun-moon', 'Sun and moon'),
    ('quickactions', 'QuickActionsWidgetProvider', 'local:quick-actions', 'Quick actions'),
    ('reminders', 'RemindersWidgetProvider', 'household-reminders', 'Household reminders'),
    ('timers', 'CountdownWidgetProvider', 'countdown', 'Countdown'),
    ('timers', 'TimerWidgetProvider', 'timer', 'Timer'),
    ('status', 'NetworkStatusWidgetProvider', 'network-status', 'Network status'),
    ('status', 'TvAudioStatusWidgetProvider', 'tv-audio-status', 'TV and audio status'),
    ('puzzles', 'DailyPuzzleWidgetProvider', 'daily-puzzle', 'Daily puzzle'),
    ('calendar', 'CalendarWidgetProvider', 'calendar', 'Calendar'),
    ('ambient', 'AmbientWidgetProvider', 'ambient-audio', 'Ambient audio'),
    ('livetv', 'LiveTvWidgetProvider', 'live-tv-favorites', 'Live TV favorites'),
    ('nowplaying', 'NowPlayingWidgetProvider', 'now-playing', 'Now Playing'),
    ('weather', 'WeatherWidgetProvider', 'weather-hourly', 'Hourly weather'),
    ('feeds', 'FeedWidgetProvider', 'custom-feed', 'Custom feed'),
    ('radar', 'RadarWidgetProvider', 'weather-radar', 'NWS radar'),
    ('uv', 'UvWidgetProvider', 'uv-forecast', 'UV forecast'),
]
for package, name, identity, title in WIDGETS:
    STUBS[f'com/jeremykenedy/firetv/ui/nativeapp/widget/{package}/{name}.java'] = f'''package com.jeremykenedy.firetv.ui.nativeapp.widget.{package};
import com.jeremykenedy.firetv.ui.nativeapp.layout.*;
public final class {name} extends ClockWidgetProvider {{
 public LayoutItem item(){{return new LayoutItem("{identity}", LayoutItem.Kind.WIDGET);}}
 public String title(){{return "{title}";}}
 public boolean defaultVisible(){{return false;}}
}}'''
STUBS['com/jeremykenedy/firetv/ui/nativeapp/widget/dailycontent/DailyContentWidgets.java'] = '''package com.jeremykenedy.firetv.ui.nativeapp.widget.dailycontent;
import com.jeremykenedy.firetv.ui.nativeapp.layout.*;
import java.util.*;
public final class DailyContentWidgets {
 public static List<WidgetProvider> providers(){
  List<WidgetProvider> out=new ArrayList<>();
  for(String id:Arrays.asList("daily-astronomy","daily-art","word-of-day","daily-trivia","daily-inspiration","daily-horoscope","daily-comic")){
   final String key=id;
   out.add(new ClockWidgetProvider(){public LayoutItem item(){return new LayoutItem(key,LayoutItem.Kind.WIDGET);} public boolean defaultVisible(){return false;}});
  }
  return out;
 }
}'''
with tempfile.TemporaryDirectory() as folder:
    work = Path(folder)
    for name, source in STUBS.items():
        path = work / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source)
    subprocess.run(['javac', '-d', work, *[work / p for p in STUBS],
                    SOURCE / 'LayoutItem.java', SOURCE / 'LayoutOptions.java', SOURCE / 'HomeSlots.java', SOURCE / 'WidgetProvider.java',
                    SOURCE / 'LayoutPreferences.java', ROOT / 'tests/layout/LayoutTest.java'], check=True)
    subprocess.run(['java', '-cp', work,
                    'com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutTest'], check=True)
