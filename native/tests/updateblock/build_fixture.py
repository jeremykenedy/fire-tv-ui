#!/usr/bin/env python3
from pathlib import Path
import hashlib
import json
import os
import shutil
import subprocess
from zipfile import ZipFile

here = Path(__file__).resolve().parent
root = here.parents[1]
sdk = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or Path.home() / 'Library/Android/sdk')
tools, android = sdk / 'build-tools/36.0.0', sdk / 'platforms/android-36/android.jar'
work = here / '.build'
for folder in (work / 'classes', work / 'dex'):
    if folder.exists(): shutil.rmtree(folder)
    folder.mkdir(parents=True)
sources = [root / 'src/com/jeremykenedy/firetv/ui/nativeapp/device/updates/UpdateBlockPolicy.java', here / 'UpdateBlockInstrumentation.java']
def run(*args): subprocess.run([str(value) for value in args], check=True)
run('javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', android, '-d', work / 'classes', *sources)
run(tools / 'd8', '--release', '--lib', android, '--min-api', '27', '--output', work / 'dex', *sorted((work / 'classes').rglob('*.class')))
run(tools / 'aapt2', 'link', '-I', android, '--manifest', here / 'AndroidManifest.xml', '-o', work / 'base.apk')
with ZipFile(work / 'base.apk', 'a') as apk: apk.write(work / 'dex/classes.dex', 'classes.dex')
run(tools / 'zipalign', '-P', '16', '-f', '4', work / 'base.apk', work / 'aligned.apk')
key = work / 'fixture.jks'
if not key.exists(): run('keytool', '-genkeypair', '-keystore', key, '-storepass', 'android', '-keypass', 'android', '-alias', 'fixture', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '3650', '-dname', 'CN=Isolated Update Fixture', '-noprompt')
apk = work / 'update-proof.apk'
run(tools / 'apksigner', 'sign', '--ks', key, '--ks-pass', 'pass:android', '--ks-key-alias', 'fixture', '--v4-signing-enabled', 'false', '--out', apk, work / 'aligned.apk')
run(tools / 'apksigner', 'verify', apk)
(work / 'proof.json').write_text(json.dumps({'package': 'com.jeremykenedy.firetv.updateproof', 'nativeExecution': 'not run', 'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(), 'network': False, 'systemChanges': False}, indent=2) + '\n')
print(apk)
