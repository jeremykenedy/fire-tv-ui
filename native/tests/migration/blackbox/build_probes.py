#!/usr/bin/env python3
"""Build original permission-free app fixtures. Does not install, launch or inspect devices."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
from zipfile import ZipFile

HERE = Path(__file__).resolve().parent
SDK = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
           or Path.home() / 'Library/Android/sdk')
TOOLS = SDK / 'build-tools/36.0.0'
ANDROID = SDK / 'platforms/android-36/android.jar'
WORK = HERE / '.build'
WORK.mkdir(exist_ok=True)


def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)


key = WORK / 'probe.jks'
if not key.exists():
    run('keytool', '-genkeypair', '-keystore', key, '-storepass', 'android', '-keypass', 'android',
        '-alias', 'probe', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '3650',
        '-dname', 'CN=Disposable Migration Probe', '-noprompt')

proof = {'deviceAccess': False, 'installed': False, 'fixtures': []}
for name, color, aliases in (('alpha', '#d64258', ('Main', 'Alternate')), ('beta', '#2688d4', ('Main',))):
    folder = WORK / name
    if folder.exists():
        shutil.rmtree(folder)
    for child in ('classes', 'dex', 'res/drawable'):
        (folder / child).mkdir(parents=True, exist_ok=True)
    package = 'com.jeremykenedy.firetv.migrationprobe.' + name
    label = 'Probe ' + name.title()
    activities = ''.join(f'''
        <activity-alias android:name=".{alias}" android:targetActivity="com.jeremykenedy.firetv.migrationprobe.ProbeActivity"
            android:exported="true" android:label="{label}{' Alternate' if alias == 'Alternate' else ''}" android:icon="@drawable/glyph" android:banner="@drawable/glyph">
            <intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LEANBACK_LAUNCHER"/></intent-filter>
        </activity-alias>''' for alias in aliases)
    manifest = folder / 'AndroidManifest.xml'
    manifest.write_text(f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{package}" android:versionCode="1" android:versionName="1.0-probe">
    <uses-sdk android:minSdkVersion="27" android:targetSdkVersion="36"/>
    <uses-feature android:name="android.software.leanback" android:required="true"/>
    <uses-feature android:name="android.hardware.touchscreen" android:required="false"/>
    <application android:label="{label}" android:icon="@drawable/glyph" android:banner="@drawable/glyph" android:allowBackup="false">
        <activity android:name="com.jeremykenedy.firetv.migrationprobe.ProbeActivity" android:exported="false"/>{activities}
    </application>
</manifest>\n''')
    (folder / 'res/drawable/glyph.xml').write_text(f'''<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="320dp" android:height="180dp" android:viewportWidth="320" android:viewportHeight="180">
    <path android:fillColor="{color}" android:pathData="M160,30 L220,90 L160,150 L100,90 Z"/>
</vector>\n''')
    run('javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', ANDROID,
        '-d', folder / 'classes', HERE / 'ProbeActivity.java')
    run(TOOLS / 'd8', '--release', '--lib', ANDROID, '--min-api', '27', '--output', folder / 'dex',
        *sorted((folder / 'classes').rglob('*.class')))
    run(TOOLS / 'aapt2', 'compile', '--dir', folder / 'res', '-o', folder / 'resources.zip')
    run(TOOLS / 'aapt2', 'link', '-I', ANDROID, '--manifest', manifest, '-o', folder / 'base.apk', folder / 'resources.zip')
    with ZipFile(folder / 'base.apk', 'a') as output:
        output.write(folder / 'dex/classes.dex', 'classes.dex')
    run(TOOLS / 'zipalign', '-P', '16', '-f', '4', folder / 'base.apk', folder / 'aligned.apk')
    apk = WORK / ('probe-' + name + '.apk')
    run(TOOLS / 'apksigner', 'sign', '--ks', key, '--ks-pass', 'pass:android', '--ks-key-alias', 'probe',
        '--v4-signing-enabled', 'false', '--out', apk, folder / 'aligned.apk')
    run(TOOLS / 'apksigner', 'verify', apk)
    proof['fixtures'].append({'package': package, 'label': label,
                             'components': [package + '/' + package + '.' + alias for alias in aliases],
                             'sha256': hashlib.sha256(apk.read_bytes()).hexdigest()})
(WORK / 'probe-build.json').write_text(json.dumps(proof, indent=2) + '\n')
print(WORK / 'probe-build.json')
