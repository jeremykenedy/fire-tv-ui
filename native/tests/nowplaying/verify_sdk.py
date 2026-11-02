#!/usr/bin/env python3
"""Compile the real module, native proof and cross-app fixture without touching shared outputs."""
from pathlib import Path
import os
import subprocess
import tempfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SDK = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or Path.home() / 'Library/Android/sdk')
with tempfile.TemporaryDirectory(prefix='nowplaying-sdk-') as work:
    subprocess.run(['javac', '-source', '8', '-target', '8', '-Xlint:-options',
                    '-bootclasspath', SDK / 'platforms/android-36/android.jar', '-cp', ROOT / '.build/classes',
                    '-sourcepath', ROOT / 'src', '-d', work,
                    *sorted((ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp/widget/nowplaying').glob('*.java')),
                    HERE / 'NowPlayingInstrumentation.java', *sorted((HERE / 'fixture').glob('*.java'))], check=True)
print('Now Playing production module, native proof and real MediaSession fixture compile against SDK36.')
