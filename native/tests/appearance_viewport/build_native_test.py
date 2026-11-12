#!/usr/bin/env python3
"""Build the guarded appearance viewport proof with the reviewed candidate's classes and lab key."""
from pathlib import Path
import argparse
import subprocess
from zipfile import ZipFile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SDK = Path.home() / 'Library/Android/sdk'
TOOLS = SDK / 'build-tools/36.0.0'
ANDROID = SDK / 'platforms/android-36/android.jar'
parser = argparse.ArgumentParser()
parser.add_argument('--classes', type=Path, required=True)
parser.add_argument('--keystore', type=Path, required=True)
args = parser.parse_args()
WORK = HERE / '.build'
for folder in (WORK, WORK / 'classes', WORK / 'dex'):
    folder.mkdir(parents=True, exist_ok=True)
for folder, pattern in ((WORK / 'classes', '*.class'), (WORK / 'dex', '*.dex')):
    for stale in folder.rglob(pattern): stale.unlink()

def run(*values):
    subprocess.run([str(value) for value in values], check=True)

run('javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', ANDROID,
    '-cp', args.classes, '-d', WORK / 'classes', HERE / 'AppearanceViewportInstrumentation.java',
    ROOT / 'tests/state_guard/GuardStore.java')
run(TOOLS / 'd8', '--release', '--lib', ANDROID, '--min-api', '27', '--output', WORK / 'dex',
    *sorted((WORK / 'classes').rglob('*.class')))
run(TOOLS / 'aapt2', 'link', '-I', ANDROID, '--manifest', HERE / 'AndroidManifest.xml', '-o', WORK / 'base.apk')
with ZipFile(WORK / 'base.apk', 'a') as archive:
    archive.write(WORK / 'dex/classes.dex', 'classes.dex')
run(TOOLS / 'zipalign', '-P', '16', '-f', '4', WORK / 'base.apk', WORK / 'aligned.apk')
run(TOOLS / 'apksigner', 'sign', '--ks', args.keystore, '--ks-pass', 'pass:android', '--ks-key-alias', 'preview',
    '--v4-signing-enabled', 'false', '--out', WORK / 'appearance-viewport-proof.apk', WORK / 'aligned.apk')
print(WORK / 'appearance-viewport-proof.apk')
