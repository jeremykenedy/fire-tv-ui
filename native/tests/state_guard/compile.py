#!/usr/bin/env python3
"""SDK-check the standalone private guard without touching target app build outputs."""
import os
from pathlib import Path
import subprocess
import tempfile
HERE = Path(__file__).resolve().parent
SDK = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or Path.home() / 'Library/Android/sdk')
with tempfile.TemporaryDirectory(prefix='fire-tv-state-guard-sdk-') as output:
    subprocess.run(['javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', str(SDK / 'platforms/android-36/android.jar'),
                    '-d', output, str(HERE / 'GuardStore.java'), str(HERE / 'StateGuardInstrumentation.java'), str(HERE / 'GuardProbeInstrumentation.java')], check=True)
    subprocess.run([str(SDK / 'build-tools/36.0.0/aapt2'), 'link', '-I', str(SDK / 'platforms/android-36/android.jar'),
                    '--manifest', str(HERE / 'AndroidManifest.xml'), '-o', str(Path(output) / 'guard-resources.apk')], check=True)
print('Standalone private state guard compiles against API36. No target data or device accessed.')
