#!/usr/bin/env python3
"""SDK-check backup UI/instrumentation using isolated real resource generation, without an APK."""
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SDK = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
           or Path.home() / 'Library/Android/sdk')
ANDROID = SDK / 'platforms/android-36/android.jar'
AAPT = SDK / 'build-tools/36.0.0/aapt2'
with tempfile.TemporaryDirectory(prefix='fire-tv-backup-sdk-') as directory:
    out = Path(directory)
    (out / 'gen').mkdir()
    subprocess.run([str(AAPT), 'compile', '--dir', str(ROOT / 'res'), '-o', str(out / 'res.zip')], check=True)
    subprocess.run([str(AAPT), 'link', '-o', str(out / 'resources.apk'), '-I', str(ANDROID),
                    '--manifest', str(ROOT / 'AndroidManifest.xml'), '--min-sdk-version', '27',
                    '--target-sdk-version', '36', '--java', str(out / 'gen'), str(out / 'res.zip')], check=True)
    sources = sorted((ROOT / 'src').rglob('*.java')) + sorted((out / 'gen').rglob('*.java'))
    subprocess.run(['javac', '-source', '8', '-target', '8', '-Xlint:-options', '-encoding', 'UTF-8',
                    '-bootclasspath', str(ANDROID), '-d', str(out / 'classes'), *map(str, sources),
                    str(Path(__file__).with_name('BackupInstrumentation.java')),
                    str(Path(__file__).with_name('BackupFixtureProvider.java'))], check=True)
print('Backup activity and instrumentation SDK compilation passed with real generated resources. No APK or device changes.')
