#!/usr/bin/env python3
"""Build only the unprivileged broadcast sender; never builds/installs the production candidate."""
from pathlib import Path
import os
import shutil
import subprocess
from zipfile import ZipFile

HERE = Path(__file__).resolve().parent
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

run('javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', ANDROID,
    '-d', WORK / 'classes', HERE / 'UnprivilegedSender.java')
run(TOOLS / 'd8', '--release', '--lib', ANDROID, '--min-api', '27', '--output', WORK / 'dex',
    *sorted((WORK / 'classes').rglob('*.class')))
run(TOOLS / 'aapt2', 'link', '-I', ANDROID, '--manifest', HERE / 'AndroidManifest.xml', '-o', WORK / 'base.apk')
with ZipFile(WORK / 'base.apk', 'a') as output:
    output.write(WORK / 'dex/classes.dex', 'classes.dex')
run(TOOLS / 'zipalign', '-P', '16', '-f', '4', WORK / 'base.apk', WORK / 'aligned.apk')
key = WORK / 'unprivileged-sender.jks'
if not key.exists():
    run('keytool', '-genkeypair', '-keystore', key, '-storepass', 'android', '-keypass', 'android',
        '-alias', 'sender', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '3650',
        '-dname', 'CN=Local Unprivileged Sender Fixture', '-noprompt')
run(TOOLS / 'apksigner', 'sign', '--ks', key, '--ks-pass', 'pass:android', '--ks-key-alias', 'sender',
    '--v4-signing-enabled', 'false', '--out', WORK / 'unprivileged-home-sender.apk', WORK / 'aligned.apk')
run(TOOLS / 'apksigner', 'verify', WORK / 'unprivileged-home-sender.apk')
print(WORK / 'unprivileged-home-sender.apk')
