#!/usr/bin/env python3
"""Prepare an isolated, debug-signed source candidate using the production compatibility package."""
import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parent
PACKAGE = 'com.jeremykenedy.firetv.ui'
PREVIEW = PACKAGE + '.preview'
ANDROID_NS = '{http://schemas.android.com/apk/res/android}'
VERSION = '0.1.0-source-candidate'


def attr(name):
    return ANDROID_NS + name


def canonical(node):
    return (node.tag, tuple(sorted(node.attrib.items())),
            tuple(canonical(child) for child in node))


def validate(root=ROOT):
    """Require reviewed manifest differences and preserve every current source-preview route."""
    preview = ET.parse(root / 'AndroidManifest.xml').getroot()
    production = ET.parse(root / 'production-src/AndroidManifest.xml').getroot()
    if preview.get('package') != PREVIEW or production.get('package') != PACKAGE:
        raise ValueError('Unexpected application identity')
    if any(node.get(attr('name')) == 'android.intent.category.HOME' for node in preview.find('application').iter('category')):
        raise ValueError('Preview must remain outside Home registration')
    if any(node.get(attr('name')) == 'android.accessibilityservice.AccessibilityService'
           for node in preview.find('application').iter('action')):
        raise ValueError('Preview must not register Home accessibility services')
    base = preview.find('application')
    candidate = production.find('application')
    if candidate.get(attr('debuggable')) != 'true' or candidate.get(attr('allowBackup')) != 'false':
        raise ValueError('Candidate must remain debug-only with platform backup disabled')
    for node in base:
        expected = copy.deepcopy(node)
        if expected.tag == 'activity' and expected.get(attr('name')) == PACKAGE + '.nativeapp.MainActivity':
            expected.set(attr('launchMode'), 'singleTask')
            for child in list(expected.findall('intent-filter')):
                expected.remove(child)
        if expected.tag == 'provider':
            expected.set(attr('authorities'), expected.get(attr('authorities')).replace(PREVIEW, PACKAGE))
        matches = [node for node in candidate if node.tag == expected.tag
                   and node.get(attr('name')) == expected.get(attr('name'))]
        if len(matches) != 1 or canonical(matches[0]) != canonical(expected):
            raise ValueError('Production manifest route drift: ' + str(expected.get(attr('name'))))
    for name in ('Hra', 'HomeRedirectService'):
        service = next((node for node in candidate.findall('service')
                        if node.get(attr('name')) == PACKAGE + '.' + name), None)
        if service is None or service.get(attr('permission')) != 'android.permission.BIND_ACCESSIBILITY_SERVICE':
            raise ValueError('Missing protected compatibility service: ' + name)
        if service.get(attr('exported')) != 'true' or service.get(attr('process')):
            raise ValueError('Compatibility services must bind in the shared default process')
        metadata = service.find('meta-data')
        expected_config = '@xml/home_keys_service' if name == 'Hra' else '@xml/home_windows_service'
        if metadata is None or metadata.get(attr('resource')) != expected_config:
            raise ValueError('Incorrect compatibility service metadata: ' + name)
    receiver = next((node for node in candidate.findall('receiver')
                     if node.get(attr('name')) == PACKAGE + '.BackupReceiver'), None)
    if receiver is None or receiver.get(attr('permission')) != 'android.permission.DUMP' or receiver.get(attr('exported')) != 'true':
        raise ValueError('Home command receiver must retain the DUMP caller boundary')
    if receiver.findall('intent-filter'):
        raise ValueError('Home command receiver must remain explicit-only')
    alias = next((node for node in candidate.findall('activity-alias')
                  if node.get(attr('name')) == PACKAGE + '.MainActivity'), None)
    if alias is None or alias.get(attr('targetActivity')) != PACKAGE + '.nativeapp.MainActivity':
        raise ValueError('Missing original explicit-launch component')
    categories = {node.get(attr('name')) for node in alias.iter('category')}
    if not {'android.intent.category.HOME', 'android.intent.category.DEFAULT',
            'android.intent.category.LAUNCHER', 'android.intent.category.LEANBACK_LAUNCHER'} <= categories:
        raise ValueError('Candidate launcher alias has incomplete intent filters')
    for filename, keys in [('home_keys_service.xml', 'true'), ('home_windows_service.xml', 'false')]:
        config = ET.parse(root / 'production-res/xml' / filename).getroot()
        if (config.get(attr('canRequestFilterKeyEvents')) != keys
                or config.get(attr('canRetrieveWindowContent')) != 'false'
                or config.get(attr('canPerformGestures'), 'false') != 'false'):
            raise ValueError('Unexpected service capability: ' + filename)
    for tag in ('uses-permission', 'uses-feature'):
        if [canonical(node) for node in preview.findall(tag)] != [canonical(node) for node in production.findall(tag)]:
            raise ValueError('Production permission/feature drift requires review')
    preview_queries = {canonical(node) for node in preview.find('queries')}
    if not preview_queries <= {canonical(node) for node in production.find('queries')}:
        raise ValueError('Production package visibility omitted a source route')


