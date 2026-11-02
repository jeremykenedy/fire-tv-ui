#!/usr/bin/env python3
"""Run isolated host lock regressions and Android SDK compilation. No device access."""
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp'
SDK = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
           or Path.home() / 'Library/Android/sdk')
with tempfile.TemporaryDirectory(prefix='fire-tv-locks-') as directory:
    target = Path(directory)
    subprocess.run(['javac', '-Xlint:all', '-Werror', '-d', str(target),
                    str(SOURCE / 'locks/LockState.java'), str(SOURCE / 'locks/LockResult.java'),
                    str(Path(__file__).with_name('LockTest.java'))], check=True)
    subprocess.run(['java', '-cp', str(target), 'com.jeremykenedy.firetv.ui.nativeapp.locks.LockTest'], check=True)
    subprocess.run(['javac', '-source', '8', '-target', '8', '-Xlint:-options',
                    '-bootclasspath', str(SDK / 'platforms/android-36/android.jar'),
                    '-d', str(target / 'android'), str(SOURCE / 'Pages.java'),
                    *map(str, sorted((SOURCE / 'locks').glob('*.java'))),
                    str(Path(__file__).with_name('LockInstrumentation.java'))], check=True)
print('Locks module compiled against API 36; no app data or shared build outputs accessed.')
