from pathlib import Path
import os
import subprocess
import tempfile
import unittest
from java_runtime import json_runtime

ROOT = Path(__file__).resolve().parents[1]


class AndroidIconChecks(unittest.TestCase):
    def test_existing_access_media_publication_and_index_retry_on_api28_and30(self):
        assets = ROOT / '.build/decoded/assets'
        if not (assets / 'input-icons/manifest.json').exists():
            self.skipTest('Build bundled assets first.')
        sources = {
            'android/Manifest.java': 'package android; public class Manifest {public static class permission {public static final String WRITE_EXTERNAL_STORAGE="write";}}',
            'android/content/pm/PackageManager.java': 'package android.content.pm; public class PackageManager {public static final int PERMISSION_GRANTED=0;}',
            'android/os/Build.java': 'package android.os; public class Build {public static class VERSION {public static int SDK_INT;}}',
            'android/os/Environment.java': 'package android.os; public class Environment {public static final String DIRECTORY_PICTURES="Pictures";public static java.io.File root;public static boolean allowed;public static boolean isExternalStorageManager(){return allowed;} public static java.io.File getExternalStoragePublicDirectory(String name){return new java.io.File(root,name);}}',
            'android/os/SystemClock.java': 'package android.os; public class SystemClock {public static long elapsedRealtime(){return System.nanoTime()/1000000;}}',
            'android/util/Log.java': 'package android.util;public class Log {public static int i(String t,String m){return 0;}public static int w(String t,String m){return 0;}}',
            'android/content/BroadcastReceiver.java': 'package android.content;public class BroadcastReceiver {public PendingResult goAsync(){return new PendingResult();}public static class PendingResult {public void finish(){}}}',
            'android/content/ContentValues.java': 'package android.content;public class ContentValues {public void put(String key,Integer value){if(!key.equals("is_pending")||value!=0)throw new AssertionError();}}',
            'android/content/res/AssetManager.java': 'package android.content.res;public class AssetManager {public static java.io.File root;public static int reads;public java.io.InputStream open(String path)throws java.io.IOException {reads++;return new java.io.FileInputStream(new java.io.File(root,path));}}',
            'android/net/Uri.java': 'package android.net;public class Uri {public final java.io.File file;public Uri(java.io.File f){file=f;}public String getScheme(){return "content";}public String getAuthority(){return "media";}}',
            'android/database/Cursor.java': 'package android.database;public interface Cursor extends AutoCloseable {boolean moveToFirst();int getCount();String getString(int index);int getInt(int index);void close();}',
            'android/content/Context.java': '''package android.content;
public class Context {public boolean allowed;public int checkSelfPermission(String p){return allowed?0:-1;}public Context getApplicationContext(){return this;}
public android.content.res.AssetManager getAssets(){return new android.content.res.AssetManager();}public ContentResolver getContentResolver(){return new ContentResolver();}}''',
            'android/content/ContentResolver.java': '''package android.content;
import android.database.Cursor;import android.net.Uri;
public class ContentResolver {public static int updates,queries;public static boolean wrongPath;public static final java.util.Map<String,Integer> pending=new java.util.HashMap<>();
public Cursor query(final Uri uri,final String[] columns,String s,String[] a,String order){queries++;for(String c:columns)if(android.os.Build.VERSION.SDK_INT<29&&c.equals("is_pending"))throw new AssertionError("API28 pending column");
return new Cursor(){public boolean moveToFirst(){return true;}public int getCount(){return 1;}public String getString(int i){String c=columns[i];if(c.equals("_data"))return wrongPath?"/unrelated-owner.png":uri.file.getAbsolutePath();if(c.equals("_display_name"))return uri.file.getName();if(c.equals("mime_type"))return "image/png";throw new AssertionError(c);}public int getInt(int i){return pending.getOrDefault(uri.file.getAbsolutePath(),1);}public void close(){}};}
public int update(Uri uri,ContentValues v,String s,String[] a){if(android.os.Build.VERSION.SDK_INT<29)throw new AssertionError("API28 publish write");updates++;pending.put(uri.file.getAbsolutePath(),0);return 1;}}''',
            'android/media/MediaScannerConnection.java': '''package android.media;
import android.content.Context;import android.net.Uri;
public class MediaScannerConnection {public static boolean noCallbacks;public static java.util.List<String> paths=new java.util.ArrayList<>();
public interface OnScanCompletedListener {void onScanCompleted(String path,Uri uri);}
public static void scanFile(Context c,String[] files,String[] types,OnScanCompletedListener listener){for(int i=0;i<files.length;i++){if(!"image/png".equals(types[i]))throw new AssertionError();paths.add(files[i]);if(!noCallbacks)listener.onScanCompleted(files[i],new Uri(new java.io.File(files[i])));}}}''',
        }
        with tempfile.TemporaryDirectory() as temp:
            files = []
            for name, text in sources.items():
                path = Path(temp) / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(text)
                files.append(str(path))
            jar = json_runtime()
            subprocess.run(['javac', '-cp', str(jar), '-d', temp, *files,
                            str(ROOT / 'app/src/com/jeremykenedy/firetv/ui/IconFiles.java'),
                            str(ROOT / 'app/src/com/jeremykenedy/firetv/ui/BundledIcons.java'),
                            str(ROOT / 'tests/AndroidIconsTest.java')], check=True)
            subprocess.run(['java', '-cp', temp + os.pathsep + str(jar),
                            'com.jeremykenedy.firetv.ui.AndroidIconsTest', str(assets), temp], check=True, timeout=20)
