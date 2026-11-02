#!/usr/bin/env python3
"""Run deterministic read-only status presentation checks without a device."""
from pathlib import Path
import subprocess
import tempfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
with tempfile.TemporaryDirectory() as folder:
    subprocess.run(['javac', '-d', folder,
                    ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp/widget/status/StatusReport.java',
                    HERE / 'StatusReportTest.java'], check=True)
    subprocess.run(['java', '-cp', folder,
                    'com.jeremykenedy.firetv.ui.nativeapp.widget.status.StatusReportTest'], check=True)
