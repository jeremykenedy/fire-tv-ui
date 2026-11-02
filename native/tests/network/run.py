#!/usr/bin/env python3
"""Compile the network lane in isolation and check diagnostic formatting."""
from pathlib import Path
from host_stubs import SOURCES
import subprocess
import tempfile

HERE = Path(__file__).resolve().parent
NATIVE = HERE.parents[1]
SDK = Path.home() / 'Library/Android/sdk'
ANDROID = SDK / 'platforms/android-36/android.jar'
SOURCE = NATIVE / 'src/com/jeremykenedy/firetv/ui/nativeapp'
with tempfile.TemporaryDirectory(prefix='fire-tv-network-') as target:
    sources = [SOURCE / 'Pages.java', *sorted((SOURCE / 'device/network').glob('*.java')),
               *sorted((SOURCE / 'device').glob('*.java')), HERE / 'NetworkTest.java']
    subprocess.run(['javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath',
                    str(ANDROID), '-cp', str(NATIVE / '.build/classes'), '-d', target,
                    *map(str, sources)], check=True)
    subprocess.run(['java', '-cp', target + ':' + str(ANDROID),
                    'com.jeremykenedy.firetv.ui.nativeapp.device.network.NetworkTest'], check=True)

with tempfile.TemporaryDirectory(prefix='fire-tv-network-behavior-') as target:
    root = Path(target)
    for name, content in SOURCES.items():
        path = root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
    subprocess.run(['javac', '-Xlint:all', '-Werror', '-d', target,
                    *map(str, (root / name for name in SOURCES)),
                    str(SOURCE / 'device/network/NetworkSnapshot.java'),
                    str(SOURCE / 'device/DeviceRoutes.java'),
                    str(HERE / 'NetworkSnapshotTest.java'), str(HERE / 'NetworkRoutesTest.java')], check=True)
    for name in ('network.NetworkSnapshotTest', 'NetworkRoutesTest'):
        subprocess.run(['java', '-cp', target,
                        'com.jeremykenedy.firetv.ui.nativeapp.device.' + name], check=True)
