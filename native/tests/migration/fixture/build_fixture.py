#!/usr/bin/env python3
"""Build a self-targeted migration crash fixture without shared app build outputs."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
from zipfile import ZipFile

HERE = Path(__file__).resolve().parent
NATIVE = HERE.parents[2]
SDK = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
           or Path.home() / 'Library/Android/sdk')
TOOLS = SDK / 'build-tools/36.0.0'
ANDROID = SDK / 'platforms/android-36/android.jar'
WORK = HERE / '.build'
for folder in (WORK / 'classes', WORK / 'dex'):
    if folder.exists():
        shutil.rmtree(folder)
    folder.mkdir(parents=True)
SOURCE = NATIVE / 'src/com/jeremykenedy/firetv/ui/nativeapp'
sources = sorted((SOURCE / 'migration').glob('*.java'))
sources += [SOURCE / name for name in ('folders/FolderOptions.java', 'locks/LockStore.java', 'locks/LockState.java', 'locks/LockResult.java', 'holidays/HolidayScenes.java', 'appgrid/HomeTileOrder.java', 'appgrid/AppGridOptions.java', 'focus/FocusOptions.java',
            'layout/LayoutOptions.java', 'layout/LayoutItem.java', 'cards/CardOptions.java', 'cards/CardPreferences.java',
            'appearance/AppearanceOptions.java', 'appearance/PaletteRecipes.java', 'inputs/InputEntry.java', 'inputs/InputOptions.java')]
sources += [SOURCE / ('providers/' + name) for name in ('RecommendationKeys.java', 'OwnedPlexIdentity.java', 'LegacyRecommendationIdentities.java', 'AndroidRecommendationIdentityInventory.java', 'TvRecommendations.java')]
sources.append(HERE / 'MigrationCrashInstrumentation.java')
sources.append(HERE / 'PlexIdentityInstrumentation.java')
sources.append(HERE / 'DeviceProtectionInstrumentation.java')
sources += [SOURCE / 'device/protection/LegacyDeviceProtection.java', SOURCE / 'device/protection/LegacyProtectionSession.java', SOURCE / 'device/commands/LegacySettingTransaction.java', SOURCE / 'device/commands/SettingCommandPolicy.java']

def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)

run('javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', ANDROID,
    '-d', WORK / 'classes', *sources)
run(TOOLS / 'd8', '--release', '--lib', ANDROID, '--min-api', '27', '--output', WORK / 'dex',
    *sorted((WORK / 'classes').rglob('*.class')))
run(TOOLS / 'aapt2', 'link', '-I', ANDROID, '--manifest', HERE / 'AndroidManifest.xml', '-o', WORK / 'base.apk')
with ZipFile(WORK / 'base.apk', 'a') as output:
    output.write(WORK / 'dex/classes.dex', 'classes.dex')
run(TOOLS / 'zipalign', '-P', '16', '-f', '4', WORK / 'base.apk', WORK / 'aligned.apk')
key = WORK / 'fixture.jks'
if not key.exists():
    run('keytool', '-genkeypair', '-keystore', key, '-storepass', 'android', '-keypass', 'android',
        '-alias', 'fixture', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '3650',
        '-dname', 'CN=Isolated Migration Fixture', '-noprompt')
apk = WORK / 'migration-crash-proof.apk'
run(TOOLS / 'apksigner', 'sign', '--ks', key, '--ks-pass', 'pass:android', '--ks-key-alias', 'fixture',
    '--v4-signing-enabled', 'false', '--out', apk, WORK / 'aligned.apk')
run(TOOLS / 'apksigner', 'verify', apk)
(WORK / 'build-proof.json').write_text(json.dumps({
    'package': 'com.jeremykenedy.firetv.migrationproof', 'productionDataAccess': False,
    'nativeExecution': 'not run', 'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
    'sources': {str(path.relative_to(NATIVE)): hashlib.sha256(path.read_bytes()).hexdigest() for path in sources}
}, indent=2) + '\n')
print(apk)
