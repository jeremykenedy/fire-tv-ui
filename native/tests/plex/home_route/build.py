#!/usr/bin/env python3
"""Build test instrumentation for an isolated, lab-signed production-package candidate."""
from pathlib import Path
import argparse
import hashlib
import shutil
import subprocess
from zipfile import ZipFile

here = Path(__file__).resolve().parent
parser = argparse.ArgumentParser()
parser.add_argument('--snapshot-native', type=Path, required=True)
parser.add_argument('--candidate-output', type=Path, required=True)
args = parser.parse_args()
native = args.snapshot_native.resolve()
output = args.candidate_output.resolve()
key = output / 'candidate-review.jks'
if not key.is_file(): raise SystemExit('Build the isolated review candidate first.')
sdk = Path.home() / 'Library/Android/sdk'
tools, android = sdk / 'build-tools/36.0.0', sdk / 'platforms/android-36/android.jar'
work = output / 'plex-home-proof-build'
if work.exists(): shutil.rmtree(work)
(work / 'classes').mkdir(parents=True)
(work / 'dex').mkdir()
def run(*items): subprocess.run([str(item) for item in items], check=True)
run('javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', android,
    '-sourcepath', str(native / 'src') + ':' + str(native / 'production-src') + ':' + str(native / '.build/gen'),
    '-implicit:none', '-d', work / 'classes', here / 'PlexHomeRouteInstrumentation.java',
    here.parent / 'route_fixture/RoutePlexFixture.java')
run(tools / 'd8', '--release', '--lib', android, '--min-api', '27', '--output', work / 'dex',
    *sorted((work / 'classes').rglob('*.class')))
run(tools / 'aapt2', 'link', '-I', android, '--manifest', here / 'AndroidManifest.xml', '-o', work / 'base.apk')
with ZipFile(work / 'base.apk', 'a') as apk: apk.write(work / 'dex/classes.dex', 'classes.dex')
run(tools / 'zipalign', '-P', '16', '-f', '4', work / 'base.apk', work / 'aligned.apk')
apk = output / 'plex-home-proof.apk'
run(tools / 'apksigner', 'sign', '--ks', key, '--ks-pass', 'pass:android', '--ks-key-alias', 'candidate-review',
    '--v4-signing-enabled', 'false', '--out', apk, work / 'aligned.apk')
run(tools / 'apksigner', 'verify', apk)
print(apk, hashlib.sha256(apk.read_bytes()).hexdigest())
