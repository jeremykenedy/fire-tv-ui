#!/usr/bin/env python3
"""Build emulator proof against an immutable debug candidate, never a production signer."""
import argparse
import json
from pathlib import Path
import subprocess
import tempfile
from zipfile import ZipFile

HERE = Path(__file__).resolve().parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source-native', required=True, type=Path)
parser.add_argument('--candidate-dir', required=True, type=Path)
args = parser.parse_args()
metadata = json.loads((args.candidate_dir / 'candidate-build-manifest.json').read_text())
if metadata.get('candidateOnly') is not True or metadata.get('versionCode') != 1 or metadata.get('signing') != 'isolated-debug-review':
    raise ValueError('Only the isolated review candidate is supported')
SDK = Path.home() / 'Library/Android/sdk'
TOOLS = SDK / 'build-tools/36.0.0'
ANDROID = SDK / 'platforms/android-36/android.jar'

def run(*command):
    subprocess.run([str(value) for value in command], check=True)

with tempfile.TemporaryDirectory(prefix='production-home-proof-') as directory:
    work = Path(directory)
    for name in ('dependency', 'classes', 'dex'):
        (work / name).mkdir()
    source = args.source_native / 'src/com/jeremykenedy/firetv/ui/nativeapp'
    run('javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', ANDROID,
        '-d', work / 'dependency', source / 'Pages.java', source / 'SourceStartup.java',
        *sorted((source / 'device').glob('*.java')),
        *[source / 'accessibility' / name for name in ('AccessibilityStyle.java', 'AccessibilityPreferences.java')],
        *[source / 'device/remotebuttons' / name for name in ('RemoteButtonDispatcher.java', 'RemoteButtonPreferences.java', 'RemoteButtonPolicy.java')])
    run('javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', ANDROID,
        '-cp', work / 'dependency', '-d', work / 'classes', HERE / 'ProductionHomeInstrumentation.java', HERE.parent / 'DeviceInstrumentation.java')
    run(TOOLS / 'd8', '--release', '--lib', ANDROID, '--min-api', '27', '--output', work / 'dex',
        *sorted((work / 'classes').rglob('*.class')))
    run(TOOLS / 'aapt2', 'link', '-I', ANDROID, '--manifest', HERE / 'InstrumentationManifest.xml', '-o', work / 'base.apk')
    with ZipFile(work / 'base.apk', 'a') as output:
        output.write(work / 'dex/classes.dex', 'classes.dex')
    run(TOOLS / 'zipalign', '-P', '16', '-f', '4', work / 'base.apk', work / 'aligned.apk')
    output = args.candidate_dir / 'production-home-proof.apk'
    run(TOOLS / 'apksigner', 'sign', '--ks', args.candidate_dir / 'candidate-review.jks', '--ks-pass', 'pass:android',
        '--ks-key-alias', 'candidate-review', '--v4-signing-enabled', 'false', '--out', output, work / 'aligned.apk')
    run(TOOLS / 'apksigner', 'verify', output)
    print(output)
