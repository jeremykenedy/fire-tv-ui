#!/usr/bin/env python3
from pathlib import Path
import subprocess
import tempfile
root = Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory() as folder:
    subprocess.run(['javac', '-d', folder, root / 'src/com/jeremykenedy/firetv/ui/nativeapp/inputs/InputEntry.java', root / 'tests/inputs/InputTest.java'], check=True)
    subprocess.run(['java', '-cp', folder, 'com.jeremykenedy.firetv.ui.nativeapp.inputs.InputTest'], check=True)
