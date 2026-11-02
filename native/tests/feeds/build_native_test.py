#!/usr/bin/env python3
"""Build a standalone feed fixture with no network permission; never touches shared app outputs or devices."""
from pathlib import Path
import subprocess
from zipfile import ZipFile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SOURCE = ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp'
SDK = Path.home() / 'Library/Android/sdk'
TOOLS = SDK / 'build-tools/36.0.0'
ANDROID = SDK / 'platforms/android-36/android.jar'
WORK = HERE / '.build'
for path in (WORK, WORK / 'classes', WORK / 'dex'):
    path.mkdir(exist_ok=True)
for path, pattern in ((WORK / 'classes', '*.class'), (WORK / 'dex', '*.dex')):
    for stale in path.rglob(pattern): stale.unlink()

def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)

run('javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', ANDROID,
    '-d', WORK / 'classes', SOURCE / 'layout/WidgetProvider.java', SOURCE / 'layout/LayoutItem.java',
    SOURCE / 'migration/LegacyJson.java',
    *[SOURCE / 'locks' / name for name in ('LauncherLocks.java', 'LockStore.java', 'LockState.java', 'LockResult.java')],
    *sorted((SOURCE / 'widget/feeds').glob('*.java')), HERE / 'FeedFixtureActivity.java', HERE / 'FeedInstrumentation.java')
run(TOOLS / 'd8', '--release', '--lib', ANDROID, '--min-api', '27', '--output', WORK / 'dex',
    *sorted((WORK / 'classes').rglob('*.class')))
run(TOOLS / 'aapt2', 'link', '-I', ANDROID, '--manifest', HERE / 'AndroidManifest.xml', '-o', WORK / 'base.apk')
with ZipFile(WORK / 'base.apk', 'a') as output:
    output.write(WORK / 'dex/classes.dex', 'classes.dex')
run(TOOLS / 'zipalign', '-P', '16', '-f', '4', WORK / 'base.apk', WORK / 'aligned.apk')
key = WORK / 'fixture.jks'
if not key.exists():
    run('keytool', '-genkeypair', '-keystore', key, '-storepass', 'android', '-keypass', 'android',
        '-alias', 'fixture', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '365', '-dname', 'CN=Local feed fixture', '-noprompt')
run(TOOLS / 'apksigner', 'sign', '--ks', key, '--ks-pass', 'pass:android', '--ks-key-alias', 'fixture',
    '--v4-signing-enabled', 'false', '--out', WORK / 'feed-fixture.apk', WORK / 'aligned.apk')
run(TOOLS / 'apksigner', 'verify', WORK / 'feed-fixture.apk')
print(WORK / 'feed-fixture.apk')
