#!/usr/bin/env python3
"""Check private-source constraints and the actual playback-generation/timer policy."""
from pathlib import Path
import subprocess
import tempfile
ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp/widget/ambient'
with tempfile.TemporaryDirectory(prefix='fire-tv-ambient-model-') as output:
    subprocess.run(['javac', '-Xlint:all', '-Werror', '-d', output, str(SOURCE / 'AmbientOptions.java'),
                    str(SOURCE / 'AmbientRun.java'), str(Path(__file__).with_name('AmbientTest.java'))], check=True)
    subprocess.run(['java', '-cp', output, 'com.jeremykenedy.firetv.ui.nativeapp.widget.ambient.AmbientTest'], check=True)
