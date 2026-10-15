#!/usr/bin/env python3
"""Copy the HD input icons to the TV's shared Pictures/Icons folder."""

import argparse
from concurrent.futures import ThreadPoolExecutor
import json
from pathlib import Path
import re
import shlex
import struct
import subprocess

ROOT = Path(__file__).resolve().parents[1]
DESTINATION = '/sdcard/Pictures/Icons'


def icon_files():
    entries = json.loads((ROOT / 'icons/catalog.json').read_text())
    files = []
    for entry in entries:
        name = entry['file']
        if not re.fullmatch(r'[a-z0-9-]+\.png', name):
            raise ValueError('Invalid icon filename: ' + name)
        path = ROOT / 'icons' / name
        with path.open('rb') as stream:
            header = stream.read(24)
        if (header[:8] != b'\x89PNG\r\n\x1a\n' or header[12:16] != b'IHDR'
                or len(header) != 24 or struct.unpack('>II', header[16:24]) != (1920, 1080)):
            raise ValueError('Expected a 1920x1080 PNG: ' + name)
        files.append(path)
    if not files or len(files) != len(set(files)):
        raise ValueError('The icon catalog must have unique images.')
    return files


def install_icons(serial):
    files = icon_files()
    adb = ['adb', '-s', serial]
    subprocess.run(adb + ['shell', 'mkdir', '-p', DESTINATION], check=True)
    subprocess.run(adb + ['push', *map(str, files), DESTINATION + '/'], check=True)

    def scan(path):
        subprocess.run(adb + ['shell', 'am', 'broadcast', '-a',
                             'android.intent.action.MEDIA_SCANNER_SCAN_FILE', '-d',
                             'file://' + DESTINATION + '/' + path.name],
                       check=True, stdout=subprocess.DEVNULL)

    with ThreadPoolExecutor(max_workers=4) as executor:
        list(executor.map(scan, files))
    sdk = int(subprocess.check_output(adb + ['shell', 'getprop', 'ro.build.version.sdk'],
                                      text=True).strip())
    if sdk >= 29:
        query = ['content', 'query', '--uri', 'content://media/external/images/media',
                 '--projection', '_id:_display_name:is_pending', '--where',
                 "relative_path='Pictures/Icons/'"]
        rows = subprocess.check_output(adb + ['shell', shlex.join(query)], text=True)
        records = {}
        for row in rows.splitlines():
            match = re.search(r'_id=(\d+), _display_name=([^,]+), is_pending=(\d+)', row)
            if match:
                records[match.group(2)] = match.group(1)
        for path in files:
            if path.name not in records:
                raise RuntimeError('The TV did not index ' + path.name)

        def publish(path):
            subprocess.run(adb + ['shell', 'content', 'update', '--uri',
                                 'content://media/external/images/media/' + records[path.name],
                                 '--bind', 'is_pending:i:0'], check=True,
                           stdout=subprocess.DEVNULL)

        with ThreadPoolExecutor(max_workers=4) as executor:
            list(executor.map(publish, files))
        published = subprocess.check_output(adb + ['shell', shlex.join(query)], text=True)
        visible = set(re.findall(r'_display_name=([^,]+), is_pending=0\b', published))
        if any(path.name not in visible for path in files):
            raise RuntimeError('The TV has not published all icon images to its pickers.')
    print(f'Installed and indexed {len(files)} HD icons in {DESTINATION}.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True, help='The ADB device serial or IP:port')
    options = parser.parse_args()
    install_icons(options.serial)
