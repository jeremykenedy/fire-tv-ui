#!/usr/bin/env python3
"""Check Home routing behavior and compile only original device sources against Android."""
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp/device'
SDK = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
           or Path.home() / 'Library/Android/sdk')
with tempfile.TemporaryDirectory(prefix='fire-tv-device-') as folder:
    work = Path(folder)
    subprocess.run(['javac', '-d', str(work), str(SOURCE / 'HomeRoutingPolicy.java'),
                    str(ROOT / 'tests/device/HomeRoutingPolicyTest.java')], check=True)
    subprocess.run(['java', '-cp', str(work),
                    'com.jeremykenedy.firetv.ui.nativeapp.device.HomeRoutingPolicyTest'], check=True)
    subprocess.run(['javac', '-encoding', 'UTF-8', '-source', '8', '-target', '8',
                    '-Xlint:-options', '-bootclasspath', str(SDK / 'platforms/android-36/android.jar'),
                    '-d', str(work / 'android'), str(ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp/Pages.java'),
                    *map(str, sorted(SOURCE.glob('*.java'))), str(ROOT / 'tests/device/DeviceInstrumentation.java')], check=True)
    print('Device module Android SDK compilation passed')
