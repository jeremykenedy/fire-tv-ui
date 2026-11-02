#!/usr/bin/env python3
"""Verify year calculations and compile the Android widget without device changes."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp'
WIDGET = SOURCE / 'widget/yearprogress'
ANDROID = Path.home() / 'Library/Android/sdk/platforms/android-36/android.jar'
with tempfile.TemporaryDirectory(prefix='year-progress-') as work:
    subprocess.run(['javac', '-d', work, WIDGET / 'YearProgress.java', Path(__file__).with_name('YearProgressTest.java')], check=True)
    subprocess.run(['java', '-cp', work, 'com.jeremykenedy.firetv.ui.nativeapp.widget.yearprogress.YearProgressTest'], check=True)
    subprocess.run(['javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', ANDROID,
                    '-d', work, SOURCE / 'layout/WidgetProvider.java', SOURCE / 'layout/LayoutItem.java',
                    *sorted(WIDGET.glob('*.java'))], check=True)
    print('Year progress Android API36 compilation passed.')
