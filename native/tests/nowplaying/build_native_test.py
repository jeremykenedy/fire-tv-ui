#!/usr/bin/env python3
"""Build native proof and synthetic cross-app MediaSession fixture; installation is operator-owned."""
from pathlib import Path
import argparse
import os
import subprocess
from zipfile import ZipFile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SDK = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or Path.home() / 'Library/Android/sdk')
TOOLS = SDK / 'build-tools/36.0.0'
ANDROID = SDK / 'platforms/android-36/android.jar'
parser = argparse.ArgumentParser()
parser.add_argument('--output', type=Path, default=HERE / '.build')
WORK = parser.parse_args().output

def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)

def package(name, manifest, sources, fixture=False):
    folder = WORK / name
    classes = folder / 'classes'
    dex = folder / 'dex'
    classes.mkdir(parents=True, exist_ok=True)
    dex.mkdir(parents=True, exist_ok=True)
    for stale in classes.rglob('*.class'):
        stale.unlink()
    for stale in dex.glob('*.dex'):
        stale.unlink()
    arguments = ['javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', ANDROID, '-d', classes]
    if not fixture:
        arguments += ['-sourcepath', str(ROOT / 'src') + ':' + str(ROOT / '.build/gen'), '-implicit:none']
    run(*arguments, *sources)
    run(TOOLS / 'd8', '--release', '--lib', ANDROID, '--min-api', '27', '--output', dex, *sorted(classes.rglob('*.class')))
    run(TOOLS / 'aapt2', 'link', '-I', ANDROID, '--manifest', manifest, '-o', folder / 'base.apk')
    with ZipFile(folder / 'base.apk', 'a') as output:
        output.write(dex / 'classes.dex', 'classes.dex')
    run(TOOLS / 'zipalign', '-P', '16', '-f', '4', folder / 'base.apk', folder / 'aligned.apk')
    artifact = WORK / (name + '.apk')
    run(TOOLS / 'apksigner', 'sign', '--ks', ROOT / '.build/preview.jks', '--ks-pass', 'pass:android',
        '--ks-key-alias', 'preview', '--v4-signing-enabled', 'false', '--out', artifact, folder / 'aligned.apk')
    print(artifact)

package('nowplaying-proof', HERE / 'AndroidManifest.xml', [HERE / 'NowPlayingInstrumentation.java'])
package('nowplaying-fixture', HERE / 'fixture/AndroidManifest.xml', sorted((HERE / 'fixture').glob('*.java')), fixture=True)
