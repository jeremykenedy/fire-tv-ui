#!/usr/bin/env python3
"""SDK-check the independent ambient module and silent fixture without shared build output."""
import os
from pathlib import Path
import subprocess
import tempfile
ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp'
HERE = Path(__file__).resolve().parent
SDK = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or Path.home() / 'Library/Android/sdk')
with tempfile.TemporaryDirectory(prefix='fire-tv-ambient-sdk-') as output:
    sources = list((JAVA / 'widget/ambient').glob('*.java')) + [JAVA / 'Pages.java', JAVA / 'layout/WidgetProvider.java', JAVA / 'layout/LayoutItem.java', HERE / 'AmbientInstrumentation.java'] + list((HERE / 'fixture').glob('*.java'))
    subprocess.run(['javac', '-source', '8', '-target', '8', '-Xlint:-options', '-encoding', 'UTF-8', '-bootclasspath', str(SDK / 'platforms/android-36/android.jar'), '-d', output, *map(str, sources)], check=True)
    for name in ('AndroidManifest.xml', 'fixture/AndroidManifest.xml'):
        subprocess.run([str(SDK / 'build-tools/36.0.0/aapt2'), 'link', '-I', str(SDK / 'platforms/android-36/android.jar'), '--manifest', str(HERE / name), '-o', str(Path(output) / (name.replace('/', '-') + '.apk'))], check=True)
print('Ambient source, instrumentation, silent provider fixture and manifests compile against API36. No device or shared build changes.')
