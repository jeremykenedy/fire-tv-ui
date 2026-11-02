#!/usr/bin/env python3
"""Verify inventory semantics and compile the isolated screensaver administration module."""
from pathlib import Path
import subprocess
import json
import re
import tempfile
ROOT = Path(__file__).resolve().parents[2]
MODULE = ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp/device/screensavers'
SDK = Path.home() / 'Library/Android/sdk/platforms/android-36/android.jar'
catalog = json.loads((ROOT.parent / 'app/screensavers.json').read_text())
actual = []
for line in (MODULE / 'ScreensaverInstallCatalog.java').read_text().splitlines():
    match = re.fullmatch(r'        new Entry\((.*), ([0-9]+)L\),', line)
    if match:
        actual.append(json.loads('[' + match[1] + ']') + [int(match[2])])
expected = [[entry.get(key, '') for key in ('name', 'package', 'repo', 'tag', 'url', 'sha256', 'description')]
            + [entry['bytes']] for entry in catalog]
assert actual == expected, 'Bundled install metadata must match maintained reviewed source'
with tempfile.TemporaryDirectory() as folder:
    subprocess.run(['javac', '-d', folder, MODULE / 'ScreensaverEntry.java',
                    Path(__file__).with_name('ScreensaverEntryTest.java')], check=True)
    subprocess.run(['java', '-cp', folder,
                    'com.jeremykenedy.firetv/ui/nativeapp/device/screensavers/ScreensaverEntryTest'.replace('/', '.')], check=True)
    subprocess.run(['javac', '-d', folder,
                    ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp/updates/UpdateCoordinator.java',
                    MODULE / 'CatalogArtifact.java', MODULE / 'ScreensaverInstallCatalog.java',
                    Path(__file__).with_name('CatalogArtifactTest.java')], check=True)
    subprocess.run(['java', '-cp', folder,
                    'com.jeremykenedy.firetv.ui.nativeapp.device.screensavers.CatalogArtifactTest'], check=True)
    subprocess.run(['javac', '-source', '8', '-target', '8', '-Xlint:-options',
                    '-bootclasspath', SDK, '-classpath', str(ROOT / '.build/classes'), '-d', folder,
                    ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp/Pages.java', *sorted(MODULE.glob('*.java')), *sorted((ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp/updates').glob('*.java')), Path(__file__).with_name('ScreensaverInstrumentation.java')], check=True)
