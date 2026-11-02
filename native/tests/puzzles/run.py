#!/usr/bin/env python3
"""Validate original daily boards, then compile native puzzle source in temporary output."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp'
ANDROID = Path.home() / 'Library/Android/sdk/platforms/android-36/android.jar'
with tempfile.TemporaryDirectory(prefix='daily-puzzles-') as folder:
    subprocess.run(['javac', '-d', folder, SOURCE / 'widget/puzzles/DailyPuzzle.java',
                    ROOT / 'tests/puzzles/DailyPuzzleTest.java'], check=True)
    subprocess.run(['java', '-cp', folder, 'com.jeremykenedy.firetv.ui.nativeapp.widget.puzzles.DailyPuzzleTest'], check=True)
    subprocess.run(['javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', ANDROID,
                    '-d', folder, SOURCE / 'layout/WidgetProvider.java', SOURCE / 'layout/LayoutItem.java',
                    SOURCE / 'layout/LayoutOptions.java', SOURCE / 'layout/LayoutPreferences.java',
                    *sorted((SOURCE / 'widget/puzzles').glob('*.java')),
                    ROOT / 'tests/puzzles/PuzzleInstrumentation.java'], check=True)
