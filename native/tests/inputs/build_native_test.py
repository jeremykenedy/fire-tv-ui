#!/usr/bin/env python3
"""Build only isolated input fixtures/proof; does not install or operate any device."""
from pathlib import Path
import shutil
import os
import subprocess
from zipfile import ZipFile

here = Path(__file__).resolve().parent
root = here.parents[1]
sdk = Path.home() / 'Library/Android/sdk'
tools = sdk / 'build-tools/36.0.0'
android = sdk / 'platforms/android-36/android.jar'
work = here / '.build'
work.mkdir(exist_ok=True)
signer = root / '.build/preview.jks'
if not signer.exists():
    raise SystemExit('Build the preview first for its existing test signer.')

def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)

for name, manifest, sources in (
    ('input-fixture', here / 'fixture/AndroidManifest.xml', list((here / 'fixture').glob('*.java'))),
    ('input-proof', here / 'AndroidManifest.xml', [here / 'InputInstrumentation.java']),
):
    build = work / name
    if build.exists():
        shutil.rmtree(build)
    (build / 'classes').mkdir(parents=True)
    (build / 'dex').mkdir()
    dependencies = list((root / 'src/com/jeremykenedy/firetv/ui/nativeapp/inputs').glob('*.java')) if name == 'input-proof' else []
    run('javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', android,
        '-sourcepath', os.pathsep.join([str(root / 'src'), str(root / '.build/gen')]),
        '-d', build / 'classes', *dependencies, *sources)
    classes = sorted((build / 'classes').rglob('*.class'))
    if name == 'input-proof':
        classes = [path for path in classes if path.name.startswith('InputInstrumentation')]
    run(tools / 'd8', '--release', '--lib', android, '--min-api', '27', '--output', build / 'dex', *classes)
    resources = []
    if name == 'input-fixture':
        run(tools / 'aapt2', 'compile', '--dir', here / 'fixture/res', '-o', build / 'resources.zip')
        resources = [build / 'resources.zip']
    run(tools / 'aapt2', 'link', '-I', android, '--manifest', manifest, '-o', build / 'base.apk', *resources)
    with ZipFile(build / 'base.apk', 'a') as output:
        output.write(build / 'dex/classes.dex', 'classes.dex')
    run(tools / 'zipalign', '-P', '16', '-f', '4', build / 'base.apk', build / 'aligned.apk')
    run(tools / 'apksigner', 'sign', '--ks', signer, '--ks-pass', 'pass:android', '--ks-key-alias', 'preview',
        '--v4-signing-enabled', 'false', '--out', work / f'{name}.apk', build / 'aligned.apk')
    print(work / f'{name}.apk')
