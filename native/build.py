#!/usr/bin/env python3
"""Build the isolated source preview without a supplied APK or Smali."""
import argparse
import hashlib
import json
import os
import re
import xml.etree.ElementTree as ET
from pathlib import Path
import subprocess
import shutil
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parent


def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)


PREVIEW_PACKAGE = 'com.jeremykenedy.firetv.ui.preview'
ANDROID = '{http://schemas.android.com/apk/res/android}'


def release_config(path):
    path = path.resolve()
    if ROOT not in path.parents or not path.is_file() or path.stat().st_size > 16384:
        raise ValueError('Release configuration must be a bounded file inside native/.')
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError('Duplicate release configuration field.')
            result[key] = value
        return result
    config = json.loads(path.read_text(), object_pairs_hook=unique)
    fields = {'schemaVersion', 'applicationId', 'version', 'versionCode', 'minSdk', 'targetSdk',
              'debuggable', 'allowBackup', 'signerSha256', 'keyAlias'}
    if not isinstance(config, dict) or set(config) != fields:
        raise ValueError('Unexpected release configuration fields.')
    number = r'(?:0|[1-9][0-9]*)'
    identifier = r'(?:0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*)'
    semver = number + r'\.' + number + r'\.' + number + r'-' + identifier + r'(?:\.' + identifier + r')*(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?'
    if (type(config['schemaVersion']) is not int or config['schemaVersion'] != 1
            or config['applicationId'] != PREVIEW_PACKAGE
            or not isinstance(config['version'], str) or len(config['version']) > 100
            or not re.fullmatch(semver, config['version'])
            or type(config['versionCode']) is not int or not 2 <= config['versionCode'] <= 2100000000
            or type(config['minSdk']) is not int or config['minSdk'] != 27
            or type(config['targetSdk']) is not int or config['targetSdk'] != 36
            or config['debuggable'] is not False or config['allowBackup'] is not False
            or not isinstance(config['signerSha256'], str) or not re.fullmatch('[a-f0-9]{64}', config['signerSha256'])
            or not isinstance(config['keyAlias'], str) or not re.fullmatch('[A-Za-z0-9_.-]{1,100}', config['keyAlias'])):
        raise ValueError('Release configuration requires a versioned, nondebug preview prerelease and reviewed signer.')
    manifest = ET.parse(ROOT / 'AndroidManifest.xml').getroot()
    app = manifest.find('application')
    if (manifest.get('package') != PREVIEW_PACKAGE or app is None
            or app.get(ANDROID + 'debuggable') != 'false' or app.get(ANDROID + 'allowBackup') != 'false'):
        raise ValueError('Release manifest must explicitly preserve the nondebug isolated preview identity.')
    if (any(node.get(ANDROID + 'name') == 'android.intent.category.HOME' for node in app.iter('category'))
            or any(node.get(ANDROID + 'name') == 'android.accessibilityservice.AccessibilityService'
                   for node in app.iter('action'))):
        raise ValueError('Preview release must not register Home or accessibility takeover.')
    return config


def signing_options(args, config):
    signing = (args.keystore, args.ks_pass_env, args.key_pass_env)
    if config is None:
        if any(signing) or args.output_dir:
            raise ValueError('Explicit release signing/output requires --release-config.')
        return
    if args.debug:
        raise ValueError('--debug cannot be combined with --release-config.')
    if args.output_dir:
        destination = args.output_dir.resolve()
        if destination == ROOT or any(folder == destination or folder in destination.parents
                                      for folder in (ROOT / 'src', ROOT / 'res')):
            raise ValueError('Release output must be separate from source and resource inputs.')
    if args.unsigned:
        if any(signing):
            raise ValueError('--unsigned cannot use signing arguments.')
        return
    if not args.keystore or not args.keystore.is_file() or not args.ks_pass_env:
        raise ValueError('Signing requires an existing explicit --keystore and --ks-pass-env.')
    for name in (args.ks_pass_env, args.key_pass_env):
        if name is not None and (not re.fullmatch('[A-Za-z_][A-Za-z0-9_]*', name) or not os.environ.get(name)):
            raise ValueError('Signing secret environment names must exist and contain a value.')


def source_hashes(config_path=None):
    inputs = sorted((ROOT / 'src').rglob('*.java')) + sorted(p for p in (ROOT / 'res').rglob('*') if p.is_file())
    inputs += [ROOT / 'AndroidManifest.xml', Path(__file__).resolve()]
    if config_path is not None:
        inputs.append(config_path.resolve())
    return {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest() for path in inputs}


