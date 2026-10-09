#!/usr/bin/env python3
"""Capture small previews from installed screensavers on an Android 11 Fire TV."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import time

ROOT = Path(__file__).resolve().parents[1]


def full_component(value):
    if not re.fullmatch(r'[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+', value):
        raise ValueError('Use a package/class screensaver component.')
    package, name = value.split('/', 1)
    return package + '/' + (package + name if name.startswith('.') else name)


def preview_file(component):
    return hashlib.sha256(full_component(component).encode('utf-8')).hexdigest() + '.jpg'


def capture(serial, components, output, delay, device_copy=False):
    adb = ['adb', '-s', serial]

    def shell(*args):
        return subprocess.check_output(adb + ['shell', *args], text=True).strip()

    if shell('getprop', 'ro.build.version.sdk') != '30':
        raise ValueError('This capture command supports Android 11 / API 30 TVs.')
    if shutil.which('magick') is None:
        raise ValueError('Install ImageMagick to resize screenshots.')
    output.mkdir(parents=True, exist_ok=True)
    before = {key: shell('settings', 'get', 'secure', key)
              for key in ('screensaver_components', 'screensaver_enabled')}
    package_ui = 'com.jeremykenedy.firetv.ui'
    protected_ui = shell('pm', 'list', 'packages', package_ui) == 'package:' + package_ui

    def write_setting(key, value):
        if protected_ui and not (key == 'screensaver_enabled' and value == 'null'):
            result = shell('am', 'broadcast', '--include-stopped-packages',
                           '-n', package_ui + '/.BackupReceiver', '--es', 'operation', 'setting',
                           '--es', 'namespace', 'secure', '--es', 'key', key, '--es', 'value', value)
            if 'Broadcast completed: result=0, data="ok"' not in result:
                raise ValueError('Fire TV UI did not save the capture setting: ' + result)
        elif value == 'null':
            shell('settings', 'delete', 'secure', key)
        else:
            shell('settings', 'put', 'secure', key, value)

    index_path = output / 'index.json'
    index = json.loads(index_path.read_text()) if index_path.exists() else {}
    try:
        with tempfile.TemporaryDirectory(prefix='fire-tv-ui-previews-') as directory:
            raw = Path(directory) / 'screenshot.png'
            for component in components:
                canonical = full_component(component)
                package, name = canonical.split('/', 1)
                shell('service', 'call', 'dreams', '2')
                time.sleep(1)
                # Fire OS restricts dream activities to the selected package,
                # including while previewing a dream. Restore these below.
                write_setting('screensaver_components', canonical)
                write_setting('screensaver_enabled', '1')
                shell('service', 'call', 'dreams', '6', 'i32', '0', 'i32', '1',
                      's16', package, 's16', name)
                print('Waiting ' + str(delay) + ' seconds for ' + component + ' to render...', flush=True)
                time.sleep(delay)
                state = shell('dumpsys', 'dreams')
                if 'mCurrentDreamName=ComponentInfo{' + canonical + '}' not in state:
                    raise ValueError('Screensaver did not start: ' + component)
                with raw.open('wb') as stream:
                    subprocess.run(adb + ['exec-out', 'screencap', '-p'], stdout=stream, check=True)
                filename = preview_file(component)
                destination = output / filename
                subprocess.run(['magick', str(raw), '-resize', '384x216',
                                '-quality', '88', str(destination)], check=True)
                index[canonical] = filename
                index_path.write_text(json.dumps(index, indent=2, sort_keys=True) + '\n')
                if device_copy:
                    device_dir = '/sdcard/Android/data/com.jeremykenedy.firetv.ui/files/screensaver-previews'
                    shell('mkdir', '-p', device_dir)
                    subprocess.run(adb + ['push', str(destination), device_dir + '/' + filename], check=True)
                print('Captured ' + component + ': ' + str(destination), flush=True)
    finally:
        shell('service', 'call', 'dreams', '2')
        for key, value in before.items():
            write_setting(key, value)
        after = {key: shell('settings', 'get', 'secure', key) for key in before}
        if before != after:
            raise ValueError('TV screensaver settings changed during capture. Check the device.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--component', action='append', required=True)
    parser.add_argument('--output', type=Path, default=ROOT / 'app/previews')
    parser.add_argument('--delay', type=float, default=6)
    parser.add_argument('--device-copy', action='store_true')
    options = parser.parse_args()
    if options.delay < 2 or options.delay > 120:
        parser.error('--delay must be between 2 and 120 seconds')
    try:
        capture(options.serial, options.component, options.output, options.delay, options.device_copy)
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        parser.exit(1, 'Capture failed: ' + str(error) + '\n')
