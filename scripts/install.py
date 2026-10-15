#!/usr/bin/env python3
"""Install Fire TV UI and grant TV screensaver and persistent backup access."""

import argparse
import json
from pathlib import Path
import subprocess

from install_icons import install_icons

ROOT = Path(__file__).resolve().parents[1]
CONFIG = json.loads((ROOT / 'build.json').read_text())

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True, help='The ADB device serial or IP:port')
    parser.add_argument('--apk', type=Path,
                        default=ROOT / ('dist/fire-tv-ui-' + CONFIG['version'] + '.apk'))
    options = parser.parse_args()
    if not options.apk.is_file():
        parser.error('Build a signed APK first or pass --apk.')
    adb = ['adb', '-s', options.serial]
    home = CONFIG['package'] + '/' + CONFIG['package'] + '.HomeRedirectService'
    controls = CONFIG['package'] + '/' + CONFIG['package'] + '.Hra'
    before = subprocess.check_output(adb + ['shell', 'settings', 'get', 'secure',
                                           'enabled_accessibility_services'], text=True).strip()
    had_home = home in before.split(':')
    subprocess.run(adb + ['install', '-r', str(options.apk)], check=True)
    subprocess.run(adb + ['shell', 'pm', 'grant', CONFIG['package'],
                         'android.permission.WRITE_SECURE_SETTINGS'], check=True)
    sdk = int(subprocess.check_output(adb + ['shell', 'getprop', 'ro.build.version.sdk'], text=True).strip())
    if sdk >= 30:
        subprocess.run(adb + ['shell', 'appops', 'set', '--uid', CONFIG['package'],
                             'MANAGE_EXTERNAL_STORAGE', 'allow'], check=True)
    else:
        for permission in ('READ_EXTERNAL_STORAGE', 'WRITE_EXTERNAL_STORAGE'):
            subprocess.run(adb + ['shell', 'pm', 'grant', CONFIG['package'],
                                 'android.permission.' + permission], check=True)
    for operation in ('GET_USAGE_STATS', 'REQUEST_INSTALL_PACKAGES'):
        subprocess.run(adb + ['shell', 'appops', 'set', CONFIG['package'], operation, 'allow'], check=True)
    if had_home:
        subprocess.run(adb + ['shell', 'am', 'broadcast', '--include-stopped-packages', '-n',
                             CONFIG['package'] + '/.BackupReceiver', '--es', 'operation', 'home-on'], check=True)
    install_icons(options.serial)
    subprocess.run(adb + ['shell', 'am', 'start', '-n',
                         CONFIG['package'] + '/.MainActivity'], check=True)
