#!/usr/bin/env python3
"""Compile the protection lane in isolation and check sleep status formatting."""
from pathlib import Path
import subprocess
import tempfile

HERE = Path(__file__).resolve().parent
NATIVE = HERE.parents[1]
SDK = Path.home() / 'Library/Android/sdk'
ANDROID = SDK / 'platforms/android-36/android.jar'
SOURCE = NATIVE / 'src/com/jeremykenedy/firetv/ui/nativeapp'
with tempfile.TemporaryDirectory(prefix='fire-tv-protection-') as target:
    sources = [SOURCE / 'Pages.java', *sorted((SOURCE / 'device/protection').glob('*.java')),
               *sorted((SOURCE / 'device').glob('*.java')), HERE / 'ProtectionTest.java', HERE / 'LegacyDeviceProtectionTest.java']
    subprocess.run(['javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath',
                    str(ANDROID), '-sourcepath', str(NATIVE / 'src'), '-cp', str(NATIVE / '.build/classes'), '-d', target,
                    *map(str, sources)], check=True)
    subprocess.run(['java', '-cp', target + ':' + str(ANDROID),
                    'com.jeremykenedy.firetv.ui.nativeapp.device.protection.ProtectionTest'], check=True)

    subprocess.run(['java', '-cp', target + ':' + str(ANDROID),
                    'com.jeremykenedy.firetv.ui.nativeapp.device.protection.LegacyDeviceProtectionTest'], check=True)
