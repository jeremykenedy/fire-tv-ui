#!/usr/bin/env python3
"""Prepare an isolated, debug-signed source candidate using the production compatibility package."""
import argparse
import copy
import hashlib
import json
import os
import re
import shutil
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
RELEASE_LABELS = {
    PACKAGE + '.Hra': 'Fire TV UI Home keys',
    PACKAGE + '.HomeRedirectService': 'Fire TV UI Home window fallback',
}


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
    expected_application = dict(base.attrib)
    expected_application[attr('name')] = PACKAGE + '.SourceApplication'
    expected_application[attr('label')] = '@string/candidate_app_name'
    expected_application[attr('debuggable')] = 'true'
    if candidate.attrib != expected_application or candidate.get(attr('allowBackup')) != 'false':
        raise ValueError('Candidate application attributes drifted from the reviewed contract')
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
    compatibility = {(tag, PACKAGE + '.' + name) for tag, name in (
        ('service', 'Hra'), ('service', 'HomeRedirectService'), ('receiver', 'BackupReceiver'),
        ('receiver', 'DeviceBootReceiver'), ('activity-alias', 'MainActivity'))}
    compatibility.add(('service', PACKAGE + '.nativeapp.device.protection.LegacyProtectionService'))
    preview_components = {(node.tag, node.get(attr('name'))) for node in base}
    for node in candidate:
        if (node.tag, node.get(attr('name'))) not in preview_components | compatibility:
            raise ValueError('Unreviewed production component: ' + str(node.get(attr('name'))))
    protection = [node for node in candidate.findall('service')
                  if node.get(attr('name')) == PACKAGE + '.nativeapp.device.protection.LegacyProtectionService']
    expected_service = ET.Element('service', {attr('name'): PACKAGE + '.nativeapp.device.protection.LegacyProtectionService',
                                              attr('exported'): 'false', attr('foregroundServiceType'): 'specialUse'})
    ET.SubElement(expected_service, 'property', {attr('name'): 'android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE',
                                                  attr('value'): 'Maintain explicitly saved device settings'})
    if len(protection) != 1 or canonical(protection[0]) != canonical(expected_service):
        raise ValueError('Device protection service must remain private and limited to its reviewed foreground use')
    boot = [node for node in candidate.findall('receiver')
            if node.get(attr('name')) == PACKAGE + '.DeviceBootReceiver']
    if (len(boot) != 1 or boot[0].get(attr('exported')) != 'false'
            or boot[0].get(attr('process')) or boot[0].get(attr('directBootAware')) == 'true'
            or [node.get(attr('name')) for node in boot[0].iter('action')]
                != ['android.intent.action.BOOT_COMPLETED']):
        raise ValueError('Boot receiver must remain private and boot-completed only')
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
    if (alias is None or alias.get(attr('targetActivity')) != PACKAGE + '.nativeapp.MainActivity'
            or alias.get(attr('exported')) != 'true' or alias.get(attr('permission'))
            or alias.get(attr('process'))):
        raise ValueError('Missing original explicit-launch component')
    categories = {node.get(attr('name')) for node in alias.iter('category')}
    if categories != {'android.intent.category.HOME', 'android.intent.category.DEFAULT',
                       'android.intent.category.LAUNCHER', 'android.intent.category.LEANBACK_LAUNCHER'}:
        raise ValueError('Candidate launcher alias has unexpected intent filters')
    filters = alias.findall('intent-filter')
    expected_filters = {frozenset(('android.intent.category.LAUNCHER', 'android.intent.category.LEANBACK_LAUNCHER')),
                        frozenset(('android.intent.category.HOME', 'android.intent.category.DEFAULT'))}
    if (len(filters) != 2 or len(alias) != 2
            or {frozenset(c.get(attr('name')) for c in f.findall('category')) for f in filters} != expected_filters
            or any(f.attrib or len(f.findall('action')) != 1
                   or f.find('action').get(attr('name')) != 'android.intent.action.MAIN'
                   or len(f) != 1 + len(f.findall('category')) for f in filters)):
        raise ValueError('Candidate launcher alias intent contract changed')
    for filename, keys in [('home_keys_service.xml', 'true'), ('home_windows_service.xml', 'false')]:
        config = ET.parse(root / 'production-res/xml' / filename).getroot()
        if (config.get(attr('canRequestFilterKeyEvents')) != keys
                or config.get(attr('canRetrieveWindowContent')) != 'false'
                or config.get(attr('canPerformGestures'), 'false') != 'false'):
            raise ValueError('Unexpected service capability: ' + filename)
    for tag in ('uses-permission', 'uses-feature'):
        expected = [canonical(node) for node in preview.findall(tag)]
        if tag == 'uses-permission':
            for permission in ('RECEIVE_BOOT_COMPLETED', 'FOREGROUND_SERVICE', 'FOREGROUND_SERVICE_SPECIAL_USE'):
                expected.append(canonical(ET.Element(tag, {attr('name'): 'android.permission.' + permission})))
        if sorted(expected) != sorted(canonical(node) for node in production.findall(tag)):
            raise ValueError('Production permission/feature drift requires review')
    preview_queries = {canonical(node) for node in preview.find('queries')}
    accessibility = ET.Element('intent')
    ET.SubElement(accessibility, 'action', {attr('name'): 'android.settings.ACCESSIBILITY_SETTINGS'})
    if preview_queries | {canonical(accessibility)} != {canonical(node) for node in production.find('queries')}:
        raise ValueError('Production package visibility drift requires review')