def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)


def build(output, sdk):
    validate()
    output = output.resolve()
    if output == ROOT or ROOT in output.parents:
        raise ValueError('Choose an isolated output directory outside native/')
    output.mkdir(parents=True, exist_ok=True)
    tools = sdk / 'build-tools/36.0.0'
    android = sdk / 'platforms/android-36/android.jar'
    with tempfile.TemporaryDirectory(prefix='fire-tv-source-candidate-') as temporary:
        work = Path(temporary)
        for folder in ('gen', 'classes', 'dex'):
            (work / folder).mkdir()
        run(tools / 'aapt2', 'compile', '--dir', ROOT / 'res', '-o', work / 'base-res.zip')
        run(tools / 'aapt2', 'compile', '--dir', ROOT / 'production-res', '-o', work / 'production-res.zip')
        run(tools / 'aapt2', 'link', '-o', work / 'resources.apk', '-I', android,
            '--manifest', ROOT / 'production-src/AndroidManifest.xml', '--min-sdk-version', '27',
            '--target-sdk-version', '36', '--version-code', '1', '--version-name', VERSION,
            '--java', work / 'gen', '--custom-package', PREVIEW, work / 'base-res.zip', work / 'production-res.zip')
        sources = sorted((ROOT / 'src').rglob('*.java')) + sorted((ROOT / 'production-src').rglob('*.java'))
        run('javac', '-encoding', 'UTF-8', '-source', '8', '-target', '8', '-Xlint:-options',
            '-bootclasspath', android, '-d', work / 'classes', *sources,
            *sorted((work / 'gen').rglob('*.java')))
        classes = work / 'classes.txt'
        classes.write_text('\n'.join(str(path) for path in sorted((work / 'classes').rglob('*.class'))) + '\n')
        run(tools / 'd8', '--release', '--lib', android, '--min-api', '27',
            '--output', work / 'dex', '@' + str(classes))
        with ZipFile(work / 'resources.apk') as resource, ZipFile(work / 'combined.apk', 'w') as target:
            for entry in resource.infolist():
                target.writestr(entry, resource.read(entry))
            target.write(work / 'dex/classes.dex', 'classes.dex')
        run(tools / 'zipalign', '-P', '16', '-f', '4', work / 'combined.apk', work / 'aligned.apk')
        # Isolated disposable signer. No production signer argument or environment lookup exists.
        key = output / 'candidate-review.jks'
        if not key.exists():
            run('keytool', '-genkeypair', '-keystore', key, '-storepass', 'android', '-keypass', 'android',
                '-alias', 'candidate-review', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '3650',
                '-dname', 'CN=Local Source Candidate Review', '-noprompt')
        apk = output / 'fire-tv-ui-source-production-package-candidate.apk'
        run(tools / 'apksigner', 'sign', '--ks', key, '--ks-pass', 'pass:android',
            '--ks-key-alias', 'candidate-review', '--v4-signing-enabled', 'false', '--out', apk, work / 'aligned.apk')
        run(tools / 'apksigner', 'verify', apk)
        run(tools / 'zipalign', '-c', '-P', '16', '4', apk)
        inputs = sources + [ROOT / 'AndroidManifest.xml', ROOT / 'production-src/AndroidManifest.xml', Path(__file__).resolve()]
        inputs += sorted(path for folder in ('res', 'production-res') for path in (ROOT / folder).rglob('*') if path.is_file())
        manifest = {'applicationId': PACKAGE, 'version': VERSION, 'versionCode': 1,
                    'sourceResourceNamespace': PREVIEW, 'candidateOnly': True, 'productionReplacement': False, 'releaseReady': False,
                    'signing': 'isolated-debug-review',
                    'toolchain': {'androidPlatform': 36, 'buildTools': '36.0.0', 'minApi': 27,
                                  'javac': subprocess.check_output(['javac', '-version'], stderr=subprocess.STDOUT, text=True).strip(),
                                  'androidJarSha256': hashlib.sha256(android.read_bytes()).hexdigest()}, 'binaryLauncherInputs': [], 'apk': apk.name,
                    'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
                    'sourceInputs': {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest() for path in inputs},
                    'compiledClasses': sorted(str(path.relative_to(work / 'classes')) for path in (work / 'classes').rglob('*.class'))}
        (output / 'candidate-build-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
        print(apk)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='Validate candidate manifest/config without building.')
    parser.add_argument('--review-candidate', action='store_true', help='Build only a debug-review candidate, never a release.')
    parser.add_argument('--output-dir', type=Path)
    args = parser.parse_args()
    validate()
    if args.check:
        print('Candidate manifest and service contract checks passed; no build performed')
        return
    if not args.review_candidate or args.output_dir is None:
        parser.error('Building requires --review-candidate and --output-dir outside native/')
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
               or Path.home() / 'Library/Android/sdk')
    build(args.output_dir, sdk)


if __name__ == '__main__':
    main()
