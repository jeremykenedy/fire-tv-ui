#!/usr/bin/env python3
"""Run sanitized Plex fixture regressions and compile the integration without accounts or devices."""
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
REPOSITORY = ROOT.parent
sys.path.insert(0, str(REPOSITORY / 'tests'))
from java_runtime import json_runtime

SOURCE = ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp'
ANDROID = Path.home() / 'Library/Android/sdk/platforms/android-36/android.jar'
PURE = ['PlexTransportPolicy', 'PlexValues', 'PlexLibrary', 'PlexFeedSettings', 'PlexFeedData', 'PlexItemLinks',
        'PlexResources', 'PlexArtworkRedirect', 'PlexHomeUsers', 'PlexSession', 'PlexConnection', 'LegacyPlexCardLookup', 'LegacyPlexArtworkPath']
TESTS = ['PlexLibraryTest', 'PlexFeedDataTest', 'PlexItemLinksTest', 'PlexFeedSettingsTest',
         'PlexResourcesTest', 'PlexArtworkRedirectTest', 'PlexHomeUsersTest', 'PlexAdapterTest', 'PlexProfileConnectionTest', 'PlexSessionTest', 'LegacyPlexCardLookupTest', 'LegacyPlexArtworkPathTest']
with tempfile.TemporaryDirectory(prefix='source-plex-tests-') as directory:
    work = Path(directory)
    runtime = str(json_runtime())
    # Android stubs are only used to resolve method signatures. Fixture tests never invoke storage APIs.
    subprocess.run(['javac', '-cp', runtime + ':' + str(ANDROID), '-d', str(work),
                    *[str(SOURCE / 'providers/plex' / (name + '.java')) for name in PURE],
                    *[str(Path(__file__).parent / (name + '.java')) for name in TESTS]], check=True)
    for name in TESTS:
        subprocess.run(['java', '-cp', str(work) + ':' + runtime + ':' + str(ANDROID),
                        'com.jeremykenedy.firetv.ui.nativeapp.providers.plex.' + name], check=True)
    integration = work / 'integration'
    integration.mkdir()
    files = list((SOURCE / 'providers/plex').glob('*.java')) + [SOURCE / 'providers/discovery' / (name + '.java') for name in ('DiscoveryItem', 'PlexDiscoveryMetadata')] + [
        SOURCE / 'providers/TvRecommendations.java', SOURCE / 'providers/RecommendationActions.java', SOURCE / 'providers/RecommendationKeys.java', SOURCE / 'providers/OwnedPlexIdentity.java', SOURCE / 'providers/NotificationRecommendations.java', SOURCE / 'providers/NotificationRules.java',
        *[SOURCE / 'locks' / (name + '.java') for name in ('LauncherLocks', 'LockStore', 'LockState', 'LockResult')],
        SOURCE / 'layout/LayoutItem.java', SOURCE / 'Pages.java', SOURCE / 'SourceStartup.java', SOURCE / 'MenuActivity.java', SOURCE / 'MenuTransition.java', SOURCE / 'HomeDialogBuilder.java', Path(__file__).parent / 'PlexFixture.java']
    subprocess.run(['javac', '-source', '8', '-target', '8', '-Xlint:-options',
                    '-bootclasspath', str(ANDROID), '-classpath', str(ROOT / '.build/classes'), '-d', str(integration), *map(str, files), *map(str, (SOURCE / 'accessibility').glob('*.java'))], check=True)
    print('Plex isolated integration compilation passed')
