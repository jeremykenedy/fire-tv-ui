#!/usr/bin/env python3
"""Check recommendation read boundaries without an account or device mutation."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp'
STUBS = {
'com/jeremykenedy/firetv/ui/nativeapp/providers/OwnedPlexIdentity.java': '''package com.jeremykenedy.firetv.ui.nativeapp.providers;
import android.content.Context; class OwnedPlexIdentity { static String canonical(Context c,android.database.Cursor r,String s){return null;} static boolean notification(Context c,android.database.Cursor r,String s){return false;} }''',
'com/jeremykenedy/firetv/ui/nativeapp/providers/plex/PlexRepository.java': '''package com.jeremykenedy.firetv.ui.nativeapp.providers.plex;
import android.content.Context;import android.os.CancellationSignal;import java.util.*;
import com.jeremykenedy.firetv.ui.nativeapp.providers.TvRecommendations;
public class PlexRepository {
 public enum Status {DISABLED,NOT_CONNECTED,READY,EMPTY,UNAVAILABLE}
 public static boolean fail; public static int reads; public static Status next=Status.NOT_CONNECTED;
 public static List<TvRecommendations.Feed> feeds=Collections.emptyList();
 public static boolean enabled(Context context){if(fail)throw new IllegalStateException();return context.plexEnabled;}
 public static Result read(Context context,CancellationSignal cancellation){reads++;return new Result();}
 public static class Result {public final Status status=next;public final List<TvRecommendations.Feed> feeds=PlexRepository.feeds;
 public String message(){return status.name();}}
}''',
'com/jeremykenedy/firetv/ui/nativeapp/providers/NotificationRecommendations.java': '''package com.jeremykenedy.firetv.ui.nativeapp.providers;
import android.content.Context;import android.os.CancellationSignal;import java.util.*;
public class NotificationRecommendations {
 public static Result read(Context context,CancellationSignal cancellation){return new Result();}
 public static class Result {
 public final List<TvRecommendations.Feed> feeds=Collections.emptyList();
 public final TvRecommendations.SourceStatus status=new TvRecommendations.SourceStatus("notifications","Notifications",
 TvRecommendations.SourceStatus.State.DISABLED,"Off");}
}''',
'android/os/Build.java': 'package android.os; public class Build { public static class VERSION { public static int SDK_INT=34; } }',
'android/os/OperationCanceledException.java': 'package android.os; public class OperationCanceledException extends RuntimeException {}',
'android/os/CancellationSignal.java': '''package android.os; public class CancellationSignal {
 private boolean canceled; public void cancel(){canceled=true;} public void throwIfCanceled(){if(canceled)throw new OperationCanceledException();}
}''',
'android/net/Uri.java': '''package android.net; public class Uri {
 public final String value; private Uri(String value){this.value=value;}
 public static Uri parse(String value){return new Uri(value);}
 public String getScheme(){int i=value.indexOf(':');return i<0?null:value.substring(0,i);}
 public String getAuthority(){int i=value.indexOf("://");if(i<0)return null;int end=value.indexOf('/',i+3);return value.substring(i+3,end<0?value.length():end);}
}''',
'android/database/Cursor.java': '''package android.database; public interface Cursor extends AutoCloseable {
 boolean moveToNext();int getColumnIndex(String name);int getColumnIndexOrThrow(String name);
 boolean isNull(int index);String getString(int index);long getLong(int index);void close();
}''',
'android/content/pm/ProviderInfo.java': 'package android.content.pm; public class ProviderInfo { public String packageName; }',
'android/content/pm/PackageManager.java': '''package android.content.pm; public class PackageManager {
 public static final int PERMISSION_GRANTED=0;public boolean available=true;
 public final java.util.Map<String,String> owners=new java.util.HashMap<>();
 public ProviderInfo resolveContentProvider(String name,int flags){
  if(!available)return null;ProviderInfo info=new ProviderInfo();info.packageName=owners.get(name);
  return name.equals("android.media.tv")||info.packageName!=null?info:null;
 }
 public Object getApplicationInfo(String source,int flags)throws NameNotFoundException{return source;}
 public CharSequence getApplicationLabel(Object info){return info.toString();}
 public static class NameNotFoundException extends Exception {}
}''',
'android/content/ContentResolver.java': '''package android.content;
import java.util.*;import android.net.Uri;import android.database.Cursor;
public class ContentResolver {
 public final Map<String,Cursor> tables=new HashMap<>();public int queries;public String fail;
 public Cursor query(Uri uri,String[] fields,String selection,String[] args,String order,android.os.CancellationSignal cancellation){
  if(cancellation!=null)cancellation.throwIfCanceled();
  queries++;String name=uri.value.substring(uri.value.lastIndexOf('/')+1);
  if(name.equals(fail))throw new SecurityException();return tables.get(name);
 }
}''',
'android/content/Context.java': '''package android.content; import android.content.pm.PackageManager;
public class Context {
 public final PackageManager packages=new PackageManager();public final ContentResolver resolver=new ContentResolver();
 public String getPackageName(){return "fixture.owner";} public boolean plexEnabled; public boolean permission=true;public PackageManager getPackageManager(){return packages;}
 public ContentResolver getContentResolver(){return resolver;}public int checkSelfPermission(String name){return permission?0:-1;}
}''',
'android/media/tv/TvContract.java': '''package android.media.tv;import android.net.Uri;
public class TvContract {
 public static final String AUTHORITY="android.media.tv";
 public static class Channels {public static final Uri CONTENT_URI=Uri.parse("content://android.media.tv/channel");}
 public static class PreviewPrograms {public static final Uri CONTENT_URI=Uri.parse("content://android.media.tv/preview_program");}
 public static class WatchNextPrograms {public static final Uri CONTENT_URI=Uri.parse("content://android.media.tv/watch_next_program");}
}''',
}
with tempfile.TemporaryDirectory() as folder:
    work = Path(folder)
    for name, source in STUBS.items():
        path = work / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source)
    subprocess.run(['javac', '-d', work, *[work / p for p in STUBS],
                    SOURCE / 'layout/LayoutItem.java',
                    SOURCE / 'providers/TvRecommendations.java', SOURCE / 'providers/RecommendationKeys.java',
                    SOURCE / 'providers/RecommendationSources.java', SOURCE / 'providers/NotificationRules.java',
                    SOURCE / 'providers/LegacyRecommendationIdentities.java',
                    ROOT / 'tests/providers/LegacyIdentityTest.java', ROOT / 'tests/providers/RecommendationsTest.java'], check=True)
    subprocess.run(['java', '-cp', work,
                    'com.jeremykenedy.firetv.ui.nativeapp.providers.RecommendationsTest'], check=True)

    subprocess.run(['java', '-cp', work,
                    'com.jeremykenedy.firetv.ui.nativeapp.providers.LegacyIdentityTest'], check=True)
