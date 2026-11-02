#!/usr/bin/env python3
"""Compile the status module independently of the frozen combined preview."""
from pathlib import Path
import os
import subprocess
import tempfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SOURCE = ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp'
SDK = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
           or Path.home() / 'Library/Android/sdk')
with tempfile.TemporaryDirectory() as folder:
    sources = [SOURCE / 'layout/LayoutItem.java', SOURCE / 'layout/WidgetProvider.java',
               SOURCE / 'device/network/NetworkSnapshot.java', *sorted((SOURCE / 'widget/status').glob('*.java'))]
    sources += sorted(HERE.glob('*Instrumentation.java'))
    subprocess.run(['javac', '-source', '8', '-target', '8', '-Xlint:-options',
                    '-bootclasspath', SDK / 'platforms/android-36/android.jar', '-d', folder, *sources], check=True)
print('Status widget module compiles against SDK36 with the existing network snapshot and actual layout contract.')
