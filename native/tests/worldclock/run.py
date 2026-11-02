#!/usr/bin/env python3
"""Run deterministic timezone and bounded clock-state checks without a device."""
from pathlib import Path
import subprocess
import tempfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
with tempfile.TemporaryDirectory() as folder:
    subprocess.run(['javac', '-d', folder,
                    ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp/widget/worldclock/WorldClocks.java',
                    HERE / 'WorldClocksTest.java'], check=True)
    subprocess.run(['java', '-cp', folder,
                    'com.jeremykenedy.firetv.ui.nativeapp.widget.worldclock.WorldClocksTest'], check=True)
