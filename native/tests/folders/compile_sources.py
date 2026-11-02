#!/usr/bin/env python3
"""Compile folder UI, instrumentation and direct dependencies into a temporary directory."""
from pathlib import Path
import subprocess
import tempfile

here = Path(__file__).resolve().parent
native = here.parents[1]
source = native / 'src/com/jeremykenedy/firetv/ui/nativeapp'
android = Path.home() / 'Library/Android/sdk/platforms/android-36/android.jar'
files = [*sorted((source / 'folders').glob('*.java')), *sorted((source / 'locks').glob('*.java')),
         source / 'Pages.java', source / 'appgrid/AppInventory.java', here / 'FolderInstrumentation.java']
with tempfile.TemporaryDirectory(prefix='fire-tv-folder-compile-') as work:
    subprocess.run(['javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', str(android),
                    '-d', work, *[str(file) for file in files]], check=True)
print('Folder UI and instrumentation compile passed')
