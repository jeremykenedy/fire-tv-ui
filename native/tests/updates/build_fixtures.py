#!/usr/bin/env python3
"""Build isolated fixture APKs only. Does not install, contact a device or use production keys."""
from pathlib import Path
from zipfile import ZipFile
import hashlib
import json
import subprocess

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SDK = Path.home() / 'Library/Android/sdk'
TOOLS = SDK / 'build-tools/36.0.0'
ANDROID = SDK / 'platforms/android-36/android.jar'
WORK = HERE / '.fixtures'
PKG = 'com.jeremykenedy.firetv.ui.updatefixture'
SOURCE = ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp'
WORK.mkdir(exist_ok=True)

def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)

for label in ('trusted', 'foreign'):
    key = WORK / (label + '.jks')
    if not key.exists():
        run('keytool', '-genkeypair', '-keystore', key, '-storepass', 'android', '-keypass', 'android',
            '-alias', 'fixture', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '365',
            '-dname', 'CN=Local updater fixture ' + label, '-noprompt')
classes = WORK / 'classes'
classes.mkdir(exist_ok=True)
run('javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', ANDROID,
    '-d', classes, SOURCE / 'Pages.java', *sorted((SOURCE / 'updates').glob('*.java')),
    HERE / 'fixtures/FixtureActivity.java')
dex = WORK / 'dex'
dex.mkdir(exist_ok=True)
run(TOOLS / 'd8', '--release', '--lib', ANDROID, '--min-api', '27', '--output', dex,
    *sorted(classes.rglob('*.class')))

def build(name, version, signer='trusted', package=PKG, assets=None):
    manifest = WORK / (name + '.xml')
    manifest.write_text(f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{package}" android:versionCode="{version}" android:versionName="fixture-{version}">
      <uses-sdk android:minSdkVersion="27" android:targetSdkVersion="36" />
      <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
      <application android:label="Local updater fixture" android:theme="@android:style/Theme.Material.NoActionBar" android:allowBackup="false">
        <activity android:name="com.jeremykenedy.firetv.ui.updatefixture.FixtureActivity" android:exported="true" />
        <provider android:name="com.jeremykenedy.firetv.ui.nativeapp.updates.UpdateFileProvider" android:authorities="{package}.updates" android:exported="false" android:grantUriPermissions="true" />
      </application>
    </manifest>''')
    base = WORK / (name + '-base.apk')
    args = [TOOLS / 'aapt2', 'link', '-I', ANDROID, '--manifest', manifest, '-o', base]
    if assets: args += ['-A', assets]
    run(*args)
    with ZipFile(base, 'a') as output: output.write(dex / 'classes.dex', 'classes.dex')
    aligned = WORK / (name + '-aligned.apk')
    run(TOOLS / 'zipalign', '-P', '16', '-f', '4', base, aligned)
    target = WORK / (name + '.apk')
    run(TOOLS / 'apksigner', 'sign', '--ks', WORK / (signer + '.jks'), '--ks-pass', 'pass:android',
        '--ks-key-alias', 'fixture', '--v4-signing-enabled', 'false', '--out', target, aligned)
    run(TOOLS / 'apksigner', 'verify', target)
    return target

assets = WORK / 'assets'
assets.mkdir(exist_ok=True)
artifacts = {}
for name, version, signer, package in [('upgrade', 2, 'trusted', PKG), ('wrong-signer', 2, 'foreign', PKG),
        ('wrong-package', 2, 'trusted', PKG + '.foreign'), ('older', 1, 'trusted', PKG)]:
    apk = build(name, version, signer, package)
    raw = apk.read_bytes()
    (assets / (name + '.apk')).write_bytes(raw)
    metadata = {'bytes': len(raw), 'sha256': hashlib.sha256(raw).hexdigest()}
    (assets / (name + '.json')).write_text(json.dumps(metadata))
    artifacts[name] = {**metadata, 'package': package, 'version': version, 'signer': signer}
# A valid archive with deliberately false metadata exercises the digest rejection.
(assets / 'wrong-digest.apk').write_bytes((assets / 'upgrade.apk').read_bytes())
(assets / 'wrong-digest.json').write_text(json.dumps({'bytes': artifacts['upgrade']['bytes'], 'sha256': '0' * 64}))
base = build('installed-v1', 1, assets=assets)
artifacts['installed-v1'] = {'sha256': hashlib.sha256(base.read_bytes()).hexdigest(), 'package': PKG, 'version': 1, 'signer': 'trusted'}
(WORK / 'artifacts.json').write_text(json.dumps(artifacts, indent=2) + '\n')
proof_classes = WORK / 'proof-classes'
proof_classes.mkdir(exist_ok=True)
run('javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', ANDROID,
    '-cp', classes, '-d', proof_classes, HERE / 'fixtures/FixtureInstrumentation.java')
proof_dex = WORK / 'proof-dex'
proof_dex.mkdir(exist_ok=True)
run(TOOLS / 'd8', '--release', '--lib', ANDROID, '--min-api', '27', '--output', proof_dex,
    *sorted(proof_classes.rglob('*.class')))
proof_manifest = WORK / 'proof.xml'
proof_manifest.write_text(f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{PKG}.proof">
<uses-sdk android:minSdkVersion="27" android:targetSdkVersion="36" />
<application android:label="Local update fixture verification" />
<instrumentation android:name="{PKG}.FixtureInstrumentation" android:targetPackage="{PKG}" />
</manifest>''')
run(TOOLS / 'aapt2', 'link', '-I', ANDROID, '--manifest', proof_manifest, '-o', WORK / 'proof-base.apk')
with ZipFile(WORK / 'proof-base.apk', 'a') as output: output.write(proof_dex / 'classes.dex', 'classes.dex')
run(TOOLS / 'zipalign', '-P', '16', '-f', '4', WORK / 'proof-base.apk', WORK / 'proof-aligned.apk')
run(TOOLS / 'apksigner', 'sign', '--ks', WORK / 'trusted.jks', '--ks-pass', 'pass:android',
    '--ks-key-alias', 'fixture', '--v4-signing-enabled', 'false', '--out', WORK / 'fixture-proof.apk', WORK / 'proof-aligned.apk')
run(TOOLS / 'apksigner', 'verify', WORK / 'fixture-proof.apk')
print(WORK / 'artifacts.json')
