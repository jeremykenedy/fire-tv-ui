#!/usr/bin/env python3
"""Build the guarded preview-only saved setup UI fixture."""
from pathlib import Path
import hashlib
import subprocess
import tempfile
from zipfile import ZipFile

HERE = Path(__file__).resolve().parent
NATIVE = HERE.parents[1]
SDK = Path.home() / 'Library/Android/sdk'
TOOLS = SDK / 'build-tools/36.0.0'
ANDROID = SDK / 'platforms/android-36/android.jar'
OUTPUT = HERE / 'legacy-setup-ui-proof.apk'


def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)


with tempfile.TemporaryDirectory(prefix='legacy-setup-ui-') as directory:
    work = Path(directory)
    (work / 'classes').mkdir()
    (work / 'dex').mkdir()
    run('javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', ANDROID,
        '-cp', NATIVE / '.build/classes', '-d', work / 'classes', HERE / 'LegacySetupUiInstrumentation.java', HERE / 'LegacyIconSetupInstrumentation.java', HERE / 'CurrentIconSetupInstrumentation.java',
        NATIVE / 'tests/state_guard/GuardStore.java')
    run(TOOLS / 'd8', '--release', '--lib', ANDROID, '--min-api', '27', '--output', work / 'dex',
        *sorted((work / 'classes').rglob('*.class')))
    run(TOOLS / 'aapt2', 'link', '-I', ANDROID, '--manifest', HERE / 'LegacySetupUiManifest.xml', '-o', work / 'base.apk')
    with ZipFile(work / 'base.apk', 'a') as apk:
        apk.write(work / 'dex/classes.dex', 'classes.dex')
    run(TOOLS / 'zipalign', '-P', '16', '-f', '4', work / 'base.apk', work / 'aligned.apk')
    run(TOOLS / 'apksigner', 'sign', '--ks', NATIVE / '.build/preview.jks', '--ks-pass', 'pass:android',
        '--ks-key-alias', 'preview', '--v4-signing-enabled', 'false', '--out', OUTPUT, work / 'aligned.apk')
    run(TOOLS / 'apksigner', 'verify', OUTPUT)
print(f'{OUTPUT} sha256={hashlib.sha256(OUTPUT.read_bytes()).hexdigest()}')
