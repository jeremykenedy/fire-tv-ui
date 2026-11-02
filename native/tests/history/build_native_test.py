#!/usr/bin/env python3
"""Build the History Home overlay against the frozen preview and its signer."""
from pathlib import Path
import subprocess
from zipfile import ZipFile

HERE = Path(__file__).resolve().parent
CANDIDATE = HERE.parents[1]
SDK = Path.home() / 'Library/Android/sdk'
TOOLS = SDK / 'build-tools/36.0.0'
ANDROID = SDK / 'platforms/android-36/android.jar'
WORK = HERE / '.build'
for folder in (WORK, WORK / 'classes', WORK / 'dex'):
    folder.mkdir(parents=True, exist_ok=True)
for folder, pattern in ((WORK / 'classes', '*.class'), (WORK / 'dex', '*.dex')):
    for stale in folder.rglob(pattern):
        stale.unlink()

def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)

run('javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', ANDROID,
    '-cp', CANDIDATE / '.build/classes', '-d', WORK / 'classes', HERE / 'HistoryHomeInstrumentation.java',
    CANDIDATE / 'tests/state_guard/GuardStore.java')
run(TOOLS / 'd8', '--release', '--lib', ANDROID, '--min-api', '27', '--output', WORK / 'dex',
    *sorted((WORK / 'classes').rglob('*.class')))
run(TOOLS / 'aapt2', 'link', '-I', ANDROID, '--manifest', HERE / 'AndroidManifest.xml', '-o', WORK / 'base.apk')
with ZipFile(WORK / 'base.apk', 'a') as output:
    output.write(WORK / 'dex/classes.dex', 'classes.dex')
run(TOOLS / 'zipalign', '-P', '16', '-f', '4', WORK / 'base.apk', WORK / 'aligned.apk')
run(TOOLS / 'apksigner', 'sign', '--ks', CANDIDATE / '.build/preview.jks', '--ks-pass', 'pass:android',
    '--ks-key-alias', 'preview', '--v4-signing-enabled', 'false', '--out', WORK / 'history-home-proof.apk', WORK / 'aligned.apk')
run(TOOLS / 'apksigner', 'verify', WORK / 'history-home-proof.apk')
print(WORK / 'history-home-proof.apk')