def input_hashes():
    sources = sorted((ROOT / 'src').rglob('*.java')) + sorted((ROOT / 'production-src').rglob('*.java'))
    inputs = sources + [ROOT / 'AndroidManifest.xml', ROOT / 'production-src/AndroidManifest.xml',
                        ROOT / 'production-release.json', Path(__file__).resolve()]
    inputs += sorted(path for folder in ('res', 'production-res') for path in (ROOT / folder).rglob('*') if path.is_file())
    return {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest() for path in inputs}


def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)


def release_config(path=ROOT / 'production-release.json'):
    config = json.loads(path.read_text())
    expected = {'applicationId', 'version', 'versionCode', 'previousVersionCode', 'signerSha256'}
    if set(config) != expected or config['applicationId'] != PACKAGE:
        raise ValueError('Invalid production release identity')
    if (not isinstance(config['version'], str)
            or not re.fullmatch(r'(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)', config['version'])):
        raise ValueError('Production version must be a stable semantic version')
    if (type(config['versionCode']) is not int or type(config['previousVersionCode']) is not int
            or config['previousVersionCode'] < 78 or config['versionCode'] <= config['previousVersionCode']
            or config['versionCode'] > 2100000000):
        raise ValueError('Production version code must increase from the shipped version')
    if config['signerSha256'] != '9a6416b9dc3c996d591b185d600b8ab76e1c7b01846db8d6063b399caf7b5a11':
        raise ValueError('Production signer pin does not match the shipped identity')
    return config


def production_signer():
    key = Path(os.environ.get('FIRE_TV_UI_KEYSTORE') or Path.home() / '.android/keys/fire-tv-ui.jks')
    password = Path(os.environ.get('FIRE_TV_UI_PASSWORD_FILE') or key.with_suffix('.pass'))
    alias = os.environ.get('FIRE_TV_UI_KEY_ALIAS', 'fire-tv-ui')
    if not key.is_file() or not password.is_file() or not alias:
        raise ValueError('Existing production keystore, password file and alias are required')
    return key, password, alias


def verify_signer_output(result, expected):
    fingerprints = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)', result)
    if len(fingerprints) != 1 or fingerprints[0].lower() != expected:
        raise ValueError('Signed APK certificate does not match the shipped production signer')


def verify_apk_metadata(result, config):
    package = ("package: name='" + PACKAGE + "' versionCode='" + str(config['versionCode'])
               + "' versionName='" + config['version'] + "'")
    if not any(line.startswith(package) for line in result.splitlines()):
        raise ValueError('Signed APK identity or version does not match the production contract')
    if any(line.startswith('application-debuggable') for line in result.splitlines()):
        raise ValueError('Production APK must not be debuggable')


