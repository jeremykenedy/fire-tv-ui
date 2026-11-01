#!/usr/bin/env python3
"""Check recommendation read boundaries without an account or device mutation."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp'
STUBS = {
'android/net/Uri.java': '''package android.net; public class Uri {
 public final String value; private Uri(String value){this.value=value;}
 public static Uri parse(String value){return new Uri(value);}
 public String getScheme(){int i=value.indexOf(':');return i<0?null:value.substring(0,i);}
}''',
'android/database/Cursor.java': '''package android.database; public interface Cursor extends AutoCloseable {
 boolean moveToNext();int getColumnIndex(String name);int getColumnIndexOrThrow(String name);
 boolean isNull(int index);String getString(int index);long getLong(int index);void close();
}''',
'android/content/pm/PackageManager.java': '''package android.content.pm; public class PackageManager {
 public static final int PERMISSION_GRANTED=0;public boolean available=true;
 public Object resolveContentProvider(String name,int flags){return available?new Object():null;}
 public Object getApplicationInfo(String source,int flags)throws NameNotFoundException{return source;}
 public CharSequence getApplicationLabel(Object info){return info.toString();}
 public static class NameNotFoundException extends Exception {}
}''',
'android/content/ContentResolver.java': '''package android.content;
import java.util.*;import android.net.Uri;import android.database.Cursor;
public class ContentResolver {
 public final Map<String,Cursor> tables=new HashMap<>();public int queries;public String fail;
 public Cursor query(Uri uri,String[] fields,String selection,String[] args,String order){
  queries++;String name=uri.value.substring(uri.value.lastIndexOf('/')+1);
  if(name.equals(fail))throw new SecurityException();return tables.get(name);
 }
}''',
'android/content/Context.java': '''package android.content; import android.content.pm.PackageManager;
public class Context {
 public final PackageManager packages=new PackageManager();public final ContentResolver resolver=new ContentResolver();
 public boolean permission=true;public PackageManager getPackageManager(){return packages;}
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
                    ROOT / 'tests/providers/RecommendationsTest.java'], check=True)
    subprocess.run(['java', '-cp', work,
                    'com.jeremykenedy.firetv.ui.nativeapp.providers.RecommendationsTest'], check=True)
