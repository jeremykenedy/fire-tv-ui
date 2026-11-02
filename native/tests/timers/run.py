#!/usr/bin/env python3
"""Run local timezone, elapsed timer and restart behavior checks in isolated output."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp'
ANDROID = Path.home() / 'Library/Android/sdk/platforms/android-36/android.jar'
with tempfile.TemporaryDirectory(prefix='local-timers-') as folder:
    subprocess.run(['javac', '-d', folder, SOURCE / 'widget/timers/CountdownEntry.java',
                    SOURCE / 'widget/timers/TimerState.java', ROOT / 'tests/timers/LocalTimerTest.java'], check=True)
    subprocess.run(['java', '-cp', folder, 'com.jeremykenedy.firetv.ui.nativeapp.widget.timers.LocalTimerTest'], check=True)
    subprocess.run(['javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', ANDROID,
                    '-d', folder, SOURCE / 'layout/WidgetProvider.java', SOURCE / 'layout/LayoutItem.java',
                    SOURCE / 'layout/LayoutOptions.java', SOURCE / 'layout/LayoutPreferences.java',
                    *sorted((SOURCE / 'widget/timers').glob('*.java')),
                    ROOT / 'tests/timers/TimerInstrumentation.java'], check=True)
