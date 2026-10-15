#!/usr/bin/env python3
"""Build Fire TV UI with the JDK, Android SDK, and a verified Apktool jar."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import sys
import urllib.request
from zipfile import ZipFile

from patch_launcher import patch
from install_icons import icon_files

ROOT = Path(__file__).resolve().parents[1]
CONFIG = json.loads((ROOT / 'build.json').read_text())


def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def build(unsigned=False):
    base = ROOT / 'vendor/launcher-base.apk'
    if digest(base) != CONFIG['base_sha256']:
        raise ValueError('The base APK does not match the pinned SHA-256.')
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
               or Path.home() / 'Library/Android/sdk')
    tools = sdk / 'build-tools/36.0.0'
    android_jar = sdk / 'platforms/android-36/android.jar'
    if not android_jar.is_file() or not (tools / 'd8').is_file():
        raise ValueError('Install Android SDK platform 36 and build-tools 36.0.0.')
    cache = ROOT / '.cache'
    cache.mkdir(exist_ok=True)
    jar = cache / 'apktool.jar'
    if not jar.is_file() or digest(jar) != CONFIG['apktool_sha256']:
        url = ('https://github.com/iBotPeaches/Apktool/releases/download/v'
               + CONFIG['apktool_version'] + '/apktool_' + CONFIG['apktool_version'] + '.jar')
        print('Downloading pinned Apktool', flush=True)
        with urllib.request.urlopen(url) as response:
            jar.write_bytes(response.read())
    if digest(jar) != CONFIG['apktool_sha256']:
        raise ValueError('Apktool checksum verification failed.')

    work = ROOT / '.build'
    if work.exists():
        shutil.rmtree(work)
    work.mkdir()
    decoded = work / 'decoded'
    run('java', '-jar', jar, 'd', '-p', cache / 'framework', '-o', decoded, base)
    patch(decoded)
    run('java', '-jar', jar, 'b', '-p', cache / 'framework', decoded,
        '-o', work / 'launcher.apk')

    classes = work / 'classes'
    classes.mkdir()
    sources = sorted((ROOT / 'app/src').rglob('*.java'))
    run('javac', '-encoding', 'UTF-8', '-source', '8', '-target', '8',
        '-Xlint:-options', '-bootclasspath', android_jar, '-d', classes, *sources)
    dex = work / 'dex'
    dex.mkdir()
    class_list = work / 'classes.txt'
    class_list.write_text('\n'.join(str(path) for path in sorted(classes.rglob('*.class'))) + '\n')
    run(tools / 'd8', '--release', '--lib', android_jar, '--min-api', '27',
        '--output', dex, '@' + str(class_list))
    with ZipFile(work / 'launcher.apk') as source, ZipFile(work / 'combined.apk', 'w') as archive:
        for entry in source.infolist():
            archive.writestr(entry, source.read(entry))
        archive.write(dex / 'classes.dex', 'classes2.dex')
    run(tools / 'zipalign', '-P', '16', '-f', '4',
        work / 'combined.apk', work / 'aligned.apk')
    dist = ROOT / 'dist'
    dist.mkdir(exist_ok=True)
    output = dist / ('fire-tv-ui-' + CONFIG['version'] + '.apk')
    if unsigned:
        output = output.with_name(output.stem + '-unsigned.apk')
        shutil.copy(work / 'aligned.apk', output)
    else:
        keystore = Path(os.environ.get('FIRE_TV_UI_KEYSTORE')
                        or Path.home() / '.android/keys/fire-tv-ui.jks')
        password_file = Path(os.environ.get('FIRE_TV_UI_PASSWORD_FILE')
                             or keystore.with_suffix('.pass'))
        alias = os.environ.get('FIRE_TV_UI_KEY_ALIAS', 'fire-tv-ui')
        if not keystore.exists():
            if 'FIRE_TV_UI_KEYSTORE' in os.environ:
                raise ValueError('The specified signing key does not exist.')
            keystore.parent.mkdir(parents=True, exist_ok=True)
            descriptor = os.open(password_file, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(descriptor, 'w') as stream:
                stream.write(secrets.token_urlsafe(32) + '\n')
            run('keytool', '-genkeypair', '-keystore', keystore,
                '-storepass:file', password_file, '-alias', alias, '-keyalg', 'RSA',
                '-keysize', '2048', '-validity', '10950', '-dname', 'CN=Fire TV UI', '-noprompt')
            keystore.chmod(0o600)
            print(f'Created signing key: {keystore}. Keep this key for future updates.')
        run(tools / 'apksigner', 'sign', '--ks', keystore, '--ks-key-alias', alias,
            '--ks-pass', 'file:' + str(password_file), '--v4-signing-enabled', 'false',
            '--out', output, work / 'aligned.apk')
        run(tools / 'apksigner', 'verify', '--verbose', output)
    run(tools / 'zipalign', '-c', '-P', '16', '4', output)
    checksum = digest(output)
    output.with_suffix('.apk.sha256').write_text(checksum + '  ' + output.name + '\n')
    print(f'Built {output}\nSHA-256: {checksum}')
    icons = dist / ('fire-tv-ui-icons-' + CONFIG['version'] + '.zip')
    with ZipFile(icons, 'w') as archive:
        for path in icon_files():
            archive.write(path, 'Icons/' + path.name)
        archive.write(ROOT / 'icons/catalog.json', 'Icons/catalog.json')
        archive.write(ROOT / 'icons/NOTICE.txt', 'Icons/NOTICE.txt')
    icons.with_suffix('.zip.sha256').write_text(digest(icons) + '  ' + icons.name + '\n')
    print(f'Built {icons}')
    return output


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--unsigned', action='store_true')
    options = parser.parse_args()
    try:
        build(options.unsigned)
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        print(f'Build failed: {error}', file=sys.stderr)
        sys.exit(1)
