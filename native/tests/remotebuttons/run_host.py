#!/usr/bin/env python3
"""Run remote key policy behavior without an Android device."""
from pathlib import Path
import subprocess
import tempfile

here = Path(__file__).resolve().parent
source = here.parents[1] / 'src/com/jeremykenedy/firetv/ui/nativeapp/device/remotebuttons'
with tempfile.TemporaryDirectory(prefix='remote-buttons-') as work:
    subprocess.run(['javac', '-d', work, str(source / 'RemoteButtonPolicy.java'),
                    str(here / 'RemoteButtonPolicyTest.java')], check=True)
    subprocess.run(['java', '-cp', work,
                    'com.jeremykenedy.firetv.ui.nativeapp.device.remotebuttons.RemoteButtonPolicyTest'], check=True)
