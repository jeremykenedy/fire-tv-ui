import os
from pathlib import Path
import subprocess
import tempfile
import unittest

from java_runtime import json_runtime
from test_update_download import UPDATE_STUBS

ROOT = Path(__file__).resolve().parents[1]


class StagedUpdateChecks(unittest.TestCase):
    def test_authenticated_handoff_rejects_incompatible_or_stale_payloads(self):
        jar = json_runtime()
        stubs = dict(UPDATE_STUBS)
        stubs.update({
            'android/os/Environment.java': '''package android.os;
public class Environment {
 public static final String DIRECTORY_DOWNLOADS="Downloads";
 public static java.io.File folder;
 public static java.io.File getExternalStoragePublicDirectory(String type){return folder;}
}''',
            'android/util/AtomicFile.java': '''package android.util;
public class AtomicFile {
 final java.io.File file; public AtomicFile(java.io.File f){file=f;}
 public java.io.FileOutputStream startWrite() throws Exception{return new java.io.FileOutputStream(file);}
 public void finishWrite(java.io.FileOutputStream s) throws Exception{s.close();}
 public void failWrite(java.io.FileOutputStream s) throws Exception{s.close();}
 public java.io.FileInputStream openRead() throws Exception{return new java.io.FileInputStream(file);}
}''',
            'com/jeremykenedy/firetv/ui/BackupStore.java': '''package com.jeremykenedy.firetv.ui;
public class BackupStore {public static boolean access=true; public static boolean hasAccess(){return access;}}''',
        })
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp)
            files = []
            for name, content in stubs.items():
                target = folder / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(content)
                files.append(str(target))
            real = ROOT / 'app/src/com/jeremykenedy/firetv/ui'
            sources = [real / name for name in ('ScreensaverCatalog.java', 'ScreensaverDownload.java',
                       'StagedUiUpdate.java', 'UpdateVersions.java')]
            subprocess.run(['javac', '-cp', str(jar), '-d', temp, *files,
                            *map(str, sources), str(ROOT / 'tests/StagedUpdateTest.java')], check=True)
            subprocess.run(['java', '-cp', temp + os.pathsep + str(jar),
                            'com.jeremykenedy.firetv.ui.StagedUpdateTest', temp], check=True)
