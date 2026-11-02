#!/usr/bin/env python3
"""Build the emulator-only photo frame instrumentation against the source preview."""
from pathlib import Path
import subprocess
from zipfile import ZipFile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SDK = Path.home() / 'Library/Android/sdk'
TOOLS = SDK / 'build-tools/36.0.0'
ANDROID = SDK / 'platforms/android-36/android.jar'
WORK = HERE / '.build'
for folder in (WORK, WORK / 'classes', WORK / 'dex'):
    folder.mkdir(parents=True, exist_ok=True)

for folder, suffix in ((WORK / 'classes', '*.class'), (WORK / 'dex', '*.dex')):
    for stale in folder.rglob(suffix):
        stale.unlink()

def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)

run('javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', ANDROID,
    '-sourcepath', str(ROOT / 'src') + ':' + str(ROOT / '.build/gen'), '-implicit:none', '-d', WORK / 'classes', HERE / 'PhotosInstrumentation.java')
run(TOOLS / 'd8', '--release', '--lib', ANDROID, '--min-api', '27', '--output', WORK / 'dex',
    *sorted((WORK / 'classes').rglob('*.class')))
run(TOOLS / 'aapt2', 'link', '-I', ANDROID, '--manifest', HERE / 'AndroidManifest.xml', '-o', WORK / 'base.apk')
with ZipFile(WORK / 'base.apk', 'a') as output:
    output.write(WORK / 'dex/classes.dex', 'classes.dex')
run(TOOLS / 'zipalign', '-P', '16', '-f', '4', WORK / 'base.apk', WORK / 'aligned.apk')
run(TOOLS / 'apksigner', 'sign', '--ks', ROOT / '.build/preview.jks', '--ks-pass', 'pass:android',
    '--ks-key-alias', 'preview', '--v4-signing-enabled', 'false', '--out', WORK / 'photos-proof.apk', WORK / 'aligned.apk')
print(WORK / 'photos-proof.apk')

FIXTURE = HERE / 'fixture'
for folder in (WORK / 'fixture-classes', WORK / 'fixture-dex'):
    folder.mkdir(exist_ok=True)
run('javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', ANDROID,
    '-d', WORK / 'fixture-classes', *sorted(FIXTURE.glob('*.java')))
run(TOOLS / 'd8', '--release', '--lib', ANDROID, '--min-api', '27', '--output', WORK / 'fixture-dex',
    *sorted((WORK / 'fixture-classes').rglob('*.class')))
run(TOOLS / 'aapt2', 'link', '-I', ANDROID, '--manifest', FIXTURE / 'AndroidManifest.xml', '-o', WORK / 'fixture-base.apk')
with ZipFile(WORK / 'fixture-base.apk', 'a') as output:
    output.write(WORK / 'fixture-dex/classes.dex', 'classes.dex')
run(TOOLS / 'zipalign', '-P', '16', '-f', '4', WORK / 'fixture-base.apk', WORK / 'fixture-aligned.apk')
run(TOOLS / 'apksigner', 'sign', '--ks', ROOT / '.build/preview.jks', '--ks-pass', 'pass:android',
    '--ks-key-alias', 'preview', '--v4-signing-enabled', 'false', '--out', WORK / 'photo-fixture.apk', WORK / 'fixture-aligned.apk')
print(WORK / 'photo-fixture.apk')
