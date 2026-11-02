#!/usr/bin/env python3
"""Build only the standalone emulator callback fixture, with no preview or vendor inputs."""
from pathlib import Path
import os
import shutil
import subprocess
from zipfile import ZipFile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
SOURCE = ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp/device'
SDK = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
           or Path.home() / 'Library/Android/sdk')
TOOLS = SDK / 'build-tools/36.0.0'
ANDROID = SDK / 'platforms/android-36/android.jar'
WORK = HERE / '.build'
WORK.mkdir(exist_ok=True)
for folder in ('classes', 'dex'):
    shutil.rmtree(WORK / folder, ignore_errors=True)
    (WORK / folder).mkdir()

def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)

sources = [SOURCE / (name + '.java') for name in (
    'HomeRoutingPolicy', 'HomeHandoffStore', 'HomeSetupPolicy', 'HomeSetup', 'HomeKeyService', 'DeviceRoutes',
    'HomeCommandHandler', 'HomeCommandPolicy', 'HomeCommandStore')]
run('javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', ANDROID,
    '-d', WORK / 'classes', *sources, HERE / 'RoutingFixture.java')
run(TOOLS / 'd8', '--release', '--lib', ANDROID, '--min-api', '27', '--output', WORK / 'dex',
    *sorted((WORK / 'classes').rglob('*.class')))
run(TOOLS / 'aapt2', 'link', '-I', ANDROID, '--manifest', HERE / 'AndroidManifest.xml', '-o', WORK / 'base.apk')
with ZipFile(WORK / 'base.apk', 'a') as output:
    output.write(WORK / 'dex/classes.dex', 'classes.dex')
run(TOOLS / 'zipalign', '-P', '16', '-f', '4', WORK / 'base.apk', WORK / 'aligned.apk')
key = WORK / 'fixture.jks'
if not key.exists():
    run('keytool', '-genkeypair', '-keystore', key, '-storepass', 'android', '-keypass', 'android',
        '-alias', 'fixture', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '3650',
        '-dname', 'CN=Local Home Routing Fixture', '-noprompt')
run(TOOLS / 'apksigner', 'sign', '--ks', key, '--ks-pass', 'pass:android', '--ks-key-alias', 'fixture',
    '--v4-signing-enabled', 'false', '--out', WORK / 'routing-fixture.apk', WORK / 'aligned.apk')
run(TOOLS / 'apksigner', 'verify', WORK / 'routing-fixture.apk')
print(WORK / 'routing-fixture.apk')
