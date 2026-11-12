#!/usr/bin/env python3
"""Build the emulator-only card instrumentation against the source preview."""
from pathlib import Path
import subprocess
from zipfile import ZipFile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SDK = Path.home() / 'Library/Android/sdk'
TOOLS = SDK / 'build-tools/36.0.0'
ANDROID = SDK / 'platforms/android-36/android.jar'
WORK = HERE / '.build'
for folder in (WORK, WORK / 'classes', WORK / 'dex'):
    folder.mkdir(parents=True, exist_ok=True)

for folder, suffix in ((WORK / 'classes', '*.class'), (WORK / 'dex', '*.dex')):
    for stale in folder.rglob(suffix):
        stale.unlink()

def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)

run('javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', ANDROID,
    '-sourcepath', str(ROOT / 'src') + ':' + str(ROOT / '.build/gen'), '-implicit:none', '-d', WORK / 'classes', HERE / 'CardControlsInstrumentation.java', HERE / 'CardVisibilityInstrumentation.java', HERE / 'CardHideInstrumentation.java', HERE / 'LegacyHiddenCardsInstrumentation.java', HERE / 'LegacyPlexCardsInstrumentation.java', ROOT / 'tests/allapps/fixture/OrdinaryActivity.java', HERE / 'ChannelRenderInstrumentation.java', HERE / 'RenderArtworkProvider.java', HERE / 'HomeContentInstrumentation.java', HERE / 'PlexCardsInstrumentation.java', HERE / 'DiscoveryInstrumentation.java', ROOT / 'tests/plex/PlexFixture.java')
run(TOOLS / 'd8', '--release', '--lib', ANDROID, '--min-api', '27', '--output', WORK / 'dex',
    *sorted((WORK / 'classes').rglob('*.class')))
run(TOOLS / 'aapt2', 'link', '-I', ANDROID, '--manifest', HERE / 'AndroidManifest.xml', '-o', WORK / 'base.apk')
with ZipFile(WORK / 'base.apk', 'a') as output:
    output.write(WORK / 'dex/classes.dex', 'classes.dex')
run(TOOLS / 'zipalign', '-P', '16', '-f', '4', WORK / 'base.apk', WORK / 'aligned.apk')
run(TOOLS / 'apksigner', 'sign', '--ks', ROOT / '.build/preview.jks', '--ks-pass', 'pass:android',
    '--ks-key-alias', 'preview', '--v4-signing-enabled', 'false', '--out', WORK / 'cards-proof.apk', WORK / 'aligned.apk')
print(WORK / 'cards-proof.apk')

# Reuse the existing recommendation producer with two distinct Android publishers.
import xml.etree.ElementTree as ET
fixture = ROOT / 'tests/providers/fixture'
classes = WORK / 'hide-fixture-classes'
dex = WORK / 'hide-fixture-dex'
classes.mkdir(exist_ok=True)
dex.mkdir(exist_ok=True)
run('javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', ANDROID,
    '-d', classes, *sorted(fixture.glob('*.java')))
run(TOOLS / 'd8', '--release', '--lib', ANDROID, '--min-api', '27', '--output', dex, *sorted(classes.rglob('*.class')))
android = '{http://schemas.android.com/apk/res/android}'
for package, label, name in (
        ('com.jeremykenedy.firetv.ui.providerfixture', 'Recommendation test producer', 'card-hide-producer'),
        ('com.jeremykenedy.firetv.ui.othercardfixture', 'Other recommendation producer', 'card-hide-other')):
    tree = ET.parse(fixture / 'AndroidManifest.xml')
    original = tree.getroot().get('package')
    tree.getroot().set('package', package)
    tree.getroot().find('application').set(android + 'label', label)
    for node in tree.getroot().iter():
        value = node.get(android + 'name', '')
        if value.startswith('.'):
            node.set(android + 'name', original + value)
    manifest = WORK / (name + '-manifest.xml')
    tree.write(manifest, encoding='utf-8', xml_declaration=True)
    base = WORK / (name + '-base.apk')
    aligned = WORK / (name + '-aligned.apk')
    run(TOOLS / 'aapt2', 'link', '-I', ANDROID, '--manifest', manifest, '-o', base)
    with ZipFile(base, 'a') as output:
        output.write(dex / 'classes.dex', 'classes.dex')
    run(TOOLS / 'zipalign', '-P', '16', '-f', '4', base, aligned)
    run(TOOLS / 'apksigner', 'sign', '--ks', ROOT / '.build/preview.jks', '--ks-pass', 'pass:android',
        '--ks-key-alias', 'preview', '--v4-signing-enabled', 'false', '--out', WORK / (name + '.apk'), aligned)
