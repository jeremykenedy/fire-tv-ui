#!/usr/bin/env python3
"""Build isolated emulator producer and provider instrumentation; does not install or run either."""
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
WORK.mkdir(exist_ok=True)
KEY = WORK / 'fixture.jks'

def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)

if not KEY.exists():
    run('keytool', '-genkeypair', '-keystore', KEY, '-storepass', 'android', '-keypass', 'android',
        '-alias', 'preview', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000',
        '-dname', 'CN=Local provider test')

# Match the preview signer for instrumentation without writing shared build outputs.
SIGNER = ROOT / '.build/preview.jks'
if not SIGNER.exists():
    raise SystemExit('Build the preview first so instrumentation can share its test signer.')

for name, manifest, sources, dependencies, key in (
    ('provider-fixture', HERE / 'fixture/AndroidManifest.xml', list((HERE / 'fixture').glob('*.java')), [], KEY),
    ('provider-proof', HERE / 'AndroidManifest.xml', [HERE / 'ProviderInstrumentation.java'],
     list((ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp/providers').glob('*.java'))
     + [ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp' / source for source in (
         'layout/LayoutItem.java', 'layout/LayoutOptions.java', 'layout/LayoutPreferences.java',
         'cards/CardOptions.java', 'cards/CardPreferences.java',
         'appgrid/AppGridOptions.java', 'appgrid/AppGridPreferences.java')], SIGNER),
):
    build = WORK / name
    if build.exists():
        shutil.rmtree(build)
    (build / 'classes').mkdir(parents=True)
    (build / 'dex').mkdir()
    run('javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', ANDROID,
        '-d', build / 'classes', *dependencies, *sources)
    # Production classes compile for API verification, but instrumentation loads them from its target.
    dex_classes = sorted((build / 'classes').rglob('*.class'))
    if name == 'provider-proof':
        dex_classes = [path for path in dex_classes if path.name.startswith('ProviderInstrumentation')]
    run(TOOLS / 'd8', '--release', '--lib', ANDROID, '--min-api', '27', '--output', build / 'dex', *dex_classes)
    run(TOOLS / 'aapt2', 'link', '-I', ANDROID, '--manifest', manifest, '-o', build / 'base.apk')
    with ZipFile(build / 'base.apk', 'a') as output:
        output.write(build / 'dex/classes.dex', 'classes.dex')
    run(TOOLS / 'zipalign', '-P', '16', '-f', '4', build / 'base.apk', build / 'aligned.apk')
    run(TOOLS / 'apksigner', 'sign', '--ks', key, '--ks-pass', 'pass:android', '--ks-key-alias', 'preview',
        '--v4-signing-enabled', 'false', '--out', WORK / f'{name}.apk', build / 'aligned.apk')
    print(WORK / f'{name}.apk')