def make_release_manifest(source, destination):
    document = ET.parse(source)
    application = document.getroot().find('application')
    application.set(attr('debuggable'), 'false')
    application.set(attr('label'), 'Fire TV UI')
    for service in application.findall('service'):
        name = service.get(attr('name'))
        if name in RELEASE_LABELS:
            service.set(attr('label'), RELEASE_LABELS[name])
    document.write(destination, encoding='utf-8', xml_declaration=True)


def verify_release_labels(badging, tree):
    if "application-label:'Fire TV UI'" not in badging.splitlines():
        raise ValueError('Production application label is incorrect')
    services = re.split(r'(?m)^      E: service \(line=\d+\)\s*$', tree)
    for name, label in RELEASE_LABELS.items():
        matches = [section for section in services[1:]
                   if '="' + name + '"' in section.split('      E:', 1)[0]]
        if len(matches) != 1 or '="' + label + '"' not in matches[0].split('      E:', 1)[0]:
            raise ValueError('Production service label is incorrect: ' + name)


def assert_frozen(expected):
    if input_hashes() != expected:
        raise RuntimeError('Build inputs changed during compilation; freeze source and rebuild before validation.')


def build(output, sdk, release=False, unsigned=False):
    validate()
    config = release_config() if release else None
    signer = production_signer() if release and not unsigned else None
    frozen_inputs = input_hashes()
    output = output.resolve()
    if output == ROOT or ROOT in output.parents:
        raise ValueError('Choose an isolated output directory outside native/')
    output.mkdir(parents=True, exist_ok=True)
    if release and any(output.iterdir()):
        raise ValueError('Production output directory must be empty')
    tools = sdk / 'build-tools/36.0.0'
    android = sdk / 'platforms/android-36/android.jar'
    with tempfile.TemporaryDirectory(prefix='fire-tv-source-candidate-') as temporary:
        work = Path(temporary)
        for folder in ('gen', 'classes', 'dex'):
            (work / folder).mkdir()
        run(tools / 'aapt2', 'compile', '--dir', ROOT / 'res', '-o', work / 'base-res.zip')
        run(tools / 'aapt2', 'compile', '--dir', ROOT / 'production-res', '-o', work / 'production-res.zip')
        manifest_path = ROOT / 'production-src/AndroidManifest.xml'
        if release:
            manifest_path = work / 'AndroidManifest.xml'
            make_release_manifest(ROOT / 'production-src/AndroidManifest.xml', manifest_path)
        run(tools / 'aapt2', 'link', '-o', work / 'resources.apk', '-I', android,
            '--manifest', manifest_path, '--min-sdk-version', '27',
            '--target-sdk-version', '36', '--version-code', str(config['versionCode'] if release else 1),
            '--version-name', config['version'] if release else VERSION,
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
        assert_frozen(frozen_inputs)
        if release:
            if unsigned:
                apk = work / 'aligned.apk'
            else:
                key, password, alias = signer
                apk = work / 'release.apk'
                run(tools / 'apksigner', 'sign', '--ks', key, '--ks-pass', 'file:' + str(password),
                    '--ks-key-alias', alias, '--v4-signing-enabled', 'false', '--out', apk, work / 'aligned.apk')
                result = subprocess.check_output([str(tools / 'apksigner'), 'verify', '--print-certs', str(apk)], text=True)
                verify_signer_output(result, config['signerSha256'])
            metadata = subprocess.check_output([str(tools / 'aapt'), 'dump', 'badging', str(apk)], text=True)
            verify_apk_metadata(metadata, config)
            tree = subprocess.check_output([str(tools / 'aapt'), 'dump', 'xmltree', str(apk), 'AndroidManifest.xml'], text=True)
            verify_release_labels(metadata, tree)
            run(tools / 'zipalign', '-c', '-P', '16', '4', apk)
            assert_frozen(frozen_inputs)
            name = 'fire-tv-ui-' + config['version'] + ('-unsigned.apk' if unsigned else '.apk')
            target = output / name
            tool_paths = [tools / name for name in ('aapt', 'aapt2', 'd8', 'lib/d8.jar', 'zipalign', 'apksigner', 'lib/apksigner.jar')]
            tool_paths += [android, Path(shutil.which('javac')).resolve(), Path(shutil.which('java')).resolve()]
            tool_hashes = {str(path): hashlib.sha256(path.read_bytes()).hexdigest() for path in tool_paths}
            source_commit = subprocess.check_output(['git', '-C', str(ROOT), 'rev-parse', 'HEAD'], text=True).strip()
            assert_frozen(frozen_inputs)
            provenance = {'applicationId': PACKAGE, 'version': config['version'],
                          'versionCode': config['versionCode'], 'debuggable': False,
                          'signing': 'unsigned' if unsigned else 'production',
                          'signerSha256': None if unsigned else config['signerSha256'],
                          'deployable': False, 'candidateOnly': True,
                          'releaseReady': False, 'sourceResourceNamespace': PREVIEW,
                          'binaryLauncherInputs': [], 'apk': name, 'bytes': apk.stat().st_size,
                          'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
                          'sourceCommit': source_commit, 'sourceInputs': frozen_inputs,
                          'toolchain': {'sha256': tool_hashes, 'androidPlatform': 36, 'buildTools': '36.0.0', 'minApi': 27,
                                        'javac': subprocess.check_output(['javac', '-version'], stderr=subprocess.STDOUT, text=True).strip(),
                                        'androidJarSha256': hashlib.sha256(android.read_bytes()).hexdigest()},
                          'compiledClasses': sorted(str(path.relative_to(work / 'classes')) for path in (work / 'classes').rglob('*.class'))}
            shutil.copyfile(apk, target)
            (output / 'production-build-manifest.json').write_text(json.dumps(provenance, indent=2) + '\n')
            print(target)
            return
        # Isolated disposable review signer, never used by release mode.
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
        assert_frozen(frozen_inputs)
        manifest = {'applicationId': PACKAGE, 'version': VERSION, 'versionCode': 1,
                    'sourceResourceNamespace': PREVIEW, 'candidateOnly': True, 'productionReplacement': False, 'releaseReady': False,
                    'signing': 'isolated-debug-review',
                    'toolchain': {'androidPlatform': 36, 'buildTools': '36.0.0', 'minApi': 27,
                                  'javac': subprocess.check_output(['javac', '-version'], stderr=subprocess.STDOUT, text=True).strip(),
                                  'androidJarSha256': hashlib.sha256(android.read_bytes()).hexdigest()}, 'binaryLauncherInputs': [], 'apk': apk.name,
                    'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
                    'sourceInputs': frozen_inputs,
                    'compiledClasses': sorted(str(path.relative_to(work / 'classes')) for path in (work / 'classes').rglob('*.class'))}
        (output / 'candidate-build-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
        print(apk)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='Validate candidate manifest/config without building.')
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument('--review-candidate', action='store_true', help='Build an isolated debug-review candidate.')
    modes.add_argument('--release', action='store_true', help='Build a production-signed candidate after required gates are approved.')
    modes.add_argument('--unsigned-release', action='store_true', help='Build an unsigned, nondeployable production candidate for CI.')
    parser.add_argument('--output-dir', type=Path)
    args = parser.parse_args()
    validate()
    if args.check:
        print('Candidate manifest and service contract checks passed; no build performed')
        return
    if not (args.review_candidate or args.release or args.unsigned_release) or args.output_dir is None:
        parser.error('Building requires --review-candidate, --release or --unsigned-release and --output-dir outside native/')
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
               or Path.home() / 'Library/Android/sdk')
    build(args.output_dir, sdk, release=args.release or args.unsigned_release, unsigned=args.unsigned_release)


if __name__ == '__main__':
    main()
