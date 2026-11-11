#!/usr/bin/env python3
"""Build disposable actual-service fixtures using an existing review candidate signer."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
from zipfile import ZipFile

HERE = Path(__file__).resolve().parent
NATIVE = HERE.parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--candidate-key', type=Path, required=True)
parser.add_argument('--output-dir', type=Path, required=True)
args = parser.parse_args()
if not args.candidate_key.is_file():
    raise ValueError('Use the already built isolated candidate review key.')
sdk = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or Path.home() / 'Library/Android/sdk')
tools = sdk / 'build-tools/36.0.0'
android = sdk / 'platforms/android-36/android.jar'
work = args.output_dir.resolve()
work.mkdir(parents=True, exist_ok=True)

def run(*values):
    subprocess.run([str(value) for value in values], check=True)

def package(name, manifest, classes=None):
    folder = work / name
    folder.mkdir(exist_ok=True)
    manifest_path = folder / 'AndroidManifest.xml'
    manifest_path.write_text(manifest)
    run(tools / 'aapt2', 'link', '-I', android, '--manifest', manifest_path, '-o', folder / 'base.apk')
    if classes:
        dex = folder / 'dex'
        dex.mkdir(exist_ok=True)
        run(tools / 'd8', '--release', '--lib', android, '--min-api', '27', '--output', dex, *classes)
        with ZipFile(folder / 'base.apk', 'a') as out:
            out.write(dex / 'classes.dex', 'classes.dex')
    run(tools / 'zipalign', '-P', '16', '-f', '4', folder / 'base.apk', folder / 'aligned.apk')
    apk = work / (name + '.apk')
    run(tools / 'apksigner', 'sign', '--ks', args.candidate_key, '--ks-pass', 'pass:android',
        '--ks-key-alias', 'candidate-review', '--v4-signing-enabled', 'false', '--out', apk, folder / 'aligned.apk')
    run(tools / 'apksigner', 'verify', apk)
    return apk

common = '<uses-sdk android:minSdkVersion="27" android:targetSdkVersion="36" />'
for pkg, name in [('com.amazon.device.software.ota', 'inert-ota'), ('com.amazon.venezia', 'inert-store')]:
    package(name, '<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="' + pkg + '">' + common +
        '<application android:hasCode="false" android:allowBackup="false" /></manifest>')
seed_source = HERE / 'SeedApplication.java'
seed_hash = hashlib.sha256(seed_source.read_bytes()).hexdigest()
seed_classes = work / 'seed-classes'
seed_classes.mkdir(exist_ok=True)
run('javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', android, '-d', seed_classes, seed_source)
package('provider-free-seed', '<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.jeremykenedy.firetv.ui" android:versionCode="1" android:versionName="0.1.0-source-candidate">' + common +
    '<application android:name="com.jeremykenedy.firetv.updatecontinuityproof.SeedApplication" android:debuggable="true" android:allowBackup="false" /></manifest>', list(seed_classes.rglob('*.class')))
classes = work / 'classes'
if classes.exists():
    shutil.rmtree(classes)
classes.mkdir()
updates = NATIVE / 'src/com/jeremykenedy/firetv/ui/nativeapp/device/updates'
sources = [HERE / 'ContinuityInstrumentation.java', HERE / 'SeedInstrumentation.java', HERE / 'CompetingVpn.java', HERE / 'CompetingActivity.java',
           NATIVE / 'tests/state_guard/GuardStore.java', NATIVE / 'tests/state_guard/StateGuardInstrumentation.java',
           NATIVE / 'src/com/jeremykenedy/firetv/ui/nativeapp/SourceStartup.java']
sources += [updates / name for name in ['UpdateBlockService.java', 'UpdateBlockPolicy.java', 'UpdateBlockStatus.java', 'UpdateBlockStarts.java']]
frozen = {str(path): hashlib.sha256(path.read_bytes()).hexdigest() for path in sources + [Path(__file__)]}
run('javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', android, '-d', classes, *sources)
fixture_classes = sorted((classes / 'com/jeremykenedy/firetv/updatecontinuityproof').rglob('*.class'))
fixture_classes += sorted((classes / 'com/jeremykenedy/firetv/ui/nativeapp/stateguard').rglob('*.class'))
manifest = '<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.jeremykenedy.firetv.updatecontinuityproof">' + common + """
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<application android:label="VPN continuity fixture" android:allowBackup="false" android:theme="@android:style/Theme.Material.NoActionBar">
<activity android:name=".CompetingActivity" android:exported="true" />
<service android:name=".CompetingVpn" android:exported="false" android:permission="android.permission.BIND_VPN_SERVICE" android:foregroundServiceType="specialUse">
<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="Disposable VPN ownership verification" />
<intent-filter><action android:name="android.net.VpnService" /></intent-filter></service>
</application>
<instrumentation android:name=".SeedInstrumentation" android:targetPackage="com.jeremykenedy.firetv.ui" />
<instrumentation android:name=".ContinuityInstrumentation" android:targetPackage="com.jeremykenedy.firetv.ui" />
<instrumentation android:name="com.jeremykenedy.firetv.ui.nativeapp.stateguard.StateGuardInstrumentation" android:targetPackage="com.jeremykenedy.firetv.ui" />
</manifest>"""
package('continuity-proof', manifest, fixture_classes)
inputs = sources + [Path(__file__)]
if seed_hash != hashlib.sha256(seed_source.read_bytes()).hexdigest():
    raise RuntimeError('Seed input changed during compilation.')
if frozen != {str(path): hashlib.sha256(path.read_bytes()).hexdigest() for path in inputs}:
    raise RuntimeError('Fixture inputs changed during compilation.')
(work / 'fixture-build.json').write_text(json.dumps({'networkPermission': False, 'signer': 'existing isolated candidate review key',
    'sourceInputs': {str(path.relative_to(NATIVE)): hashlib.sha256(path.read_bytes()).hexdigest() for path in inputs + [seed_source]},
    'apks': {path.name: hashlib.sha256(path.read_bytes()).hexdigest() for path in work.glob('*.apk')}}, indent=2) + '\n')
