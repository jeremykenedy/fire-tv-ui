#!/usr/bin/env python3
"""Prepare isolated saver fixture and native administration proof. Does not install or run."""
from pathlib import Path
import shutil
import subprocess
from zipfile import ZipFile
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SDK = Path.home() / 'Library/Android/sdk'
TOOLS = SDK / 'build-tools/36.0.0'
ANDROID = SDK / 'platforms/android-36/android.jar'
WORK = HERE / '.build'
SIGNER = ROOT / '.build/preview.jks'
if not SIGNER.exists():
    raise SystemExit('Build the preview first; proof uses its isolated signer.')

def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)

for name in ('fixture', 'proof'):
    build = WORK / name
    if build.exists():
        shutil.rmtree(build)
    (build / 'classes').mkdir(parents=True)
    (build / 'dex').mkdir()
    fixture = name == 'fixture'
    sources = sorted((HERE / 'fixture').glob('*.java')) if fixture else [HERE / 'ScreensaverInstrumentation.java']
    run('javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', ANDROID,
        '-cp', ROOT / '.build/classes', '-d', build / 'classes', *sources)
    run(TOOLS / 'd8', '--release', '--lib', ANDROID, '--min-api', '27', '--output', build / 'dex',
        *sorted((build / 'classes').rglob('*.class')))
    resources = []
    if fixture:
        run(TOOLS / 'aapt2', 'compile', '--dir', HERE / 'fixture/res', '-o', build / 'res.zip')
        resources = [build / 'res.zip']
    run(TOOLS / 'aapt2', 'link', '-I', ANDROID, '--manifest',
        HERE / ('fixture/AndroidManifest.xml' if fixture else 'AndroidManifest.xml'),
        '-o', build / 'base.apk', *resources)
    with ZipFile(build / 'base.apk', 'a') as output:
        output.write(build / 'dex/classes.dex', 'classes.dex')
    run(TOOLS / 'zipalign', '-P', '16', '-f', '4', build / 'base.apk', build / 'aligned.apk')
    run(TOOLS / 'apksigner', 'sign', '--ks', SIGNER, '--ks-pass', 'pass:android', '--ks-key-alias', 'preview',
        '--v4-signing-enabled', 'false', '--out', WORK / f'saver-{name}.apk', build / 'aligned.apk')
    print(WORK / f'saver-{name}.apk')
