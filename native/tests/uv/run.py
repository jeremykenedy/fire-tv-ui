#!/usr/bin/env python3
"""Run bounded UV parsing/transport/cache tests without network requests or device access."""
from pathlib import Path
import subprocess
import sys
import tempfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(ROOT.parent / 'tests'))
from java_runtime import json_runtime

runtime = json_runtime()
SOURCE = ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp/widget/uv'
with tempfile.TemporaryDirectory() as folder:
    subprocess.run(['javac', '-cp', runtime, '-d', folder,
                    *[SOURCE / name for name in ('UvForecast.java', 'UvHttp.java', 'UvRepository.java', 'UvRequest.java')],
                    HERE / 'UvTest.java'], check=True)
    subprocess.run(['java', '-cp', str(folder) + ':' + str(runtime),
                    'com.jeremykenedy.firetv.ui.nativeapp.widget.uv.UvTest', HERE / 'epa-97201-historical.json'], check=True)
