#!/usr/bin/env python3
"""Test durable private-state recovery with sanitized temporary filesystem fixtures."""
from pathlib import Path
import subprocess
import tempfile
HERE = Path(__file__).resolve().parent
with tempfile.TemporaryDirectory(prefix='fire-tv-state-guard-host-') as output:
    subprocess.run(['javac', '-Xlint:all', '-Werror', '-d', output,
                    str(HERE / 'GuardStore.java'), str(HERE / 'GuardStoreTest.java')], check=True)
    subprocess.run(['java', '-cp', output,
                    'com.jeremykenedy.firetv.ui.nativeapp.stateguard.GuardStoreTest'], check=True)
