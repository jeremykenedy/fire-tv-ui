#!/usr/bin/env python3
"""Build an isolated fixture/instrumentation APK against the existing preview classes."""
import os
from pathlib import Path
import subprocess
from zipfile import ZipFile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SDK = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or Path.home() / 'Library/Android/sdk')
TOOLS = SDK / 'build-tools/36.0.0'
ANDROID = SDK / 'platforms/android-36/android.jar'
WORK = HERE / '.build'
for folder in (WORK, WORK / 'classes', WORK / 'dex'):
    folder.mkdir(parents=True, exist_ok=True)
for path in (WORK / 'classes').rglob('*.class'):
    path.unlink()
def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)
run('javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', ANDROID,
    '-cp', ROOT / '.build/classes', '-d', WORK / 'classes', HERE / 'BackupInstrumentation.java', HERE / 'BackupFixtureProvider.java')
run(TOOLS / 'd8', '--release', '--lib', ANDROID, '--min-api', '27', '--output', WORK / 'dex', *sorted((WORK / 'classes').rglob('*.class')))
run(TOOLS / 'aapt2', 'link', '-I', ANDROID, '--manifest', HERE / 'AndroidManifest.xml', '-o', WORK / 'base.apk')
with ZipFile(WORK / 'base.apk', 'a') as output:
    output.write(WORK / 'dex/classes.dex', 'classes.dex')
run(TOOLS / 'zipalign', '-P', '16', '-f', '4', WORK / 'base.apk', WORK / 'aligned.apk')
run(TOOLS / 'apksigner', 'sign', '--ks', ROOT / '.build/preview.jks', '--ks-pass', 'pass:android', '--ks-key-alias', 'preview',
    '--v4-signing-enabled', 'false', '--out', WORK / 'backup-proof.apk', WORK / 'aligned.apk')
print(WORK / 'backup-proof.apk')
