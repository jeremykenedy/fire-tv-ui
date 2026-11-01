#!/usr/bin/env python3
"""Build the isolated source preview without a supplied APK or Smali."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parent


def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--debug', action='store_true', help='Sign with an isolated preview key.')
    args = parser.parse_args()
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
               or Path.home() / 'Library/Android/sdk')
    tools = sdk / 'build-tools/36.0.0'
    android = sdk / 'platforms/android-36/android.jar'
    work = ROOT / '.build'
    dist = ROOT / 'dist'
    for folder in (work, dist, work / 'gen', work / 'classes', work / 'dex'):
        folder.mkdir(parents=True, exist_ok=True)
    # Remove compiled intermediates so deleted classes cannot survive a rebuild.
    for folder, suffix in ((work / 'classes', '*.class'), (work / 'gen', '*.java'),
                           (work / 'dex', '*.dex')):
        for old in folder.rglob(suffix):
            old.unlink()
    run(tools / 'aapt2', 'compile', '--dir', ROOT / 'res', '-o', work / 'res.zip')
    run(tools / 'aapt2', 'link', '-o', work / 'resources.apk', '-I', android,
        '--manifest', ROOT / 'AndroidManifest.xml', '--min-sdk-version', '27',
        '--target-sdk-version', '36', '--version-code', '1', '--version-name', '0.1.0-preview',
        '--java', work / 'gen', work / 'res.zip')
    sources = sorted((ROOT / 'src').rglob('*.java')) + sorted((work / 'gen').rglob('*.java'))
    run('javac', '-encoding', 'UTF-8', '-source', '8', '-target', '8', '-Xlint:-options',
        '-bootclasspath', android, '-d', work / 'classes', *sources)
    classes = work / 'classes.txt'
    classes.write_text('\n'.join(str(p) for p in sorted((work / 'classes').rglob('*.class'))) + '\n')
    run(tools / 'd8', '--release', '--lib', android, '--min-api', '27',
        '--output', work / 'dex', '@' + str(classes))
    with ZipFile(work / 'resources.apk') as source, ZipFile(work / 'combined.apk', 'w') as target:
        for info in source.infolist():
            target.writestr(info, source.read(info))
        target.write(work / 'dex/classes.dex', 'classes.dex')
    unsigned = dist / 'fire-tv-ui-source-preview-unsigned.apk'
    run(tools / 'zipalign', '-P', '16', '-f', '4', work / 'combined.apk', unsigned)
    output = unsigned
    if args.debug:
        key = work / 'preview.jks'
        if not key.exists():
            run('keytool', '-genkeypair', '-keystore', key, '-storepass', 'android',
                '-keypass', 'android', '-alias', 'preview', '-keyalg', 'RSA', '-keysize', '2048',
                '-validity', '3650', '-dname', 'CN=Fire TV UI Source Preview', '-noprompt')
        output = dist / 'fire-tv-ui-source-preview.apk'
        run(tools / 'apksigner', 'sign', '--ks', key, '--ks-pass', 'pass:android',
            '--ks-key-alias', 'preview', '--v4-signing-enabled', 'false', '--out', output, unsigned)
        run(tools / 'apksigner', 'verify', output)
    run(tools / 'zipalign', '-c', '-P', '16', '4', output)
    inputs = sorted((ROOT / 'src').rglob('*.java')) + sorted((ROOT / 'res').rglob('*.xml'))
    inputs += [ROOT / 'AndroidManifest.xml', Path(__file__).resolve()]
    manifest = {'applicationId': 'com.jeremykenedy.firetv.ui.preview',
                'version': '0.1.0-preview', 'productionReplacement': False,
                'binaryLauncherInputs': [], 'apk': output.name,
                'sha256': hashlib.sha256(output.read_bytes()).hexdigest(),
                'sourceInputs': {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest()
                                 for p in inputs}}
    (dist / 'build-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(output)


if __name__ == '__main__':
    main()