def certificate(tools, apk):
    result = subprocess.run([str(tools / 'apksigner'), 'verify', '--print-certs', str(apk)],
                            capture_output=True, text=True, check=True)
    hashes = re.findall(r'^Signer #[0-9]+ certificate SHA-256 digest: ([a-fA-F0-9]{64})$', result.stdout, re.MULTILINE)
    if len(hashes) != 1:
        raise ValueError('The release APK must have exactly one verified signing certificate.')
    return hashes[0].lower()


def sign_release(tools, unsigned, output, config, args):
    command = [tools / 'apksigner', 'sign', '--ks', args.keystore, '--ks-key-alias', config['keyAlias'],
               '--ks-pass', 'env:' + args.ks_pass_env, '--v4-signing-enabled', 'false', '--out', output, unsigned]
    if args.key_pass_env:
        command[2:2] = ['--key-pass', 'env:' + args.key_pass_env]
    try:
        run(*command)
        signer = certificate(tools, output)
        if signer != config['signerSha256']:
            raise ValueError('The actual release signing certificate differs from the reviewed preview signer.')
        return signer
    except Exception:
        output.unlink(missing_ok=True)
        raise


def verify_apk(tools, apk, config):
    badging = subprocess.check_output([str(tools / 'aapt2'), 'dump', 'badging', str(apk)], text=True)
    identity = re.search(r"^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'", badging, re.MULTILINE)
    floor = re.search(r"^(?:minSdkVersion|sdkVersion):'([0-9]+)'$", badging, re.MULTILINE)
    target = re.search(r"^targetSdkVersion:'([0-9]+)'$", badging, re.MULTILINE)
    if (not identity or identity.groups() != (config['applicationId'], str(config['versionCode']), config['version'])
            or not floor or int(floor.group(1)) != config['minSdk']
            or not target or int(target.group(1)) != config['targetSdk']
            or 'application-debuggable' in badging):
        raise ValueError('The actual APK identity, version or SDK configuration differs from the reviewed release.')
    tree = subprocess.check_output([str(tools / 'aapt2'), 'dump', 'xmltree', str(apk), '--file', 'AndroidManifest.xml'], text=True)
    attributes = []
    depth = None
    for line in tree.splitlines():
        indent = len(line) - len(line.lstrip())
        if 'E: application ' in line:
            if depth is not None:
                raise ValueError('Unexpected APK application structure.')
            depth = indent
        elif depth is not None:
            if line.lstrip().startswith('E:') and indent <= depth:
                break
            if indent == depth + 2 and line.lstrip().startswith('A:'):
                attributes.append(line.strip())
    for name in ('debuggable', 'allowBackup'):
        if not any(re.fullmatch(r'A: http://schemas.android.com/apk/res/android:' + name + r'\(0x[0-9a-f]+\)=false', line)
                   for line in attributes):
            raise ValueError('The actual APK must explicitly disable debugging and platform backup.')


