#!/usr/bin/env python3
from pathlib import Path
import subprocess
import tempfile
root = Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory() as folder:
    subprocess.run(['javac', '-d', folder, root / 'src/com/jeremykenedy/firetv/ui/nativeapp/appgrid/AppGridOptions.java', root / 'tests/appgrid/AppGridTest.java'], check=True)
    subprocess.run(['java', '-cp', folder, 'com.jeremykenedy.firetv.ui.nativeapp.appgrid.AppGridTest'], check=True)
