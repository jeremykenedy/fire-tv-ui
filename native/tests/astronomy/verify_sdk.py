#!/usr/bin/env python3
"""Compile owned widget and instrumentation in an isolated directory without a shared APK build."""
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
    work = Path(folder)
    subprocess.run(['javac', '-source', '8', '-target', '8', '-Xlint:-options',
                    '-bootclasspath', SDK / 'platforms/android-36/android.jar', '-d', work,
                    SOURCE / 'layout/LayoutItem.java', SOURCE / 'layout/WidgetProvider.java',
                    *sorted((SOURCE / 'widget/astronomy').glob('*.java')),
                    HERE / 'AstronomyInstrumentation.java'], check=True)
print('Astronomy provider, view, settings, model, preferences and native instrumentation compile against Android SDK 36.')
