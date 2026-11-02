#!/usr/bin/env python3
"""Check source-scoped discovery filters with sanitized metadata; compile native integration."""
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT.parent / 'tests'))
from java_runtime import json_runtime
SOURCE = ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp'
ANDROID = Path.home() / 'Library/Android/sdk/platforms/android-36/android.jar'
with tempfile.TemporaryDirectory(prefix='discovery-tests-') as output:
    work = Path(output)
    runtime = str(json_runtime())
    pure = [SOURCE / 'providers/discovery' / (name + '.java') for name in (
        'DiscoveryItem', 'DiscoveryOptions', 'DiscoveryFilter', 'PlexDiscoveryMetadata')]
    subprocess.run(['javac', '-cp', runtime, '-d', str(work), *map(str, pure),
                    str(Path(__file__).parent / 'DiscoveryTest.java')], check=True)
    subprocess.run(['java', '-cp', str(work) + ':' + runtime,
                    'com.jeremykenedy.firetv.ui.nativeapp.providers.discovery.DiscoveryTest'], check=True)
    integration = work / 'integration'; integration.mkdir()
    files = list((SOURCE / 'providers').rglob('*.java')) + list((SOURCE / 'locks').glob('*.java')) + [SOURCE / file for file in (
        'migration/PresentationMappingContext.java', 'migration/PrivateMediaReference.java',
        'Pages.java', 'layout/LayoutItem.java', 'layout/LayoutOptions.java', 'layout/LayoutPreferences.java',
        'cards/CardOptions.java', 'cards/CardPreferences.java', 'appgrid/AppGridOptions.java', 'appgrid/AppGridPreferences.java')]
    subprocess.run(['javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', str(ANDROID), '-classpath', str(ROOT / '.build/classes'),
                    '-d', str(integration), *map(str, files), *map(str, (SOURCE / 'accessibility').glob('*.java'))], check=True)
    print('Discovery Android integration compilation passed')
