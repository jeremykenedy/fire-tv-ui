#!/usr/bin/env python3
"""Strict host codec checks. Outputs only sanitized fixtures in temporary directories."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp'
with tempfile.TemporaryDirectory(prefix='fire-tv-portable-backup-') as directory:
    sources = [JAVA / 'backup/PortableBackup.java', JAVA / 'migration/PreferenceSnapshot.java',
               JAVA / 'migration/LegacyJson.java', Path(__file__).with_name('PortableBackupTest.java')]
    subprocess.run(['javac', '-Xlint:all', '-Werror', '-d', directory, *map(str, sources)], check=True)
    subprocess.run(['java', '-cp', directory,
                    'com.jeremykenedy.firetv.ui.nativeapp.backup.PortableBackupTest'], check=True)

# Household payload checks use only sanitized private-content fixtures and small Android adapters.
with tempfile.TemporaryDirectory(prefix='fire-tv-household-backup-') as directory:
    work = Path(directory)
    stubs = {
        'android/content/Context.java': """package android.content;
public class Context { public static final int MODE_PRIVATE=0;
private final java.util.Map<String,SharedPreferences> stores=new java.util.HashMap<>();
public SharedPreferences getSharedPreferences(String name,int mode){if(!stores.containsKey(name))stores.put(name,new SharedPreferences());return stores.get(name);} }""",
        'android/content/SharedPreferences.java': """package android.content;
public class SharedPreferences {private final java.util.Map<String,Object> values=new java.util.HashMap<>();
public java.util.Map<String,?> getAll(){return new java.util.HashMap<>(values);}
public Editor edit(){return new Editor();} public class Editor {
private final java.util.Map<String,Object> edits=new java.util.HashMap<>();private boolean clear;
public Editor clear(){clear=true;return this;}public Editor putString(String key,String value){edits.put(key,value);return this;}
public boolean commit(){if(clear)values.clear();values.putAll(edits);return true;}}}""",
    }
    for name, source in stubs.items():
        path = work / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source)
    sources = [JAVA / 'backup/PortableBackup.java', JAVA / 'backup/BackupPayload.java',
               JAVA / 'backup/HouseholdContent.java', JAVA / 'migration/PreferenceSnapshot.java',
               JAVA / 'migration/LegacyJson.java', JAVA / 'widget/notes/NotesOptions.java',
               JAVA / 'widget/notes/NotesPreferences.java', JAVA / 'widget/reminders/RemindersOptions.java',
               JAVA / 'widget/reminders/RemindersPreferences.java', Path(__file__).with_name('HouseholdBackupTest.java')]
    subprocess.run(['javac', '-Xlint:all', '-Werror', '-d', directory,
                    *map(str, (work / name for name in stubs)), *map(str, sources)], check=True)
    subprocess.run(['java', '-cp', directory,
                    'com.jeremykenedy.firetv.ui.nativeapp.backup.HouseholdBackupTest'], check=True)

with tempfile.TemporaryDirectory(prefix='fire-tv-original-backup-') as directory:
    sources = [JAVA / 'backup/OriginalBackup.java', JAVA / 'migration/PreferenceSnapshot.java',
               JAVA / 'migration/LegacyJson.java', Path(__file__).with_name('OriginalBackupTest.java')]
    subprocess.run(['javac', '-Xlint:all', '-Werror', '-d', directory, *map(str, sources)], check=True)
    subprocess.run(['java', '-cp', directory,
                    'com.jeremykenedy.firetv.ui.nativeapp.backup.OriginalBackupTest'], check=True)
