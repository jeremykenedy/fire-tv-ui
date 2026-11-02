#!/usr/bin/env python3
"""Check private watchlist persistence and title behavior."""
from pathlib import Path
import subprocess
import tempfile
import sys
import os
sys.path.insert(0, str(Path(__file__).resolve().parents[3] / "tests"))
from java_runtime import json_runtime

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp'
STUBS = {
    'android/content/Context.java': '''package android.content;
public class Context { public static final int MODE_PRIVATE=0;
public final SharedPreferences prefs=new SharedPreferences();
public SharedPreferences getSharedPreferences(String name,int mode){return prefs;} }''',
    'android/content/SharedPreferences.java': '''package android.content;
import java.util.*;
public class SharedPreferences {
public final Map<String,Object> values=new HashMap<>(); public boolean failNext;
public Map<String,?> getAll(){return new HashMap<>(values);} public Editor edit(){return new Editor();}
public class Editor { private boolean clear; private final Map<String,Object> edits=new HashMap<>();
public Editor remove(String key){edits.put(key,null);return this;} public Editor clear(){clear=true;return this;} public Editor putString(String key,String value){edits.put(key,value);return this;}
public boolean commit(){if(clear)values.clear();for (Map.Entry<String,Object> entry : edits.entrySet()) {if(entry.getValue()==null) values.remove(entry.getKey()); else values.put(entry.getKey(),entry.getValue());} if(failNext){failNext=false;return false;}return true;} }
}''',
}
with tempfile.TemporaryDirectory() as folder:
    work = Path(folder)
    for name, contents in STUBS.items():
        path = work / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(contents)
    jar = json_runtime()
    subprocess.run(['javac', '-cp', str(jar), '-d', work, *[work / name for name in STUBS],
                    SOURCE / 'migration/LegacyJson.java', SOURCE / 'widget/feeds/FeedSource.java',
                    SOURCE / 'widget/watchlist/Watchlist.java', SOURCE / 'widget/watchlist/WatchlistStore.java',
                    ROOT / 'tests/watchlist/WatchlistTest.java'], check=True)
    subprocess.run(['java', '-cp', str(work) + os.pathsep + str(jar),
                    'com.jeremykenedy.firetv.ui.nativeapp.widget.watchlist.WatchlistTest'], check=True)

    lookup = work / "lookup"
    lookup.mkdir()
    fake = {
        "android/os/CancellationSignal.java": "package android.os; public class CancellationSignal { public void throwIfCanceled(){} }",
        "android/os/OperationCanceledException.java": "package android.os; public class OperationCanceledException extends RuntimeException {}",
        "com/jeremykenedy/firetv/ui/nativeapp/providers/plex/PlexConnection.java": 'package com.jeremykenedy.firetv.ui.nativeapp.providers.plex; class PlexConnection { String machine; static PlexConnection load(android.content.Context c){return null;} PlexConnection forSource(String s){return null;} String sourceKey(){return "";} }',
        "com/jeremykenedy/firetv/ui/nativeapp/providers/plex/PlexApi.java": "package com.jeremykenedy.firetv.ui.nativeapp.providers.plex; class PlexApi { PlexApi(android.content.Context c,long g,android.os.CancellationSignal s){} org.json.JSONObject server(PlexConnection c,String p){return null;} }",
        "com/jeremykenedy/firetv/ui/nativeapp/providers/plex/PlexRepository.java": "package com.jeremykenedy.firetv.ui.nativeapp.providers.plex; class PlexRepository { static boolean enabled(android.content.Context c){return true;} }",
    }
    for name, contents in fake.items():
        target = lookup / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(contents)
    plex = SOURCE / "providers/plex"
    subprocess.run(["javac", "-cp", str(jar), "-d", lookup, str(work / "android/content/Context.java"), str(work / "android/content/SharedPreferences.java"),
                    *[lookup / name for name in fake],
                    *[plex / (name + ".java") for name in ("PlexTransportPolicy", "PlexValues", "PlexLibrary", "PlexItemLinks", "PlexSession", "LegacyPlexCardLookup", "PlexWatchlistLookup")],
                    ROOT / "tests/watchlist/PlexWatchlistLookupTest.java"], check=True)
    subprocess.run(["java", "-cp", str(lookup) + os.pathsep + str(jar),
                    "com.jeremykenedy.firetv.ui.nativeapp.providers.plex.PlexWatchlistLookupTest"], check=True)
