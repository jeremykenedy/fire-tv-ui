#!/usr/bin/env python3
"""Exercise live guide interval/identity/timezone behavior and compile the isolated widget."""
from pathlib import Path
import subprocess
import tempfile
ROOT = Path(__file__).resolve().parents[2]
MODULE = ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp/widget/livetv'
ANDROID = Path.home() / 'Library/Android/sdk/platforms/android-36/android.jar'
with tempfile.TemporaryDirectory() as folder:
    subprocess.run(['javac', '-d', folder, MODULE / 'LiveGuide.java', Path(__file__).with_name('LiveGuideTest.java')], check=True)
    subprocess.run(['java', '-cp', folder, 'com.jeremykenedy.firetv.ui.nativeapp.widget.livetv.LiveGuideTest'], check=True)
    subprocess.run(['javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', ANDROID,
                    '-sourcepath', ROOT / 'src', '-d', folder, *sorted(MODULE.glob('*.java')),
                    ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp/layout/WidgetProvider.java',
                    ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp/layout/LayoutItem.java',
                    ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp/Pages.java',
                    Path(__file__).with_name('LiveTvInstrumentation.java'),
                    *sorted(Path(__file__).with_name('fixture').glob('*.java'))], check=True)
