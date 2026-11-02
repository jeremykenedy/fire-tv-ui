#!/usr/bin/env python3
"""Build a fixed read-only recovery inspection helper without changing candidate output."""
import argparse
import hashlib
from pathlib import Path
import subprocess
from zipfile import ZipFile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--candidate-native-root', type=Path, required=True)
parser.add_argument('--candidate-sha256', required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
candidate = args.candidate_native_root.resolve()
if hashlib.sha256((candidate / 'dist/fire-tv-ui-source-preview.apk').read_bytes()).hexdigest() != args.candidate_sha256:
    raise SystemExit('Candidate APK does not match the authorized hash.')
here = Path(__file__).resolve().parent
work = args.output.resolve()
if work.exists():
    raise SystemExit('Use a new isolated output directory.')
for folder in (work, work / 'classes', work / 'dex'):
    folder.mkdir(parents=True, exist_ok=True)
sdk = Path.home() / 'Library/Android/sdk'
android = sdk / 'platforms/android-36/android.jar'
tools = sdk / 'build-tools/36.0.0'

def run(*parts):
    subprocess.run([str(part) for part in parts], check=True)

run('javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', android,
    '-d', work / 'classes', here / 'TimerRecoveryInspectionInstrumentation.java')
run(tools / 'd8', '--release', '--lib', android, '--min-api', '27', '--output', work / 'dex',
    *sorted((work / 'classes').rglob('*.class')))
run(tools / 'aapt2', 'link', '-I', android, '--manifest', here / 'InspectionAndroidManifest.xml', '-o', work / 'base.apk')
with ZipFile(work / 'base.apk', 'a') as output:
    output.write(work / 'dex/classes.dex', 'classes.dex')
run(tools / 'zipalign', '-P', '16', '-f', '4', work / 'base.apk', work / 'aligned.apk')
run(tools / 'apksigner', 'sign', '--ks', candidate / '.build/preview.jks', '--ks-pass', 'pass:android',
    '--ks-key-alias', 'preview', '--v4-signing-enabled', 'false', '--out', work / 'timer-inspection-proof.apk', work / 'aligned.apk')
print(work / 'timer-inspection-proof.apk')
print(hashlib.sha256((work / 'timer-inspection-proof.apk').read_bytes()).hexdigest())