def source_revision():
    try:
        commit = subprocess.check_output(['git', '-C', str(ROOT), 'rev-parse', 'HEAD'], text=True, stderr=subprocess.DEVNULL).strip()
        dirty = bool(subprocess.check_output(['git', '-C', str(ROOT), 'status', '--porcelain'], text=True, stderr=subprocess.DEVNULL).strip())
        return commit, dirty
    except (OSError, subprocess.CalledProcessError):
        return None, None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--debug', action='store_true', help='Sign a local lab build with its isolated review key.')
    mode.add_argument('--unsigned', action='store_true', help='Build without any signing credentials.')
    parser.add_argument('--release-config', type=Path, help='Reviewed native preview prerelease configuration.')
    parser.add_argument('--output-dir', type=Path, help='Isolated release output and intermediate directory.')
    parser.add_argument('--keystore', type=Path, help='Existing private preview signing key, never generated by a release build.')
    parser.add_argument('--ks-pass-env', help='Name of the environment variable holding the keystore password.')
    parser.add_argument('--key-pass-env', help='Optional environment variable name for the key password.')
    args = parser.parse_args()
    config = release_config(args.release_config) if args.release_config else None
    signing_options(args, config)
    version = config['version'] if config else '0.1.0-preview'
    version_code = config['versionCode'] if config else 1
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
               or Path.home() / 'Library/Android/sdk')
    tools = sdk / 'build-tools/36.0.0'
    android = sdk / 'platforms/android-36/android.jar'
    input_hashes = source_hashes(args.release_config)
    dist = args.output_dir.resolve() if args.output_dir else ROOT / 'dist'
    work = dist / '.build' if config else ROOT / '.build'
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
        '--target-sdk-version', '36', '--version-code', str(version_code), '--version-name', version,
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
    stem = 'fire-tv-ui-source-preview' + ('-' + version if config else '')
    unsigned = dist / (stem + '-unsigned.apk')
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
    signer = None
    if config and not args.unsigned:
        output = dist / (stem + '.apk')
        signer = sign_release(tools, unsigned, output, config, args)
    run(tools / 'zipalign', '-c', '-P', '16', '4', output)
    if config:
        try:
            verify_apk(tools, output, config)
        except Exception:
            output.unlink(missing_ok=True)
            raise
    current_hashes = source_hashes(args.release_config)
    if current_hashes != input_hashes:
        raise RuntimeError('Build inputs changed during compilation; freeze the source and rebuild before validation.')
    compiler = Path(shutil.which('javac')).resolve()
    runtime = Path(shutil.which('java')).resolve()
    properties = subprocess.check_output(['java', '-XshowSettings:properties', '-version'], stderr=subprocess.STDOUT, text=True)
    java_home = Path(next(line.split('=', 1)[1].strip() for line in properties.splitlines() if line.strip().startswith('java.home =')))
    tool_files = [compiler, runtime, java_home / 'bin/javac', java_home / 'bin/java', java_home / 'lib/modules', android] + [tools / name for name in
                  ('aapt2', 'd8', 'lib/d8.jar', 'zipalign', 'apksigner', 'lib/apksigner.jar')]
    toolchain = {'javaHome': str(java_home), 'javacVersion': subprocess.check_output(['javac', '-version'], stderr=subprocess.STDOUT, text=True).strip(),
                 'javaVersion': subprocess.check_output(['java', '-version'], stderr=subprocess.STDOUT, text=True).strip(),
                 'sha256': {str(path): hashlib.sha256(path.read_bytes()).hexdigest() for path in tool_files}}
    source_commit, source_dirty = source_revision()
    manifest = {'sourceCommit': source_commit, 'sourceTreeDirty': source_dirty, 'apkManifestVerified': config is not None,
                'applicationId': 'com.jeremykenedy.firetv.ui.preview',
                'version': version, 'versionCode': version_code, 'productionReplacement': False,
                'minSdk': 27, 'targetSdk': 36, 'debuggable': False if config else None, 'allowBackup': False,
                'releaseConfigured': config is not None, 'signing': 'maintained-preview' if signer else 'lab-debug' if args.debug else 'unsigned',
                'signerSha256': signer, 'expectedSignerSha256': config['signerSha256'] if config else None,
                'bytes': output.stat().st_size,
                'binaryLauncherInputs': [], 'apk': output.name,
                'sha256': hashlib.sha256(output.read_bytes()).hexdigest(),
                'sourceInputs': input_hashes, 'toolchain': toolchain}
    (dist / 'build-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    if config:
        public = {key: manifest[key] for key in ('sourceCommit', 'sourceTreeDirty', 'applicationId', 'version', 'versionCode',
                  'minSdk', 'targetSdk', 'debuggable', 'allowBackup', 'productionReplacement', 'binaryLauncherInputs',
                  'apk', 'bytes', 'sha256', 'signing', 'signerSha256', 'expectedSignerSha256', 'apkManifestVerified', 'sourceInputs')}
        public['toolchain'] = {'javacVersion': toolchain['javacVersion'], 'javaVersion': toolchain['javaVersion'],
                               'androidPlatform': 36, 'buildTools': '36.0.0',
                               'sha256': {label: toolchain['sha256'][str(path)] for label, path in zip(
                                   ('javacLauncher', 'javaLauncher', 'javacBinary', 'javaBinary', 'javaModules', 'androidJar',
                                    'aapt2', 'd8', 'd8Jar', 'zipalign', 'apksigner', 'apksignerJar'), tool_files)}}
        (dist / 'release-artifact.json').write_text(json.dumps(public, indent=2) + '\n')
        output.with_suffix('.apk.sha256').write_text(manifest['sha256'] + '  ' + output.name + '\n')
    print(output)


if __name__ == '__main__':
    main()
