#!/usr/bin/env python3
"""Exercise the real update verifier with injected reviewed metadata and artifact readers."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp/updates'
with tempfile.TemporaryDirectory() as output:
    subprocess.run(['javac', '-d', output, str(SOURCE / 'UpdateCoordinator.java'),
                    str(SOURCE / 'ReleaseTransport.java'), str(SOURCE / 'StagedUpdateStore.java'),
                    str(Path(__file__).with_name('UpdateCoordinatorTest.java'))], check=True)
    subprocess.run(['java', '-cp', output,
                    'com.jeremykenedy.firetv.ui.nativeapp.updates.UpdateCoordinatorTest'], check=True)
