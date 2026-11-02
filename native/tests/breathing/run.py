#!/usr/bin/env python3
"""Check paced phases and lifecycle math, then compile native views in temporary output."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp'
ANDROID = Path.home() / 'Library/Android/sdk/platforms/android-36/android.jar'
with tempfile.TemporaryDirectory(prefix='breathing-widget-') as folder:
    subprocess.run(['javac', '-d', folder, SOURCE / 'widget/breathing/BreathingSession.java',
                    ROOT / 'tests/breathing/BreathingSessionTest.java'], check=True)
    subprocess.run(['java', '-cp', folder, 'com.jeremykenedy.firetv.ui.nativeapp.widget.breathing.BreathingSessionTest'], check=True)
    subprocess.run(['javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', ANDROID,
                    '-d', folder, SOURCE / 'HomeDialogBuilder.java', SOURCE / 'layout/WidgetProvider.java', SOURCE / 'layout/LayoutItem.java',
                    SOURCE / 'layout/LayoutOptions.java', SOURCE / 'layout/LayoutPreferences.java',
                    *sorted((SOURCE / 'widget/breathing').glob('*.java')),
                    ROOT / 'tests/breathing/BreathingInstrumentation.java'], check=True)
