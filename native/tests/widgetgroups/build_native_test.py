#!/usr/bin/env python3
"""Build the enabled-widget native proof using the reviewed preview debug signer."""
import argparse
import os
from pathlib import Path
import subprocess
from zipfile import ZipFile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SDK = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or Path.home() / 'Library/Android/sdk')
TOOLS = SDK / 'build-tools/36.0.0'
ANDROID = SDK / 'platforms/android-36/android.jar'
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--keystore', type=Path, required=True, help='Reviewed candidate preview.jks; never a production signer.')
args = parser.parse_args()
WORK = HERE / '.build'
for folder in (WORK, WORK / 'classes', WORK / 'dex'):
    folder.mkdir(parents=True, exist_ok=True)
for folder, suffix in ((WORK / 'classes', '*.class'), (WORK / 'dex', '*.dex')):
    for path in folder.rglob(suffix):
        path.unlink()
def run(*arguments):
    subprocess.run([str(value) for value in arguments], check=True)
run('javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', ANDROID, '-cp', ROOT / '.build/classes', '-d', WORK / 'classes', HERE / 'EnabledWidgetInstrumentation.java', ROOT / 'tests/state_guard/GuardStore.java')
run(TOOLS / 'd8', '--release', '--lib', ANDROID, '--min-api', '27', '--output', WORK / 'dex', *sorted((WORK / 'classes').rglob('*.class')))
run(TOOLS / 'aapt2', 'link', '-I', ANDROID, '--manifest', HERE / 'AndroidManifest.xml', '-o', WORK / 'base.apk')
with ZipFile(WORK / 'base.apk', 'a') as output:
    output.write(WORK / 'dex/classes.dex', 'classes.dex')
run(TOOLS / 'zipalign', '-P', '16', '-f', '4', WORK / 'base.apk', WORK / 'aligned.apk')
run(TOOLS / 'apksigner', 'sign', '--ks', args.keystore, '--ks-pass', 'pass:android', '--ks-key-alias', 'preview',
    '--v4-signing-enabled', 'false', '--out', WORK / 'enabled-widget-proof.apk', WORK / 'aligned.apk')
run(TOOLS / 'apksigner', 'verify', WORK / 'enabled-widget-proof.apk')
print(WORK / 'enabled-widget-proof.apk')
