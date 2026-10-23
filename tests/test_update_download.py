from pathlib import Path
import os
import subprocess
import tempfile
import unittest

from java_runtime import json_runtime

ROOT = Path(__file__).resolve().parents[1]

UPDATE_STUBS = {
    'android/content/Context.java': '''package android.content;
public class Context {
 final java.io.File cache; public Context(java.io.File f){cache=f;}
 public java.io.File getCacheDir(){return cache;}
 public android.content.pm.PackageManager getPackageManager(){return new android.content.pm.PackageManager();}
 public android.content.res.AssetManager getAssets(){return null;}
 public String getPackageName(){return "com.jeremykenedy.firetv.ui";}
}''',
    'android/content/res/AssetManager.java': '''package android.content.res;
public class AssetManager { public java.io.InputStream open(String name){return null;} }''',
    'android/os/Build.java': '''package android.os;
public class Build { public static class VERSION {public static int SDK_INT=34;} }''',
    'android/content/pm/Signature.java': '''package android.content.pm;
public class Signature {
 final byte value; public Signature(byte b){value=b;}
 public boolean equals(Object other){return other instanceof Signature && ((Signature)other).value==value;}
}''',
    'android/content/pm/SigningInfo.java': '''package android.content.pm;
public class SigningInfo {
 final Signature[] values; public SigningInfo(Signature[] s){values=s;}
 public Signature[] getApkContentsSigners(){return values;}
}''',
    'android/content/pm/PackageInfo.java': '''package android.content.pm;
public class PackageInfo {
 public String packageName,versionName; public Signature[] signatures; public SigningInfo signingInfo;
 public int versionCode; public long getLongVersionCode(){return versionCode;}
}''',
    'android/content/pm/PackageManager.java': '''package android.content.pm;
public class PackageManager {
 public static final int GET_SIGNATURES=64,GET_SIGNING_CERTIFICATES=134217728;
 public static PackageInfo installed,archive;
 public PackageInfo getPackageArchiveInfo(String path,int flags){return archive;}
 public PackageInfo getPackageInfo(String name,int flags) throws NameNotFoundException {
  if(installed==null)throw new NameNotFoundException();return installed;
 }
 public static class NameNotFoundException extends Exception { }
}''',
}


class UpdateDownloadChecks(unittest.TestCase):
    def test_archive_integrity_signature_version_and_cancellation(self):
        jar = json_runtime()
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp)
            files = []
            for name, content in UPDATE_STUBS.items():
                target = folder / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(content)
                files.append(str(target))
            cache = folder / 'apk-cache'
            cache.mkdir()
            real = ROOT / 'app/src/com/jeremykenedy/firetv/ui'
            subprocess.run(['javac', '-cp', str(jar), '-d', temp, *files,
                            str(real / 'ScreensaverCatalog.java'), str(real / 'ScreensaverDownload.java'),
                            str(ROOT / 'tests/UpdateDownloadTest.java')], check=True)
            subprocess.run(['java', '-cp', temp + os.pathsep + str(jar),
                            'com.jeremykenedy.firetv.ui.UpdateDownloadTest', str(cache)], check=True)
