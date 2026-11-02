#!/usr/bin/env python3
"""Compile optional All apps source and fixture in a private temporary directory."""
from pathlib import Path
import subprocess
import tempfile

here = Path(__file__).resolve().parent
source = here.parents[1] / 'src/com/jeremykenedy/firetv/ui/nativeapp'
files = [*sorted((source / 'allapps').glob('*.java')), *sorted((source / 'locks').glob('*.java')),
         source / 'Pages.java', source / 'appgrid/AppInventory.java', source / 'appgrid/AppGridOptions.java',
         source / 'appgrid/AppGridPreferences.java', here / 'fixture/OrdinaryActivity.java']
android = Path.home() / 'Library/Android/sdk/platforms/android-36/android.jar'
with tempfile.TemporaryDirectory(prefix='fire-tv-allapps-android-') as work:
    subprocess.run(['javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', android,
                    '-sourcepath', source.parents[4], '-d', work, *files], check=True)
print('All apps Activity, control, inventory and fixture compile passed')
