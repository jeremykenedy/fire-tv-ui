#!/usr/bin/env python3
"""Run original folder identity and membership behavior checks without Android."""
from pathlib import Path
import subprocess
import tempfile

here = Path(__file__).resolve().parent
native = here.parents[1]
source = native / 'src/com/jeremykenedy/firetv/ui/nativeapp/folders/FolderOptions.java'
with tempfile.TemporaryDirectory(prefix='fire-tv-folders-') as work:
    subprocess.run(['javac', '-d', work, str(source), str(here / 'FolderOptionsTest.java')], check=True)
    subprocess.run(['java', '-cp', work, 'com.jeremykenedy.firetv.ui.nativeapp.folders.FolderOptionsTest'], check=True)
