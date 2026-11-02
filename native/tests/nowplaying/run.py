#!/usr/bin/env python3
"""Check media action policy against actual Android constants without a running device."""
from pathlib import Path
import os
import subprocess
import tempfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SDK = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or Path.home() / 'Library/Android/sdk')
with tempfile.TemporaryDirectory(prefix='nowplaying-host-') as work:
    subprocess.run(['javac', '-cp', SDK / 'platforms/android-36/android.jar', '-d', work,
                    ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp/widget/nowplaying/SessionControls.java',
                    HERE / 'SessionControlsTest.java'], check=True)
    subprocess.run(['java', '-cp', work, 'com.jeremykenedy.firetv.ui.nativeapp.widget.nowplaying.SessionControlsTest'], check=True)
