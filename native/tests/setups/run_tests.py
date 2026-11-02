#!/usr/bin/env python3
"""Host-test sanitized setup state, faults and media; compile the native manager UI."""
import ast
from pathlib import Path
import re
import os
import subprocess
import sys
import tempfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
NATIVE = ROOT / 'native'
SOURCE = NATIVE / 'src/com/jeremykenedy/firetv/ui/nativeapp'
sys.path.insert(0, str(ROOT / 'tests'))
from java_runtime import json_runtime

# Reuse the maintained setup harness Android doubles, adding named stores and journal support.
tree = ast.parse((ROOT / 'tests/test_saved_setups.py').read_text())
sources = next(ast.literal_eval(node.value) for node in ast.walk(tree)
               if isinstance(node, ast.Assign) and any(isinstance(t, ast.Name) and t.id == 'sources' for t in node.targets))
sources.pop('com/jeremykenedy/firetv/ui/DeviceSettings.java')
sources['android/content/SharedPreferences.java'] = sources['android/content/SharedPreferences.java'].replace(
    'Editor putInt(String k,int v);', 'Editor putInt(String k,int v); Editor putLong(String k,long v); Editor putFloat(String k,float v);')
sources['android/content/SharedPreferences.java'] = sources['android/content/SharedPreferences.java'].replace(
    'Editor edit();', 'Editor edit(); default String getString(String key,String value){return (String)getAll().getOrDefault(key,null)==null?value:(String)getAll().get(key);}')
sources['android/content/Context.java'] = sources['android/content/Context.java'].replace(
    'public final java.io.File files;', '''public static final int MODE_PRIVATE=0;
 public final Map<String,Prefs> stores=new LinkedHashMap<>();
 public SharedPreferences getSharedPreferences(String name,int mode){if(!stores.containsKey(name))stores.put(name,new Prefs());return stores.get(name);}
 public java.io.File getNoBackupFilesDir(){java.io.File dir=new java.io.File(files,"no-backup");dir.mkdirs();return dir;}
 public final java.io.File files;''').replace(
    'public Editor putInt(String k,int v){', 'public Editor putLong(String k,long v){edits.put(k,v);return this;} public Editor putFloat(String k,float v){edits.put(k,v);return this;} public Editor putInt(String k,int v){')
sources['android/util/AtomicFile.java'] = sources['android/util/AtomicFile.java'].replace(
    'public java.io.FileInputStream openRead()', 'public static boolean failDelete; public void delete(){if(failDelete)return;file.delete();temp.delete();} public java.io.FileInputStream openRead()').replace(
    'return new java.io.FileOutputStream(temp);', 'file.getParentFile().mkdirs();return new java.io.FileOutputStream(temp);')
sources['android/util/Base64.java'] = '''package android.util; public final class Base64 {
 public static final int NO_WRAP=2;
 public static String encodeToString(byte[] value,int flags){return java.util.Base64.getEncoder().encodeToString(value);}
 public static byte[] decode(String value,int flags){return java.util.Base64.getDecoder().decode(value);}
}'''
# Resource constants are compile-only; tests exercise the real reviewed catalog entries.
catalog = (SOURCE / 'icons/IconCatalog.java').read_text()
resources = sorted(set(re.findall(r'R.drawable.(\w+)', catalog)))
sources['com/jeremykenedy/firetv/ui/preview/R.java'] = 'package com.jeremykenedy.firetv.ui.preview; public final class R {public static final class drawable {' + ''.join(f'public static final int {name}={i+1};' for i, name in enumerate(resources)) + '}}'
with tempfile.TemporaryDirectory(prefix='fire-tv-setups-') as temporary:
    target = Path(temporary)
    stubs = []
    for name, value in sources.items():
        path = target / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(value)
        stubs.append(path)
    dependencies = [SOURCE / 'accessibility/AccessibilityPreferences.java', SOURCE / 'holidays/HolidayScenes.java', SOURCE / 'migration/PreferenceSnapshot.java', SOURCE / 'migration/PrivateMediaReference.java',
                    SOURCE / 'inputs/InputEntry.java', SOURCE / 'inputs/InputOptions.java', SOURCE / 'migration/LegacyPresentationMapper.java',
                    SOURCE / 'migration/LegacyJson.java', SOURCE / 'migration/LegacyFolderMigration.java', SOURCE / 'migration/LegacyAppearanceMapper.java', SOURCE / 'migration/LauncherMigration.java', SOURCE / 'migration/PresentationMappingContext.java', SOURCE / 'appgrid/AppGridOptions.java', SOURCE / 'appgrid/HomeTileOrder.java',
                    SOURCE / 'focus/FocusOptions.java', SOURCE / 'cards/CardOptions.java',
                    SOURCE / 'layout/LayoutItem.java', SOURCE / 'layout/LayoutOptions.java',
                    SOURCE / 'folders/FolderOptions.java', SOURCE / 'folders/FolderPreferences.java',
                    SOURCE / 'icons/IconCatalog.java', SOURCE / 'appearance/LegacyGradients.java', SOURCE / 'appearance/AppearanceOptions.java', SOURCE / 'appearance/AppearancePreferences.java', SOURCE / 'appearance/PaletteRecipes.java', SOURCE / 'widget/worldclock/WorldClocks.java',
                    SOURCE / 'widget/breathing/BreathingSession.java']
    managers = sorted(path for path in (SOURCE / 'setups').glob('*.java') if path.name != 'SavedSetupsActivity.java')
    jar = json_runtime()
    subprocess.run(['javac', '-Xlint:unchecked', '-cp', str(jar), '-d', str(target),
                    *map(str, stubs + dependencies + managers), str(HERE / 'SetupTest.java')], check=True)
    subprocess.run(['java', '-cp', str(target) + os.pathsep + str(jar),
                    'com.jeremykenedy.firetv.ui.nativeapp.setups.SetupTest', str(target)], check=True)
    android = Path.home() / 'Library/Android/sdk/platforms/android-36/android.jar'
    subprocess.run(['javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', str(android), '-classpath', str(NATIVE / '.build/classes'),
                    '-d', str(target / 'android'), *map(str, dependencies + [SOURCE / 'migration/AndroidPresentationInventory.java', SOURCE / 'migration/IdentityInventory.java', SOURCE / 'migration/AndroidPrivateMedia.java'] + [SOURCE / ('providers/' + name) for name in ('RecommendationKeys.java', 'OwnedPlexIdentity.java', 'LegacyRecommendationIdentities.java', 'AndroidRecommendationIdentityInventory.java', 'TvRecommendations.java')] + list((SOURCE / 'setups').glob('*.java'))),
                    str(target / 'com/jeremykenedy/firetv/ui/preview/R.java'), str(HERE / 'SetupInstrumentation.java')], check=True)
print('Saved setup host checks and isolated Android API 36 manager compilation passed.')
