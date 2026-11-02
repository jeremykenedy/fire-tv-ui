#!/usr/bin/env python3
"""Build a private synthetic Plex route fixture with no internet permission."""
from pathlib import Path
import hashlib
import json
import os
import shutil
import subprocess
from zipfile import ZipFile

here = Path(__file__).resolve().parent
native = here.parents[2]
sdk = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or Path.home() / 'Library/Android/sdk')
tools, android = sdk / 'build-tools/36.0.0', sdk / 'platforms/android-36/android.jar'
work = here / '.build'
for folder in (work / 'classes', work / 'dex'):
    if folder.exists(): shutil.rmtree(folder)
    folder.mkdir(parents=True)
source = native / 'src/com/jeremykenedy/firetv/ui/nativeapp'
plex = source / 'providers/plex'
names = ('LegacyPlexCardActivity', 'LegacyPlexCardLookup', 'LegacyPlexArtworkProvider', 'LegacyPlexArtworkPath', 'PlexConnection', 'PlexLibrary', 'PlexItemLinks',
         'PlexFeedSettings', 'PlexValues', 'PlexTransportPolicy', 'PlexApi', 'PlexKeys', 'PlexSession',
         'PlexResources', 'PlexHomeUsers', 'PlexArtworkRedirect')
sources = [plex / (name + '.java') for name in names] + [source / 'Pages.java', source / 'SourceStartup.java', source / 'providers/RecommendationActions.java', source / 'providers/TvRecommendations.java', source / 'providers/RecommendationKeys.java', source / 'providers/NotificationRecommendations.java', source / 'providers/NotificationRules.java', source / 'providers/OwnedPlexIdentity.java', source / 'providers/plex/PlexRepository.java', source / 'providers/plex/PlexFeedData.java', source / 'providers/plex/PlexArtworkProvider.java', source / 'layout/LayoutItem.java', source / 'locks/LauncherLocks.java', source / 'locks/LockStore.java', source / 'locks/LockState.java', source / 'locks/LockResult.java',
    *sorted((source / 'accessibility').glob('*.java')), here / 'LegacyPlexRouteInstrumentation.java', here / 'RoutePlexFixture.java', here / 'CoverActivity.java']
def run(*args): subprocess.run([str(value) for value in args], check=True)
run('javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', android, '-d', work / 'classes', *sources)
run(tools / 'd8', '--release', '--lib', android, '--min-api', '27', '--output', work / 'dex', *sorted((work / 'classes').rglob('*.class')))
run(tools / 'aapt2', 'link', '-I', android, '--manifest', here / 'AndroidManifest.xml', '-o', work / 'base.apk')
with ZipFile(work / 'base.apk', 'a') as apk: apk.write(work / 'dex/classes.dex', 'classes.dex')
run(tools / 'zipalign', '-P', '16', '-f', '4', work / 'base.apk', work / 'aligned.apk')
key = work / 'fixture.jks'
if not key.exists(): run('keytool', '-genkeypair', '-keystore', key, '-storepass', 'android', '-keypass', 'android', '-alias', 'fixture', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '3650', '-dname', 'CN=Isolated Plex Route Fixture', '-noprompt')
apk = work / 'plex-route-proof.apk'
run(tools / 'apksigner', 'sign', '--ks', key, '--ks-pass', 'pass:android', '--ks-key-alias', 'fixture', '--v4-signing-enabled', 'false', '--out', apk, work / 'aligned.apk')
run(tools / 'apksigner', 'verify', apk)
(work / 'proof.json').write_text(json.dumps({'package': 'com.jeremykenedy.firetv.plexrouteproof', 'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(), 'nativeExecution': 'not run', 'networkPermission': True, 'networkScope': 'fixture-bound IPv4 loopback only'}, indent=2) + '\n')
print(apk)

# A separate inert receiver proves an actual explicit launch without a Plex account.
fake_classes, fake_dex = work / 'fake-classes', work / 'fake-dex'
for folder in (fake_classes, fake_dex):
    if folder.exists(): shutil.rmtree(folder)
    folder.mkdir(parents=True)
run('javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', android, '-d', fake_classes, here / 'FakePlexActivity.java')
run(tools / 'd8', '--release', '--lib', android, '--min-api', '27', '--output', fake_dex, *sorted(fake_classes.rglob('*.class')))
run(tools / 'aapt2', 'link', '-I', android, '--manifest', here / 'FakePlexManifest.xml', '-o', work / 'fake-base.apk')
with ZipFile(work / 'fake-base.apk', 'a') as archive: archive.write(fake_dex / 'classes.dex', 'classes.dex')
run(tools / 'zipalign', '-P', '16', '-f', '4', work / 'fake-base.apk', work / 'fake-aligned.apk')
fake = work / 'fake-plex-handler.apk'
run(tools / 'apksigner', 'sign', '--ks', key, '--ks-pass', 'pass:android', '--ks-key-alias', 'fixture', '--v4-signing-enabled', 'false', '--out', fake, work / 'fake-aligned.apk')
run(tools / 'apksigner', 'verify', fake)
print(fake, hashlib.sha256(fake.read_bytes()).hexdigest())
