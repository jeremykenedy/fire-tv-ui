#!/usr/bin/env python3
"""Build isolated remote behavior instrumentation, without device operations."""
from pathlib import Path
import subprocess
import shutil
from zipfile import ZipFile

here = Path(__file__).resolve().parent
root = here.parents[1]
sdk = Path.home() / 'Library/Android/sdk'
tools = sdk / 'build-tools/36.0.0'
android = sdk / 'platforms/android-36/android.jar'
work = here / '.build'
for name in ('classes', 'dex'):
    if (work / name).exists():
        shutil.rmtree(work / name)
    (work / name).mkdir(parents=True, exist_ok=True)
source = root / 'src/com/jeremykenedy/firetv/ui/nativeapp/device/remotebuttons'
def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)
run('javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', android,
    '-d', work / 'classes', *[source / name for name in ('RemoteButtonPolicy.java', 'RemoteButtonPreferences.java', 'RemoteButtonDispatcher.java')],
    here / 'RemoteButtonsInstrumentation.java')
run(tools / 'd8', '--release', '--lib', android, '--min-api', '27', '--output', work / 'dex',
    *sorted((work / 'classes').rglob('*.class')))
run(tools / 'aapt2', 'link', '-I', android, '--manifest', here / 'AndroidManifest.xml', '-o', work / 'base.apk')
with ZipFile(work / 'base.apk', 'a') as apk:
    apk.write(work / 'dex/classes.dex', 'classes.dex')
run(tools / 'zipalign', '-P', '16', '-f', '4', work / 'base.apk', work / 'aligned.apk')
run(tools / 'apksigner', 'sign', '--ks', root / '.build/preview.jks', '--ks-pass', 'pass:android',
    '--ks-key-alias', 'preview', '--v4-signing-enabled', 'false', '--out', work / 'remote-button-proof.apk', work / 'aligned.apk')
print(work / 'remote-button-proof.apk')
