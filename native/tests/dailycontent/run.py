#!/usr/bin/env python3
"""Run daily edition behavior checks and compile Android views without touching devices."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp'
ANDROID = Path.home() / 'Library/Android/sdk/platforms/android-36/android.jar'
with tempfile.TemporaryDirectory(prefix='daily-content-') as folder:
    subprocess.run(['javac', '-d', folder, SOURCE / 'widget/dailycontent/DailyContent.java',
                    ROOT / 'tests/dailycontent/DailyContentTest.java'], check=True)
    subprocess.run(['java', '-cp', folder, 'com.jeremykenedy.firetv.ui.nativeapp.widget.dailycontent.DailyContentTest'], check=True)
    subprocess.run(['javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', ANDROID,
                    '-d', folder, SOURCE / 'layout/WidgetProvider.java', SOURCE / 'layout/LayoutItem.java',
                    *sorted((SOURCE / 'widget/dailycontent').glob('*.java')),
                    ROOT / 'tests/dailycontent/DailyContentInstrumentation.java'], check=True)
